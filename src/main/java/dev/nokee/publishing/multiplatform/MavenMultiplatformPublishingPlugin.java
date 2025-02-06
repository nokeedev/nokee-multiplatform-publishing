package dev.nokee.publishing.multiplatform;

import dev.nokee.publishing.multiplatform.maven.MavenMultiplatformPublication;
import org.gradle.api.Action;
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
			NamedDomainObjectProvider<MavenPublication> rootPublication = publishing.getPublications().register(name, MavenPublication.class);
			return objects.newInstance(Publication.class, name, rootPublication);
		});
	}

	/*private*/ static abstract /*final*/ class Publication implements MavenMultiplatformPublication {
		private final String name;
		private final NamedDomainObjectProvider<MavenPublication> rootPublication;

		@Inject
		public Publication(String name, NamedDomainObjectProvider<MavenPublication> rootPublication) {
			this.name = name;
			this.rootPublication = rootPublication;
		}

		@Override
		public VariantPublications getVariantPublications() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void rootPublication(Action<? super MavenPublication> configureAction) {
			throw new UnsupportedOperationException();
		}

		@Override
		public NamedDomainObjectProvider<MavenPublication> getRootPublication() {
			return rootPublication;
		}

		@Override
		public String getName() {
			return name;
		}
	}
}
