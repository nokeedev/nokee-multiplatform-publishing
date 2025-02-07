package dev.nokee.publishing.multiplatform.fixtures;

/**
 * A fixture for dealing with Maven repositories.
 */
public interface MavenRepository extends Repository {
	@Override
	MavenModule module(String groupId, String artifactId);

	@Override
	MavenModule module(String groupId, String artifactId, String version);
}
