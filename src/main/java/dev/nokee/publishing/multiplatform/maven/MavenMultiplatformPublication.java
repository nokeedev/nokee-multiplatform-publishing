package dev.nokee.publishing.multiplatform.maven;

import dev.nokee.platform.base.View;
import dev.nokee.publishing.multiplatform.MultiplatformPublication;
import dev.nokee.publishing.multiplatform.PlatformAwarePublication;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.publish.maven.MavenPublication;

public interface MavenMultiplatformPublication extends MultiplatformPublication, PlatformAwarePublication {
	void rootPublication(Action<? super MavenPublication> configureAction);
	NamedDomainObjectProvider<MavenPublication> getRootPublication();

	PlatformPublications getPlatformPublication();

	interface PlatformPublications extends View<MavenPublication> {
		NamedDomainObjectProvider<MavenPublication> register(String name);
		NamedDomainObjectProvider<MavenPublication> register(String name, Action<? super MavenPublication> configureAction);
	}
}
