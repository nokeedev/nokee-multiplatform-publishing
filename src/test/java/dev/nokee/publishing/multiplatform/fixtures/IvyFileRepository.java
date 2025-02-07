package dev.nokee.publishing.multiplatform.fixtures;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class IvyFileRepository implements IvyRepository {
	private final String dirPattner = "[organisation]/[module]/[revision]";
	private final String ivyFilePattern = "ivy-[revision].xml";
	private final String artifactFilePattern = "[artifact]-[revision](-[classifier])(.[ext])";
	private final Path rootDirectory;

	public IvyFileRepository(Path rootDirectory) {
		this.rootDirectory = rootDirectory;
	}

	@Override
	public String getArtifactPattern() {
		return artifactFilePattern;
	}

	@Override
	public String getIvyPattern() {
		return ivyFilePattern;
	}

	@Override
	public URI getUri() {
		return rootDirectory.toUri();
	}

	@Override
	public IvyModule module(String organisation, String module, String revision) {
		return new IvyModule() {
			@Override
			public String getOrganisation() {
				return organisation;
			}

			@Override
			public String getModule() {
				return module;
			}

			@Override
			public String getRevision() {
				return revision;
			}

			@Override
			public boolean isPublished() {
				return Files.exists(rootDirectory.resolve(organisation).resolve(module).resolve(revision));
			}

			@Override
			public ModuleArtifact artifact(Map<String, ?> options) {
				return new ModuleArtifact() {
					@Override
					public String getRelativePath() {
						return organisation + "/" + module + "/" + revision + "/" + options.get("name") + "-" + revision + "." + options.get("ext");
					}

					@Override
					public URI getUri() {
						return rootDirectory.resolve(getRelativePath()).toUri();
					}

					@Override
					public String getName() {
						return options.get("name") + "-" + revision + "." + options.get("ext");
					}
				};
			}
		};
	}

	public static IvyFileRepository ivyRepository(Path repository) {
		return new IvyFileRepository(repository);
	}
}
