package dev.nokee.publishing.multiplatform;

import org.gradle.api.publish.Publication;

interface MultiplatformPublicationInternal extends MultiplatformPublication {
	String moduleNameOf(Publication variantPublication);
}
