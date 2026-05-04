package com.axin.axinagent.tool;

import cn.hutool.core.io.FileUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FileSystemTool {

	private static final int MAX_READ_LENGTH = 3000;
	private static final Path SANDBOX_ROOT = Paths.get(System.getProperty("user.dir"), "workspace")
			.toAbsolutePath()
			.normalize();

	@Tool(description = "Read file content from workspace sandbox")
	public String readFile(@ToolParam(description = "File path under workspace sandbox") String filePath) {
		try {
			FileUtil.mkdir(SANDBOX_ROOT.toFile());
			Path targetPath = resolveSandboxPath(filePath);
			if (!FileUtil.exist(targetPath.toFile()) || FileUtil.isDirectory(targetPath.toFile())) {
				return "File not found: " + targetPath;
			}
			String content = FileUtil.readUtf8String(targetPath.toFile());
			return truncate(content, MAX_READ_LENGTH);
		} catch (Exception e) {
			return "Error reading file: " + e.getMessage();
		}
	}

	@Tool(description = "Write file content to workspace sandbox")
	public String writeFile(
			@ToolParam(description = "File path under workspace sandbox") String filePath,
			@ToolParam(description = "Content to write") String content) {
		try {
			FileUtil.mkdir(SANDBOX_ROOT.toFile());
			Path targetPath = resolveSandboxPath(filePath);
			Path parent = targetPath.getParent();
			if (parent != null) {
				FileUtil.mkdir(parent.toFile());
			}
			FileUtil.writeUtf8String(content, targetPath.toFile());
			return "File written successfully: " + targetPath;
		} catch (Exception e) {
			return "Error writing file: " + e.getMessage();
		}
	}

	@Tool(description = "List directory entries under workspace sandbox")
	public String listDirectory(@ToolParam(description = "Directory path under workspace sandbox") String directoryPath) {
		try {
			FileUtil.mkdir(SANDBOX_ROOT.toFile());
			Path targetPath = resolveSandboxPath(directoryPath);
			if (!FileUtil.exist(targetPath.toFile()) || !FileUtil.isDirectory(targetPath.toFile())) {
				return "Directory not found: " + targetPath;
			}
			List<String> entries;
			try (var stream = Files.list(targetPath)) {
				entries = stream
						.sorted(Comparator.comparing(path -> path.getFileName().toString()))
						.map(path -> (Files.isDirectory(path) ? "[DIR] " : "[FILE] ") + path.getFileName())
						.collect(Collectors.toList());
			}
			return entries.isEmpty() ? "Directory is empty" : String.join("\n", entries);
		} catch (IOException e) {
			return "Error listing directory: " + e.getMessage();
		} catch (Exception e) {
			return "Error listing directory: " + e.getMessage();
		}
	}

	private Path resolveSandboxPath(String inputPath) {
		if (inputPath == null || inputPath.isBlank()) {
			return SANDBOX_ROOT;
		}
		Path rawPath = Paths.get(inputPath);
		Path resolvedPath = rawPath.isAbsolute()
				? rawPath.toAbsolutePath().normalize()
				: SANDBOX_ROOT.resolve(rawPath).normalize();
		if (!resolvedPath.startsWith(SANDBOX_ROOT)) {
			throw new IllegalArgumentException("Path outside sandbox is not allowed: " + inputPath);
		}
		return resolvedPath;
	}

	private String truncate(String text, int maxLength) {
		if (text == null) {
			return "";
		}
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength) + "...(内容已截断)";
	}
}
