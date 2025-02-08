package dev.nokee.publishing.multiplatform;

import org.gradle.api.publish.Publication;

import java.util.Map;

interface MultiplatformPublicationInternal {
	String moduleNameOf(Publication platformPublication);
	Map<MinimalGMVPublication, String> getModuleNames();
}
