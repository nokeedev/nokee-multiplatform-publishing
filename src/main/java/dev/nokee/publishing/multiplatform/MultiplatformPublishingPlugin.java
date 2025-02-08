package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.backports.DependencyFactory;
import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.*;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.Publication;
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
	private final TaskContainer tasks;

	@Inject
	public MultiplatformPublishingPlugin(TaskContainer tasks) {
		this.tasks = tasks;
	}

	@Override
	public void apply(Project project) {
		project.getPluginManager().apply(PublishingPlugin.class); // because we are a publishing plugin

		MultiplatformPublishingExtension extension = project.getExtensions().create("multiplatform", MultiplatformPublishingExtension.class);

		project.getPluginManager().withPlugin("maven-publish", ignored(() -> {
			project.getPluginManager().apply(MavenMultiplatformPublishingPlugin.class);
		}));
		project.getPluginManager().withPlugin("ivy-publish", ignored(() -> {
			project.getPluginManager().apply(IvyMultiplatformPublishingPlugin.class);
		}));

		extension.getPublications().withType(new TypeOf<AbstractMultiplatformPublication<? extends Publication>>() {}.getConcreteClass()).configureEach(publication -> {
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
					}));
					tasks.withType(PublishToMavenLocal.class).configureEach(publishTasks(platformPublication, task -> {
						task.doFirst("", ignored(new Runnable() {
							@Override
							public void run() {
								task.getPublication().setArtifactId(variantArtifactIds.get(wrap(task.getPublication())));
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
					}));
				}
			});
		});



		// PUBLISH ROOT after variants
		extension.getPublications().withType(new TypeOf<MultiplatformPublication<? extends Publication>>() {}.getConcreteClass()).configureEach(publication -> {
			// Component publication must run after variant publications
			publication.getBridgePublication().configure(publishTasks(project.getTasks(), task -> {
				task.mustRunAfter((Callable<?>) () -> {
					return publication.getPlatformPublications().getElements().get().stream().map(it -> task.getName().replace(capitalize(publication.getBridgePublication().getName()), capitalize(it.getName()))).collect(Collectors.toList());
				});
			}));
		});




		// Complete ROOT module metadata remote variants
		extension.getPublications().withType(new TypeOf<AbstractMultiplatformPublication<? extends Publication>>() {}.getConcreteClass()).configureEach(project.getObjects().newInstance(AbstractMultiplatformPublicationAction.class, project.getResources()));



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

		@Inject
		public AbstractMultiplatformPublicationAction(ObjectFactory objects, TaskContainer tasks, ResourceHandler resources) {
			this.factory = objects.newInstance(DependencyFactory.class);
			this.tasks = tasks;
			this.resources = resources;
		}

		@Override
		public void execute(AbstractMultiplatformPublication<? extends Publication> publication) {
			publication.bridgePublication(bridgePublication -> {
				if (bridgePublication instanceof MavenPublication) {
					tasks.withType(PublishToMavenRepository.class).configureEach(publishTasks(bridgePublication, task -> {
						backup(bridgePublication, task, ignored(() -> {
							task.doFirst("", ignored(generateBridgeMetadata(publication.getPlatforms(), wrap(task.getPublication()), ArtifactPathResolver.forMaven(task.getRepository()))));
						}));
					}));

					tasks.withType(PublishToMavenLocal.class).configureEach(publishTasks(bridgePublication, task -> {
						backup(bridgePublication, task, ignored(() -> {
							task.doFirst("", ignored(generateBridgeMetadata(publication.getPlatforms(), wrap(task.getPublication()), ArtifactPathResolver.forMaven(ProjectBuilder.builder().build().getRepositories().mavenLocal()))));
						}));
					}));
				}

				if (bridgePublication instanceof IvyPublication) {
					tasks.withType(PublishToIvyRepository.class).configureEach(publishTasks(bridgePublication, task -> {
						backup(bridgePublication, task, ignored(() -> {
							task.doFirst("", ignored(generateBridgeMetadata(publication.getPlatforms(), wrap(task.getPublication()), ArtifactPathResolver.forIvy(task.getRepository()))));
						}));
					}));
				}
			});
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

		private Runnable generateBridgeMetadata(Provider<Set<String>> platformNames, MinimalGMVPublication platformPublication, ArtifactPathResolver resolver) {
			return new Runnable() {
				@Override
				public void run() {
					String groupId = platformPublication.getGroup();
					String version = platformPublication.getVersion();
					List<ExternalModuleDependency> variants = platformNames.get().stream().map(it -> factory.create(groupId + ":" + it + ":" + version)).toList();

					File moduleFile = tasks.named(generateMetadataFileTaskName(platformPublication.delegate()), GenerateModuleMetadata.class).get().getOutputFile().get().getAsFile();
					Map<String, Object> origRoot = (Map<String, Object>) new JsonSlurper().parse(moduleFile);
					List<Object> vars = (List<Object>) origRoot.get("variants");

					for (ExternalModuleDependency variant : variants) {
						URI l = resolver.resolve(variant);
						Map<String, Object> root = (Map<String, Object>) new JsonSlurper().parse(resources.getText().fromUri(l).asReader());
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
}
