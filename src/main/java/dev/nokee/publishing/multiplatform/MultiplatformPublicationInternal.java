package dev.nokee.publishing.multiplatform;

import org.gradle.api.publish.Publication;

interface MultiplatformPublicationInternal {
	String moduleNameOf(Publication platformPublication);
}
