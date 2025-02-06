package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.NamedDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import dev.nokee.publishing.multiplatform.maven.MavenMultiplatformPublication;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.*;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

import javax.inject.Inject;

import static dev.nokee.commons.provider.CollectionElementTransformer.transformEach;

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
			return objects.newInstance(DefaultPublication.class, Names.of(name), rootPublication, new NamedDomainObjectRegistry<>(publishing.getPublications().containerWithType(MavenPublication.class)), publishing.getPublications().withType(MavenPublication.class));
		});

		extension.getPublications().withType(DefaultPublication.class).configureEach(publication -> {
			publication.getVariantPublications().whenElementFinalized(variantPublication -> {
				final String variantName = StringGroovyMethods.uncapitalize(variantPublication.getName().substring(publication.getName().length()));
				variantPublication.setArtifactId(publication.getRootPublication().get().getArtifactId() + "_" + variantName);
				variantPublication.setGroupId(publication.getRootPublication().get().getGroupId());
			});
		});

		extension.getPublications().withType(MavenMultiplatformPublication.class).configureEach(publication -> {
			publication.getPlatforms().set(publication.getVariantPublications().getElements().map(transformEach(MavenPublication::getArtifactId)));
		});
	}

	/*private*/ static abstract /*final*/ class DefaultPublication implements MavenMultiplatformPublication {
		private final Names names;
		private final NamedDomainObjectProvider<MavenPublication> rootPublication;
		private final DefaultVariantPublications variantPublications;

		@Inject
		public DefaultPublication(Names names, NamedDomainObjectProvider<MavenPublication> rootPublication, NamedDomainObjectRegistry<MavenPublication> registry, NamedDomainObjectCollection<MavenPublication> collection, ObjectFactory objects) {
			this.names = names;
			this.rootPublication = rootPublication;
			this.variantPublications = objects.newInstance(DefaultVariantPublications.class, names, registry, collection);
		}

		@Override
		public DefaultVariantPublications getVariantPublications() {
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

		/*private*/ static abstract /*final*/ class DefaultVariantPublications extends AbstractVariantPublications<MavenPublication> implements VariantPublications {
			private final Names names;
			private final NamedDomainObjectRegistry<MavenPublication> registry;

			@Inject
			public DefaultVariantPublications(Names names, NamedDomainObjectRegistry<MavenPublication> registry, NamedDomainObjectCollection<MavenPublication> collection, ProviderFactory providers, ObjectFactory objects) {
				super(MavenPublication.class, collection, objects.newInstance(Finalizer.class), providers, objects);
				this.names = names;
				this.registry = registry;
			}

			@Override
			public NamedDomainObjectProvider<MavenPublication> register(String name) {
				return register(names.append(name), registry::register);
			}
		}
	}
}
