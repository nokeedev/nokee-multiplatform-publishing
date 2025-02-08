package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.NamedDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.maven.MavenPublication;

import javax.inject.Inject;

/*private*/ abstract /*final*/ class MavenMultiplatformPublication extends AbstractMultiplatformPublication<MavenPublication> implements MultiplatformPublication<MavenPublication>, PlatformAwarePublication, MultiplatformPublicationInternal {
	@Inject
	@SuppressWarnings("unchecked")
	public MavenMultiplatformPublication(Names names, NamedDomainObjectProvider<MavenPublication> bridgePublication, NamedDomainObjectRegistry<MavenPublication> registry, NamedDomainObjectCollection<MavenPublication> collection, ObjectFactory objects) {
		super(names, bridgePublication, objects.newInstance(PlatformPublicationsContainer.class, MavenPublication.class, names, registry, collection));
	}

	@Override
	public String toString() {
		return "Maven multiplatform publication '" + getName() + "'";
	}
}
