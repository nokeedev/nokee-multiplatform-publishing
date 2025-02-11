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

class ForMultiplatformClosureIntegrationTests {
	@TempDir Path testDirectory;
	Project project;

	@BeforeEach
	void setup() {
		project = ProjectBuilder.builder().withProjectDir(testDirectory.toFile()).build();
	}

	@Test
	void throwsSensibleExceptionUsingJavaApi() {
		// TODO: Improve error message
		assertThat(() -> ForMultiplatformClosure.forProject(project), throwsException(message("Cannot get property 'forMultiplatform' on extra properties extension as it does not exist")));
	}
}
