package dev.nokee.publishing.multiplatform;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.maven.MavenPublication;

abstract class MinimalGMVPublication {
	public final String getName() {
		return delegate().getName();
	}

	public abstract String getGroup();
	public abstract void setGroup(String value);

	public abstract String getModule();
	public abstract void setModule(String value);

	public abstract String getVersion();
	public abstract void setVersion(String value);

	protected abstract Publication delegate();

	@Override
	public int hashCode() {
		return delegate().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof MinimalGMVPublication)) return false;

		MinimalGMVPublication other = (MinimalGMVPublication) obj;
		return delegate().equals(other.delegate());
	}

	public static <T extends Publication, OUT> Transformer<OUT, T> wrap(Transformer<? extends OUT, ? super MinimalGMVPublication> mapper) {
		return publication -> mapper.transform(wrap(publication));
	}

	public static MinimalGMVPublication wrap(Publication publication) {
		if (publication instanceof IvyPublication) {
			return new MinimalGMVPublication() {
				@Override
				public String getGroup() {
					return ((IvyPublication) publication).getOrganisation();
				}

				@Override
				public void setGroup(String value) {
					((IvyPublication) publication).setOrganisation(value);
				}

				@Override
				public String getModule() {
					return ((IvyPublication) publication).getModule();
				}

				@Override
				public void setModule(String value) {
					((IvyPublication) publication).setModule(value);
				}

				@Override
				public String getVersion() {
					return ((IvyPublication) publication).getRevision();
				}

				@Override
				public void setVersion(String value) {
					((IvyPublication) publication).setRevision(value);
				}

				@Override
				protected Publication delegate() {
					return publication;
				}
			};
		} else if (publication instanceof MavenPublication) {
			return new MinimalGMVPublication() {
				@Override
				public String getGroup() {
					return ((MavenPublication) publication).getGroupId();
				}

				@Override
				public void setGroup(String value) {
					((MavenPublication) publication).setGroupId(value);
				}

				@Override
				public String getModule() {
					return ((MavenPublication) publication).getArtifactId();
				}

				@Override
				public void setModule(String value) {
					((MavenPublication) publication).setArtifactId(value);
				}

				@Override
				public String getVersion() {
					return ((MavenPublication) publication).getVersion();
				}

				@Override
				public void setVersion(String value) {
					((MavenPublication) publication).setVersion(value);
				}

				@Override
				protected Publication delegate() {
					return publication;
				}
			};
		} else {
			throw new UnsupportedOperationException();
		}
	}

	public static <T extends Publication> Action<T> wrap(Action<? super MinimalGMVPublication> configureAction) {
		return publication -> configureAction.execute(wrap(publication));
	}
}
