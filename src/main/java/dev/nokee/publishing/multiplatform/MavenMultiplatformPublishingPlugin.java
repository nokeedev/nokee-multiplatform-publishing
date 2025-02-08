package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.NamedDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import dev.nokee.publishing.multiplatform.maven.MavenMultiplatformPublication;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskContainer;

import javax.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;

import static dev.nokee.publishing.multiplatform.MinimalGMVPublication.wrap;

abstract /*final*/ class MavenMultiplatformPublishingPlugin implements Plugin<Project> {
	private final ObjectFactory objects;
	private final TaskContainer tasks;

	@Inject
	public MavenMultiplatformPublishingPlugin(ObjectFactory objects, TaskContainer tasks) {
		this.objects = objects;
		this.tasks = tasks;
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
		private final Map<MinimalGMVPublication, String> variantArtifactIds = new LinkedHashMap<>();

		@Inject
		@SuppressWarnings("unchecked")
		public DefaultPublication(Names names, NamedDomainObjectProvider<MavenPublication> bridgePublication, NamedDomainObjectRegistry<MavenPublication> registry, NamedDomainObjectCollection<MavenPublication> collection, ObjectFactory objects) {
			super(names, bridgePublication, (PlatformPublicationsContainer<MavenPublication>) objects.newInstance(PlatformPublicationsContainer.class, MavenPublication.class, names, registry, collection));
		}

		@Override
		public String moduleNameOf(Publication platformPublication) {
			assert platformPublication instanceof MavenPublication;
			return variantArtifactIds.get(wrap(platformPublication));
		}

		@Override
		public Map<MinimalGMVPublication, String> getModuleNames() {
			return variantArtifactIds;
		}

		@Override
		public String toString() {
			return "Maven multiplatform publication '" + getName() + "'";
		}
	}
}
