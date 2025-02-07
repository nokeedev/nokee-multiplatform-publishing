package dev.nokee.publishing.multiplatform;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.plugins.PublishingPlugin;

import javax.inject.Inject;

/*private*/ abstract /*final*/ class MultiplatformPublishingPlugin implements Plugin<Project> {
	@Inject
	public MultiplatformPublishingPlugin() {}

	@Override
	public void apply(Project project) {
		project.getPluginManager().apply(PublishingPlugin.class); // because we are a publishing plugin

		MultiplatformPublishingExtension extension = project.getExtensions().create("multiplatform", MultiplatformPublishingExtension.class);

		project.getPluginManager().withPlugin("maven-publish", ignored(() -> {
			project.getPluginManager().apply(MavenMultiplatformPublishingPlugin.class);
		}));
		project.getPluginManager().withPlugin("ivy-publish", ignored(() -> {
			project.getPluginManager().apply(IvyMultiplatformPublishingPlugin.class);
		}));

		project.afterEvaluate(ignored(() -> {
			extension.getPublications().all(ignored(() -> {}));
		}));
	}

	// TODO: Move to nokee-commons
	private static <T> Action<T> ignored(Runnable runnable) {
		return new Action<T>() {
			@Override
			public void execute(T ignored) {
				runnable.run();
			}
		};
	}
}
