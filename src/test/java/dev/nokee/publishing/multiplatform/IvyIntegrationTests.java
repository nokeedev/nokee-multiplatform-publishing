package dev.nokee.publishing.multiplatform;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.IvyPublication;
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
	void createsBridgePublication() {
		assertThat(publications.named("test"), providerOf(allOf(named("test"), instanceOf(IvyPublication.class))));
	}

	@Test
	void canAccessBridgePublication() {
		assertThat(subject.getBridgePublication(), providerOf(publications.getByName("test")));
	}

	@Test
	void canConfigureBridgePublication() {
		Action<IvyPublication> action = Mockito.mock();

		subject.bridgePublication(action);

		ArgumentCaptor<IvyPublication> captor = ArgumentCaptor.captor();
		Mockito.verify(action).execute(captor.capture());
		assertThat(captor.getAllValues(), contains(publications.getByName("test")));
	}

	@Test
	void canRegisterPlatformPublications() {
		assertThat(subject.getPlatformPublications().register("debug"), providerOf(allOf(named("testDebug"), instanceOf(IvyPublication.class))));
	}

	@Test
	void defaultsPlatformPublicationModule() {
		subject.bridgePublication(it -> it.setModule("my-app"));
		IvyPublication platformPublication = subject.getPlatformPublications().register("debug").get();
		((ViewInternal) subject.getPlatformPublications()).finalizeNow();
		assertThat("required by Gradle when multiple publications", platformPublication.getModule(), equalTo("my-app"));
		assertThat(((MultiplatformPublicationInternal) subject).moduleNameOf(platformPublication), equalTo("my-app_debug"));
	}

	@Test
	void canOverridePlatformPublicationArtifactId() {
		subject.bridgePublication(it -> it.setModule("my-app"));
		IvyPublication platformPublication = subject.getPlatformPublications().register("debug").get();
		platformPublication.setModule("myAppDebug");
		((ViewInternal) subject.getPlatformPublications()).finalizeNow();
		assertThat("required by Gradle when multiple publications", platformPublication.getModule(), equalTo("my-app"));
		assertThat(((MultiplatformPublicationInternal) subject).moduleNameOf(platformPublication), equalTo("myAppDebug"));
	}

	@Test
	void defaultsPlatformPublicationOrganization() {
		subject.bridgePublication(it -> it.setOrganisation("com.example"));
		Provider<String> organization = subject.getPlatformPublications().register("debug").map(IvyPublication::getOrganisation);
		((ViewInternal) subject.getPlatformPublications()).finalizeNow();
		assertThat(organization, providerOf("com.example"));
	}

	@Test
	void defaultsPlatformPublicationRevision() {
		subject.bridgePublication(it -> it.setRevision("1.2"));
		Provider<String> revision = subject.getPlatformPublications().register("debug").map(IvyPublication::getRevision);
		((ViewInternal) subject.getPlatformPublications()).finalizeNow();
		assertThat(revision, providerOf("1.2"));
	}

	@Test
	void defaultsPlatformsToPlatformPublications() {
		subject.bridgePublication(it -> it.setModule("my-lib"));
		subject.getPlatformPublications().register("debug");
		subject.getPlatformPublications().register("release");
		assertThat(subject.getPlatformArtifacts(), providerOf(contains("my-lib_debug", "my-lib_release")));
	}
}
