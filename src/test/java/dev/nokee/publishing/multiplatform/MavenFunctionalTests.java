//package dev.nokee.publishing.multiplatform;
//
//import dev.gradleplugins.runnerkit.GradleExecutor;
//import dev.gradleplugins.runnerkit.GradleRunner;
//import dev.nokee.commons.sources.GradleBuildElement;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.io.TempDir;
//
//import java.nio.file.Path;
//
//import static dev.gradleplugins.buildscript.syntax.Syntax.groovyDsl;
//
//class MavenFunctionalTests {
//	@TempDir Path testDirectory;
//	GradleBuildElement build;
//	GradleRunner runner;
//
//	@BeforeEach
//	void setup() {
//		build = GradleBuildElement.inDirectory(testDirectory);
//		runner = runnerFor(build).withPluginClasspath();
//		build.getBuildFile().plugins(it -> it.id("dev.nokee.multiplatform-publishing"));
//		build.getBuildFile().append(groovyDsl("""
//			import dev.nokee.publishing.multiplatform.maven.MavenMultiplatformPublication
//import org.gradle.api.component.SoftwareComponentFactory
//import org.gradle.api.publish.maven.MavenPublication
//
//import javax.inject.Inject
//			abstract class SoftwareComponentFactoryProvider {
//                private final SoftwareComponentFactory service
//
//                @Inject
//				SoftwareComponentFactoryProvider(SoftwareComponentFactory service) {
//                    this.service = service
//                }
//
//                SoftwareComponentFactory get() { service }
//			}
//
//			def factory = objects.newInstance(SoftwareComponentFactoryProvider).get()
//
//			factory.adhoc('cpp')
//			factory.adhoc('cppDebug')
//			factory.adhoc('cppRelease')
//
//
//			multiplatform.publications.register('cpp', MavenMultiplatformPublication) {
//				rootPublication { from components.cpp }
//				variantPublications.register('debug') { from components.cppDebug }
//				variantPublications.register('release') { from components.cppRelease }
//			}
//			"""));
//	}
//
//	static GradleRunner runnerFor(GradleBuildElement build) {
//		// TODO: Check if there is a wrapper
//		return GradleRunner.create(GradleExecutor.gradleTestKit()).inDirectory(build.getLocation().toFile());
//	}
//}
