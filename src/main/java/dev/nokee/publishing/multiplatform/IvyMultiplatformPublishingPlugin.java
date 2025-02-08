package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.NamedDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import dev.nokee.publishing.multiplatform.ivy.IvyMultiplatformPublication;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.tasks.TaskContainer;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static dev.nokee.publishing.multiplatform.MinimalGMVPublication.wrap;

abstract /*final*/ class IvyMultiplatformPublishingPlugin implements Plugin<Project> {
	private final ObjectFactory objects;
    private final TaskContainer tasks;

    @Inject
	public IvyMultiplatformPublishingPlugin(ObjectFactory objects, TaskContainer tasks) {
		this.objects = objects;
        this.tasks = tasks;
    }

	@Override
	public void apply(Project project) {
		MultiplatformPublishingExtension extension = project.getExtensions().getByType(MultiplatformPublishingExtension.class);
		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
		extension.getPublications().registerFactory(IvyMultiplatformPublication.class, name -> {
			NamedDomainObjectProvider<IvyPublication> bridgePublication = publishing.getPublications().register(name, IvyPublication.class);
			return objects.newInstance(DefaultPublication.class, Names.of(name), bridgePublication, new NamedDomainObjectRegistry<>(publishing.getPublications().containerWithType(IvyPublication.class)), publishing.getPublications().withType(IvyPublication.class));
		});
	}

	/*private*/ static abstract /*final*/ class DefaultPublication extends AbstractMultiplatformPublication<IvyPublication> implements IvyMultiplatformPublication, MultiplatformPublicationInternal {
		private final Map<MinimalGMVPublication, String> variantModules = new HashMap<>();

		@Inject
		@SuppressWarnings("unchecked")
		public DefaultPublication(Names names, NamedDomainObjectProvider<IvyPublication> bridgePublication, NamedDomainObjectRegistry<IvyPublication> registry, NamedDomainObjectCollection<IvyPublication> collection, ObjectFactory objects) {
			super(names, bridgePublication, objects.newInstance(PlatformPublicationsContainer.class, IvyPublication.class, names, registry, collection));
		}

		@Override
		public String moduleNameOf(Publication platformPublication) {
			assert platformPublication instanceof IvyPublication;
			return variantModules.get(wrap(platformPublication));
		}

		@Override
		public Map<MinimalGMVPublication, String> getModuleNames() {
			return variantModules;
		}

		@Override
		public String toString() {
			return "Ivy multiplatform publication '" + getName() + "'";
		}
	}
}
