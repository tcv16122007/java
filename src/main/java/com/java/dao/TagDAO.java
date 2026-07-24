package com.java.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.java.model.Tag;

public class TagDAO {
    public List<Tag> findAll() {
        List<Tag> list = new ArrayList<>();
        String sql = "SELECT * FROM Tag WHERE status = 1";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Tag t = new Tag();
                t.setTagId(rs.getLong("tag_id"));
                t.setTagName(rs.getString("tag_name"));
                t.setDescription(rs.getString("description"));
                t.setStatus(rs.getBoolean("status"));
                list.add(t);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find tags error", e);
        }
        return list;
    }
}