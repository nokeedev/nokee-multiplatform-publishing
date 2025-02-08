package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.NamedDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import dev.nokee.publishing.multiplatform.maven.MavenMultiplatformPublication;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

import javax.inject.Inject;

abstract /*final*/ class MavenMultiplatformPublishingPlugin implements Plugin<Project> {
	private final ObjectFactory objects;

	@Inject
	public MavenMultiplatformPublishingPlugin(ObjectFactory objects) {
		this.objects = objects;
	}

	@Override
	public void apply(Project project) {
		MultiplatformPublishingExtension extension = project.getExtensions().getByType(MultiplatformPublishingExtension.class);
		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
		extension.getPublications().registerFactory(MavenMultiplatformPublication.class, name -> {
			NamedDomainObjectProvider<MavenPublication> bridgePublication = publishing.getPublications().register(name, MavenPublication.class);
			return objects.newInstance(DefaultPublication.class, Names.of(name), bridgePublication, new NamedDomainObjectRegistry<>(publishing.getPublications().containerWithType(MavenPublication.class)), publishing.getPublications().withType(MavenPublication.class));
		});
	}

	/*private*/ static abstract /*final*/ class DefaultPublication extends AbstractMultiplatformPublication<MavenPublication> implements MavenMultiplatformPublication, MultiplatformPublicationInternal {
		@Inject
		@SuppressWarnings("unchecked")
		public DefaultPublication(Names names, NamedDomainObjectProvider<MavenPublication> bridgePublication, NamedDomainObjectRegistry<MavenPublication> registry, NamedDomainObjectCollection<MavenPublication> collection, ObjectFactory objects) {
			super(names, bridgePublication, (PlatformPublicationsContainer<MavenPublication>) objects.newInstance(PlatformPublicationsContainer.class, MavenPublication.class, names, registry, collection));
		}

		@Override
		public String toString() {
			return "Maven multiplatform publication '" + getName() + "'";
		}
	}
}
