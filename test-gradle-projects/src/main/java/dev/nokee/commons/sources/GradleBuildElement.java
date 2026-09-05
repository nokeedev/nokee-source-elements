package dev.nokee.commons.sources;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import dev.gradleplugins.buildscript.io.GradleBuildFile;
import dev.gradleplugins.buildscript.io.GradleSettingsFile;
import dev.gradleplugins.fixtures.sources.Element;
import dev.gradleplugins.fixtures.sources.ProjectElement;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static dev.gradleplugins.buildscript.ast.expressions.MethodCallExpression.call;
import static dev.gradleplugins.buildscript.syntax.Syntax.string;

public class GradleBuildElement extends ProjectElement {
	private final List<Element> elements = new ArrayList<>();
	private final GradleBuildFile buildFile;
	private final GradleSettingsFile settingsFile;
	private final FileSystem fs;
	private final Path location;
	// TODO: Should we use String instead of path
//	public GradleBuild inDirectory(Path directory);

//	GradleBuild includePluginBuild(GradleBuild build);
//	GradleBuild includeBuild(GradleBuild build);
//	GradleBuild include(String path, GradleProject project);
//	GradleBuild include(GradleProject project);

	public GradleBuildElement(FileSystem fs, Path location, GradleBuildFile buildFile, GradleSettingsFile settingsFile) {
		this.fs = fs;
		this.location = location;
		this.buildFile = buildFile;
		this.settingsFile = settingsFile;
	}
//
	public GradleBuildFile getBuildFile() {
		return buildFile;
	}
	public GradleSettingsFile getSettingsFile() {
		return settingsFile;
	}
//
//	List<GradleBuild> getIncludedBuilds();
//	Map<String, GradleProject> getProjects();


	@Override
	public Path getLocation() {
		return location;
	}

	@Override
	public GradleBuildElement writeToDirectory(Path directory) {
		// TODO: fails IF writing over files already existing
		copyDirectory(location, directory);

		// TODO: Try to just reuse the GradleBuildFile and GradleSettingsFile to avoid parsing
		return new GradleBuildElement(directory.getFileSystem(), directory, buildFile.writeToDirectory(directory), settingsFile.writeToDirectory(directory)) {
			@Override
			public String toString() {
				return "written " + GradleBuildElement.this + " to '" + directory + "'";
			}
		};
	}

	public GradleBuildElement configure(UnaryOperator<GradleBuildElement> configureAction) {
		return configureAction.apply(this);
	}

	public static UnaryOperator<GradleBuildElement> includeBuild(InDirectoryElement<GradleBuildElement> build) {
		return self -> {
			self.elements.add(build.writeToDirectory(self.getLocation()));
			self.settingsFile.append(call("includeBuild", string(build.getPath().toString())));
			return self;
		};
	}

	public static UnaryOperator<GradleBuildElement> pluginBuild(InDirectoryElement<GradleBuildElement> build) {
		return self -> {
			self.elements.add(build.writeToDirectory(self.getLocation()));
			self.settingsFile.pluginManagement(it -> it.add(call("includeBuild", string(build.getPath().toString()))));
			return self;
		};
	}

	public static UnaryOperator<GradleBuildElement> include(InDirectoryElement<GradleProjectElement> project) {
		return self -> {
			self.elements.add(project.writeToDirectory(self.getLocation()));
			self.settingsFile.append(call("include", string(inferProjectPath(project))));
			return self;
		};
	}

//	public static UnaryOperator<GradleBuild> include(String path, InDirectoryElement<GradleProject> project) {
//		assert path.startsWith(":");
//		return self -> {
//			self.elements.add(project.writeToDirectory(self.getLocation()));
//			self.settingsFile.append(call("include", string(path)));
//			if (!path.equals(inferProjectPath(project))) {
//				// TODO: Same for custom build file
//				throw new UnsupportedOperationException("configuring project path not yet supported");
//			}
//			return self;
//		};
//	}

	private static String inferProjectPath(InDirectoryElement<GradleProjectElement> project) {
		return ":" + StreamSupport.stream(project.getPath().spliterator(), false).map(Path::toString).collect(Collectors.joining(":"));
	}

	public static GradleBuildElement empty() {
		FileSystem fs = Jimfs.newFileSystem();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				fs.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}));
		return inDirectory(fs.getPath("/"));
	}

	public static GradleBuildElement fromDirectory(Path directory) {
		// Load settings.gradle[.kts] in GradleSettingsFile
		// Extract plugin build
		// Extract\
		FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				fs.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}));
		// copy dir
		Path root = fs.getPath("/");
		copyDirectory(directory, root);

		return new GradleBuildElement(fs, root, GradleBuildFile.fromDirectory(directory), GradleSettingsFile.fromDirectory(directory)) {
			@Override
			public String toString() {
				return "Gradle build from '" + directory + "'";
			}
		};
	}

	public static GradleBuildElement inDirectory(Path directory) {
		return new GradleBuildElement(directory.getFileSystem(), directory, GradleBuildFile.inDirectory(directory), GradleSettingsFile.inDirectory(directory)) {
			@Override
			public String toString() {
				return "Gradle build in '" + directory + "'";
			}
		};
	}


	private static void copyDirectory(Path srcDir, Path destDir) {
		try {
			System.out.println("===== " + srcDir + " ===== " + destDir);
			Files.walkFileTree(srcDir, Collections.singleton(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path src, BasicFileAttributes attrs) throws IOException {
					Path relative = srcDir.relativize(src);
					Path dst = destDir.resolve(relative.toString());
					Files.createDirectories(dst.getParent());
					System.out.println("Copying " + src + " to " + dst);
					Files.copy(src, dst);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
