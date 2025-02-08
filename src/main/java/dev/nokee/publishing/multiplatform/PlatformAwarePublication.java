package dev.nokee.publishing.multiplatform;

import org.gradle.api.provider.SetProperty;

interface PlatformAwarePublication {
	SetProperty<String> getPlatforms();
}
