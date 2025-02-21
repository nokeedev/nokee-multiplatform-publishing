# Multiplatform Publishing Plugin

## Usage

The plugin works in tandem with core publishing plugins: `maven-publish` and `ivy-publish`.

### Maven Publish

```groovy
plugins {
	id 'dev.nokee.multiplatform-publishing'
	id 'maven-publish'
}

publishing {
	publications forMultiplatform('cpp', MavenPublication) {
		/* see ??? */
	}
}
```

### Ivy Publish

```groovy
plugins {
	id 'dev.nokee.multiplatform-publishing'
	id 'ivy-publish'
}

publishing {
	publications forMultiplatform('cpp', IvyPublication) {
		/* see ??? */
	}
}
```

## Publications

One bridge publication matching the name of the multiplatform publication, then zero or more platform publications.
Despite modeling a multiplatform publication as multiple standard Gradle publication, there are a few constraint that developers must follow:

1. All publications (bridge and platforms) must have the same group id (Maven) or organization (Ivy)
2. All publications (bridge and platforms) must have the same version (Maven) or revision (Ivy)
3. All platform publications must be published _before_ the bridge publication (except for Maven local publishing)

In cases where not all platform publications are available during publications (i.e. multi-machine publications - native ecosystem), developers **must** declare all platform artifact names.
It ensures we don't publish the bridge publication before all platform publications.
We infer the variants in the bridge publication from the published platform publications' metadata.
It also ensures a full atomic publications from the consumer's point of view.

## Extensions

The plugin adds an extensions `ForMultiplatformClosure` to register multiplatform publication on Gradle's standard publishing extension (see Usage section).
