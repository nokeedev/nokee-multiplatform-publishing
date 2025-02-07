package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.collections.NamedDomainObjectRegistry;
import dev.nokee.commons.names.Names;
import dev.nokee.publishing.multiplatform.ivy.IvyMultiplatformPublication;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.*;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.IvyPublication;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static dev.nokee.commons.provider.CollectionElementTransformer.transformEach;

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
			return objects.newInstance(DefaultPublication.class, Names.of(name), rootPublication, new NamedDomainObjectRegistry<>(publishing.getPublications().containerWithType(IvyPublication.class)), publishing.getPublications().withType(IvyPublication.class));
		});

		extension.getPublications().withType(DefaultPublication.class).configureEach(publication -> {
			publication.getVariantPublications().configureEach(variantPublication -> {
				publication.variantModules.put(variantPublication, variantPublication.getModule());
			});
			publication.getVariantPublications().whenElementFinalized(variantPublication -> {
				publication.variantModules.compute(variantPublication, (k, v) -> {
					assert v != null;
					// no change, use default value
					if (v.equals(k.getModule())) {
						final String variantName = StringGroovyMethods.uncapitalize(variantPublication.getName().substring(publication.getName().length()));
						return publication.getRootPublication().get().getModule() + "_" + variantName;
					}
					return k.getModule(); // use overwritten value
				});

				variantPublication.setModule(publication.getRootPublication().get().getModule());
				variantPublication.setOrganisation(publication.getRootPublication().get().getOrganisation());
				variantPublication.setRevision(publication.getRootPublication().get().getRevision());
			});
			publication.getPlatforms().set(publication.getVariantPublications().getElements().map(transformEach(publication.variantModules::get)));
		});
	}

	/*private*/ static abstract /*final*/ class DefaultPublication implements IvyMultiplatformPublication, MultiplatformPublicationInternal {
		private final String name;
		private final NamedDomainObjectProvider<IvyPublication> rootPublication;
		private final DefaultVariantPublications variantPublications;
		private final Map<IvyPublication, String> variantModules = new HashMap<>();

		@Inject
		public DefaultPublication(Names names, NamedDomainObjectProvider<IvyPublication> rootPublication, NamedDomainObjectRegistry<IvyPublication> registry, NamedDomainObjectCollection<IvyPublication> collection, ObjectFactory objects) {
			this.name = names.toString();
			this.rootPublication = rootPublication;
			this.variantPublications = objects.newInstance(DefaultVariantPublications.class, names, registry, collection);
		}

		@Override
		public String moduleNameOf(Publication variantPublication) {
			assert variantPublication instanceof IvyPublication;
			return variantModules.get(variantPublication);
		}

		@Override
		public DefaultVariantPublications getVariantPublications() {
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
			return name;
		}

		/*private*/ static abstract /*final*/ class DefaultVariantPublications extends AbstractVariantPublications<IvyPublication> implements VariantPublications {
			private final Names names;
			private final NamedDomainObjectRegistry<IvyPublication> registry;

			@Inject
			public DefaultVariantPublications(Names names, NamedDomainObjectRegistry<IvyPublication> registry, NamedDomainObjectCollection<IvyPublication> collection, ProviderFactory providers, ObjectFactory objects) {
				super(IvyPublication.class, collection, objects.newInstance(Finalizer.class), providers, objects);
				this.names = names;
				this.registry = registry;
			}

			@Override
			public NamedDomainObjectProvider<IvyPublication> register(String name) {
				return register(names.append(name), registry::register);
			}

			@Override
			public NamedDomainObjectProvider<IvyPublication> register(String name, Action<? super IvyPublication> configureAction) {
				NamedDomainObjectProvider<IvyPublication> result = register(names.append(name), registry::register);
				result.configure(configureAction);
				return result;
			}
		}
	}
}
