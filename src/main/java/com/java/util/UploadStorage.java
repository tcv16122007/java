package com.java.util;

import java.io.File;
import java.nio.file.Path;

import jakarta.servlet.ServletContext;

public final class UploadStorage {
    private UploadStorage() { }

    public static Path root(ServletContext context) {
        String configured = System.getenv("BLOG_UPLOAD_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        String realPath = context.getRealPath("/uploads");
        if (realPath != null) return Path.of(realPath).toAbsolutePath().normalize();
        Object temp = context.getAttribute("jakarta.servlet.context.tempdir");
        Path tempRoot = temp instanceof File file ? file.toPath() : Path.of(System.getProperty("java.io.tmpdir"));
        return tempRoot.resolve("blog-se-uploads").toAbsolutePath().normalize();
    }
}