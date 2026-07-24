package com.java.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=BlogSE;encrypt=true;trustServerCertificate=true;useUnicode=true;characterEncoding=UTF-8;";
    private static final String USER = "sa";
    private static final String PASSWORD = "161207";

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("JDBC driver not found", e);
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}