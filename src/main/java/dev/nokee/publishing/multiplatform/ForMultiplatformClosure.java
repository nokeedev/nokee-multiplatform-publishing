package dev.nokee.publishing.multiplatform;

import org.gradle.api.Action;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublicationContainer;

/**
 * Represents the {@literal forMultiplatform} closure configured by {@literal dev.nokee.multiplatform-publishing} plugin.
 *
 * <pre>
 * <code>
 * publishing {
 *     publications forMultiplatform('cpp', MavenPublication) {
 *         // ...
 *     }
 * }
 * </code>
 * </pre>
 */
public interface ForMultiplatformClosure {
	/**
	 * Returns a configure action that register a multiplatform publication if absent.
	 *
	 * @param name  the multiplatform publication name
	 * @param type  the multiplatform publication type
	 * @return a configure action to use with {@link org.gradle.api.publish.PublishingExtension#publications(Action)}.
	 * @param <PublicationType>  the publication type
	 */
	<PublicationType extends Publication> Action<PublicationContainer> call(String name, Class<PublicationType> type);

	/**
	 * Returns a configure action that register a multiplatform publication if absent and configure it.
	 *
	 * @param name  the multiplatform publication name
	 * @param type  the multiplatform publication type
	 * @param configureAction  the configure action for the multiplatform publication
	 * @return a configure action to use with {@link org.gradle.api.publish.PublishingExtension#publications(Action)}.
	 * @param <PublicationType>  the publication type
	 */
	<PublicationType extends Publication> Action<PublicationContainer> call(String name, Class<PublicationType> type, Action<? super MultiplatformPublication<PublicationType>> configureAction);
}
