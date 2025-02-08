package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.NamedDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import dev.nokee.publishing.multiplatform.maven.MavenMultiplatformPublication;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.maven.MavenPublication;

import javax.inject.Inject;

/*private*/ abstract /*final*/ class DefaultMavenMultiplatformPublication extends AbstractMultiplatformPublication<MavenPublication> implements MavenMultiplatformPublication, MultiplatformPublicationInternal {
	@Inject
	@SuppressWarnings("unchecked")
	public DefaultMavenMultiplatformPublication(Names names, NamedDomainObjectProvider<MavenPublication> bridgePublication, NamedDomainObjectRegistry<MavenPublication> registry, NamedDomainObjectCollection<MavenPublication> collection, ObjectFactory objects) {
		super(names, bridgePublication, (PlatformPublicationsContainer<MavenPublication>) objects.newInstance(PlatformPublicationsContainer.class, MavenPublication.class, names, registry, collection));
	}

	@Override
	public String toString() {
		return "Maven multiplatform publication '" + getName() + "'";
	}
}
