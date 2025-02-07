package dev.nokee.publishing.multiplatform.fixtures;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import dev.gradleplugins.grava.publish.metadata.GradleModuleMetadata;
import org.gradle.api.publish.maven.MavenArtifact;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

public class MavenRepositoryMatchers {

	public static <T extends Repository> Matcher<T> publishedModule(String coordinate) {
		return new FeatureMatcher<T, Module>(published(), "", "") {
			@Override
			protected Module featureValueOf(T actual) {
				String[] tokens = coordinate.split(":");
				return actual.module(tokens[0], tokens[1], tokens[2]);
			}
		};
	}

	public static <T extends Module> Matcher<T> published() {
		return new FeatureMatcher<T, Boolean>(is(true), "", "") {
			@Override
			protected Boolean featureValueOf(T actual) {
				return actual.isPublished();
			}
		};
	}

	public static <T extends Module> Matcher<T> moduleMetadata(Matcher<? super GradleModuleMetadata> matcher) {
		return new FeatureMatcher<T, GradleModuleMetadata>(matcher, "", "") {
			@Override
			protected GradleModuleMetadata featureValueOf(T actual) {
				Path file = null;
				if (actual instanceof MavenModule) {
					file = Path.of(((MavenModule) actual).getModuleMetadata().getUri());
				} else if (actual instanceof IvyModule) {
					file = Path.of(((IvyModule) actual).getModuleMetadata().getUri());
				}
				try (GradleModuleMetadataReader reader = new GradleModuleMetadataReader(Files.newBufferedReader(file))) {
					return reader.read();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	public static Matcher<GradleModuleMetadata> component(Matcher<? super GradleModuleMetadata.Component> matcher) {
		return new FeatureMatcher<GradleModuleMetadata, GradleModuleMetadata.Component>(matcher, "", "") {
			@Override
			protected GradleModuleMetadata.Component featureValueOf(GradleModuleMetadata actual) {
				return actual.getComponent().get(); // TODO: Better error
			}
		};
	}

	public static Matcher<GradleModuleMetadata.Component> module(Matcher<? super String> matcher) {
		return new FeatureMatcher<GradleModuleMetadata.Component, String>(matcher, "", "") {
			@Override
			protected String featureValueOf(GradleModuleMetadata.Component actual) {
				return actual.getModule();
			}
		};
	}

	public static Matcher<GradleModuleMetadata> remoteVariants(Matcher<? super Iterable<GradleModuleMetadata.RemoteVariant>> matcher) {
		return new FeatureMatcher<GradleModuleMetadata, List<GradleModuleMetadata.RemoteVariant>>(matcher, "", "") {
			@Override
			protected List<GradleModuleMetadata.RemoteVariant> featureValueOf(GradleModuleMetadata actual) {
				return actual.getVariants().stream().filter(it -> it instanceof GradleModuleMetadata.RemoteVariant).map(GradleModuleMetadata.RemoteVariant.class::cast).toList();
			}
		};
	}

	public static Matcher<GradleModuleMetadata> remoteVariant(Matcher<? super GradleModuleMetadata.RemoteVariant> matcher) {
		return remoteVariants(hasItem(matcher));
	}


	public static final class GradleModuleMetadataReader implements Closeable {
		private static final Type CAPABILITIES_TYPE = new TypeToken<List<GradleModuleMetadata.Capability>>() {
		}.getType();
		private static final Type EXCLUDES_TYPE = new TypeToken<Set<GradleModuleMetadata.Exclude>>() {
		}.getType();
		private static final Type ATTRIBUTES_TYPE = new TypeToken<List<GradleModuleMetadata.Attribute>>() {
		}.getType();
		private static final Type DEPENDENCIES_TYPE = new TypeToken<List<GradleModuleMetadata.Dependency>>() {
		}.getType();
		private static final Type DEPENDENCY_CONSTRAINTS_TYPE = new TypeToken<List<GradleModuleMetadata.DependencyConstraint>>() {
		}.getType();
		private static final Type FILES_TYPE = new TypeToken<List<GradleModuleMetadata.File>>() {}.getType();
		private static final Type VARIANTS_TYPE = new TypeToken<List<GradleModuleMetadata.Variant>>() {}.getType();

		private final Reader reader;

		public GradleModuleMetadataReader(Reader reader) {
			this.reader = reader;
		}

		public GradleModuleMetadata read() throws IOException {
			Gson gson = new GsonBuilder().setPrettyPrinting()
				.registerTypeAdapter(ATTRIBUTES_TYPE, AttributesSerializer.INSTANCE)
				.registerTypeAdapter(VARIANTS_TYPE, VariantsSerializer.INSTANCE)
				.create();
			return gson.fromJson(reader, GradleModuleMetadata.class);
		}

		@Override
		public void close() throws IOException {
			reader.close();
		}

		private enum AttributesSerializer implements JsonDeserializer<List<GradleModuleMetadata.Attribute>> {
			INSTANCE;

			@Override
			public List<GradleModuleMetadata.Attribute> deserialize(JsonElement json, Type typeOfSrc, JsonDeserializationContext context) {
				return json.getAsJsonObject().asMap().entrySet().stream().map(entry -> {
					return GradleModuleMetadata.Attribute.ofAttribute(entry.getKey(), entry.getValue().getAsString());
				}).toList();
			}
		}

		private enum VariantsSerializer implements JsonDeserializer<List<GradleModuleMetadata.Variant>> {
			INSTANCE;

			@Override
			public List<GradleModuleMetadata.Variant> deserialize(JsonElement json, Type typeOfSrc, JsonDeserializationContext context) {
				return json.getAsJsonArray().asList().stream().map(it -> deserialize(it, context)).toList();
			}

			private GradleModuleMetadata.Variant deserialize(JsonElement json, JsonDeserializationContext context) {
				JsonObject obj = json.getAsJsonObject();
				if (obj.has("available-at")) {
					return context.deserialize(json, GradleModuleMetadata.RemoteVariant.class);
				} else {
					return context.deserialize(json, GradleModuleMetadata.LocalVariant.class);
				}
			}
		}
	}
}
