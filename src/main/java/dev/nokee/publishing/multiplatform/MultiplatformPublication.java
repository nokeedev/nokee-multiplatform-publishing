package dev.nokee.publishing.multiplatform;

import dev.nokee.platform.base.View;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.publish.Publication;

/**
 * Represent a multiplatform publication of the specified publication type.
 * We define multiplatform publication as a single bridge publication multiplexing to multiple platform specific publication.
 *
 * @param <PublicationType>  the publication type
 */
public interface MultiplatformPublication<PublicationType extends Publication> extends Named {
	/**
	 * Configures bridge publication with specified configure action.
	 *
	 * @param configureAction  the configure action to apply to the bridge publication
	 */
	default void bridgePublication(Action<? super PublicationType> configureAction) {
		getBridgePublication().configure(configureAction);
	}

	/**
	 * {@return the bridge publication of this multiplatform publication.}
	 */
	NamedDomainObjectProvider<PublicationType> getBridgePublication();

	/**
	 * {@return the platform specific publication container of this multiplatform publication.}
	 */
	PlatformPublications<PublicationType> getPlatformPublications();

	/**
	 * Represents the platform publications of a multiplatform publication.
	 * @param <PlatformPublicationType>  the publication type for each platform
	 */
	interface PlatformPublications<PlatformPublicationType extends Publication> extends View<PlatformPublicationType> {
		/**
		 * Registers platform publication owned by this multiplatform publication of the specified name.
		 *
		 * @param name  the name of the publication
		 * @return a provider to registered platform publication
		 */
		NamedDomainObjectProvider<PlatformPublicationType> register(String name);

		/**
		 * Registers platform publication owned by this multiplatform publication of the specified name and configures it.
		 *
		 * @param name  the name of the publication
		 * @param configureAction  the configure action for the registered publication
		 * @return a provider to registered platform publication
		 */
		NamedDomainObjectProvider<PlatformPublicationType> register(String name, Action<? super PlatformPublicationType> configureAction);
	}
}
