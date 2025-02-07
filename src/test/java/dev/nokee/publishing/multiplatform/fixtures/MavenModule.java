package dev.nokee.publishing.multiplatform.fixtures;

import java.util.Map;

public interface MavenModule extends Module {
	/**
	 * Returns the POM file of this module.
	 */
    ModuleArtifact getPom();

    String getGroupId();

    String getArtifactId();

    String getVersion();

	default ModuleArtifact getModuleMetadata() {
		return artifact(Map.of("type", "module"));
	}
}
