package com.axin.axinagent.infrastructure.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * 基于本地文件系统的存储实现。
 */
@Slf4j
@Service
public class LocalStorageServiceImpl implements StorageService {

    private final Path rootPath;

    public LocalStorageServiceImpl(@Value("${storage.root-path:./tmp}") String rootPath) {
        this.rootPath = Paths.get(rootPath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootPath);
            log.info("本地存储根目录初始化完成: {}", rootPath);
        } catch (IOException e) {
            throw new IllegalStateException("初始化存储根目录失败: " + rootPath, e);
        }
    }

    @Override
    public String saveFile(String category, String filename, byte[] content) throws IOException {
        String safeCategory = sanitizeSegment(category, "misc");
        String safeFilename = sanitizeSegment(filename, "file.dat");

        Path targetDir = rootPath.resolve(safeCategory).normalize();
        ensureWithinRoot(targetDir);
        Files.createDirectories(targetDir);

        Path targetFile = targetDir.resolve(safeFilename).normalize();
        ensureWithinRoot(targetFile);
        Files.write(targetFile, Objects.requireNonNullElse(content, new byte[0]),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        return safeCategory + "/" + safeFilename;
    }

    @Override
    public String getFilePath(String category, String filename) {
        String safeCategory = sanitizeSegment(category, "misc");
        String safeFilename = sanitizeSegment(filename, "file.dat");
        Path filePath = rootPath.resolve(safeCategory).resolve(safeFilename).normalize();
        ensureWithinRoot(filePath);
        return filePath.toString();
    }

    private String sanitizeSegment(String raw, String defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String sanitized = raw.replace("\\", "_")
                .replace("/", "_")
                .replace("..", "_")
                .trim();
        return sanitized.isBlank() ? defaultValue : sanitized;
    }

    private void ensureWithinRoot(Path target) {
        if (!target.startsWith(rootPath)) {
            throw new IllegalArgumentException("非法路径，超出存储根目录: " + target);
        }
    }
}
