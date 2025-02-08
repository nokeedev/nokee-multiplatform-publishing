import dev.nokee.publishing.multiplatform.ForMultiplatformClosure
import dev.nokee.publishing.multiplatform.MultiplatformPublication
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublicationContainer

inline fun <reified S : Publication> Project.forMultiplatform(
	name: String,
	noinline configureAction: MultiplatformPublication<S>.() -> Unit
): Action<PublicationContainer> {
	return (this.extensions.extraProperties.get("forMultiplatform") as ForMultiplatformClosure).call(name, S::class.java, configureAction)
}
