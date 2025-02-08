package dev.nokee.publishing.multiplatform;

import dev.nokee.commons.names.FullyQualifiedName;
import dev.nokee.platform.base.View;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.Namer;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.Publication;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

class ViewAdapter<T> implements ViewInternal, View<T> {
	private final Class<T> elementType;
	private final Namer<T> namer;
	private final NamedDomainObjectCollection<T> collection;
	private final ProviderFactory providers;
	private final ObjectFactory objects;
	private final Set<String> knownElements = new LinkedHashSet<>();
	private final Finalizer finalizer;

	protected ViewAdapter(Class<T> elementType, Namer<T> namer, NamedDomainObjectCollection<T> collection, Finalizer finalizer, ProviderFactory providers, ObjectFactory objects) {
		this.elementType = elementType;
		this.namer = namer;
		this.collection = collection;
		this.finalizer = finalizer;
		this.providers = providers;
		this.objects = objects;
	}

	protected <R> R register(FullyQualifiedName fullName, Function<? super String, ? extends R> action) {
		// TODO: assert not finalized
		knownElements.add(fullName.toString());
		return action.apply(fullName.toString());
	}

	@Override
	public void configureEach(Action<? super T> configureAction) {
		// TODO: Assert not finalized
		doConfigureEach(configureAction);
	}

	private void doConfigureEach(Action<? super T> configureAction) {
		collection.configureEach(it -> {
			if (knownElements.contains(namer.determineName(it))) {
				configureAction.execute(it);
			}
		});
	}

	public void whenElementFinalized(Action<? super T> finalizeAction) {
		// TODO: What does it means to add a finalized action when view is finalized?
		finalizer.whenFinalizing(() -> doConfigureEach(finalizeAction));
	}

	@Override
	public Provider<Set<T>> getElements() {
		return providers.provider(() -> {
			finalizeNow();
			SetProperty<T> result = objects.setProperty(elementType);
			for (String knownElement : knownElements) {
				result.add(collection.named(knownElement));
			}
			return result;
		}).flatMap(it -> it);
	}

	@Override
	public void finalizeNow() {
		finalizer.now();
	}

	static abstract class Finalizer {
		private final List<Runnable> actions = new ArrayList<>();
		private final Project project;

		@Inject
		public Finalizer(Project project) {
			this.project = project;
			// TODO: Register lifecycle afterevaluate to mark the view as finalized
		}

		public void whenFinalizing(Runnable action) {
			action = once(action);
			actions.add(action);
			project.afterEvaluate(ignored(action));
		}

		public void now() {
			while (!actions.isEmpty()) {
				actions.remove(0).run();
			}
		}
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

	private static Runnable once(Runnable runnable) {
		return new Runnable() {
			private boolean ran = false;

			@Override
			public void run() {
				if (!ran) {
					try {
						runnable.run();
					} finally {
						ran = true;
					}
				}
			}
		};
	}
}
