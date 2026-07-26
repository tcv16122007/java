package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.java.model.Comment;

public class CommentDAO {

    // ==== Tìm comment theo bài viết (có like, dislike, replies) ====
    public List<Comment> findByPost(long postId, Long currentUserId) {
        String sql = "SELECT c.*, u.username, " +
                     "(SELECT COUNT(*) FROM Comment_Interaction ci WHERE ci.comment_id = c.comment_id AND ci.type = 'LIKE') as likeCount, " +
                     "(SELECT COUNT(*) FROM Comment_Interaction ci WHERE ci.comment_id = c.comment_id AND ci.type = 'DISLIKE') as dislikeCount, " +
                     "CASE WHEN EXISTS(SELECT 1 FROM Comment_Interaction ci WHERE ci.comment_id = c.comment_id AND ci.user_id = ? AND ci.type = 'LIKE') THEN 1 ELSE 0 END as likedByMe, " +
                     "CASE WHEN EXISTS(SELECT 1 FROM Comment_Interaction ci WHERE ci.comment_id = c.comment_id AND ci.user_id = ? AND ci.type = 'DISLIKE') THEN 1 ELSE 0 END as dislikedByMe " +
                     "FROM Comment c " +
                     "LEFT JOIN [User] u ON c.user_id = u.user_id " +
                     "WHERE c.post_id = ? AND c.status = 'VISIBLE' AND c.parent_id IS NULL " +
                     "ORDER BY c.created_at ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            long uid = currentUserId != null ? currentUserId : -1;
            ps.setLong(1, uid);
            ps.setLong(2, uid);
            ps.setLong(3, postId);
            ResultSet rs = ps.executeQuery();
            List<Comment> list = new ArrayList<>();
            while (rs.next()) {
                Comment c = mapRow(rs);
                c.setLikedByCurrentUser(rs.getInt("likedByMe") == 1);
                c.setDislikedByCurrentUser(rs.getInt("dislikedByMe") == 1);
                c.setLikeCount(rs.getInt("likeCount"));
                c.setDislikeCount(rs.getInt("dislikeCount"));
                c.setReplies(findReplies(c.getCommentId(), currentUserId));
                list.add(c);
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Find comments by post error", e);
        }
    }

