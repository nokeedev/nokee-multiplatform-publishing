package dev.nokee.publishing.multiplatform.fixtures;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class MavenFileRepository implements MavenRepository {
    private final Path rootDirectory;

    public MavenFileRepository(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public URI getUri() {
        return rootDirectory.toUri();
    }

	@Override
	public MavenModule module(String groupId, String artifactId) {
		return module(groupId, artifactId, "1.0");
	}

	@Override
    public MavenModule module(String groupId, String artifactId, String version) {
        return new MavenModule() {
            @Override
            public boolean isPublished() {
                return Files.exists(rootDirectory.resolve(groupId.replace('.', '/')).resolve(artifactId).resolve(version));
            }

			@Override
			public ModuleArtifact artifact(Map<String, ?> options) {
				return new ModuleArtifact() {
					@Override
					public String getRelativePath() {
						// TODO: Support classifier
						return groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "." + options.get("type");
					}

					@Override
					public URI getUri() {
						return rootDirectory.resolve(getRelativePath()).toUri();
					}

					@Override
					public String getName() {
						return artifactId + "-" + version + ".xml";
					}
				};
			}

			@Override
            public ModuleArtifact getPom() {
                return new ModuleArtifact() {
                    @Override
                    public String getRelativePath() {
                        return groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".pom";
                    }

                    @Override
                    public URI getUri() {
                        return rootDirectory.resolve(getRelativePath()).toUri();
                    }

                    @Override
                    public String getName() {
                        return "pom-" + version + ".xml";
                    }
                };
            }

            @Override
            public String getGroupId() {
                return groupId;
            }

            @Override
            public String getArtifactId() {
                return artifactId;
            }

            @Override
            public String getVersion() {
                return version;
            }
        };
    }

	public static MavenFileRepository mavenRepository(Path repository) {
		return new MavenFileRepository(repository);
	}
}
