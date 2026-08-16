package com.java.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBConnection {
    private static final String DEFAULT_URL = "jdbc:sqlserver://localhost:1433;databaseName=BlogSE;encrypt=true;trustServerCertificate=true;useUnicode=true;characterEncoding=UTF-8;";

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Không tìm thấy SQL Server JDBC Driver", e);
        }
        String url = env("BLOG_DB_URL", DEFAULT_URL);
        String user = env("BLOG_DB_USER", "sa");
        String password = env("BLOG_DB_PASSWORD", "161207");
        if (password.isBlank()) {
            throw new SQLException("Chưa cấu hình BLOG_DB_PASSWORD. Xem README-RUN.md.");
        }
        return DriverManager.getConnection(url, user, password);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}