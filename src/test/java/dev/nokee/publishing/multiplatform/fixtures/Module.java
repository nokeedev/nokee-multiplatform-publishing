package dev.nokee.publishing.multiplatform.fixtures;

import java.util.Map;

/**
 * Represents a module in a repository.
 */
public interface Module {
	boolean isPublished();
	ModuleArtifact artifact(Map<String, ?> options);
}
