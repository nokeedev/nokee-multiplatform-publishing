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

import dev.gradleplugins.runnerkit.GradleRunner;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

public class M2Installation implements UnaryOperator<GradleRunner> {
    private final Path testDirectory;
    private boolean initialized = false;
    private Path userHomeDirectory;
    private Path userM2Directory;
    private Path userSettingsFile;
    private Path globalMavenDirectory;
    private Path globalSettingsFile;
    private Path isolatedMavenRepoForLeakageChecks;
    private boolean isolateMavenLocal = true;

    public M2Installation(Path testDirectory) {
        this.testDirectory = testDirectory;
    }

    private void init() {
        if (!initialized) {
			try {
				userHomeDirectory = Files.createDirectories(testDirectory.resolve("maven_home"));
				userM2Directory = Files.createDirectories(userHomeDirectory.resolve(".m2"));
				userSettingsFile = userM2Directory.resolve("settings.xml");
				globalMavenDirectory = Files.createDirectories(userHomeDirectory.resolve("m2_home"));
				globalSettingsFile = globalMavenDirectory.resolve("conf/settings.xml");
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			System.out.println("M2 home: " + userHomeDirectory);

            initialized = true;
        }
    }

    public Path getUserHomeDir() {
        init();
        return userHomeDirectory;
    }

    public Path getUserM2Directory() {
        init();
        return userM2Directory;
    }

    public Path getUserSettingsFile() {
        init();
        return userSettingsFile;
    }

    public Path getGlobalMavenDirectory() {
        init();
        return globalMavenDirectory;
    }

    public Path getGlobalSettingsFile() {
        init();
        return globalSettingsFile;
    }

    public MavenLocalRepository mavenRepo() {
        init();
        return new MavenLocalRepository(userM2Directory.resolve("repository"));
    }

    public M2Installation generateUserSettingsFile(MavenLocalRepository userRepository) {
        init();
		try {
			Files.writeString(userSettingsFile,
				"<settings>\n"
				+ "    <localRepository>" + userRepository.getRootDirectory().toAbsolutePath() + "</localRepository>\n"
				+ "</settings>");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
    }

    public M2Installation generateGlobalSettingsFile() {
        return generateGlobalSettingsFile(mavenRepo());
    }

    public M2Installation generateGlobalSettingsFile(MavenLocalRepository globalRepository) {
        init();
		try {
			Files.writeString(globalSettingsFile,
				"<settings>\n"
					+ "    <localRepository>" + globalRepository.getRootDirectory().toAbsolutePath() + "</localRepository>\n"
					+ "</settings>");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
    }

    @Override
    public GradleRunner apply(GradleRunner gradleExecuter) {
        init();
        GradleRunner result = gradleExecuter.withUserHomeDirectory(userHomeDirectory.toFile());
        // if call `using m2`, then we disable the automatic isolation of m2
        isolateMavenLocal = false;
        if (Files.exists(globalMavenDirectory)) {
            result = result.withEnvironmentVariable("M2_HOME", globalMavenDirectory.toAbsolutePath().toString());
        }
        return result;
    }

    public GradleRunner isolateMavenLocalRepo(GradleRunner gradleExecuter) {
        gradleExecuter = gradleExecuter.beforeExecute(executer -> {
            if (isolateMavenLocal) {
				try {
					isolatedMavenRepoForLeakageChecks = Files.createDirectories(executer.getWorkingDirectory().toPath().resolve("m2-home-should-not-be-filled"));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
				return setMavenLocalLocation(executer, isolatedMavenRepoForLeakageChecks);
            }
            return executer;
        });
        gradleExecuter = gradleExecuter.afterExecute(executer -> {
            if (isolateMavenLocal) {
				if (!isEmptyDirectory(isolatedMavenRepoForLeakageChecks)) {
					throw new UnsupportedOperationException(String.format("%s is not an empty directory.", isolatedMavenRepoForLeakageChecks));
				}
            }
        });

        return gradleExecuter;
    }

	private static boolean isEmptyDirectory(Path dir) {
		File[] dirListing = dir.toFile().listFiles();
		return dirListing != null && dirListing.length == 0;
	}

    private static GradleRunner setMavenLocalLocation(GradleRunner runner, Path destination) {
        return runner.withArgument("-Dmaven.repo.local=" + destination.toAbsolutePath());
    }
}

