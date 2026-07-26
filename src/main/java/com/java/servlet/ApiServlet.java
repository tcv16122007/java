package com.java.servlet;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.java.dao.CategoryDAO;
import com.java.dao.CommentDAO;
import com.java.dao.InteractionDAO;
import com.java.dao.PasswordResetTokenDAO;
import com.java.dao.PostDAO;
import com.java.dao.TagDAO;
import com.java.dao.UserDAO;
import com.java.dao.UserSettingsDAO;
import com.java.model.Category;
import com.java.model.Comment;
import com.java.model.PasswordResetToken;
import com.java.model.Post;
import com.java.model.Tag;
import com.java.model.User;
import com.java.model.UserSettings;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/*")
public class ApiServlet extends HttpServlet {

    private final Gson gson = new Gson();
    private final UserDAO userDAO = new UserDAO();
    private final PostDAO postDAO = new PostDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final CommentDAO commentDAO = new CommentDAO();
    private final TagDAO tagDAO = new TagDAO();
    private final InteractionDAO interactionDAO = new InteractionDAO();
    private final UserSettingsDAO settingsDAO = new UserSettingsDAO();
    private final PasswordResetTokenDAO resetTokenDAO = new PasswordResetTokenDAO();

    // ============ GET ============
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();
        HttpSession session = req.getSession(false);
        User currentUser = (session != null) ? (User) session.getAttribute("user") : null;

        try {
            if (path == null || path.equals("/")) {
                resp.getWriter().write("{\"message\":\"Blog SE API is running!\"}");
                return;
            }

            switch (path) {
                case "/current-user" -> {
                    if (currentUser != null) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("success", true);
                        map.put("user", currentUser);
                        resp.getWriter().write(gson.toJson(map));
                    } else {
                        Map<String, Object> map = new HashMap<>();
                        map.put("success", false);
                        map.put("message", "Chưa đăng nhập");
                        resp.getWriter().write(gson.toJson(map));
                    }
                }
                case "/posts" -> {
                    String action = req.getParameter("action");
                    if (action == null) {
                        resp.getWriter().write("{\"error\":\"Missing action parameter\"}");
                        return;
                    }

                    switch (action) {
                        case "list" -> {
                            List<Post> posts = postDAO.findApproved();
                            resp.getWriter().write(gson.toJson(posts));
                        }
                        case "my" -> {
                            if (currentUser == null) {
                                Map<String, Object> err = new HashMap<>();
                                err.put("error", "Chưa đăng nhập");
                                resp.getWriter().write(gson.toJson(err));
                            } else {
                                List<Post> posts = postDAO.findByAuthor(currentUser.getUserId());
                                resp.getWriter().write(gson.toJson(posts));
                            }
                        }
                        case "pending" -> {
                            if (currentUser == null || (!"MODERATOR".equals(currentUser.getRole()) && !"ADMIN".equals(currentUser.getRole()))) {
                                Map<String, Object> err = new HashMap<>();
                                err.put("error", "Không có quyền");
                                resp.getWriter().write(gson.toJson(err));
                            } else {
                                List<Post> posts = postDAO.findPending();
                                resp.getWriter().write(gson.toJson(posts));
                            }
                        }
                        case "detail" -> {
                            long id = Long.parseLong(req.getParameter("id"));
                            Post p = postDAO.findById(id);
                            if (p != null) {
                                if ("APPROVED".equals(p.getStatus())) {
                                    postDAO.incrementViewCount(id);
                                }
                                resp.getWriter().write(gson.toJson(p));
                            } else {
                                Map<String, Object> err = new HashMap<>();
                                err.put("error", "Không tìm thấy bài viết");
                                resp.getWriter().write(gson.toJson(err));
                            }
                        }
                        case "filter" -> {
                            String categoryId = req.getParameter("categoryId");
                            String tagId = req.getParameter("tagId");
                            String keyword = req.getParameter("keyword");
                            String sort = req.getParameter("sort");
                            List<Post> posts = postDAO.filter(categoryId, tagId, null, keyword, sort);
                            resp.getWriter().write(gson.toJson(posts));
                        }
                        default -> {
                            Map<String, Object> err = new HashMap<>();
                            err.put("error", "Hành động không hợp lệ: " + action);
                            resp.getWriter().write(gson.toJson(err));
                        }
                    }
                }
                case "/categories" -> {
                    List<Category> categories = categoryDAO.findAll();
                    resp.getWriter().write(gson.toJson(categories));
                }
                case "/tags" -> {
                    List<Tag> tags = tagDAO.findAll();
                    resp.getWriter().write(gson.toJson(tags));
                }
                case "/comments" -> {
                    String action = req.getParameter("action");
                    if ("all".equals(action)) {
                        if (currentUser == null || (!"MODERATOR".equals(currentUser.getRole()) && !"ADMIN".equals(currentUser.getRole()))) {
                            Map<String, Object> err = new HashMap<>();
                            err.put("error", "Không có quyền");
                            resp.getWriter().write(gson.toJson(err));
                            return;
                        }
                        List<Comment> comments = commentDAO.findAll();
                        resp.getWriter().write(gson.toJson(comments));
                    } else {
                        long postId = Long.parseLong(req.getParameter("postId"));
                        Long userId = currentUser != null ? currentUser.getUserId() : null;
                        List<Comment> comments = commentDAO.findByPost(postId, userId);
                        resp.getWriter().write(gson.toJson(comments));
                    }
                }
                case "/settings" -> {
                    if (currentUser == null) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Chưa đăng nhập");
                        resp.getWriter().write(gson.toJson(err));
                    } else {
                        UserSettings s = settingsDAO.findByUserId(currentUser.getUserId());
                        if (s == null) {
                            s = new UserSettings();
                            s.setUserId(currentUser.getUserId());
                            s.setTheme("light");
                            s.setPrimaryColor("#667eea");
                            s.setBackgroundColor("#f4f6f9");
                            s.setTextColor("#1a1a2e");
                        }
                        resp.getWriter().write(gson.toJson(s));
                    }
                }
                case "/users" -> {
                    if (currentUser == null || !"ADMIN".equals(currentUser.getRole())) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Không có quyền");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    String action = req.getParameter("action");
                    if ("list".equals(action)) {
                        List<User> users = userDAO.findAll();
                        resp.getWriter().write(gson.toJson(users));
                    } else if ("search".equals(action)) {
                        String keyword = req.getParameter("keyword");
                        List<User> users = userDAO.search(keyword);
                        resp.getWriter().write(gson.toJson(users));
                    } else if ("detail".equals(action)) {
                        long id = Long.parseLong(req.getParameter("id"));
                        User u = userDAO.findById(id);
                        if (u != null) resp.getWriter().write(gson.toJson(u));
                        else resp.getWriter().write("{\"error\":\"User not found\"}");
                    } else {
                        resp.getWriter().write("{\"error\":\"Invalid action\"}");
                    }
                }
                default -> {
                    Map<String, Object> err = new HashMap<>();
                    err.put("error", "Endpoint không tồn tại: " + path);
                    resp.getWriter().write(gson.toJson(err));
                }
            }
        } catch (NumberFormatException e) {
            resp.getWriter().write("{\"error\":\"Invalid number format: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // ============ POST ============
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();
        HttpSession session = req.getSession(false);
        User currentUser = (session != null) ? (User) session.getAttribute("user") : null;

        try {
            if (path == null || path.equals("/")) {
                Map<String, Object> err = new HashMap<>();
                err.put("error", "Invalid path");
                resp.getWriter().write(gson.toJson(err));
                return;
            }

            switch (path) {
                // ===== REGISTER =====
                case "/register" -> {
                    String fullName = req.getParameter("fullName");
                    String username = req.getParameter("username");
                    String email = req.getParameter("email");
                    String password = req.getParameter("password");
                    Map<String, Object> result = new HashMap<>();

                    if (fullName == null || username == null || email == null || password == null) {
                        result.put("success", false);
                        result.put("message", "Thiếu thông tin đăng ký");
                        resp.getWriter().write(gson.toJson(result));
                        return;
                    }

                    if (userDAO.findByUsername(username.trim()) != null) {
                        result.put("success", false);
                        result.put("message", "Tên đăng nhập đã tồn tại");
                        resp.getWriter().write(gson.toJson(result));
                        return;
                    }
                    if (userDAO.findByEmail(email.trim()) != null) {
                        result.put("success", false);
                        result.put("message", "Email đã được sử dụng");
                        resp.getWriter().write(gson.toJson(result));
                        return;
                    }

                    User newUser = new User();
                    newUser.setFullName(fullName.trim());
                    newUser.setUsername(username.trim());
                    newUser.setEmail(email.trim());
                    newUser.setPassword(password);
                    newUser.setRole("USER");
                    newUser.setStatus("ACTIVE");
                    boolean ok = userDAO.insert(newUser);
                    result.put("success", ok);
                    result.put("message", ok ? "Đăng ký thành công" : "Đăng ký thất bại");
                    resp.getWriter().write(gson.toJson(result));
                }

                // ===== LOGIN =====
                case "/login" -> {
                    String username = req.getParameter("username");
                    String password = req.getParameter("password");
                    Map<String, Object> result = new HashMap<>();
                    if (username == null || password == null) {
                        result.put("success", false);
                        result.put("message", "Thiếu thông tin đăng nhập");
                    } else {
                        User user = userDAO.login(username, password);
                        if (user != null) {
                            session = req.getSession();
                            session.setAttribute("user", user);
                            result.put("success", true);
                            result.put("user", user);
                        } else {
                            User blocked = userDAO.findByUsername(username);
                            if (blocked != null && "BLOCKED".equals(blocked.getStatus())) {
                                result.put("success", false);
                                result.put("message", "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ Admin!");
                            } else {
                                result.put("success", false);
                                result.put("message", "Sai tài khoản hoặc mật khẩu");
                            }
                        }
                    }
                    resp.getWriter().write(gson.toJson(result));
                }

                // ===== LOGOUT =====
                case "/logout" -> {
                    if (session != null) session.invalidate();
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "Đã đăng xuất");
                    resp.getWriter().write(gson.toJson(result));
                }

                // ===== FORGOT PASSWORD =====
                case "/forgot-password" -> {
                    String username = req.getParameter("username");
                    String email = req.getParameter("email");
                    Map<String, Object> result = new HashMap<>();

                    if (username == null || email == null || username.trim().isEmpty() || email.trim().isEmpty()) {
                        result.put("success", false);
                        result.put("message", "Vui lòng nhập đầy đủ thông tin");
                        resp.getWriter().write(gson.toJson(result));
                        return;
                    }

                    User user = userDAO.findByUsername(username.trim());
                    if (user == null || !user.getEmail().equalsIgnoreCase(email.trim())) {
                        result.put("success", false);
                        result.put("message", "Tên đăng nhập hoặc email không đúng");
                        resp.getWriter().write(gson.toJson(result));
                        return;
                    }

                    resetTokenDAO.deleteByUserId(user.getUserId());
                    String token = UUID.randomUUID().toString();
                    PasswordResetToken resetToken = new PasswordResetToken();
                    resetToken.setUserId(user.getUserId());
                    resetToken.setToken(token);
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.HOUR, 1);
                    resetToken.setExpiryDate(cal.getTime());
                    resetTokenDAO.save(resetToken);

                    result.put("success", true);
                    result.put("token", token);
                    result.put("message", "Xác thực thành công. Vui lòng đặt lại mật khẩu.");
                    resp.getWriter().write(gson.toJson(result));
                }

                // ===== RESET PASSWORD =====
                case "/reset-password" -> {
                    String token = req.getParameter("token");
                    String newPassword = req.getParameter("newPassword");
                    Map<String, Object> result = new HashMap<>();
                    if (token == null || newPassword == null) {
                        result.put("success", false);
                        result.put("message", "Thiếu thông tin");
                        resp.getWriter().write(gson.toJson(result));
                        return;
                    }
                    PasswordResetToken resetToken = resetTokenDAO.findByToken(token);
                    if (resetToken == null) {
                        result.put("success", false);
                        result.put("message", "Token không hợp lệ hoặc đã hết hạn");
                        resp.getWriter().write(gson.toJson(result));
                        return;
                    }
                    boolean updated = userDAO.updatePassword(resetToken.getUserId(), newPassword);
                    if (updated) {
                        resetTokenDAO.markUsed(resetToken.getId());
                        result.put("success", true);
                        result.put("message", "Đặt lại mật khẩu thành công");
                    } else {
                        result.put("success", false);
                        result.put("message", "Cập nhật mật khẩu thất bại");
                    }
                    resp.getWriter().write(gson.toJson(result));
                }

                // ===== POSTS =====
                case "/posts" -> {
                    if (currentUser == null) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Chưa đăng nhập");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    // Chỉ USER mới được đăng bài
                    if (!"USER".equals(currentUser.getRole())) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Chỉ thành viên mới được đăng bài");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    String action = req.getParameter("action");
                    if (action == null) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Missing action parameter");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }

                    switch (action) {
                        case "add" -> {
                            String title = req.getParameter("title");
                            String content = req.getParameter("content");
                            Map<String, Object> result = new HashMap<>();
                            if (title == null || content == null) {
                                result.put("error", "Thiếu tiêu đề hoặc nội dung");
                            } else {
                                Post p = new Post();
                                p.setTitle(title);
                                p.setContent(content);
                                p.setSummary(req.getParameter("summary"));
                                p.setCategoryId(Long.parseLong(req.getParameter("categoryId")));
                                p.setAuthorId(currentUser.getUserId());
                                p.setStatus("PENDING");
                                boolean ok = postDAO.insert(p);
                                result.put("success", ok);
                                result.put("message", ok ? "Đã gửi bài, chờ duyệt" : "Lỗi khi đăng bài");
                            }
                            resp.getWriter().write(gson.toJson(result));
                        }
                        case "approve" -> {
                            if (!"MODERATOR".equals(currentUser.getRole()) && !"ADMIN".equals(currentUser.getRole())) {
                                Map<String, Object> err = new HashMap<>();
                                err.put("error", "Không có quyền duyệt bài");
                                resp.getWriter().write(gson.toJson(err));
                                return;
                            }
                            long id = Long.parseLong(req.getParameter("id"));
                            boolean ok = postDAO.approve(id, currentUser.getUserId());
                            Map<String, Object> result = new HashMap<>();
                            result.put("success", ok);
                            result.put("message", ok ? "Đã duyệt bài" : "Duyệt thất bại");
                            resp.getWriter().write(gson.toJson(result));
                        }
                        case "reject" -> {
                            if (!"MODERATOR".equals(currentUser.getRole()) && !"ADMIN".equals(currentUser.getRole())) {
                                Map<String, Object> err = new HashMap<>();
                                err.put("error", "Không có quyền từ chối bài");
                                resp.getWriter().write(gson.toJson(err));
                                return;
                            }
                            long id = Long.parseLong(req.getParameter("id"));
                            boolean ok = postDAO.reject(id, currentUser.getUserId());
                            Map<String, Object> result = new HashMap<>();
                            result.put("success", ok);
                            result.put("message", ok ? "Đã từ chối bài" : "Từ chối thất bại");
                            resp.getWriter().write(gson.toJson(result));
                        }
                        case "delete" -> {
                            long id = Long.parseLong(req.getParameter("id"));
                            Post p = postDAO.findById(id);
                            if (p == null || (p.getAuthorId() != currentUser.getUserId() && !"ADMIN".equals(currentUser.getRole()))) {
                                Map<String, Object> err = new HashMap<>();
                                err.put("error", "Không có quyền xóa bài viết này");
                                resp.getWriter().write(gson.toJson(err));
                                return;
                            }
                            boolean ok = postDAO.delete(id);
                            Map<String, Object> result = new HashMap<>();
                            result.put("success", ok);
                            result.put("message", ok ? "Đã xóa bài viết" : "Xóa thất bại");
                            resp.getWriter().write(gson.toJson(result));
                        }
                        case "resubmit" -> {
                            long id = Long.parseLong(req.getParameter("id"));
                            Post p = postDAO.findById(id);
                            Map<String, Object> result = new HashMap<>();
                            if (p == null || p.getAuthorId() != currentUser.getUserId()) {
                                result.put("error", "Không có quyền gửi lại bài này");
                            } else if (!"REJECTED".equals(p.getStatus())) {
                                result.put("error", "Chỉ được gửi lại bài đã bị từ chối");
                            } else {
                                boolean ok = postDAO.resubmit(id);
                                result.put("success", ok);
                                result.put("message", ok ? "Đã gửi lại bài, chờ duyệt" : "Gửi lại thất bại");
                            }
                            resp.getWriter().write(gson.toJson(result));
                        }
                        default -> {
                            Map<String, Object> err = new HashMap<>();
                            err.put("error", "Hành động không hợp lệ: " + action);
                            resp.getWriter().write(gson.toJson(err));
                        }
                    }
                }

                // ===== COMMENTS =====
                case "/comments" -> {
                    if (currentUser == null) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Chưa đăng nhập");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    String action = req.getParameter("action");
                    if (action == null) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Missing action parameter");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }

                    switch (action) {
                        case "add" -> {
                            String content = req.getParameter("content");
                            long postId = Long.parseLong(req.getParameter("postId"));
                            String parentIdStr = req.getParameter("parentId");
                            Map<String, Object> result = new HashMap<>();
                            if (content == null || content.trim().isEmpty()) {
                                result.put("error", "Nội dung bình luận không được để trống");
                            } else {
                                Comment c = new Comment();
                                c.setContent(content.trim());
                                c.setUserId(currentUser.getUserId());
                                c.setPostId(postId);
                                boolean ok;
                                if (parentIdStr != null && !parentIdStr.isEmpty()) {
                                    c.setParentId(Long.parseLong(parentIdStr));
                                    ok = commentDAO.insertReply(c);
                                } else {
                                    ok = commentDAO.insert(c);
                                }
                                result.put("success", ok);
                                result.put("message", ok ? "Đã bình luận" : "Bình luận thất bại");
                            }
                            resp.getWriter().write(gson.toJson(result));
                        }
                        case "toggle" -> {
                            if (!"MODERATOR".equals(currentUser.getRole()) && !"ADMIN".equals(currentUser.getRole())) {
                                Map<String, Object> err = new HashMap<>();
                                err.put("error", "Không có quyền");
                                resp.getWriter().write(gson.toJson(err));
                                return;
                            }
                            long id = Long.parseLong(req.getParameter("id"));
                            Comment c = commentDAO.findById(id);
                            if (c == null) {
                                resp.getWriter().write("{\"error\":\"Comment not found\"}");
                                return;
                            }
                            String newStatus = "VISIBLE".equals(c.getStatus()) ? "HIDDEN" : "VISIBLE";
                            boolean ok = commentDAO.updateStatus(id, newStatus);
                            Map<String, Object> result = new HashMap<>();
                            result.put("success", ok);
                            result.put("message", ok ? "Đã cập nhật trạng thái bình luận" : "Cập nhật thất bại");
                            resp.getWriter().write(gson.toJson(result));
                        }
                        case "delete" -> {
                            if (!"MODERATOR".equals(currentUser.getRole()) && !"ADMIN".equals(currentUser.getRole())) {
                                Map<String, Object> err = new HashMap<>();
                                err.put("error", "Không có quyền xóa bình luận");
                                resp.getWriter().write(gson.toJson(err));
                                return;
                            }
                            long id = Long.parseLong(req.getParameter("id"));
                            boolean ok = commentDAO.delete(id);
                            Map<String, Object> result = new HashMap<>();
                            result.put("success", ok);
                            result.put("message", ok ? "Đã xóa bình luận" : "Xóa thất bại");
                            resp.getWriter().write(gson.toJson(result));
                        }
                        default -> {
                            Map<String, Object> err = new HashMap<>();
                            err.put("error", "Hành động không hợp lệ: " + action);
                            resp.getWriter().write(gson.toJson(err));
                        }
                    }
                }

                // ===== COMMENT LIKE =====
                case "/comment/like" -> {
                    if (currentUser == null) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Chưa đăng nhập");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    long commentId = Long.parseLong(req.getParameter("id"));
                    boolean ok = commentDAO.likeComment(currentUser.getUserId(), commentId);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", ok);
                    if (ok) {
                        result.put("likeCount", commentDAO.countLikes(commentId));
                        result.put("dislikeCount", commentDAO.countDislikes(commentId));
                        result.put("message", "Đã thích bình luận");
                    } else {
                        result.put("message", "Thích thất bại");
                    }
                    resp.getWriter().write(gson.toJson(result));
                }

                // ===== COMMENT UNLIKE =====
                case "/comment/unlike" -> {
                    if (currentUser == null) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Chưa đăng nhập");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    long commentId = Long.parseLong(req.getParameter("id"));
                    boolean ok = commentDAO.unlikeComment(currentUser.getUserId(), commentId);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", ok);
                    if (ok) {
                        result.put("likeCount", commentDAO.countLikes(commentId));
                        result.put("dislikeCount", commentDAO.countDislikes(commentId));
                        result.put("message", "Đã bỏ thích bình luận");
                    } else {
                        result.put("message", "Bỏ thích thất bại");
                    }
                    resp.getWriter().write(gson.toJson(result));
                }

                // ===== COMMENT DISLIKE =====
                case "/comment/dislike" -> {
                    if (currentUser == null) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Chưa đăng nhập");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    long commentId = Long.parseLong(req.getParameter("id"));
                    boolean ok = commentDAO.dislikeComment(currentUser.getUserId(), commentId);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", ok);
                    if (ok) {
                        result.put("likeCount", commentDAO.countLikes(commentId));
                        result.put("dislikeCount", commentDAO.countDislikes(commentId));
                        result.put("message", "Đã không thích bình luận");
                    } else {
                        result.put("message", "Không thích thất bại");
                    }
                    resp.getWriter().write(gson.toJson(result));
                }

                // ===== LIKE POST =====
                case "/like" -> {
                    if (currentUser == null) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Chưa đăng nhập");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    long postId = Long.parseLong(req.getParameter("id"));
                    boolean ok = interactionDAO.like(currentUser.getUserId(), postId);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", ok);
                    if (ok) {
                        result.put("likeCount", interactionDAO.countLikes(postId));
                        result.put("message", "Đã thích bài viết");
                    } else {
                        result.put("message", "Thích thất bại (có thể đã thích)");
                    }
                    resp.getWriter().write(gson.toJson(result));
                }
                case "/unlike" -> {
                    if (currentUser == null) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Chưa đăng nhập");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    long postId = Long.parseLong(req.getParameter("id"));
                    boolean ok = interactionDAO.unlike(currentUser.getUserId(), postId);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", ok);
                    if (ok) {
                        result.put("likeCount", interactionDAO.countLikes(postId));
                        result.put("message", "Đã bỏ thích bài viết");
                    } else {
                        result.put("message", "Bỏ thích thất bại (có thể chưa thích)");
                    }
                    resp.getWriter().write(gson.toJson(result));
                }

                // ===== SETTINGS =====
                case "/settings" -> {
                    if (currentUser == null) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Chưa đăng nhập");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    UserSettings s = new UserSettings();
                    s.setUserId(currentUser.getUserId());
                    s.setTheme(req.getParameter("theme"));
                    s.setPrimaryColor(req.getParameter("primaryColor"));
                    s.setSecondaryColor(req.getParameter("secondaryColor"));
                    s.setBackgroundColor(req.getParameter("backgroundColor"));
                    s.setTextColor(req.getParameter("textColor"));
                    s.setFontFamily(req.getParameter("fontFamily"));
                    s.setCoverImage(req.getParameter("coverImage"));
                    s.setCustomCss(req.getParameter("customCss"));
                    boolean ok = settingsDAO.upsert(s);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", ok);
                    result.put("message", ok ? "Đã cập nhật cài đặt" : "Cập nhật thất bại");
                    resp.getWriter().write(gson.toJson(result));
                }

                // ===== USERS (Admin) =====
                case "/users" -> {
                    if (currentUser == null || !"ADMIN".equals(currentUser.getRole())) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "Không có quyền");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    String action = req.getParameter("action");
                    if (action == null) {
                        resp.getWriter().write("{\"error\":\"Missing action\"}");
                        return;
                    }

                    switch (action) {
                        case "block" -> {
                            long id = Long.parseLong(req.getParameter("id"));
                            String status = req.getParameter("status");
                            boolean ok = userDAO.updateStatus(id, status);
                            Map<String, Object> result = new HashMap<>();
                            result.put("success", ok);
                            result.put("message", ok ? "Đã cập nhật trạng thái" : "Cập nhật thất bại");
                            resp.getWriter().write(gson.toJson(result));
                        }
                        case "changeRole" -> {
                            long id = Long.parseLong(req.getParameter("id"));
                            String role = req.getParameter("role");
                            boolean ok = userDAO.updateRole(id, role);
                            Map<String, Object> result = new HashMap<>();
                            result.put("success", ok);
                            result.put("message", ok ? "Đã đổi quyền" : "Đổi quyền thất bại");
                            resp.getWriter().write(gson.toJson(result));
                        }
                        case "updateProfile" -> {
                            long id = currentUser.getUserId();
                            String fullName = req.getParameter("fullName");
                            String email = req.getParameter("email");
                            String oldPassword = req.getParameter("oldPassword");
                            String newPassword = req.getParameter("newPassword");

                            Map<String, Object> result = new HashMap<>();
                            if (fullName == null || fullName.trim().isEmpty()) {
                                result.put("success", false);
                                result.put("message", "Họ tên không được để trống");
                                resp.getWriter().write(gson.toJson(result));
                                return;
                            }
                            if (newPassword != null && !newPassword.isEmpty()) {
                                if (oldPassword == null || oldPassword.isEmpty()) {
                                    result.put("success", false);
                                    result.put("message", "Vui lòng nhập mật khẩu cũ");
                                    resp.getWriter().write(gson.toJson(result));
                                    return;
                                }
                                if (newPassword.length() < 4) {
                                    result.put("success", false);
                                    result.put("message", "Mật khẩu mới ít nhất 4 ký tự");
                                    resp.getWriter().write(gson.toJson(result));
                                    return;
                                }
                                User u = userDAO.findById(id);
                                if (u == null || !u.getPassword().equals(oldPassword)) {
                                    result.put("success", false);
                                    result.put("message", "Mật khẩu cũ không đúng");
                                    resp.getWriter().write(gson.toJson(result));
                                    return;
                                }
                                userDAO.updatePassword(id, newPassword);
                            }
                            User u = new User();
                            u.setUserId(id);
                            u.setFullName(fullName);
                            u.setEmail(email);
                            boolean ok = userDAO.updateProfile(u);
                            result.put("success", ok);
                            result.put("message", ok ? "Đã cập nhật thông tin" : "Cập nhật thất bại");
                            resp.getWriter().write(gson.toJson(result));
                        }
                        default -> {
                            resp.getWriter().write("{\"error\":\"Invalid action\"}");
                        }
                    }
                }

                default -> {
                    Map<String, Object> err = new HashMap<>();
                    err.put("error", "Endpoint không tồn tại: " + path);
                    resp.getWriter().write(gson.toJson(err));
                }
            }
        } catch (NumberFormatException e) {
            resp.getWriter().write("{\"error\":\"Invalid number format: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}