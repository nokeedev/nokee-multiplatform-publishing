package dev.nokee.publishing.multiplatform;

import org.gradle.api.Project;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static dev.nokee.commons.hamcrest.Has.has;
import static dev.nokee.commons.hamcrest.gradle.NamedMatcher.named;
import static dev.nokee.commons.hamcrest.gradle.ProjectMatchers.*;
import static dev.nokee.commons.hamcrest.gradle.ThrowableMatchers.message;
import static dev.nokee.commons.hamcrest.gradle.ThrowableMatchers.throwsException;
import static dev.nokee.commons.hamcrest.gradle.provider.ProviderOfMatcher.providerOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class BasicIntegrationTests {
	@TempDir Path testDirectory;
	Project project;

	@BeforeEach
	void setup() {
		project = ProjectBuilder.builder().withProjectDir(testDirectory.toFile()).build();
		project.getPluginManager().apply("dev.nokee.multiplatform-publishing");
	}

	@Test
	void hasMultiplatformPublishingExtension() {
		assertThat(project, has(extension(named("multiplatform"), publicType(MultiplatformPublishingExtension.class))));
	}

	@Test
	void appliesPublishingPlugin() {
		assertThat(project, has(plugin(PublishingPlugin.class)));
	}

	@Nested
	class MavenPublishPluginIntegrationTests {
		@Test
		void cannotCreateMavenMultiplatformPublicationWithoutMavenPublishPluginApplied() {
			assertThat(() -> project.getExtensions().getByType(MultiplatformPublishingExtension.class).getPublications().create("test", MavenMultiplatformPublication.class),
				throwsException(message(startsWith("Cannot create a MavenMultiplatformPublication because this type is not known to this container."))));
		}

		@Test
		void canCreateMavenMultiplatformPublication() {
			project.getPluginManager().apply("maven-publish");
			MultiplatformPublishingExtension subject = project.getExtensions().getByType(MultiplatformPublishingExtension.class);
			assertThat(subject.getPublications().register("test", MavenMultiplatformPublication.class),
				providerOf(allOf(named("test"), instanceOf(MavenMultiplatformPublication.class))));
		}
	}

	@Nested
	class IvyPublishPluginIntegrationTests {
		@Test
		void cannotCreateIvyMultiplatformPublicationWithoutIvyPublishPluginApplied() {
			assertThat(() -> project.getExtensions().getByType(MultiplatformPublishingExtension.class).getPublications().create("test", IvyMultiplatformPublication.class),
				throwsException(message(startsWith("Cannot create a IvyMultiplatformPublication because this type is not known to this container."))));
		}

		@Test
		void canCreateIvyMultiplatformPublication() {
			project.getPluginManager().apply("ivy-publish");
			MultiplatformPublishingExtension subject = project.getExtensions().getByType(MultiplatformPublishingExtension.class);
			assertThat(subject.getPublications().register("test", IvyMultiplatformPublication.class),
				providerOf(allOf(named("test"), instanceOf(IvyMultiplatformPublication.class))));
		}
	}
}
