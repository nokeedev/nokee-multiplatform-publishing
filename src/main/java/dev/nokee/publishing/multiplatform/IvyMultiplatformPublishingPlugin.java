package dev.nokee.publishing.multiplatform;

import dev.nokee.publishing.multiplatform.ivy.IvyMultiplatformPublication;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.IvyPublication;

import javax.inject.Inject;

abstract /*final*/ class IvyMultiplatformPublishingPlugin implements Plugin<Project> {
	private final ObjectFactory objects;

	@Inject
	public IvyMultiplatformPublishingPlugin(ObjectFactory objects) {
		this.objects = objects;
	}

	@Override
	public void apply(Project project) {
		MultiplatformPublishingExtension extension = project.getExtensions().getByType(MultiplatformPublishingExtension.class);
		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
		extension.getPublications().registerFactory(IvyMultiplatformPublication.class, name -> {
			NamedDomainObjectProvider<IvyPublication> rootPublication = publishing.getPublications().register(name, IvyPublication.class);
			return objects.newInstance(Publication.class, name, rootPublication);
		});
	}

	/*private*/ static abstract /*final*/ class Publication implements IvyMultiplatformPublication {
		private final String name;
        private final NamedDomainObjectProvider<IvyPublication> rootPublication;

        @Inject
		public Publication(String name, NamedDomainObjectProvider<IvyPublication> rootPublication) {
			this.name = name;
            this.rootPublication = rootPublication;
        }

		@Override
		public VariantPublications getVariantPublications() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void rootPublication(Action<? super IvyPublication> configureAction) {
			throw new UnsupportedOperationException();
		}

		@Override
		public NamedDomainObjectProvider<IvyPublication> getRootPublication() {
			return rootPublication;
		}

		@Override
		public String getName() {
			return name;
		}
	}
}
