package dev.nokee.publishing.multiplatform.ivy;

import dev.nokee.publishing.multiplatform.MultiplatformPublication;
import dev.nokee.publishing.multiplatform.PlatformAwarePublication;
import org.gradle.api.publish.ivy.IvyPublication;

public interface IvyMultiplatformPublication extends MultiplatformPublication<IvyPublication>, PlatformAwarePublication {}
