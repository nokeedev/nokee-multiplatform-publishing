package dev.nokee.publishing.multiplatform;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
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

class MavenIntegrationTests {
	@TempDir Path testDirectory;
	Project project;
	MavenMultiplatformPublication subject;
	NamedDomainObjectContainer<Publication> publications;

	@BeforeEach
	void setup() {
		project = ProjectBuilder.builder().withProjectDir(testDirectory.toFile()).build();
		project.getPluginManager().apply("dev.nokee.multiplatform-publishing");
		project.getPluginManager().apply("maven-publish");
		subject = project.getExtensions().getByType(MultiplatformPublishingExtension.class).getPublications().create("test", MavenMultiplatformPublication.class);
		publications = project.getExtensions().getByType(PublishingExtension.class).getPublications();
	}

	@Test
	void createsBridgePublication() {
		assertThat(publications.named("test"), providerOf(allOf(named("test"), instanceOf(MavenPublication.class))));
	}

	@Test
	void canAccessBridgePublication() {
		assertThat(subject.getBridgePublication(), providerOf(publications.getByName("test")));
	}

	@Test
	void canConfigureBridgePublication() {
		Action<MavenPublication> action = Mockito.mock();

		subject.bridgePublication(action);

		ArgumentCaptor<MavenPublication> captor = ArgumentCaptor.captor();
		Mockito.verify(action).execute(captor.capture());
		assertThat(captor.getAllValues(), contains(publications.getByName("test")));
	}

	@Test
	void canRegisterPlatformPublications() {
		assertThat(subject.getPlatformPublications().register("debug"), providerOf(allOf(named("testDebug"), instanceOf(MavenPublication.class))));
	}

	@Test
	void defaultsPlatformPublicationArtifactId() {
		subject.bridgePublication(it -> it.setArtifactId("my-app"));
		MavenPublication platformPublication = subject.getPlatformPublications().register("debug").get();
		((ViewInternal) subject.getPlatformPublications()).finalizeNow();
		assertThat("required by Gradle when multiple publications", platformPublication.getArtifactId(), equalTo("my-app"));
		assertThat(((MultiplatformPublicationInternal) subject).moduleNameOf(platformPublication), equalTo("my-app_debug"));
	}

	@Test
	void canOverridePlatformPublicationArtifactId() {
		subject.bridgePublication(it -> it.setArtifactId("my-app"));
		MavenPublication platformPublication = subject.getPlatformPublications().register("debug").get();
		platformPublication.setArtifactId("myAppDebug");
		((ViewInternal) subject.getPlatformPublications()).finalizeNow();
		assertThat("required by Gradle when multiple publications", platformPublication.getArtifactId(), equalTo("my-app"));
		assertThat(((MultiplatformPublicationInternal) subject).moduleNameOf(platformPublication), equalTo("myAppDebug"));
	}

	@Test
	void defaultsPlatformPublicationGroup() {
		subject.bridgePublication(it -> it.setGroupId("com.example"));
		Provider<String> module = subject.getPlatformPublications().register("debug").map(MavenPublication::getGroupId);
		((ViewInternal) subject.getPlatformPublications()).finalizeNow();
		assertThat(module, providerOf("com.example"));
	}

	@Test
	void defaultsPlatformPublicationVersion() {
		subject.bridgePublication(it -> it.setVersion("1.2"));
		Provider<String> version = subject.getPlatformPublications().register("debug").map(MavenPublication::getVersion);
		((ViewInternal) subject.getPlatformPublications()).finalizeNow();
		assertThat(version, providerOf("1.2"));
	}

	@Test
	void defaultsPlatformsToPlatformPublications() {
		subject.bridgePublication(it -> it.setArtifactId("my-lib"));
		subject.getPlatformPublications().register("debug");
		subject.getPlatformPublications().register("release");
		assertThat(subject.getPlatforms(), providerOf(contains("my-lib_debug", "my-lib_release")));
	}
}
