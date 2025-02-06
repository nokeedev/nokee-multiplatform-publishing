package dev.nokee.publishing.multiplatform;

import dev.nokee.publishing.multiplatform.ivy.IvyMultiplatformPublication;
import dev.nokee.publishing.multiplatform.maven.MavenMultiplatformPublication;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.file.Path;

import static dev.nokee.commons.hamcrest.gradle.NamedMatcher.named;
import static dev.nokee.commons.hamcrest.gradle.provider.ProviderOfMatcher.providerOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class IvyIntegrationTests {
	@TempDir Path testDirectory;
	Project project;
	IvyMultiplatformPublication subject;
	NamedDomainObjectContainer<Publication> publications;

	@BeforeEach
	void setup() {
		project = ProjectBuilder.builder().withProjectDir(testDirectory.toFile()).build();
		project.getPluginManager().apply("dev.nokee.multiplatform-publishing");
		project.getPluginManager().apply("ivy-publish");
		subject = project.getExtensions().getByType(MultiplatformPublishingExtension.class).getPublications().create("test", IvyMultiplatformPublication.class);
		publications = project.getExtensions().getByType(PublishingExtension.class).getPublications();
	}

	@Test
	void createsRootPublication() {
		assertThat(publications.named("test"), providerOf(allOf(named("test"), instanceOf(IvyPublication.class))));
	}

	@Test
	void canAccessRootPublication() {
		assertThat(subject.getRootPublication(), providerOf(publications.getByName("test")));
	}

	@Test
	void canConfigureRootPublication() {
		Action<IvyPublication> action = Mockito.mock();

		subject.rootPublication(action);

		ArgumentCaptor<IvyPublication> captor = ArgumentCaptor.captor();
		Mockito.verify(action).execute(captor.capture());
		assertThat(captor.getAllValues(), contains(publications.getByName("test")));
	}
}
