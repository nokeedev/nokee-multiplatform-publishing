package dev.nokee.platform.base;

import org.gradle.api.Action;
import org.gradle.api.provider.Provider;

import java.util.Set;

public interface View<T> {
	void configureEach(Action<? super T> configureAction);

	Provider<Set<T>> getElements();
}
