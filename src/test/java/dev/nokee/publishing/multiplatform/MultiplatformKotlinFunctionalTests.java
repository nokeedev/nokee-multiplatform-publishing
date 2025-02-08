package dev.nokee.publishing.multiplatform;

import dev.gradleplugins.runnerkit.GradleExecutor;
import dev.gradleplugins.runnerkit.GradleRunner;
import dev.nokee.commons.sources.GradleBuildElement;
import dev.nokee.publishing.multiplatform.fixtures.M2Installation;
import dev.nokee.publishing.multiplatform.fixtures.MavenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static dev.gradleplugins.buildscript.syntax.Syntax.groovyDsl;
import static dev.gradleplugins.buildscript.syntax.Syntax.kotlinDsl;
import static dev.nokee.publishing.multiplatform.fixtures.MavenFileRepository.mavenRepository;

class MultiplatformKotlinFunctionalTests {
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

		build.getSettingsFile().append(groovyDsl("""
			rootProject.name = 'test-project'
		"""));
	}

	static GradleRunner runnerFor(GradleBuildElement build) {
		// TODO: Check if there is a wrapper
		return GradleRunner.create(GradleExecutor.gradleTestKit()).inDirectory(build.getLocation().toFile());
	}

	@Test
	void canUseForMultiplatformFromKotlinDslBuildScript() {
		build.getBuildFile().useKotlinDsl();
		build.getBuildFile().append(kotlinDsl("""
			publishing {
				publications(forMultiplatform<MavenPublication>("cpp") {
					bridgePublication {
						groupId = "com.example"
						version = "1.0"
					}
					platformPublications.register("debug")
					platformPublications.register("release")
				})
				repositories {
					maven { url = uri("repo") }
				}
			}
		"""));
		runner.withTasks("help").build();
	}

	@Test
	void canUseForMultiplatformFromGroovyDslBuildScript() {
		build.getBuildFile().useGroovyDsl();
		build.getBuildFile().append(groovyDsl("""
			publishing {
				publications forMultiplatform('cpp', MavenPublication) {
					bridgePublication {
						groupId = 'com.example'
						version = '1.0'
					}
					platformPublications.register('debug')
					platformPublications.register('release')
				}
				repositories {
					maven { url = 'repo' }
				}
			}
		"""));
		runner.withTasks("help").build();
	}
}
