package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.java.model.Tag;

public class TagDAO {
    public List<Tag> findAll() {
        List<Tag> list = new ArrayList<>();
        String sql = "SELECT * FROM Tag WHERE status = 1 ORDER BY tag_name";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Find tags error", e);
        }
        return list;
    }

    public Tag findById(long id) {
        String sql = "SELECT * FROM Tag WHERE tag_id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapRow(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Find tag error", e);
        }
    }

    public boolean insert(Tag tag) {
        return execute("INSERT INTO Tag(tag_name,description,status) VALUES (?,?,1)", tag.getTagName(), tag.getDescription());
    }

    public boolean update(Tag tag) {
        return execute("UPDATE Tag SET tag_name=?, description=? WHERE tag_id=?", tag.getTagName(), tag.getDescription(), tag.getTagId());
    }

    public boolean delete(long id) {
        return execute("UPDATE Tag SET status=0 WHERE tag_id=?", id);
    }

    private boolean execute(String sql, Object... params) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Tag update error", e);
        }
    }

    private Tag mapRow(ResultSet rs) throws SQLException {
        Tag tag = new Tag();
        tag.setTagId(rs.getLong("tag_id"));
        tag.setTagName(rs.getString("tag_name"));
        tag.setDescription(rs.getString("description"));
        tag.setStatus(rs.getBoolean("status"));
        return tag;
    }
}