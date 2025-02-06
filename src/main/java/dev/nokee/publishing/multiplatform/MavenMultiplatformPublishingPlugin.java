package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.PolymorphicDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import dev.nokee.publishing.multiplatform.maven.MavenMultiplatformPublication;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

import javax.inject.Inject;
import java.util.Set;

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
			return objects.newInstance(DefaultPublication.class, Names.of(name), rootPublication, new PolymorphicDomainObjectRegistry<>(publishing.getPublications()));
		});
	}

	/*private*/ static abstract /*final*/ class DefaultPublication implements MavenMultiplatformPublication {
		private final Names names;
		private final NamedDomainObjectProvider<MavenPublication> rootPublication;
		private final VariantPublications variantPublications;

		@Inject
		public DefaultPublication(Names names, NamedDomainObjectProvider<MavenPublication> rootPublication, PolymorphicDomainObjectRegistry<Publication> registry) {
			this.names = names;
			this.rootPublication = rootPublication;
			this.variantPublications = new VariantPublications() {
				@Override
				public NamedDomainObjectProvider<MavenPublication> register(String name) {
					return registry.register(names.append(name).toString(), MavenPublication.class);
				}

				@Override
				public void configureEach(Action<? super MavenPublication> configureAction) {
					throw new UnsupportedOperationException();
				}

				@Override
				public Provider<Set<MavenPublication>> getElements() {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Override
		public VariantPublications getVariantPublications() {
			return variantPublications;
		}

		@Override
		public void rootPublication(Action<? super MavenPublication> configureAction) {
			rootPublication.configure(configureAction);
		}

		@Override
		public NamedDomainObjectProvider<MavenPublication> getRootPublication() {
			return rootPublication;
		}

		@Override
		public String getName() {
			return names.toString();
		}
	}
}
