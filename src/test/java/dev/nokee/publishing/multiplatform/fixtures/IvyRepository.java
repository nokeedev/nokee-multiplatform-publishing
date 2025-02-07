package dev.nokee.publishing.multiplatform.fixtures;

public interface IvyRepository extends Repository {
	String getArtifactPattern();

	String getIvyPattern();

	@Override
	default IvyModule module(String organisation, String module) {
		return module(organisation, module, "1.0");
	}

	@Override
	IvyModule module(String organisation, String module, String revision);
}
