package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InteractionHistoryDAO {

    public List<Map<String, Object>> getUserInteractions(long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT 'post' as type, i.post_id, p.title, i.type as action, i.created_at " +
                     "FROM Interaction i " +
                     "LEFT JOIN Post p ON i.post_id = p.post_id " +
                     "WHERE i.user_id = ? " +
                     "UNION " +
                     "SELECT 'comment' as type, c.comment_id, c.content, ci.type as action, ci.created_at " +
                     "FROM Comment_Interaction ci " +
                     "LEFT JOIN Comment c ON ci.comment_id = c.comment_id " +
                     "WHERE ci.user_id = ? " +
                     "ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("type", rs.getString("type"));
                long postId = rs.getLong("post_id");
                long commentId = rs.getLong("comment_id");
                map.put("id", postId != 0 ? postId : commentId);
                map.put("titleOrContent", rs.getString("title") != null ? rs.getString("title") : rs.getString("content"));
                map.put("action", rs.getString("action"));
                map.put("createdAt", rs.getString("created_at"));
                list.add(map);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Get interaction history error", e);
        }
        return list;
    }
}