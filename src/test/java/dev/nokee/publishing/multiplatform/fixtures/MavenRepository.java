package dev.nokee.publishing.multiplatform.fixtures;

/**
 * A fixture for dealing with Maven repositories.
 */
public interface MavenRepository extends Repository {
	@Override
	default MavenModule module(String groupId, String artifactId) {
		return module(groupId, artifactId, "1.0");
	}

	@Override
	MavenModule module(String groupId, String artifactId, String version);
}
