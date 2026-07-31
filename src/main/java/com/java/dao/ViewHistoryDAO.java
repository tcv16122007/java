package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ViewHistoryDAO {

    public void addViewHistory(long userId, long postId) {
        String sql = "MERGE INTO ViewHistory AS target " +
                     "USING (SELECT ? AS user_id, ? AS post_id) AS source " +
                     "ON target.user_id = source.user_id AND target.post_id = source.post_id " +
                     "WHEN MATCHED THEN UPDATE SET viewed_at = GETDATE() " +
                     "WHEN NOT MATCHED THEN INSERT (user_id, post_id) VALUES (?, ?);";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, postId);
            ps.setLong(3, userId);
            ps.setLong(4, postId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Add view history error", e);
        }
    }

    public List<Map<String, Object>> getViewHistoryByUser(long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT vh.post_id, p.title, vh.viewed_at " +
                     "FROM ViewHistory vh " +
                     "INNER JOIN Post p ON vh.post_id = p.post_id " +
                     "WHERE vh.user_id = ? AND p.status = 'APPROVED' " +
                     "ORDER BY vh.viewed_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("postId", rs.getLong("post_id"));
                map.put("title", rs.getString("title"));
                map.put("viewedAt", rs.getString("viewed_at"));
                list.add(map);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Get view history error", e);
        }
        return list;
    }
}