package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.NamedDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import dev.nokee.publishing.multiplatform.maven.MavenMultiplatformPublication;
import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.*;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.reflect.TypeOf;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.nokee.commons.names.PublishingTaskNames.*;
import static dev.nokee.commons.provider.CollectionElementTransformer.transformEach;
import static dev.nokee.publishing.multiplatform.MinimalGMVPublication.wrap;
import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;

abstract /*final*/ class MavenMultiplatformPublishingPlugin implements Plugin<Project> {
	private final ObjectFactory objects;
	private final TaskContainer tasks;

	@Inject
	public MavenMultiplatformPublishingPlugin(ObjectFactory objects, TaskContainer tasks) {
		this.objects = objects;
		this.tasks = tasks;
	}

	@Override
	public void apply(Project project) {
		MultiplatformPublishingExtension extension = project.getExtensions().getByType(MultiplatformPublishingExtension.class);
		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
		extension.getPublications().registerFactory(MavenMultiplatformPublication.class, name -> {
			NamedDomainObjectProvider<MavenPublication> bridgePublication = publishing.getPublications().register(name, MavenPublication.class);
			return objects.newInstance(DefaultPublication.class, Names.of(name), bridgePublication, new NamedDomainObjectRegistry<>(publishing.getPublications().containerWithType(MavenPublication.class)), publishing.getPublications().withType(MavenPublication.class));
		});

		extension.getPublications().withType(new TypeOf<DefaultPublication>() {}.getConcreteClass()).configureEach(publication -> {
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
						return publication.getBridgePublication().get().getArtifactId() + "_" + variantName;
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
			});

			publication.getPlatformPublications().configureEach(publishTasks(tasks.withType(PublishToMavenRepository.class), task -> {
				task.doFirst("", ignored(new Runnable() {
					@Override
					public void run() {
						task.getPublication().setArtifactId(variantArtifactIds.get(wrap(task.getPublication())));
					}
				}));
			}));
			publication.getPlatformPublications().configureEach(publishTasks(tasks.withType(PublishToMavenLocal.class), task -> {
				task.doFirst("", ignored(new Runnable() {
					@Override
					public void run() {
						task.getPublication().setArtifactId(variantArtifactIds.get(wrap(task.getPublication())));
					}
				}));
			}));
		});



		// PUBLISH ROOT after variants
		extension.getPublications().withType(DefaultPublication.class).configureEach(publication -> {
			// Component publication must run after variant publications
			publication.getBridgePublication().configure(publishTasks(project.getTasks(), task -> {
				task.mustRunAfter((Callable<?>) () -> {
					return publication.getPlatformPublications().getElements().get().stream().map(it -> task.getName().replace(capitalize(publication.getBridgePublication().getName()), capitalize(it.getName()))).collect(Collectors.toList());
				});
			}));
		});



		// Complete ROOT module metadata remote variants
		extension.getPublications().withType(DefaultPublication.class).configureEach(publication -> {
			publication.getBridgePublication().configure(publishTasks(project.getTasks().withType(PublishToMavenRepository.class), task -> {
				task.doFirst("", ignored(new Runnable() {
					@Override
					public void run() {
						String groupId = task.getPublication().getGroupId();
						String version = task.getPublication().getVersion();
						List<ExternalModuleDependency> variants = publication.getPlatforms().get().stream().map(it -> {
							return (ExternalModuleDependency) project.getDependencies().create(groupId + ":" + it + ":" + version);
						}).toList();

						File moduleFile = project.getTasks().named(generateMetadataFileTaskName(publication.getBridgePublication().get()), GenerateModuleMetadata.class).get().getOutputFile().get().getAsFile();
						Map<String, Object> origRoot = (Map<String, Object>) new JsonSlurper().parse(moduleFile);
						List<Object> vars = (List<Object>) origRoot.get("variants");

						for (ExternalModuleDependency variant : variants) {
							URI l = task.getRepository().getUrl().resolve(variant.getGroup().replace(".", "/") + "/" + variant.getName() + "/" + variant.getVersion() + "/" + variant.getName() + "-" + variant.getVersion() + ".module");
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

			// TODO: Support maven local
			// TODO: Support ivy repositories
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

	/*private*/ static abstract /*final*/ class DefaultPublication extends AbstractMultiplatformPublication<MavenPublication> implements MavenMultiplatformPublication, MultiplatformPublicationInternal {
		private final Map<MinimalGMVPublication, String> variantArtifactIds = new LinkedHashMap<>();

		@Inject
		@SuppressWarnings("unchecked")
		public DefaultPublication(Names names, NamedDomainObjectProvider<MavenPublication> bridgePublication, NamedDomainObjectRegistry<MavenPublication> registry, NamedDomainObjectCollection<MavenPublication> collection, ObjectFactory objects) {
			super(names, bridgePublication, (PlatformPublicationsContainer<MavenPublication>) objects.newInstance(PlatformPublicationsContainer.class, MavenPublication.class, names, registry, collection));
		}

		@Override
		public String moduleNameOf(Publication platformPublication) {
			assert platformPublication instanceof MavenPublication;
			return variantArtifactIds.get(wrap(platformPublication));
		}

		@Override
		public Map<MinimalGMVPublication, String> getModuleNames() {
			return variantArtifactIds;
		}

		@Override
		public String toString() {
			return "multiplatform Maven publication '" + getName() + "'";
		}
	}
}
