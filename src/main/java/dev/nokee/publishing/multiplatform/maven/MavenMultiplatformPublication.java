package dev.nokee.publishing.multiplatform.maven;

import dev.nokee.publishing.multiplatform.MultiplatformPublication;
import dev.nokee.publishing.multiplatform.PlatformAwarePublication;
import org.gradle.api.publish.maven.MavenPublication;

public interface MavenMultiplatformPublication extends MultiplatformPublication<MavenPublication>, PlatformAwarePublication {}
