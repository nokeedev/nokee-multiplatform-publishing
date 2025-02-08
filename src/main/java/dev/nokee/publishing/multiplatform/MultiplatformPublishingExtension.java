package dev.nokee.publishing.multiplatform;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.publish.Publication;

public interface MultiplatformPublishingExtension {
	ExtensiblePolymorphicDomainObjectContainer<MultiplatformPublication<? extends Publication>> getPublications();
}