    // ==== Tìm reply của một comment ====
    private List<Comment> findReplies(long parentId, Long currentUserId) {
        String sql = "SELECT c.*, u.username, " +
                     "(SELECT COUNT(*) FROM Comment_Interaction ci WHERE ci.comment_id = c.comment_id AND ci.type = 'LIKE') as likeCount, " +
                     "(SELECT COUNT(*) FROM Comment_Interaction ci WHERE ci.comment_id = c.comment_id AND ci.type = 'DISLIKE') as dislikeCount, " +
                     "CASE WHEN EXISTS(SELECT 1 FROM Comment_Interaction ci WHERE ci.comment_id = c.comment_id AND ci.user_id = ? AND ci.type = 'LIKE') THEN 1 ELSE 0 END as likedByMe, " +
                     "CASE WHEN EXISTS(SELECT 1 FROM Comment_Interaction ci WHERE ci.comment_id = c.comment_id AND ci.user_id = ? AND ci.type = 'DISLIKE') THEN 1 ELSE 0 END as dislikedByMe " +
                     "FROM Comment c " +
                     "LEFT JOIN [User] u ON c.user_id = u.user_id " +
                     "WHERE c.parent_id = ? AND c.status = 'VISIBLE' " +
                     "ORDER BY c.created_at ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            long uid = currentUserId != null ? currentUserId : -1;
            ps.setLong(1, uid);
            ps.setLong(2, uid);
            ps.setLong(3, parentId);
            ResultSet rs = ps.executeQuery();
            List<Comment> replies = new ArrayList<>();
            while (rs.next()) {
                Comment c = mapRow(rs);
                c.setLikedByCurrentUser(rs.getInt("likedByMe") == 1);
                c.setDislikedByCurrentUser(rs.getInt("dislikedByMe") == 1);
                c.setLikeCount(rs.getInt("likeCount"));
                c.setDislikeCount(rs.getInt("dislikeCount"));
                replies.add(c);
            }
            return replies;
        } catch (SQLException e) {
            throw new RuntimeException("Find replies error", e);
        }
    }

    // ==== Tìm tất cả (admin) ====
    public List<Comment> findAll() {
        List<Comment> list = new ArrayList<>();
        String sql = "SELECT c.*, u.username, p.title as postTitle FROM Comment c " +
                     "LEFT JOIN [User] u ON c.user_id = u.user_id " +
                     "LEFT JOIN Post p ON c.post_id = p.post_id " +
                     "ORDER BY c.created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Comment c = mapRow(rs);
                c.setPostTitle(rs.getString("postTitle"));
                list.add(c);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find all comments error", e);
        }
        return list;
    }

    // ==== Tìm comment theo ID ====
    public Comment findById(long id) {
        String sql = "SELECT * FROM Comment WHERE comment_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find comment by id error", e);
        }
        return null;
    }

    // ==== Thêm comment gốc (không có parent) ====
    public boolean insert(Comment comment) {
        String sql = "INSERT INTO Comment (content, user_id, post_id) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, comment.getContent());
            ps.setLong(2, comment.getUserId());
            ps.setLong(3, comment.getPostId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Insert comment error", e);
        }
    }

    // ==== Thêm reply (có parent) ====
    public boolean insertReply(Comment comment) {
        String sql = "INSERT INTO Comment (content, user_id, post_id, parent_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, comment.getContent());
            ps.setLong(2, comment.getUserId());
            ps.setLong(3, comment.getPostId());
            ps.setLong(4, comment.getParentId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Insert reply error", e);
        }
    }

    // ==== Cập nhật trạng thái ====
    public boolean updateStatus(long commentId, String status) {
        String sql = "UPDATE Comment SET status = ? WHERE comment_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, commentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update comment status error", e);
        }
    }

    public boolean delete(long id) { return updateStatus(id, "DELETED"); }
    public boolean hide(long id) { return updateStatus(id, "HIDDEN"); }
    public boolean restore(long id) { return updateStatus(id, "VISIBLE"); }

    // ==== Like comment (xóa dislike cũ nếu có) ====
    public boolean likeComment(long userId, long commentId) {
        String delDislike = "DELETE FROM Comment_Interaction WHERE user_id = ? AND comment_id = ? AND type = 'DISLIKE'";
        String insertLike = "INSERT INTO Comment_Interaction (user_id, comment_id, type) VALUES (?, ?, 'LIKE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement psDel = conn.prepareStatement(delDislike);
             PreparedStatement psIns = conn.prepareStatement(insertLike)) {
            psDel.setLong(1, userId);
            psDel.setLong(2, commentId);
            psDel.executeUpdate();
            psIns.setLong(1, userId);
            psIns.setLong(2, commentId);
            return psIns.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Like comment error", e);
        }
    }

    // ==== Unlike (xóa like) ====
    public boolean unlikeComment(long userId, long commentId) {
        String sql = "DELETE FROM Comment_Interaction WHERE user_id = ? AND comment_id = ? AND type = 'LIKE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, commentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Unlike comment error", e);
        }
    }

    // ==== Dislike comment (xóa like cũ nếu có) ====
    public boolean dislikeComment(long userId, long commentId) {
        String delLike = "DELETE FROM Comment_Interaction WHERE user_id = ? AND comment_id = ? AND type = 'LIKE'";
        String insertDislike = "INSERT INTO Comment_Interaction (user_id, comment_id, type) VALUES (?, ?, 'DISLIKE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement psDel = conn.prepareStatement(delLike);
             PreparedStatement psIns = conn.prepareStatement(insertDislike)) {
            psDel.setLong(1, userId);
            psDel.setLong(2, commentId);
            psDel.executeUpdate();
            psIns.setLong(1, userId);
            psIns.setLong(2, commentId);
            return psIns.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Dislike comment error", e);
        }
    }

    // ==== Undislike (xóa dislike) ====
    public boolean undislikeComment(long userId, long commentId) {
        String sql = "DELETE FROM Comment_Interaction WHERE user_id = ? AND comment_id = ? AND type = 'DISLIKE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, commentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Undislike comment error", e);
        }
    }

    // ==== Count likes ====
    public int countLikes(long commentId) {
        String sql = "SELECT COUNT(*) FROM Comment_Interaction WHERE comment_id = ? AND type = 'LIKE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, commentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("Count likes error", e);
        }
        return 0;
    }

    // ==== Count dislikes ====
    public int countDislikes(long commentId) {
        String sql = "SELECT COUNT(*) FROM Comment_Interaction WHERE comment_id = ? AND type = 'DISLIKE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, commentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("Count dislikes error", e);
        }
        return 0;
    }

    // ==== MAP ROW ====
    private Comment mapRow(ResultSet rs) throws SQLException {
        Comment c = new Comment();
        c.setCommentId(rs.getLong("comment_id"));
        c.setContent(rs.getString("content"));
        c.setCreatedAt(rs.getString("created_at"));
        c.setStatus(rs.getString("status"));
        c.setUserId(rs.getLong("user_id"));
        c.setPostId(rs.getLong("post_id"));
        long parent = rs.getLong("parent_id");
        if (!rs.wasNull()) c.setParentId(parent);
        try { c.setUsername(rs.getString("username")); } catch (SQLException ignore) {}
        return c;
    }
}