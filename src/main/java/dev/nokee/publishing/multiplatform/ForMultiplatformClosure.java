package dev.nokee.publishing.multiplatform;

import dev.nokee.publishing.multiplatform.ivy.IvyMultiplatformPublication;
import dev.nokee.publishing.multiplatform.maven.MavenMultiplatformPublication;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.maven.MavenPublication;

import javax.inject.Inject;

abstract /*final*/ class ForMultiplatformClosure {
	private final MultiplatformPublishingExtension multiplatform;

	@Inject
	public ForMultiplatformClosure(MultiplatformPublishingExtension multiplatform) {
		this.multiplatform = multiplatform;
	}

	public <T extends Publication> Action<PublicationContainer> call(String name, Class<T> type) {
		return call(name, type, ignored(() -> {}));
	}

	public <T extends Publication> Action<PublicationContainer> call(String name, Class<T> type, Action<? super MultiplatformPublication<T>> action) {
		return publications -> {
			Class<? extends MultiplatformPublication<?>> implementationType = null;
			if (type.equals(MavenPublication.class)) {
				implementationType = MavenMultiplatformPublication.class;
			} else if (type.equals(IvyPublication.class)) {
				implementationType = IvyMultiplatformPublication.class;
			} else {
				throw new UnsupportedOperationException("Unsupported publication type");
			}

			final NamedDomainObjectProvider<? extends MultiplatformPublication<?>> result;
			if (!multiplatform.getPublications().getNames().contains(name)) {
				result = multiplatform.getPublications().register(name, implementationType);//objects.newInstance(implementationType, name, publications.register(name, type), publications);
			} else {
				result = multiplatform.getPublications().named(name, implementationType);
			}

			((NamedDomainObjectProvider<MultiplatformPublication<T>>) result).configure(action);
		};
	}

//	public <T extends Publication> Action<PublicationContainer> call(AdhocComponentWithVariants mainComponent, Class<T> type) {
//		return call(mainComponent, type, ignored(() -> {}));
//	}
//
//	public <T extends Publication> Action<PublicationContainer> call(AdhocComponentWithVariants mainComponent, Class<T> type, Action<? super Multiplatform<T>> action) {
//		return call(mainComponent.getName(), type, o -> {
//			o.from(mainComponent);
//			o.getRootPublication().configure(it -> {
//				if (it instanceof MavenPublication) {
//					((MavenPublication) it).setArtifactId(project.getName());
//					((MavenPublication) it).suppressAllPomMetadataWarnings();
//				} else if (it instanceof IvyPublication) {
//					((IvyPublication) it).setModule(project.getName());
//					((IvyPublication) it).suppressAllIvyMetadataWarnings();
//				}
//			});
//
//			// TODO: Match cpp[CapitalLetter] as we assume cpp as prefix delimited by CamelCase
//			DomainObjectCollection<AdhocComponentWithVariants> variantComponents = project.getComponents().withType(AdhocComponentWithVariants.class).matching(it -> it.getName().startsWith(mainComponent.getName()) && !it.getName().equals(mainComponent.getName()));
//
//			variantComponents.all(component -> {
//				o.getVariantPublications().register(component.getName().substring(mainComponent.getName().length()), it -> {
//					if (it instanceof MavenPublication) {
//						((MavenPublication) it).from(component);
//						((MavenPublication) it).setArtifactId(project.getName() + "_" + uncapitalize(component.getName().substring(mainComponent.getName().length())));
//						((MavenPublication) it).suppressAllPomMetadataWarnings();
//
//						// TODO: Use "public api" for this internal API
//						((MavenPublicationInternal) it).publishWithOriginalFileName();
//					} else if (it instanceof IvyPublication) {
//						((IvyPublication) it).from(component);
//						((IvyPublication) it).setModule(project.getName() + "_" + uncapitalize(component.getName().substring(mainComponent.getName().length())));
//						((IvyPublication) it).suppressAllIvyMetadataWarnings();
//
//						// TODO: Approximate the publishWithOriginalFileName API
//					}
//				});
//			});
//
//			action.execute(o);
//		});
//	}

	// TODO: Move to nokee-commons
	private static <T> Action<T> ignored(Runnable runnable) {
		return new Action<T>() {
			@Override
			public void execute(T ignored) {
				runnable.run();
			}
		};
	}
}
