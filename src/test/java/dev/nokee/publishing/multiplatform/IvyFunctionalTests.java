package dev.nokee.publishing.multiplatform;

import dev.gradleplugins.runnerkit.BuildResult;
import dev.gradleplugins.runnerkit.GradleExecutor;
import dev.gradleplugins.runnerkit.GradleRunner;
import dev.gradleplugins.runnerkit.TaskOutcome;
import dev.nokee.commons.sources.GradleBuildElement;
import dev.nokee.publishing.multiplatform.fixtures.IvyRepository;
import dev.nokee.publishing.multiplatform.fixtures.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static dev.gradleplugins.buildscript.syntax.Syntax.groovyDsl;
import static dev.nokee.publishing.multiplatform.fixtures.IvyFileRepository.ivyRepository;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class IvyFunctionalTests {
	@TempDir Path testDirectory;
	GradleBuildElement build;
	GradleRunner runner;
	IvyRepository repository;

	@BeforeEach
	void setup() {
		System.out.println("Test directory: " + testDirectory);
		build = GradleBuildElement.inDirectory(testDirectory);
		runner = runnerFor(build).withPluginClasspath();
		repository = ivyRepository(testDirectory.resolve("repo"));
		build.getBuildFile().plugins(it -> {
			it.id("dev.nokee.multiplatform-publishing");
			it.id("ivy-publish");
		});
		build.getBuildFile().append(groovyDsl("""
			import org.gradle.api.Project
			import org.gradle.api.artifacts.ConfigurationContainer
			import org.gradle.api.attributes.Usage
			import org.gradle.api.component.AdhocComponentWithVariants
			import org.gradle.api.component.SoftwareComponentFactory
			import org.gradle.api.publish.ivy.IvyPublication

			import javax.inject.Inject
			abstract class SoftwareComponentFactoryProvider {
                private final SoftwareComponentFactory service

                @Inject
				SoftwareComponentFactoryProvider(SoftwareComponentFactory service) {
                    this.service = service
                }

                SoftwareComponentFactory get() { service }
			}

			ConfigurationContainer configurations = project.configurations
			def factory = objects.newInstance(SoftwareComponentFactoryProvider).get()

			AdhocComponentWithVariants cpp = factory.adhoc('cpp')
			components.add(cpp)
			cpp.addVariantsFromConfiguration(configurations.consumable("cppApiElements") {
				attributes {
					attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.C_PLUS_PLUS_API))
				}
				outgoing {
					artifact(file('cpp-api-headers.zip'))
				}
			}.get()) {}

			AdhocComponentWithVariants cppDebug = factory.adhoc('cppDebug')
			components.add(cppDebug)
			cppDebug.addVariantsFromConfiguration(configurations.consumable("debugLinkElements") {
				attributes {
					attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.NATIVE_LINK))
				}
				outgoing {
					artifact(file('debug/libfoo.so'))
				}
			}.get()) {}
			AdhocComponentWithVariants cppRelease = factory.adhoc('cppRelease')
			components.add(cppRelease)
			cppRelease.addVariantsFromConfiguration(configurations.consumable("releaseLinkElements") {
				attributes {
					attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.NATIVE_LINK))
				}
				outgoing {
					artifact(file('release/libfoo.so'))
				}
			}.get()) {}

			publishing {
				publications(forMultiplatform('cpp', IvyPublication) {
					bridgePublication {
						from components.cpp
						organisation = 'com.example'
						revision = '1.0'
					}
					platformPublications.register('debug') { from components.cppDebug }
					platformPublications.register('release') { from components.cppRelease }
				})
				repositories {
					ivy { url 'repo' }
				}
			}
		"""));
		build.file("cpp-api-headers.zip");
		build.file("debug/libfoo.so");
		build.file("release/libfoo.so");

		build.getSettingsFile().append(groovyDsl("""
			rootProject.name = 'test-project'
		"""));
	}

	static GradleRunner runnerFor(GradleBuildElement build) {
		// TODO: Check if there is a wrapper
		return GradleRunner.create(GradleExecutor.gradleTestKit()).inDirectory(build.getLocation().toFile());
	}

	@Nested
	class PublishToIvyRepositoryTests extends PublishToRepositoryTester {
		@Override
		protected Repository repo() {
			return repository;
		}

		@Override
		protected String publishToRepository() {
			return "publish";
		}

		@Override
		protected GradleRunner runner() {
			return runner;
		}

		@Test
		void skipsPublishBridgePublicationToIvyRepositoryOnMissingPublishedPlatformPublications() {
			BuildResult result = runner.withArgument("-x").withArgument(":publishCppDebugPublicationToIvyRepository").withTasks(":publishAllPublicationsToIvyRepository").build();
			assertThat(result.task(":publishCppPublicationToIvyRepository").getOutcome(), is(TaskOutcome.SKIPPED));
			assertThat(result.task(":publishCppPublicationToIvyRepository").getOutput(), containsString("Warning: Publication with coordinate 'com.example:test-project_debug:1.0' not published."));
		}

		@Test
		void skipsPublishBridgePublicationToIvyRepositoryDirectlyOnMissingPublishedPlatformPublications() {
			BuildResult result = runner.withTasks(":publishCppPublicationToIvyRepository").build();
			assertThat(result.task(":publishCppPublicationToIvyRepository").getOutcome(), is(TaskOutcome.SKIPPED));
			assertThat(result.task(":publishCppPublicationToIvyRepository").getOutput(), containsString("Warning: Publication with coordinate 'com.example:test-project_debug:1.0' not published."));
			assertThat(result.task(":publishCppPublicationToIvyRepository").getOutput(), containsString("Warning: Publication with coordinate 'com.example:test-project_release:1.0' not published."));
		}
	}
}
