/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.nokee.elements.core;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public final class SourceFile {
	private final String path;
	private final String name;
	private final String content;

	public SourceFile(String path, String name, String content) {
		this.path = path;
		this.name = name;
		this.content = content;
	}

	public String getPath() {
		return path;
	}

	public String getFilePath() {
		return path.isEmpty() ? name : path + "/" + name;
	}

	public String getName() {
		return name;
	}

	public SourceFile withName(String name) {
		return new SourceFile(path, name, content);
	}

	public SourceFile withName(UnaryOperator<String> transformer) {
		return new SourceFile(path, transformer.apply(name), content);
	}

	public String getContent() {
		return content;
	}

	public Path writeToDirectory(Path base) {
		String path = Stream.of(this.path, name).filter(it -> !it.isEmpty()).collect(joining(File.separator));
		final Path file = base.resolve(path);
		writeToFile(file);
		return file;
	}

	public void writeToFile(Path file) {
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, content.getBytes(Charset.defaultCharset()));
		} catch (IOException ex) {
			throw new UncheckedIOException(String.format("Unable to create source file at '%s'.", file), ex);
		}
	}

	public SourceFile withPath(UnaryOperator<Path> transformer) {
		Path newPath = transformer.apply(Paths.get(path).resolve(name));
		return new SourceFile(newPath.getParent().toString(), newPath.getFileName().toString(), content);
	}

	public SourceFile withContent(UnaryOperator<String> transformer) {
		return new SourceFile(path, name, transformer.apply(content));
	}

	private static String firstContentLine(String content) {
		String[] tokens = content.split("\n", -1);
		return Arrays.stream(tokens).map(String::trim).filter(line -> !line.isEmpty()).findFirst().map(it -> it + "...").orElse("");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		SourceFile that = (SourceFile) o;
		return Objects.equals(path, that.path) && Objects.equals(name, that.name) && Objects.equals(content, that.content);
	}

	@Override
	public int hashCode() {
		return Objects.hash(path, name, content);
	}

	@Override
	public String toString() {
		return "SourceFile{" +
			"path='" + path + '\'' +
			", name='" + name + '\'' +
			", content='" + firstContentLine(content) + '\'' +
			'}';
	}

	public static SourceFile from(Path sourcePath, ContentLoader loader) {
		try {
			return of(sourcePath, loader.load());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static SourceFile of(String sourcePath, String content) {
		return of(Paths.get(sourcePath), content);
	}

	public static SourceFile of(Path sourcePath, String content) {
		assert !sourcePath.isAbsolute() : "'sourcePath' must be relative";
		String name = sourcePath.getFileName().toString();
		Path path = sourcePath.getParent();
		return new SourceFile(path == null ? "" : path.toString().replace('\\', '/'), name, content);
	}

	public interface ContentLoader {
		String load() throws IOException;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String path;
		private String name;
		private String content;

		public Builder withPath(String path) {
			this.path = path;
			return this;
		}

		public Builder withName(String name) {
			this.name = name;
			return this;
		}

		public Builder withContent(String content) {
			this.content = content;
			return this;
		}

		public Builder withContent(ContentLoader loader) {
			try {
				this.content = loader.load();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			return this;
		}

		public SourceFile build() {
			return new SourceFile(path, name, content);
		}
	}
}
