package dev.nokee.publishing.multiplatform;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.publish.Publication;

abstract class AbstractMultiplatformPublication<T extends Publication> implements MultiplatformPublication<T>, MultiplatformPublicationInternal, PlatformAwarePublication {
	private final String name;

	protected AbstractMultiplatformPublication(String name) {
		this.name = name;
	}

	@Override
	public final String getName() {
		return name;
	}
}
