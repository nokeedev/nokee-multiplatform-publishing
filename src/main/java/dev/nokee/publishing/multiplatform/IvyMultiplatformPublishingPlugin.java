package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.NamedDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import dev.nokee.publishing.multiplatform.ivy.IvyMultiplatformPublication;
import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.*;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor;
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskContainer;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.nokee.commons.names.PublishingTaskNames.*;
import static dev.nokee.commons.provider.CollectionElementTransformer.transformEach;
import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;

abstract /*final*/ class IvyMultiplatformPublishingPlugin implements Plugin<Project> {
	private final ObjectFactory objects;
    private final TaskContainer tasks;

    @Inject
	public IvyMultiplatformPublishingPlugin(ObjectFactory objects, TaskContainer tasks) {
		this.objects = objects;
        this.tasks = tasks;
    }

	@Override
	public void apply(Project project) {
		MultiplatformPublishingExtension extension = project.getExtensions().getByType(MultiplatformPublishingExtension.class);
		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
		extension.getPublications().registerFactory(IvyMultiplatformPublication.class, name -> {
			NamedDomainObjectProvider<IvyPublication> rootPublication = publishing.getPublications().register(name, IvyPublication.class);
			return objects.newInstance(DefaultPublication.class, Names.of(name), rootPublication, new NamedDomainObjectRegistry<>(publishing.getPublications().containerWithType(IvyPublication.class)), publishing.getPublications().withType(IvyPublication.class));
		});

		extension.getPublications().withType(DefaultPublication.class).configureEach(publication -> {
			publication.getPlatformPublication().configureEach(platformPublication -> {
				publication.variantModules.put(platformPublication, platformPublication.getModule());
			});
			publication.getPlatformPublication().whenElementFinalized(platformPublication -> {
				publication.variantModules.compute(platformPublication, (k, v) -> {
					assert v != null;
					// no change, use default value
					if (v.equals(k.getModule())) {
						final String variantName = StringGroovyMethods.uncapitalize(platformPublication.getName().substring(publication.getName().length()));
						return publication.getRootPublication().get().getModule() + "_" + variantName;
					}
					return k.getModule(); // use overwritten value
				});

				platformPublication.setModule(publication.getRootPublication().get().getModule());
				platformPublication.setOrganisation(publication.getRootPublication().get().getOrganisation());
				platformPublication.setRevision(publication.getRootPublication().get().getRevision());
			});
			publication.getPlatforms().set(publication.getPlatformPublication().getElements().map(transformEach(publication.variantModules::get)));


			publication.getPlatformPublication().configureEach(platformPublication -> {
				// all generate metadata for variant
				project.getTasks().named(generateMetadataFileTaskName(platformPublication), GenerateModuleMetadata.class).configure(task -> {
					//   - doLast, override name to correct artifactId
					task.doLast("", ignored(new Runnable() {
						@Override
						public void run() {
							File out = task.getOutputFile().get().getAsFile();
							Map<String, Object> root = (Map<String, Object>) new JsonSlurper().parse(out);
							Map<String, Object> component = (Map<String, Object>) root.get("component");
							component.put("module", publication.variantModules.get(platformPublication));
							try (Writer writer = Files.newBufferedWriter(out.toPath())) {
								new JsonBuilder(root).writeTo(writer);
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						}
					}));
				});

				// all generate ivy for variant
				tasks.withType(GenerateIvyDescriptor.class).configureEach(named(generateDescriptorFileTaskName(platformPublication)::equals, task -> {
					//   - doLast, override artifactId to correct artifact Id
					task.doLast(ignored(new Runnable() {
						@Override
						public void run() {
							try {
								Pattern pattern = Pattern.compile(" module=\"[^\"]+\" ");
								List<String> lines = Files.readAllLines(task.getDestination().toPath()).stream().map(t -> {
									return pattern.matcher(t).replaceFirst(" module=\"" + publication.variantModules.get(platformPublication) + "\" ");
								}).collect(Collectors.toList());
								Files.write(task.getDestination().toPath(), lines);
							} catch (Throwable e) {
								throw new RuntimeException(e);
							}
						}
					}));
				}));
			});

			publication.getPlatformPublication().configureEach(publishTasks(tasks.withType(PublishToIvyRepository.class), task -> {
				task.doFirst("", ignored(new Runnable() {
					@Override
					public void run() {
						task.getPublication().setModule(publication.variantModules.get(task.getPublication()));
					}
				}));
			}));
		});



		// PUBLISH ROOT after variants
		extension.getPublications().withType(DefaultPublication.class).configureEach(publication -> {
			// Component publication must run after variant publications
			publication.getRootPublication().configure(publishTasks(project.getTasks(), task -> {
				task.mustRunAfter((Callable<?>) () -> {
					return publication.getPlatformPublication().getElements().get().stream().map(it -> task.getName().replace(capitalize(publication.getRootPublication().getName()), capitalize(it.getName()))).collect(Collectors.toList());
				});
			}));
		});



		// Complete ROOT module metadata remote variants
		extension.getPublications().withType(DefaultPublication.class).configureEach(publication -> {
			publication.getRootPublication().configure(publishTasks(project.getTasks().withType(PublishToIvyRepository.class), task -> {
				task.doFirst("", ignored(new Runnable() {
					@Override
					public void run() {
						String organisation = task.getPublication().getOrganisation();
						String revision = task.getPublication().getRevision();
						List<ExternalModuleDependency> variants = publication.getPlatforms().get().stream().map(it -> {
							return (ExternalModuleDependency) project.getDependencies().create(organisation + ":" + it + ":" + revision);
						}).toList();

						File moduleFile = project.getTasks().named(generateMetadataFileTaskName(publication.getRootPublication().get()), GenerateModuleMetadata.class).get().getOutputFile().get().getAsFile();
						Map<String, Object> origRoot = (Map<String, Object>) new JsonSlurper().parse(moduleFile);
						List<Object> vars = (List<Object>) origRoot.get("variants");

						for (ExternalModuleDependency variant : variants) {
							URI l = task.getRepository().getUrl().resolve(variant.getGroup() + "/" + variant.getName() + "/" + variant.getVersion() + "/" + variant.getName() + "-" + variant.getVersion() + ".module");
							Map<String, Object> root = (Map<String, Object>) new JsonSlurper().parse(project.getResources().getText().fromUri(l).asReader());
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
				}));
			}));
		});
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

	private static <T extends Publication, S extends Task> Action<T> publishTasks(TaskCollection<S> tasks, Action<? super S> action) {
		return publication -> {
			tasks.configureEach(named(publishPublicationToAnyRepositories(publication), action));
		};
	}

	/*private*/ static abstract /*final*/ class DefaultPublication implements IvyMultiplatformPublication, MultiplatformPublicationInternal {
		private final String name;
		private final NamedDomainObjectProvider<IvyPublication> rootPublication;
		private final DefaultPlatformPublications platformPublications;
		private final Map<IvyPublication, String> variantModules = new HashMap<>();

		@Inject
		public DefaultPublication(Names names, NamedDomainObjectProvider<IvyPublication> rootPublication, NamedDomainObjectRegistry<IvyPublication> registry, NamedDomainObjectCollection<IvyPublication> collection, ObjectFactory objects) {
			this.name = names.toString();
			this.rootPublication = rootPublication;
			this.platformPublications = objects.newInstance(DefaultPlatformPublications.class, names, registry, collection);
		}

		@Override
		public String moduleNameOf(Publication platformPublication) {
			assert platformPublication instanceof IvyPublication;
			return variantModules.get(platformPublication);
		}

		@Override
		public DefaultPlatformPublications getPlatformPublication() {
			return platformPublications;
		}

		@Override
		public void rootPublication(Action<? super IvyPublication> configureAction) {
			rootPublication.configure(configureAction);
		}

		@Override
		public NamedDomainObjectProvider<IvyPublication> getRootPublication() {
			return rootPublication;
		}

		@Override
		public String getName() {
			return name;
		}

		/*private*/ static abstract /*final*/ class DefaultPlatformPublications extends AbstractPlatformPublications<IvyPublication> implements PlatformPublications {
			private final Names names;
			private final NamedDomainObjectRegistry<IvyPublication> registry;

			@Inject
			public DefaultPlatformPublications(Names names, NamedDomainObjectRegistry<IvyPublication> registry, NamedDomainObjectCollection<IvyPublication> collection, ProviderFactory providers, ObjectFactory objects) {
				super(IvyPublication.class, collection, objects.newInstance(Finalizer.class), providers, objects);
				this.names = names;
				this.registry = registry;
			}

			@Override
			public NamedDomainObjectProvider<IvyPublication> register(String name) {
				return register(names.append(name), registry::register);
			}

			@Override
			public NamedDomainObjectProvider<IvyPublication> register(String name, Action<? super IvyPublication> configureAction) {
				NamedDomainObjectProvider<IvyPublication> result = register(names.append(name), registry::register);
				result.configure(configureAction);
				return result;
			}
		}
	}
}
