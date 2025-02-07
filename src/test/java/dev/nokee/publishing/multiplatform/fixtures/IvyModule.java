package dev.nokee.publishing.multiplatform.fixtures;

import java.util.Map;

public interface IvyModule extends Module {
	String getOrganisation();
	String getModule();
	String getRevision();

	default ModuleArtifact getModuleMetadata() {
		return artifact(Map.of("name", getModule(), "type", "module", "ext", "module"));
	}
}
