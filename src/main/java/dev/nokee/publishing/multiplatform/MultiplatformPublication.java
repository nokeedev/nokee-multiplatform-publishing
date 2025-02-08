package dev.nokee.publishing.multiplatform;

import dev.nokee.platform.base.View;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.publish.Publication;

public interface MultiplatformPublication<PublicationType extends Publication> extends Named {
	default void bridgePublication(Action<? super PublicationType> configureAction) {
		getBridgePublication().configure(configureAction);
	}

	NamedDomainObjectProvider<PublicationType> getBridgePublication();

	PlatformPublications<PublicationType> getPlatformPublications();

	interface PlatformPublications<T extends Publication> extends View<T> {
		NamedDomainObjectProvider<T> register(String name);
		NamedDomainObjectProvider<T> register(String name, Action<? super T> configureAction);
	}
}
