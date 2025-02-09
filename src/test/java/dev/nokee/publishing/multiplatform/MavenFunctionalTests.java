package dev.nokee.publishing.multiplatform;

import dev.gradleplugins.runnerkit.BuildResult;
import dev.gradleplugins.runnerkit.GradleExecutor;
import dev.gradleplugins.runnerkit.GradleRunner;
import dev.gradleplugins.runnerkit.TaskOutcome;
import dev.nokee.commons.sources.GradleBuildElement;
import dev.nokee.publishing.multiplatform.fixtures.M2Installation;
import dev.nokee.publishing.multiplatform.fixtures.MavenRepository;
import dev.nokee.publishing.multiplatform.fixtures.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static dev.gradleplugins.buildscript.syntax.Syntax.groovyDsl;
import static dev.nokee.commons.hamcrest.Has.has;
import static dev.nokee.commons.hamcrest.With.with;
import static dev.nokee.commons.hamcrest.gradle.NamedMatcher.named;
import static dev.nokee.publishing.multiplatform.fixtures.MavenFileRepository.mavenRepository;
import static dev.nokee.publishing.multiplatform.fixtures.MavenRepositoryMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class MavenFunctionalTests {
	@TempDir Path testDirectory;
	GradleBuildElement build;
	GradleRunner runner;
	MavenRepository repository;
	M2Installation m2;

	@BeforeEach
	void setup() {
		System.out.println("Test directory: " + testDirectory);
		build = GradleBuildElement.inDirectory(testDirectory);
		m2 = new M2Installation(testDirectory);
		runner = m2.isolateMavenLocalRepo(runnerFor(build).withPluginClasspath());
		repository = mavenRepository(testDirectory.resolve("repo"));
		build.getBuildFile().plugins(it -> {
			it.id("dev.nokee.multiplatform-publishing");
			it.id("maven-publish");
		});
		build.getBuildFile().append(groovyDsl("""
			import org.gradle.api.Project
			import org.gradle.api.artifacts.ConfigurationContainer
			import org.gradle.api.attributes.Usage
			import org.gradle.api.component.AdhocComponentWithVariants
			import org.gradle.api.component.SoftwareComponentFactory
			import org.gradle.api.publish.maven.MavenPublication

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
				publications(forMultiplatform('cpp', MavenPublication) {
					bridgePublication {
						from components.cpp
						groupId = 'com.example'
						version = '1.0'
					}
					platformPublications.register('debug') { from components.cppDebug }
					platformPublications.register('release') { from components.cppRelease }
				})
				repositories {
					maven { url 'repo' }
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
	class PublishToMavenRepositoryTests extends PublishToRepositoryTester {
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
		void skipsPublishBridgePublicationToMavenRepositoryOnMissingPublishedPlatformPublications() {
			BuildResult result = runner.withArgument("-x").withArgument(":publishCppDebugPublicationToMavenRepository").withTasks(":publishAllPublicationsToMavenRepository").build();
			assertThat(result.task(":publishCppPublicationToMavenRepository").getOutcome(), is(TaskOutcome.SKIPPED));
			assertThat(result.task(":publishCppPublicationToMavenRepository").getOutput(), containsString("Warning: Publication with coordinate 'com.example:test-project_debug:1.0' not published."));
		}

		@Test
		void skipsPublishBridgePublicationToMavenRepositoryDirectlyOnMissingPublishedPlatformPublications() {
			BuildResult result = runner.withTasks(":publishCppPublicationToMavenRepository").build();
			assertThat(result.task(":publishCppPublicationToMavenRepository").getOutcome(), is(TaskOutcome.SKIPPED));
			assertThat(result.task(":publishCppPublicationToMavenRepository").getOutput(), containsString("Warning: Publication with coordinate 'com.example:test-project_debug:1.0' not published."));
			assertThat(result.task(":publishCppPublicationToMavenRepository").getOutput(), containsString("Warning: Publication with coordinate 'com.example:test-project_release:1.0' not published."));
		}
	}

	@Nested
	class PublishToMavenLocalTests extends PublishToRepositoryTester {
		@BeforeEach
		void setup() {
			runner = runner.configure(m2);
		}

		@Override
		protected Repository repo() {
			return m2.mavenRepo();
		}

		@Override
		protected String publishToRepository() {
			return "publishToMavenLocal";
		}

		@Override
		protected GradleRunner runner() {
			return runner;
		}

		@Test
		void alwaysPublishBridgePublicationToMavenLocal() {
			BuildResult result = runner.withArgument("-x").withArgument(":publishCppDebugPublicationToMavenLocal").withTasks(":publishToMavenLocal").build();
			assertThat(result.task(":publishCppPublicationToMavenLocal").getOutcome(), is(TaskOutcome.SUCCESS));
			assertThat(result.task(":publishCppPublicationToMavenLocal").getOutput(), containsString("Warning: Publication with coordinate 'com.example:test-project_debug:1.0' not found in '...'."));
		}
	}

	@Nested
	class PublishToAllMavenRepositoriesTests {
		@BeforeEach
		void setup() {
			runner = runner.configure(m2);
			runner.withTasks("publishToMavenLocal", "publish").build();
		}

		@Test
		void canPublishToMavenLocal() {
			assertThat(repository, has(publishedModule("com.example:test-project:1.0")));
			assertThat(repository, has(publishedModule("com.example:test-project_debug:1.0")));
			assertThat(repository, has(publishedModule("com.example:test-project_release:1.0")));

			assertThat(m2.mavenRepo(), has(publishedModule("com.example:test-project:1.0")));
			assertThat(m2.mavenRepo(), has(publishedModule("com.example:test-project_debug:1.0")));
			assertThat(m2.mavenRepo(), has(publishedModule("com.example:test-project_release:1.0")));
		}

		@Test
		void bridgePublicationHasRemoteVariants() {
			assertThat(repository.module("com.example", "test-project"),
				has(moduleMetadata(with(remoteVariants(allOf(hasSize(2), contains(named("debugLinkElements"), named("releaseLinkElements"))))))));
			assertThat(m2.mavenRepo().module("com.example", "test-project"),
				has(moduleMetadata(with(remoteVariants(allOf(hasSize(2), contains(named("debugLinkElements"), named("releaseLinkElements"))))))));
		}
	}
}
