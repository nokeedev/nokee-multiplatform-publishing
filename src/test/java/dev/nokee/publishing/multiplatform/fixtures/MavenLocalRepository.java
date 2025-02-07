/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.nokee.publishing.multiplatform.fixtures;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * A fixture for dealing with the Maven Local cache.
 */
public class MavenLocalRepository implements MavenRepository {
    private final Path rootDirectory;

    public MavenLocalRepository(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public URI getUri() {
        return rootDirectory.toUri();
    }

    public Path getRootDirectory() {
        return rootDirectory;
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
}
