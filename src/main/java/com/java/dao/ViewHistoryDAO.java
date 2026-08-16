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
        String sql = "SELECT vh.post_id, p.title, p.summary, p.thumbnail, p.view_count, vh.viewed_at, " +
                     "u.user_id AS author_id, u.full_name AS author_name, c.category_name, " +
                     "(SELECT COUNT(*) FROM Interaction i WHERE i.post_id=p.post_id AND i.type='LIKE') AS like_count, " +
                     "(SELECT COUNT(*) FROM Comment cm WHERE cm.post_id=p.post_id AND cm.status='VISIBLE') AS comment_count " +
                     "FROM ViewHistory vh INNER JOIN Post p ON vh.post_id=p.post_id " +
                     "LEFT JOIN [User] u ON p.author_id=u.user_id LEFT JOIN Category c ON p.category_id=c.category_id " +
                     "WHERE vh.user_id=? AND p.status='APPROVED' ORDER BY vh.viewed_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("postId", rs.getLong("post_id"));
                    map.put("title", rs.getString("title"));
                    map.put("summary", rs.getString("summary"));
                    map.put("thumbnail", rs.getString("thumbnail"));
                    map.put("viewCount", rs.getInt("view_count"));
                    map.put("likeCount", rs.getInt("like_count"));
                    map.put("commentCount", rs.getInt("comment_count"));
                    map.put("authorId", rs.getLong("author_id"));
                    map.put("authorName", rs.getString("author_name"));
                    map.put("categoryName", rs.getString("category_name"));
                    map.put("viewedAt", rs.getString("viewed_at"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Get view history error", e);
        }
        return list;
    }
}