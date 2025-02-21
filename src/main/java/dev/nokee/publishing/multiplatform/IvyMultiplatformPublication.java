package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.NamedDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.ivy.IvyPublication;

import javax.inject.Inject;

/*private*/ abstract /*final*/ class IvyMultiplatformPublication extends AbstractMultiplatformPublication<IvyPublication> implements MultiplatformPublication<IvyPublication>, MultiplatformPublicationInternal {
	@Inject
	@SuppressWarnings("unchecked")
	public IvyMultiplatformPublication(Names names, NamedDomainObjectProvider<IvyPublication> bridgePublication, NamedDomainObjectRegistry<IvyPublication> registry, NamedDomainObjectCollection<IvyPublication> collection, ObjectFactory objects) {
		super(names, bridgePublication, objects.newInstance(PlatformPublicationsContainer.class, IvyPublication.class, names, registry, collection));
	}

	@Override
	public String toString() {
		return "Ivy multiplatform publication '" + getName() + "'";
	}
}
