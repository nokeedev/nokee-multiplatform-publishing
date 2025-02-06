package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.PolymorphicDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import dev.nokee.publishing.multiplatform.ivy.IvyMultiplatformPublication;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.IvyPublication;

import javax.inject.Inject;
import java.util.Set;

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
			return objects.newInstance(DefaultPublication.class, Names.of(name), rootPublication, new PolymorphicDomainObjectRegistry<>(publishing.getPublications()));
		});
	}

	/*private*/ static abstract /*final*/ class DefaultPublication implements IvyMultiplatformPublication {
		private final Names names;
        private final NamedDomainObjectProvider<IvyPublication> rootPublication;
		private final VariantPublications variantPublications;

        @Inject
		public DefaultPublication(Names names, NamedDomainObjectProvider<IvyPublication> rootPublication, PolymorphicDomainObjectRegistry<Publication> registry) {
			this.names = names;
            this.rootPublication = rootPublication;
			this.variantPublications = new VariantPublications() {
				@Override
				public NamedDomainObjectProvider<IvyPublication> register(String name) {
					return registry.register(names.append(name).toString(), IvyPublication.class);
				}

				@Override
				public void configureEach(Action<? super IvyPublication> configureAction) {
					throw new UnsupportedOperationException();
				}

				@Override
				public Provider<Set<IvyPublication>> getElements() {
					throw new UnsupportedOperationException();
				}
			};
        }

		@Override
		public VariantPublications getVariantPublications() {
			return variantPublications;
		}

		@Override
		public void rootPublication(Action<? super IvyPublication> configureAction) {
			rootPublication.configure(configureAction);
		}

		@Override
		public NamedDomainObjectProvider<IvyPublication> getRootPublication() {
			return rootPublication;
		}

		@Override
		public String getName() {
			return names.toString();
		}
	}
}
