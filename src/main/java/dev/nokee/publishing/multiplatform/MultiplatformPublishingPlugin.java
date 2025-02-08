package dev.nokee.publishing.multiplatform;

import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.*;
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
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.nokee.commons.names.PublishingTaskNames.*;
import static dev.nokee.commons.provider.CollectionElementTransformer.transformEach;
import static dev.nokee.publishing.multiplatform.MinimalGMVPublication.wrap;

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
}
