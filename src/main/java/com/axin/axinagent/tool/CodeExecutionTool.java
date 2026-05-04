package com.axin.axinagent.tool;

import cn.hutool.core.io.FileUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.*;

/**
 * 代码执行工具。
 * <p>
 * 生产环境建议将执行能力迁移到 Docker / Firecracker 等隔离沙箱中，避免本地进程级执行风险。
 */
public class CodeExecutionTool {

	private static final int TIMEOUT_SECONDS = 30;
	private static final int MAX_OUTPUT_LENGTH = 3000;
	private static final int MAX_CAPTURE_BYTES = MAX_OUTPUT_LENGTH * 4;

	@Tool(description = "Execute Python code snippet in a local process")
	public String executePython(@ToolParam(description = "Python code snippet") String code) {
		Path tmpDir = Paths.get(System.getProperty("user.dir"), "tmp", "code").toAbsolutePath().normalize();
		Path scriptPath = null;
		try {
			FileUtil.mkdir(tmpDir.toFile());
			scriptPath = java.nio.file.Files.createTempFile(tmpDir, "tmp_", ".py");
			java.nio.file.Files.writeString(scriptPath, code == null ? "" : code, StandardCharsets.UTF_8);
			return runProcess(List.of("python", scriptPath.toString()));
		} catch (Exception e) {
			return "Python execution error: " + e.getMessage();
		} finally {
			if (scriptPath != null) {
				try {
					java.nio.file.Files.deleteIfExists(scriptPath);
				} catch (IOException ignored) {
				}
			}
		}
	}

	@Tool(description = "Execute shell command in a local process")
	public String executeShell(@ToolParam(description = "Shell/PowerShell command") String command) {
		try {
			boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
			List<String> cmd = isWindows
					? List.of("cmd", "/c", command)
					: List.of("bash", "-c", command);
			return runProcess(cmd);
		} catch (Exception e) {
			return "Shell execution error: " + e.getMessage();
		}
	}

	private String runProcess(List<String> command) throws Exception {
		ProcessBuilder processBuilder = new ProcessBuilder(command)
				.directory(Paths.get(System.getProperty("user.dir")).toFile())
				.redirectErrorStream(true);
		Process process = processBuilder.start();

		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<CaptureResult> captureFuture = executor.submit(() -> captureOutput(process.getInputStream()));

		boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			captureFuture.cancel(true);
			executor.shutdownNow();
			return "Execution timeout after " + TIMEOUT_SECONDS + " seconds";
		}

		CaptureResult captureResult;
		try {
			captureResult = captureFuture.get(2, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			captureFuture.cancel(true);
			captureResult = new CaptureResult("", false);
		} finally {
			executor.shutdownNow();
		}

		String output = truncate(captureResult.output(), MAX_OUTPUT_LENGTH);
		if (captureResult.truncated()) {
			output = output + "...(输出已截断)";
		}
		int exitCode = process.exitValue();
		return "ExitCode=" + exitCode + "\n" + output;
	}

	private CaptureResult captureOutput(InputStream inputStream) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] chunk = new byte[1024];
		boolean truncated = false;

		int read;
		while ((read = inputStream.read(chunk)) != -1) {
			int writable = Math.min(read, MAX_CAPTURE_BYTES - buffer.size());
			if (writable > 0) {
				buffer.write(chunk, 0, writable);
			}
			if (writable < read || buffer.size() >= MAX_CAPTURE_BYTES) {
				truncated = true;
			}
		}
		return new CaptureResult(buffer.toString(StandardCharsets.UTF_8), truncated);
	}

	private String truncate(String text, int maxLength) {
		if (text == null) {
			return "";
		}
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength);
	}

	private record CaptureResult(String output, boolean truncated) {
	}
}
