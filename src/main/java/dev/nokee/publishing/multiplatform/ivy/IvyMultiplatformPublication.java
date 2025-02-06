package dev.nokee.publishing.multiplatform.ivy;

import dev.nokee.platform.base.View;
import dev.nokee.publishing.multiplatform.MultiplatformPublication;
import dev.nokee.publishing.multiplatform.PlatformAwarePublication;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.publish.ivy.IvyPublication;

public interface IvyMultiplatformPublication extends MultiplatformPublication, PlatformAwarePublication {
	void rootPublication(Action<? super IvyPublication> configureAction);
	NamedDomainObjectProvider<IvyPublication> getRootPublication();

	VariantPublications getVariantPublications();

	interface VariantPublications extends View<IvyPublication> {
		NamedDomainObjectProvider<IvyPublication> register(String name);
	}
}
