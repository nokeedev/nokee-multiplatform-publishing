package dev.nokee.publishing.multiplatform.fixtures;

import java.net.URI;

public interface Repository {
    URI getUri();

    /**
     * Defaults version to '1.0'
     */
    Module module(String group, String module);

    Module module(String group, String module, String version);
}
