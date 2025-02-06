package dev.nokee.publishing.multiplatform;

import org.gradle.api.provider.SetProperty;

public interface PlatformAwarePublication {
	SetProperty<String> getPlatforms();
}
