package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.backports.DependencyFactory;
import dev.nokee.commons.collections.NamedDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.*;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor;
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.testfixtures.ProjectBuilder;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.nokee.commons.names.PublishingTaskNames.*;
import static dev.nokee.commons.provider.CollectionElementTransformer.transformEach;
import static dev.nokee.publishing.multiplatform.MinimalGMVPublication.wrap;
import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;

/*private*/ abstract /*final*/ class MultiplatformPublishingPlugin implements Plugin<Project> {
	private static final Class<AbstractMultiplatformPublication<? extends Publication>> MultiplatformPublicationInternal = new TypeOf<AbstractMultiplatformPublication<? extends Publication>>() {}.getConcreteClass();
	private static Logger LOGGER = Logging.getLogger(MultiplatformPublishingPlugin.class);
	private final ObjectFactory objects;
	private final TaskContainer tasks;

	@Inject
	public MultiplatformPublishingPlugin(ObjectFactory objects, TaskContainer tasks) {
		this.objects = objects;
		this.tasks = tasks;
	}

	@Override
	public void apply(Project project) {
		project.getPluginManager().apply(PublishingPlugin.class); // because we are a publishing plugin

		MultiplatformPublishingExtension extension = project.getExtensions().create("multiplatform", MultiplatformPublishingExtension.class);
		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
		project.getPluginManager().withPlugin("maven-publish", ignored(() -> {
			extension.getPublications().registerFactory(MavenMultiplatformPublication.class, name -> {
				NamedDomainObjectProvider<MavenPublication> bridgePublication = publishing.getPublications().register(name, MavenPublication.class);
				return objects.newInstance(MavenMultiplatformPublication.class, Names.of(name), bridgePublication, new NamedDomainObjectRegistry<>(publishing.getPublications().containerWithType(MavenPublication.class)), publishing.getPublications().withType(MavenPublication.class));
			});
		}));
		project.getPluginManager().withPlugin("ivy-publish", ignored(() -> {
			extension.getPublications().registerFactory(IvyMultiplatformPublication.class, name -> {
				NamedDomainObjectProvider<IvyPublication> bridgePublication = publishing.getPublications().register(name, IvyPublication.class);
				return objects.newInstance(IvyMultiplatformPublication.class, Names.of(name), bridgePublication, new NamedDomainObjectRegistry<>(publishing.getPublications().containerWithType(IvyPublication.class)), publishing.getPublications().withType(IvyPublication.class));
			});
		}));

		extension.getPublications().withType(MultiplatformPublicationInternal).configureEach(publication -> {
			Map<MinimalGMVPublication, String> variantArtifactIds = publication.getModuleNames();
			publication.getPlatformPublications().configureEach(wrap(platformPublication -> {
				variantArtifactIds.put(platformPublication, platformPublication.getModule());
			}));
			publication.getPlatformPublications().whenElementFinalized(wrap(platformPublication -> {
				variantArtifactIds.compute(platformPublication, (k, v) -> {
					assert v != null;
					// no change, use default value
					if (v.equals(k.getModule())) {
						final String variantName = StringGroovyMethods.uncapitalize(platformPublication.getName().substring(publication.getName().length()));
						return publication.getBridgePublication().map(MinimalGMVPublication::wrap).get().getModule() + "_" + variantName;
					}
					return k.getModule(); // use overwritten value
				});

				MinimalGMVPublication mainPublication = publication.getBridgePublication().map(MinimalGMVPublication::wrap).get();
				platformPublication.setModule(mainPublication.getModule());
				platformPublication.setGroup(mainPublication.getGroup());
				platformPublication.setVersion(mainPublication.getVersion());
			}));
			publication.getPlatforms().set(publication.getPlatformPublications().getElements().map(transformEach(wrap(variantArtifactIds::get))));

			publication.getPlatformPublications().configureEach(platformPublication -> {
				// all generate metadata for variant
				project.getTasks().named(generateMetadataFileTaskName(platformPublication), GenerateModuleMetadata.class).configure(task -> {
					//   - doLast, override name to correct artifactId
					task.doLast("", ignored(new Runnable() {
						@Override
						public void run() {
							File out = task.getOutputFile().get().getAsFile();
							Map<String, Object> root = (Map<String, Object>) new JsonSlurper().parse(out);
							Map<String, Object> component = (Map<String, Object>) root.get("component");
							component.put("module", variantArtifactIds.get(wrap(task.getPublication().get())));
							try (Writer writer = Files.newBufferedWriter(out.toPath())) {
								new JsonBuilder(root).writeTo(writer);
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						}
					}));
				});

				if (platformPublication instanceof MavenPublication) {
					// all generate pom for variant
					tasks.withType(GenerateMavenPom.class).configureEach(named(generatePomFileTaskName(platformPublication)::equals, task -> {
						//   - doLast, override artifactId to correct artifact Id
						task.doLast(ignored(new Runnable() {
							@Override
							public void run() {
								try {
									Pattern pattern = Pattern.compile("<artifactId>[^<]+</artifactId>");
									List<String> lines = Files.readAllLines(task.getDestination().toPath()).stream().map(t -> {
										return pattern.matcher(t).replaceFirst("<artifactId>" + variantArtifactIds.get(wrap(platformPublication)) + "</artifactId>");
									}).collect(Collectors.toList());
									Files.write(task.getDestination().toPath(), lines);
								} catch (Throwable e) {
									throw new RuntimeException(e);
								}
							}
						}));
					}));

					tasks.withType(PublishToMavenRepository.class).configureEach(publishTasks(platformPublication, task -> {
						task.doFirst("", ignored(new Runnable() {
							@Override
							public void run() {
								task.getPublication().setArtifactId(variantArtifactIds.get(wrap(task.getPublication())));
							}
						}));
						task.doLast("", ignored(new Runnable() {
							@Override
							public void run() {
								task.getPublication().setArtifactId(publication.getBridgePublication().map(MavenPublication.class::cast).map(MavenPublication::getArtifactId).get());
							}
						}));
					}));
					tasks.withType(PublishToMavenLocal.class).configureEach(publishTasks(platformPublication, task -> {
						task.doFirst("", ignored(new Runnable() {
							@Override
							public void run() {
								task.getPublication().setArtifactId(variantArtifactIds.get(wrap(task.getPublication())));
							}
						}));
						task.doLast("", ignored(new Runnable() {
							@Override
							public void run() {
								task.getPublication().setArtifactId(publication.getBridgePublication().map(MavenPublication.class::cast).map(MavenPublication::getArtifactId).get());
							}
						}));
					}));
				}

				if (platformPublication instanceof IvyPublication) {
					// all generate ivy for variant
					tasks.withType(GenerateIvyDescriptor.class).configureEach(named(generateDescriptorFileTaskName(platformPublication)::equals, task -> {
						//   - doLast, override artifactId to correct artifact Id
						task.doLast(ignored(new Runnable() {
							@Override
							public void run() {
								try {
									Pattern pattern = Pattern.compile(" module=\"[^\"]+\" ");
									List<String> lines = Files.readAllLines(task.getDestination().toPath()).stream().map(t -> {
										return pattern.matcher(t).replaceFirst(" module=\"" + variantArtifactIds.get(wrap(platformPublication)) + "\" ");
									}).collect(Collectors.toList());
									Files.write(task.getDestination().toPath(), lines);
								} catch (Throwable e) {
									throw new RuntimeException(e);
								}
							}
						}));
					}));

					tasks.withType(PublishToIvyRepository.class).configureEach(publishTasks(platformPublication, task -> {
						task.doFirst("", ignored(new Runnable() {
							@Override
							public void run() {
								task.getPublication().setModule(variantArtifactIds.get(wrap(task.getPublication())));
							}
						}));
						task.doLast("", ignored(new Runnable() {
							@Override
							public void run() {
								task.getPublication().setModule(publication.getBridgePublication().map(IvyPublication.class::cast).map(IvyPublication::getModule).get());
							}
						}));
					}));
				}
			});
		});



		// PUBLISH ROOT after variants
		extension.getPublications().withType(MultiplatformPublicationInternal).configureEach(publication -> {
			// Component publication must run after variant publications
			publication.getBridgePublication().configure(publishTasks(project.getTasks(), task -> {
				task.mustRunAfter((Callable<?>) () -> {
					return publication.getPlatformPublications().getElements().get().stream().map(it -> task.getName().replace(capitalize(publication.getBridgePublication().getName()), capitalize(it.getName()))).collect(Collectors.toList());
				});
			}));
		});



		extension.getPublications().withType(MultiplatformPublicationInternal).configureEach(publication -> {
			NamedDomainObjectProvider<Configuration> canaryElements = objects.newInstance(ConfigurationRegistry.class).consumable(Names.of(publication.getName()).append("canaryElements").toString());
			canaryElements.configure(config -> {
				config.setVisible(false);
				config.setDescription("Canary elements for " + publication);
				config.attributes(attributes -> attributes.attribute(Attribute.of("dev.nokee.dummy", String.class), "oui"));
			});

			publication.bridgePublication(bridgePublication -> {
				tasks.named(generateMetadataFileTaskName(bridgePublication), GenerateModuleMetadata.class).configure(task -> {
					task.doLast("", ignored(new Runnable() {
						@Override
						public void run() {
							File out = task.getOutputFile().get().getAsFile();

							@SuppressWarnings("unchecked")
							Map<String, Object> root = (Map<String, Object>) new JsonSlurper().parse(out);
							@SuppressWarnings("unchecked")
							List<Map<String, Object>> var = (List<Map<String, Object>>) root.get("variants");
							if (var.removeIf(t -> t.get("name").equals(canaryElements.getName()))) {
								try (Writer writer = Files.newBufferedWriter(out.toPath())) {
									new JsonBuilder(root).writeTo(writer);
								} catch (IOException e) {
									throw new UncheckedIOException(e);
								}
							}
						}
					}));
				});
			});
			publication.getPlatformPublications().configureEach(platformPublication -> {
				tasks.named(generateMetadataFileTaskName(platformPublication), GenerateModuleMetadata.class).configure(task -> {
					task.doLast("", ignored(new Runnable() {
						@Override
						public void run() {
							File out = task.getOutputFile().get().getAsFile();

							@SuppressWarnings("unchecked")
							Map<String, Object> root = (Map<String, Object>) new JsonSlurper().parse(out);
							@SuppressWarnings("unchecked")
							List<Map<String, Object>> var = (List<Map<String, Object>>) root.get("variants");
							if (var.removeIf(t -> t.get("name").equals(canaryElements.getName()))) {
								try (Writer writer = Files.newBufferedWriter(out.toPath())) {
									new JsonBuilder(root).writeTo(writer);
								} catch (IOException e) {
									throw new UncheckedIOException(e);
								}
							}
						}
					}));
				});
			});
		});


		// Complete ROOT module metadata remote variants
		extension.getPublications().withType(MultiplatformPublicationInternal).configureEach(project.getObjects().newInstance(AbstractMultiplatformPublicationAction.class, project.getResources()));


		project.getExtensions().getExtraProperties().set("forMultiplatform", project.getObjects().newInstance(Closure.class, extension));

		project.afterEvaluate(ignored(() -> {
			extension.getPublications().all(ignored(() -> {}));
		}));
	}

	// TODO: Move to nokee-commons
	private static <T> Action<T> ignored(Runnable runnable) {
		return new Action<T>() {
			@Override
			public void execute(T ignored) {
				runnable.run();
			}
		};
	}

	private static <T extends Named> Action<T> named(Spec<? super String> nameFilter, Action<? super T> action) {
		return it -> {
			if (nameFilter.isSatisfiedBy(it.getName())) {
				action.execute(it);
			}
		};
	}

	private static <T extends Publication, S extends Task> Action<S> publishTasks(T publication, Action<? super S> action) {
		return named(publishPublicationToAnyRepositories(publication), action);
	}

	private static <T extends Publication, S extends Task> Action<T> publishTasks(TaskCollection<S> tasks, Action<? super S> action) {
		return publication -> {
			tasks.configureEach(named(publishPublicationToAnyRepositories(publication), action));
		};
	}

	/*private*/ static abstract /*final*/ class AbstractMultiplatformPublicationAction implements Action<AbstractMultiplatformPublication<? extends Publication>> {
		private final DependencyFactory factory;
		private final TaskContainer tasks;
		private final ResourceHandler resources;
		private final ProviderFactory providers;

		@Inject
		public AbstractMultiplatformPublicationAction(ObjectFactory objects, TaskContainer tasks, ResourceHandler resources, ProviderFactory providers) {
			this.factory = objects.newInstance(DependencyFactory.class);
			this.tasks = tasks;
			this.resources = resources;
			this.providers = providers;
		}

		@Override
		public void execute(AbstractMultiplatformPublication<? extends Publication> publication) {
			publication.bridgePublication(bridgePublication -> {
				if (bridgePublication instanceof MavenPublication) {
					tasks.withType(PublishToMavenRepository.class).configureEach(publishTasks(bridgePublication, task -> {
						task.onlyIf("", allPlatformsPublished(publication.getPlatforms(), providers.provider(task::getPublication).map(MinimalGMVPublication::wrap), providers.provider(task::getRepository).map(ArtifactPathResolver::forMaven)));
						backup(bridgePublication, task, ignored(() -> {
							task.doFirst("", ignored(generateBridgeMetadata(publication.getPlatforms(), providers.provider(task::getPublication).map(MinimalGMVPublication::wrap), providers.provider(task::getRepository).map(ArtifactPathResolver::forMaven))));
						}));
					}));

					tasks.withType(PublishToMavenLocal.class).configureEach(publishTasks(bridgePublication, task -> {
						// We don't skip publishing for MavenLocal as a special case
						backup(bridgePublication, task, ignored(() -> {
							task.doFirst("", ignored(generateBridgeMetadata(publication.getPlatforms(), providers.provider(task::getPublication).map(MinimalGMVPublication::wrap), providers.provider(() -> ProjectBuilder.builder().build().getRepositories().mavenLocal()).map(ArtifactPathResolver::forMaven))));
						}));
					}));
				}

				if (bridgePublication instanceof IvyPublication) {
					tasks.withType(PublishToIvyRepository.class).configureEach(publishTasks(bridgePublication, task -> {
						task.onlyIf("", allPlatformsPublished(publication.getPlatforms(), providers.provider(task::getPublication).map(MinimalGMVPublication::wrap), providers.provider(task::getRepository).map(ArtifactPathResolver::forIvy)));
						backup(bridgePublication, task, ignored(() -> {
							task.doFirst("", ignored(generateBridgeMetadata(publication.getPlatforms(), providers.provider(task::getPublication).map(MinimalGMVPublication::wrap), providers.provider(task::getRepository).map(ArtifactPathResolver::forIvy))));
						}));
					}));
				}
			});
		}

		private Spec<Task> allPlatformsPublished(Provider<Set<String>> platformNames, Provider<MinimalGMVPublication> platformPublication, Provider<ArtifactPathResolver> resolver) {
			return task -> {
				String groupId = platformPublication.get().getGroup();
				String version = platformPublication.get().getVersion();
				List<ExternalModuleDependency> variants = platformNames.get().stream().map(it -> factory.create(groupId + ":" + it + ":" + version)).toList();

				boolean result = true;
				for (ExternalModuleDependency variant : variants) {
					try {
						resources.getText().fromUri(resolver.get().resolve(variant)).asString();
					} catch (MissingResourceException ex) {
						LOGGER.warn(String.format("Warning: Publication with coordinate '%s:%s:%s' not published.", variant.getGroup(), variant.getName(), variant.getVersion()));
						result = false;
					}
				}
				return result;
			};
		}

		private void backup(Publication bridgePublication, Task task, Action<? super Task> action) {
			action.execute(task);

			NamedDomainObjectProvider<GenerateModuleMetadata> moduleMetadataTask = tasks.named(generateMetadataFileTaskName(bridgePublication), GenerateModuleMetadata.class);
			task.doFirst("", ignored(new Runnable() {
				@Override
				public void run() {
					GenerateModuleMetadata task = moduleMetadataTask.get();
					Path moduleFile = task.getOutputFile().get().getAsFile().toPath();
					try {
						Files.copy(moduleFile, moduleFile.getParent().resolve(moduleFile.getFileName() + ".orig"), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
			}));
			task.doLast("", ignored(new Runnable() {
				@Override
				public void run() {
					GenerateModuleMetadata metadataTask = moduleMetadataTask.get();
					Path out = metadataTask.getOutputFile().get().getAsFile().toPath();
					try {
						Files.copy(out.getParent().resolve(out.getFileName() + ".orig"), out, StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
			}));
		}

		private Runnable generateBridgeMetadata(Provider<Set<String>> platformNames, Provider<MinimalGMVPublication> platformPublication, Provider<ArtifactPathResolver> resolver) {
			return new Runnable() {
				@Override
				public void run() {
					String groupId = platformPublication.get().getGroup();
					String version = platformPublication.get().getVersion();
					List<ExternalModuleDependency> variants = platformNames.get().stream().map(it -> factory.create(groupId + ":" + it + ":" + version)).toList();

					File moduleFile = tasks.named(generateMetadataFileTaskName(platformPublication.get().delegate()), GenerateModuleMetadata.class).get().getOutputFile().get().getAsFile();
					@SuppressWarnings("unchecked")
					Map<String, Object> origRoot = (Map<String, Object>) new JsonSlurper().parse(moduleFile);
					@SuppressWarnings("unchecked")
					List<Object> vars = (List<Object>) origRoot.get("variants");

					for (ExternalModuleDependency variant : variants) {
						try {
							URI l = resolver.get().resolve(variant);
							@SuppressWarnings("unchecked")
							Map<String, Object> root = (Map<String, Object>) new JsonSlurper().parse(resources.getText().fromUri(l).asReader());
							@SuppressWarnings("unchecked")
							List<Map<String, Object>> var = (List<Map<String, Object>>) root.get("variants");
							for (Map<String, Object> v : var) {
								Map<String, Object> vv = new LinkedHashMap<>(v);
								vv.remove("dependencies");
								vv.remove("files");
								vv.put("available-at", new LinkedHashMap<String, Object>() {{
									put("url", "../../" + variant.getName() + "/" + variant.getVersion());
									put("group", variant.getGroup());
									put("module", variant.getName());
									put("version", variant.getVersion());
								}});
								vars.add(vv);
							}
						} catch (MissingResourceException ex) {
							LOGGER.warn(String.format("Warning: Publication with coordinate '%s:%s:%s' not found in '...'.", variant.getGroup(), variant.getName(), variant.getVersion()));
						}
					}

					try (Writer writer = Files.newBufferedWriter(moduleFile.toPath())) {
						new JsonBuilder(origRoot).writeTo(writer);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
			};
		}

		private interface ArtifactPathResolver {
			URI resolve(ExternalModuleDependency dependency);

			static ArtifactPathResolver forMaven(MavenArtifactRepository repository) {
				return new ArtifactPathResolver() {
					@Override
					public URI resolve(ExternalModuleDependency dependency) {
						return repository.getUrl().resolve(dependency.getGroup().replace(".", "/") + "/" + dependency.getName() + "/" + dependency.getVersion() + "/" + dependency.getName() + "-" + dependency.getVersion() + ".module");
					}
				};
			}

			static ArtifactPathResolver forIvy(IvyArtifactRepository repository) {
				return new ArtifactPathResolver() {
					@Override
					public URI resolve(ExternalModuleDependency dependency) {
						return repository.getUrl().resolve(dependency.getGroup() + "/" + dependency.getName() + "/" + dependency.getVersion() + "/" + dependency.getName() + "-" + dependency.getVersion() + ".module");
					}
				};
			}
		}
	}

	/*private*/ abstract static /*final*/ class Closure implements ForMultiplatformClosure {
		private final MultiplatformPublishingExtension multiplatform;

		@Inject
		public Closure(MultiplatformPublishingExtension multiplatform) {
			this.multiplatform = multiplatform;
		}

		@Override
		public <T extends Publication> Action<PublicationContainer> call(String name, Class<T> type) {
			return call(name, type, ignored(() -> {}));
		}

		@Override
		public <T extends Publication> Action<PublicationContainer> call(String name, Class<T> type, Action<? super MultiplatformPublication<T>> configureAction) {
			return publications -> {
				final Class<? extends MultiplatformPublication<T>> implementationType = implementationType(type);

				if (!multiplatform.getPublications().getNames().contains(name)) {
					multiplatform.getPublications().register(name, implementationType);
				}

				multiplatform.getPublications().named(name, implementationType, configureAction);
			};
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		private <T extends Publication> Class<? extends MultiplatformPublication<T>> implementationType(Class<? extends Publication> type) {
			if (type.equals(MavenPublication.class)) {
				return (Class) MavenMultiplatformPublication.class;
			} else if (type.equals(IvyPublication.class)) {
				return (Class) IvyMultiplatformPublication.class;
			} else {
				throw new UnsupportedOperationException("Unsupported publication type");
			}
		}

		// TODO: Move to nokee-commons
		private static <T> Action<T> ignored(Runnable runnable) {
			return new Action<T>() {
				@Override
				public void execute(T ignored) {
					runnable.run();
				}
			};
		}
	}
}
