package dev.nokee.publishing.multiplatform;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;

public interface MultiplatformPublishingExtension {
	ExtensiblePolymorphicDomainObjectContainer<MultiplatformPublication> getPublications();
}
