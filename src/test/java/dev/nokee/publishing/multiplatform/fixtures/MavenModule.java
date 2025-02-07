package dev.nokee.publishing.multiplatform.fixtures;

public interface MavenModule extends Module {
	/**
	 * Returns the POM file of this module.
	 */
    ModuleArtifact getPom();

    String getGroupId();

    String getArtifactId();

    String getVersion();
}
