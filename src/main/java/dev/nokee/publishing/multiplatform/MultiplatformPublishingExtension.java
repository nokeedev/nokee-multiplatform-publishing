package dev.nokee.publishing.multiplatform;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.publish.Publication;

/**
 * Represents the configuration of how to “publish” the multiplatform components of a project.
 */
interface MultiplatformPublishingExtension {
	/**
	 * {@return the multiplatform publications}
	 */
	ExtensiblePolymorphicDomainObjectContainer<MultiplatformPublication<? extends Publication>> getPublications();
}
