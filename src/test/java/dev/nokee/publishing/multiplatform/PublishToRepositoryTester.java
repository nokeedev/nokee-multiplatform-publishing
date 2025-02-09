package dev.nokee.publishing.multiplatform;

import dev.gradleplugins.runnerkit.BuildResult;
import dev.gradleplugins.runnerkit.GradleRunner;
import dev.nokee.publishing.multiplatform.fixtures.Repository;
import org.junit.jupiter.api.Test;

import static dev.nokee.commons.hamcrest.Has.has;
import static dev.nokee.commons.hamcrest.With.with;
import static dev.nokee.commons.hamcrest.gradle.NamedMatcher.named;
import static dev.nokee.publishing.multiplatform.fixtures.MavenRepositoryMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

abstract class PublishToRepositoryTester {
	protected abstract Repository repo();

	protected abstract String publishToRepository();

	protected abstract GradleRunner runner();

	@Test
	void doesNotWarnAboutMultiplePublicationsWithSameCoordinate() {
		BuildResult result = runner().withTasks(publishToRepository()).build();
		assertThat(result.getOutput(), not(containsString("Multiple publications with coordinates")));
	}

	@Test
	void publishesAllModules() {
		runner().withTasks(publishToRepository()).build();
		assertThat(repo(), has(publishedModule("com.example:test-project:1.0")));
		assertThat(repo(), has(publishedModule("com.example:test-project_debug:1.0")));
		assertThat(repo(), has(publishedModule("com.example:test-project_release:1.0")));
	}

	@Test
	void bridgePublicationHasRemoteVariants() {
		runner().withTasks(publishToRepository()).build();
		assertThat(repo().module("com.example", "test-project"),
			has(moduleMetadata(with(remoteVariants(contains(named("debugLinkElements"), named("releaseLinkElements")))))));
	}

	@Test
	void platformPublicationsHasCorrectModuleMetadataComponentModule() {
		runner().withTasks(publishToRepository()).build();
		assertThat(repo().module("com.example", "test-project_debug"),
			has(moduleMetadata(with(component(module(equalTo("test-project_debug")))))));
		assertThat(repo().module("com.example", "test-project_release"),
			has(moduleMetadata(with(component(module(equalTo("test-project_release")))))));
	}

	@Test
	void publishPlatformPublicationsBeforeBridgePublication() {
		BuildResult result = runner().withTasks(publishToRepository()).build();
		assertThat(result.getExecutedTaskPaths(), containsInRelativeOrder(startsWith(":publishCppDebugPublicationTo"), startsWith(":publishCppReleasePublicationTo"), startsWith(":publishCppPublicationTo")));
	}
}
