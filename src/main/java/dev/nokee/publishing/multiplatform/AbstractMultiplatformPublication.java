package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.NamedDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.Publication;

import javax.inject.Inject;

abstract class AbstractMultiplatformPublication<T extends Publication> implements MultiplatformPublication<T>, MultiplatformPublicationInternal, PlatformAwarePublication {
	private final String name;
	private final NamedDomainObjectProvider<T> bridgePublication;
	private final PlatformPublicationsContainer<T> platformPublications;

	protected AbstractMultiplatformPublication(Names names, NamedDomainObjectProvider<T> bridgePublication, PlatformPublicationsContainer<T> platformPublications) {
		this.name = names.toString();
		this.bridgePublication = bridgePublication;
		this.platformPublications = platformPublications;
	}

	@Override
	public final String getName() {
		return name;
	}

	@Override
	public final NamedDomainObjectProvider<T> getBridgePublication() {
		return bridgePublication;
	}

	@Override
	public final PlatformPublicationsContainer<T> getPlatformPublications() {
		return platformPublications;
	}

	/*private*/ static abstract /*final*/ class PlatformPublicationsContainer<PublicationType extends Publication> extends ViewAdapter<PublicationType> implements PlatformPublications<PublicationType> {
		private final Names names;
		private final NamedDomainObjectRegistry<PublicationType> registry;

		@Inject
		public PlatformPublicationsContainer(Class<PublicationType> publicationType, Names names, NamedDomainObjectRegistry<PublicationType> registry, NamedDomainObjectCollection<PublicationType> collection, ProviderFactory providers, ObjectFactory objects) {
			super(publicationType, Publication::getName, collection, objects.newInstance(Finalizer.class), providers, objects);
			this.names = names;
			this.registry = registry;
		}

		@Override
		public NamedDomainObjectProvider<PublicationType> register(String name) {
			return register(names.append(name), registry::register);
		}

		@Override
		public NamedDomainObjectProvider<PublicationType> register(String name, Action<? super PublicationType> configureAction) {
			NamedDomainObjectProvider<PublicationType> result = register(names.append(name), registry::register);
			result.configure(configureAction);
			return result;
		}
	}
}
