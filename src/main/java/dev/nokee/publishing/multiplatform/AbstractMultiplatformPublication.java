package dev.nokee.publishing.multiplatform;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.publish.Publication;

abstract class AbstractMultiplatformPublication<T extends Publication> implements MultiplatformPublication<T>, MultiplatformPublicationInternal, PlatformAwarePublication {
	private final String name;
	private final NamedDomainObjectProvider<T> bridgePublication;

	protected AbstractMultiplatformPublication(String name, NamedDomainObjectProvider<T> bridgePublication) {
		this.name = name;
		this.bridgePublication = bridgePublication;
	}

	@Override
	public final String getName() {
		return name;
	}

	@Override
	public final NamedDomainObjectProvider<T> getBridgePublication() {
		return bridgePublication;
	}
}
