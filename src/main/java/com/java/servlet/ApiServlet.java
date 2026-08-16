package com.java.servlet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.java.dao.BookmarkDAO;
import com.java.dao.CategoryDAO;
import com.java.dao.CommentDAO;
import com.java.dao.CommentHistoryDAO;
import com.java.dao.InteractionDAO;
import com.java.dao.InteractionHistoryDAO;
import com.java.dao.NotificationDAO;
import com.java.dao.PasswordResetTokenDAO;
import com.java.dao.PostDAO;
import com.java.dao.ReportDAO;
import com.java.dao.TagDAO;
import com.java.dao.UserDAO;
import com.java.dao.UserSettingsDAO;
import com.java.dao.ViewHistoryDAO;
import com.java.model.Category;
import com.java.model.Comment;
import com.java.model.Notification;
import com.java.model.PasswordResetToken;
import com.java.model.Post;
import com.java.model.Tag;
import com.java.model.User;
import com.java.model.UserSettings;
import com.java.util.MailService;
import com.java.util.PasswordUtil;
import com.java.util.TokenUtil;
import com.java.util.UploadStorage;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

@WebServlet("/api/*")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public class ApiServlet extends HttpServlet {
    private static final Set<String> MOD_ROLES = Set.of("MODERATOR", "ADMIN");
    private static final Set<String> USER_ROLES = Set.of("USER", "MODERATOR", "ADMIN");
    private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "BLOCKED", "RESTRICTED");
    private static final Set<String> REPORT_STATUSES = Set.of("PENDING", "PROCESSING", "RESOLVED", "REJECTED");

    private final Gson gson = new Gson();
    private final UserDAO userDAO = new UserDAO();
    private final PostDAO postDAO = new PostDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final CommentDAO commentDAO = new CommentDAO();
    private final TagDAO tagDAO = new TagDAO();
    private final InteractionDAO interactionDAO = new InteractionDAO();
    private final UserSettingsDAO settingsDAO = new UserSettingsDAO();
    private final PasswordResetTokenDAO resetTokenDAO = new PasswordResetTokenDAO();
    private final ReportDAO reportDAO = new ReportDAO();
    private final BookmarkDAO bookmarkDAO = new BookmarkDAO();
    private final ViewHistoryDAO viewHistoryDAO = new ViewHistoryDAO();
    private final CommentHistoryDAO commentHistoryDAO = new CommentHistoryDAO();
    private final InteractionHistoryDAO interactionHistoryDAO = new InteractionHistoryDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        prepare(resp);
        String path = normalizePath(req.getPathInfo());
        User currentUser = currentUser(req);
        if (req.getAttribute("forcedLogoutStatus") != null && !"/current-user".equals(path)) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, forcedLogoutMessage(req.getAttribute("forcedLogoutStatus")));
            return;
        }

        try {
            switch (path) {
                case "/" -> ok(resp, Map.of("message", "Blog SE API is running"));
                case "/current-user" -> getCurrentUser(req, resp, currentUser);
                case "/posts" -> getPosts(req, resp, currentUser);
                case "/categories" -> ok(resp, categoryDAO.findAll());
                case "/tags" -> ok(resp, tagDAO.findAll());
                case "/comments" -> getComments(req, resp, currentUser);
                case "/settings" -> getSettings(resp, currentUser);
                case "/notifications" -> getNotifications(req, resp, currentUser);
                case "/users" -> getUsers(req, resp, currentUser);
                case "/authors" -> getAuthor(req, resp);
                case "/bookmarks" -> getBookmarks(req, resp, currentUser);
                case "/history/posts" -> requireRegularUser(resp, currentUser, () -> ok(resp, viewHistoryDAO.getViewHistoryByUser(currentUser.getUserId())));
                case "/history/comments" -> requireRegularUser(resp, currentUser, () -> ok(resp, commentHistoryDAO.getCommentHistoryByUser(currentUser.getUserId())));
                case "/history/interactions" -> getInteractionHistory(req, resp, currentUser);
                case "/reports" -> requireModerator(resp, currentUser, () -> ok(resp, reportDAO.findAll()));
                case "/support/messages" -> requireModerator(resp, currentUser, () -> ok(resp, reportDAO.findSupportMessages()));
                case "/reset-password/validate" -> validateResetToken(req, resp);
                default -> error(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint không tồn tại");
            }
        } catch (IllegalArgumentException ex) {
            error(resp, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        } catch (IOException ex) {
            error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Máy chủ không thể xử lý yêu cầu");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        prepare(resp);
        String path = normalizePath(req.getPathInfo());
        User currentUser = currentUser(req);
        if (req.getAttribute("forcedLogoutStatus") != null && !Set.of("/login", "/logout").contains(path)) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, forcedLogoutMessage(req.getAttribute("forcedLogoutStatus")));
            return;
        }

        try {
            switch (path) {
                case "/register" -> register(req, resp);
                case "/login" -> login(req, resp);
                case "/logout" -> logout(req, resp);
                case "/forgot-password" -> forgotPassword(req, resp);
                case "/reset-password" -> resetPassword(req, resp);
                case "/categories" -> manageCategories(req, resp, currentUser);
                case "/tags" -> manageTags(req, resp, currentUser);
                case "/posts" -> managePosts(req, resp, currentUser);
                case "/posts/update" -> updatePost(req, resp, currentUser);
                case "/posts/restore" -> restorePost(req, resp, currentUser);
                case "/posts/report" -> reportPost(req, resp, currentUser);
                case "/comments/report" -> reportComment(req, resp, currentUser);
                case "/comments" -> manageComments(req, resp, currentUser);
                case "/comment/like" -> likeComment(req, resp, currentUser, true);
                case "/comment/unlike" -> likeComment(req, resp, currentUser, false);
                case "/comment/dislike" -> dislikeComment(req, resp, currentUser);
                case "/like" -> likePost(req, resp, currentUser, true);
                case "/unlike" -> likePost(req, resp, currentUser, false);
                case "/bookmark/toggle" -> toggleBookmark(req, resp, currentUser);
                case "/view-history" -> addViewHistory(req, resp, currentUser);
                case "/comments/restore" -> restoreComment(req, resp, currentUser);
                case "/comments/delete-by-user" -> deleteOwnComment(req, resp, currentUser);
                case "/reports/update" -> updateReport(req, resp, currentUser);
                case "/reports/delete" -> deleteReport(req, resp, currentUser);
                case "/support" -> createSupport(req, resp, currentUser);
                case "/support/resolve" -> resolveSupport(req, resp, currentUser);
                case "/notifications" -> manageNotifications(req, resp, currentUser);
                case "/settings" -> saveSettings(req, resp, currentUser);
                case "/upload-avatar" -> uploadAvatar(req, resp, currentUser);
                case "/upload-thumbnail" -> uploadThumbnail(req, resp, currentUser);
                case "/users" -> manageUsers(req, resp, currentUser);
                default -> error(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint không tồn tại");
            }
        } catch (IllegalArgumentException ex) {
            error(resp, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        } catch (ServletException | IOException ex) {
            error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Máy chủ không thể xử lý yêu cầu");
        }
    }

    private void getCurrentUser(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        if (user == null) {
            Object forcedStatus = req.getAttribute("forcedLogoutStatus");
            if (forcedStatus != null) {
                String status = String.valueOf(forcedStatus);
                ok(resp, Map.of("success", false, "forcedLogout", true, "status", status,
                    "message", forcedLogoutMessage(forcedStatus)));
                return;
            }
            ok(resp, Map.of("success", false, "message", "Chưa đăng nhập"));
            return;
        }
        ok(resp, Map.of("success", true, "user", user));
    }

    private void getPosts(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        String action = text(req, "action", 30, true);
        switch (action) {
            case "list" -> ok(resp, postDAO.findApproved());
            case "my" -> requireRegularUser(resp, user, () -> ok(resp, postDAO.findByAuthor(user.getUserId())));
            case "pending" -> requireModerator(resp, user, () -> ok(resp, postDAO.findPending()));
            case "all" -> requireAdmin(resp, user, () -> ok(resp, postDAO.findAllForAdmin()));
            case "detail" -> {
                long id = longParam(req, "id");
                Post post = postDAO.findById(id);
                if (post == null) {
                    error(resp, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy bài viết");
                    return;
                }
                if (!canViewPost(post, user)) {
                    error(resp, HttpServletResponse.SC_FORBIDDEN, "Bạn không có quyền xem bài viết này");
                    return;
                }
                if ("APPROVED".equals(post.getStatus())) {
                    postDAO.incrementViewCount(id);
                    post.setViewCount(post.getViewCount() + 1);
                }
                ok(resp, post);
            }
            case "filter" -> ok(resp, postDAO.filterWithPaging(
                optional(req, "categoryId", 30),
                optional(req, "tagId", 30),
                optional(req, "authorId", 30),
                optional(req, "keyword", 150),
                optional(req, "sort", 30),
                intParam(req, "page", 1, 1, 100000),
                intParam(req, "limit", 9, 1, 50)
            ));
            case "related" -> {
                long postId = longParam(req, "id");
                long categoryId = longParam(req, "categoryId");
                ok(resp, postDAO.findRelated(postId, categoryId, intParam(req, "limit", 3, 1, 8)));
            }
            default -> throw new IllegalArgumentException("Hành động bài viết không hợp lệ");
        }
    }

    private void getComments(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        String action = optional(req, "action", 30);
        if ("all".equals(action)) {
            requireModerator(resp, user, () -> ok(resp, commentDAO.findAll()));
            return;
        }
        long postId = longParam(req, "postId");
        Long userId = user == null ? null : user.getUserId();
        ok(resp, commentDAO.findByPost(postId, userId));
    }

    private void getSettings(HttpServletResponse resp, User user) throws IOException {
        if (user == null) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, "Chưa đăng nhập");
            return;
        }
        UserSettings settings = settingsDAO.findByUserId(user.getUserId());
        if (settings == null) {
            settings = defaultSettings(user.getUserId());
        }
        settings.setCustomCss("");
        ok(resp, settings);
    }

    private void getNotifications(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        if (user == null) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, "Chưa đăng nhập");
            return;
        }
        int limit = intParam(req, "limit", 15, 1, 50);
        List<Notification> items = notificationDAO.findByUserId(user.getUserId(), limit);
        ok(resp, Map.of("items", items, "unreadCount", notificationDAO.countUnread(user.getUserId())));
    }

    private void getUsers(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        if (!isAdmin(user)) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Không có quyền");
            return;
        }
        String action = text(req, "action", 20, true);
        switch (action) {
            case "list" -> ok(resp, userDAO.findAll());
            case "search" -> ok(resp, userDAO.search(optional(req, "keyword", 100)));
            case "detail" -> {
                User found = userDAO.findById(longParam(req, "id"));
                if (found == null) error(resp, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy người dùng");
                else ok(resp, found);
            }
            default -> throw new IllegalArgumentException("Hành động người dùng không hợp lệ");
        }
    }

    private void getAuthor(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long id = longParam(req, "id");
        User author = userDAO.findById(id);
        if (author == null || !"ACTIVE".equals(author.getStatus())) {
            error(resp, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy tác giả");
            return;
        }
        Map<String, Object> publicAuthor = new HashMap<>();
        publicAuthor.put("userId", author.getUserId());
        publicAuthor.put("fullName", author.getFullName());
        publicAuthor.put("username", author.getUsername());
        publicAuthor.put("avatar", author.getAvatar());
        publicAuthor.put("createdAt", author.getCreatedAt());
        publicAuthor.put("stats", postDAO.getAuthorStats(id));
        ok(resp, publicAuthor);
    }

    private void getBookmarks(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        if (!isRegularUser(user)) {
            error(resp, user == null ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN,
                user == null ? "Chưa đăng nhập" : "Chức năng này chỉ dành cho thành viên");
            return;
        }
        if ("list".equals(optional(req, "action", 20))) {
            ok(resp, bookmarkDAO.getBookmarksByUser(user.getUserId()));
            return;
        }
        long postId = longParam(req, "postId");
        ok(resp, Map.of("bookmarked", bookmarkDAO.isBookmarked(user.getUserId(), postId)));
    }

    private void getInteractionHistory(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireModerator(resp, user, () -> ok(resp, interactionHistoryDAO.getUserInteractions(longParam(req, "userId"))));
    }

    private void validateResetToken(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String token = text(req, "token", 200, true);
        ok(resp, Map.of("valid", resetTokenDAO.findByToken(token) != null));
    }

    private void register(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String fullName = text(req, "fullName", 100, true);
        String username = text(req, "username", 50, true);
        String email = email(req, "email");
        String password = text(req, "password", 72, true);
        PasswordUtil.validate(password);

        if (userDAO.findByUsername(username) != null) {
            conflict(resp, "Tên đăng nhập đã tồn tại");
            return;
        }
        if (userDAO.findByEmail(email) != null) {
            conflict(resp, "Email đã được sử dụng");
            return;
        }

        User user = new User();
        user.setFullName(fullName);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        user.setRole("USER");
        user.setStatus("ACTIVE");
        boolean inserted = userDAO.insert(user);
        result(resp, inserted, inserted ? "Đăng ký thành công" : "Đăng ký thất bại");
    }

    private void login(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = text(req, "username", 50, true);
        String password = text(req, "password", 72, true);
        User user = userDAO.login(username, password);
        if (user == null) {
            User existing = userDAO.findByUsername(username);
            String message = "Sai tài khoản hoặc mật khẩu";
            if (existing != null && "BLOCKED".equals(existing.getStatus())) {
                message = "Tài khoản đã bị khóa";
            } else if (existing != null && "RESTRICTED".equals(existing.getStatus())) {
                message = "Tài khoản đang bị hạn chế do đã bị báo cáo đủ 3 lần";
            }
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, message);
            return;
        }

        HttpSession oldSession = req.getSession(false);
        if (oldSession != null) oldSession.invalidate();
        HttpSession newSession = req.getSession(true);
        newSession.setMaxInactiveInterval(30 * 60);
        newSession.setAttribute("user", user);
        ok(resp, Map.of("success", true, "user", user));
    }

    private void logout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
        result(resp, true, "Đã đăng xuất");
    }

    private void forgotPassword(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = text(req, "username", 50, true);
        String email = email(req, "email");
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Nếu thông tin khớp, liên kết đặt lại mật khẩu đã được gửi.");

        User user = userDAO.findByUsername(username);
        if (user != null && email.equalsIgnoreCase(user.getEmail())) {
            resetTokenDAO.deleteExpired();
            resetTokenDAO.deleteByUserId(user.getUserId());
            String rawToken = TokenUtil.randomUrlToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getUserId());
            token.setToken(rawToken);
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MINUTE, 30);
            token.setExpiryDate(calendar.getTime());
            resetTokenDAO.save(token);

            String resetUrl = buildResetUrl(req, rawToken);
            boolean mailed = MailService.sendPasswordReset(user.getEmail(), user.getFullName(), resetUrl);
            if (!mailed) {
                System.out.println("[Blog SE development reset URL] " + resetUrl);
                if (isLocalRequest(req)) response.put("devResetUrl", resetUrl);
            }
        }
        ok(resp, response);
    }

    private void resetPassword(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String token = text(req, "token", 200, true);
        String password = text(req, "newPassword", 72, true);
        PasswordUtil.validate(password);
        PasswordResetToken resetToken = resetTokenDAO.findByToken(token);
        if (resetToken == null) {
            error(resp, HttpServletResponse.SC_BAD_REQUEST, "Liên kết không hợp lệ hoặc đã hết hạn");
            return;
        }
        boolean updated = userDAO.updatePassword(resetToken.getUserId(), password);
        if (updated) resetTokenDAO.markUsed(resetToken.getId());
        result(resp, updated, updated ? "Đặt lại mật khẩu thành công" : "Cập nhật mật khẩu thất bại");
    }

    private void manageCategories(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        if (!isAdmin(user)) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Chỉ Admin được quản lý danh mục");
            return;
        }
        String action = text(req, "action", 20, true);
        boolean success;
        switch (action) {
            case "add" -> {
                Category category = new Category();
                category.setCategoryName(text(req, "name", 100, true));
                category.setDescription(optional(req, "description", 255));
                success = categoryDAO.insert(category);
            }
            case "update" -> {
                Category category = categoryDAO.findById(longParam(req, "id"));
                if (category == null) {
                    error(resp, HttpServletResponse.SC_NOT_FOUND, "Danh mục không tồn tại");
                    return;
                }
                category.setCategoryName(text(req, "name", 100, true));
                category.setDescription(optional(req, "description", 255));
                success = categoryDAO.update(category);
            }
            case "delete" -> success = categoryDAO.delete(longParam(req, "id"));
            default -> throw new IllegalArgumentException("Hành động danh mục không hợp lệ");
        }
        result(resp, success, success ? "Đã cập nhật danh mục" : "Không thể cập nhật danh mục");
    }

    private void manageTags(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        if (!isAdmin(user)) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Chỉ Admin được quản lý thẻ");
            return;
        }
        String action = text(req, "action", 20, true);
        boolean success;
        switch (action) {
            case "add" -> {
                Tag tag = new Tag();
                tag.setTagName(text(req, "name", 100, true));
                tag.setDescription(optional(req, "description", 255));
                success = tagDAO.insert(tag);
            }
            case "update" -> {
                Tag tag = tagDAO.findById(longParam(req, "id"));
                if (tag == null) {
                    error(resp, HttpServletResponse.SC_NOT_FOUND, "Thẻ không tồn tại");
                    return;
                }
                tag.setTagName(text(req, "name", 100, true));
                tag.setDescription(optional(req, "description", 255));
                success = tagDAO.update(tag);
            }
            case "delete" -> success = tagDAO.delete(longParam(req, "id"));
            default -> throw new IllegalArgumentException("Hành động thẻ không hợp lệ");
        }
        result(resp, success, success ? "Đã cập nhật thẻ" : "Không thể cập nhật thẻ");
    }

    private void managePosts(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        if (user == null) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, "Chưa đăng nhập");
            return;
        }
        String action = text(req, "action", 30, true);
        switch (action) {
            case "add", "saveDraft" -> createPost(req, resp, user, "saveDraft".equals(action));
            case "approve" -> approvePost(req, resp, user);
            case "reject" -> rejectPost(req, resp, user);
            case "delete" -> deletePost(req, resp, user);
            case "resubmit" -> resubmitPost(req, resp, user);
            default -> throw new IllegalArgumentException("Hành động bài viết không hợp lệ");
        }
    }

    private void createPost(HttpServletRequest req, HttpServletResponse resp, User user, boolean draft) throws IOException {
        if (!"USER".equals(user.getRole())) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Chỉ thành viên được viết bài");
            return;
        }
        Post post = postFromRequest(req);
        post.setAuthorId(user.getUserId());
        post.setStatus(draft ? "DRAFT" : "PENDING");
        boolean success = postDAO.insert(post, post.getTags());
        if (success && !draft) {
            notifyModeratorsAboutPendingPost(post, user, false);
        }
        result(resp, success, success ? (draft ? "Đã lưu bản nháp" : "Đã gửi bài để duyệt") : "Không thể lưu bài viết");
    }

    private void approvePost(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        if (!isModerator(user)) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Không có quyền duyệt bài");
            return;
        }
        long id = longParam(req, "id");
        Post before = postDAO.findById(id);
        boolean success = postDAO.approve(id, user.getUserId());
        if (success && before != null) {
            notificationDAO.insert(before.getAuthorId(), "POST_APPROVED", "Bài viết đã được duyệt",
                "Bài “" + before.getTitle() + "” đã được xuất bản.", "post-detail.html?id=" + id);
        }
        result(resp, success, success ? "Đã duyệt bài" : "Không thể duyệt bài");
    }

    private void rejectPost(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        if (!isModerator(user)) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Không có quyền từ chối bài");
            return;
        }
        long id = longParam(req, "id");
        String reason = text(req, "reason", 500, true);
        Post before = postDAO.findById(id);
        if (before == null) {
            error(resp, HttpServletResponse.SC_NOT_FOUND, "Bài viết không tồn tại");
            return;
        }
        if (before.getRejectCount() >= 3) {
            error(resp, HttpServletResponse.SC_CONFLICT,
                "Bài viết đã bị từ chối đủ 3 lần. Không thể từ chối thêm; hãy duyệt hoặc xử lý bài theo quyền quản trị.");
            return;
        }
        boolean success = postDAO.reject(id, user.getUserId(), reason);
        if (success) {
            int nextRejectCount = before.getRejectCount() + 1;
            String suffix = nextRejectCount >= 3
                ? " Đây là lần từ chối thứ 3/3; bài viết này không thể gửi lại nữa. Hãy tạo bài viết mới sau khi chỉnh sửa nội dung."
                : " Số lần từ chối: " + nextRejectCount + "/3.";
            notificationDAO.insert(before.getAuthorId(), "POST_REJECTED", "Bài viết cần chỉnh sửa",
                "Bài “" + before.getTitle() + "” bị từ chối. Lý do: " + reason + suffix, "dashboard.html#posts");
        }
        result(resp, success, success ? "Đã từ chối bài và gửi lý do" : "Không thể từ chối bài");
    }

    private void deletePost(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        long id = longParam(req, "id");
        Post post = postDAO.findById(id);
        if (post == null) {
            error(resp, HttpServletResponse.SC_NOT_FOUND, "Bài viết không tồn tại");
            return;
        }
        boolean allowed = (isRegularUser(user) && post.getAuthorId() == user.getUserId()) || isAdmin(user);
        if (!allowed) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Không có quyền xóa bài viết");
            return;
        }
        result(resp, postDAO.delete(id), "Đã chuyển bài viết vào thùng rác");
    }

    private void resubmitPost(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        long id = longParam(req, "id");
        Post post = postDAO.findById(id);
        if (!isRegularUser(user) || post == null || post.getAuthorId() != user.getUserId() || !"REJECTED".equals(post.getStatus())) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Chỉ tác giả được gửi lại bài bị từ chối");
            return;
        }
        if (post.getRejectCount() >= 3) {
            error(resp, HttpServletResponse.SC_CONFLICT,
                "Bài viết đã bị từ chối đủ 3 lần và không thể gửi lại. Hãy tạo bài viết mới.");
            return;
        }
        boolean success = postDAO.resubmit(id);
        if (success) {
            notifyModeratorsAboutPendingPost(post, user, true);
        }
        result(resp, success, "Đã gửi lại bài để duyệt");
    }

    private void updatePost(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        if (user == null) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, "Chưa đăng nhập");
            return;
        }
        long postId = longParam(req, "postId");
        Post existing = postDAO.findById(postId);
        if (existing == null) {
            error(resp, HttpServletResponse.SC_NOT_FOUND, "Bài viết không tồn tại");
            return;
        }
        if (existing.getAuthorId() != user.getUserId() && !isAdmin(user)) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Không có quyền sửa bài viết");
            return;
        }

        Post updated = postFromRequest(req);
        updated.setPostId(postId);
        updated.setAuthorId(existing.getAuthorId());
        boolean resubmit = "true".equalsIgnoreCase(optional(req, "resubmit", 10));
        boolean submitForReview = "true".equalsIgnoreCase(optional(req, "submitForReview", 10));
        boolean author = existing.getAuthorId() == user.getUserId();
        boolean success;
        String successMessage;

        if (submitForReview) {
            if (!author || !"DRAFT".equals(existing.getStatus())) {
                error(resp, HttpServletResponse.SC_FORBIDDEN, "Chỉ tác giả được gửi bản nháp để duyệt");
                return;
            }
            success = postDAO.updateDraftAndSubmit(updated);
            successMessage = "Đã gửi bản nháp để duyệt";
            if (success) {
                notifyModeratorsAboutPendingPost(updated, user, false);
            }
        } else if (resubmit) {
            if (!author || !"REJECTED".equals(existing.getStatus())) {
                error(resp, HttpServletResponse.SC_FORBIDDEN, "Chỉ tác giả được sửa và gửi lại bài bị từ chối");
                return;
            }
            if (existing.getRejectCount() >= 3) {
                error(resp, HttpServletResponse.SC_CONFLICT,
                    "Bài viết đã bị từ chối đủ 3 lần và không thể gửi lại. Hãy tạo bài viết mới.");
                return;
            }
            success = postDAO.updateRejectedAndResubmit(updated);
            successMessage = "Đã sửa và gửi lại bài";
            if (success) {
                notifyModeratorsAboutPendingPost(updated, user, true);
            }
        } else {
            if (author && !Set.of("DRAFT", "PENDING").contains(existing.getStatus())) {
                error(resp, HttpServletResponse.SC_CONFLICT,
                    "Bài đã xuất bản không thể sửa trực tiếp. Hãy tạo phiên bản mới hoặc liên hệ quản trị viên.");
                return;
            }
            success = postDAO.update(updated);
            successMessage = "DRAFT".equals(existing.getStatus())
                ? "Đã lưu thay đổi bản nháp"
                : "Đã cập nhật bài viết";
        }
        result(resp, success, success ? successMessage : "Không thể cập nhật bài viết");
    }

    private void restorePost(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireAdmin(resp, user, () -> result(resp, postDAO.restore(longParam(req, "postId")), "Đã khôi phục bài viết"));
    }

    private void reportPost(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireRegularUser(resp, user, () -> {
            long postId = longParam(req, "postId");
            Post post = postDAO.findById(postId);
            if (post == null) {
                error(resp, HttpServletResponse.SC_NOT_FOUND, "Bài viết không tồn tại");
                return;
            }
            if (post.getAuthorId() == user.getUserId()) {
                error(resp, HttpServletResponse.SC_BAD_REQUEST, "Không thể báo cáo bài viết của chính mình");
                return;
            }
            if (reportDAO.hasReport(postId, null, user.getUserId())) {
                conflict(resp, "Bạn đã báo cáo bài viết này rồi");
                return;
            }
            boolean success = reportDAO.insertReport(postId, null, user.getUserId(), text(req, "reason", 255, true));
            if (success) applyReportStrike(post.getAuthorId());
            result(resp, success, "Đã gửi báo cáo");
        });
    }

    private void reportComment(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireRegularUser(resp, user, () -> {
            long commentId = longParam(req, "commentId");
            Comment comment = commentDAO.findById(commentId);
            if (comment == null) {
                error(resp, HttpServletResponse.SC_NOT_FOUND, "Bình luận không tồn tại");
                return;
            }
            if (comment.getUserId() == user.getUserId()) {
                error(resp, HttpServletResponse.SC_BAD_REQUEST, "Không thể báo cáo bình luận của chính mình");
                return;
            }
            if (reportDAO.hasReport(null, commentId, user.getUserId())) {
                conflict(resp, "Bạn đã báo cáo bình luận này rồi");
                return;
            }
            boolean success = reportDAO.insertReport(null, commentId, user.getUserId(), text(req, "reason", 255, true));
            if (success) applyReportStrike(comment.getUserId());
            result(resp, success, "Đã gửi báo cáo");
        });
    }

    private void applyReportStrike(long targetUserId) {
        User before = userDAO.findById(targetUserId);
        if (before == null || !"USER".equals(before.getRole())) return;
        User after = userDAO.addReportStrike(targetUserId);
        if (after != null && !"RESTRICTED".equals(before.getStatus()) && "RESTRICTED".equals(after.getStatus())) {
            System.out.println("[Blog SE] User #" + targetUserId + " tự động bị hạn chế sau " + after.getWarningCount() + " báo cáo.");
        }
    }

    private void manageComments(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        if (user == null) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, "Chưa đăng nhập");
            return;
        }
        String action = text(req, "action", 20, true);
        switch (action) {
            case "add" -> {
                if (!isRegularUser(user)) {
                    error(resp, HttpServletResponse.SC_FORBIDDEN, "Chỉ thành viên được bình luận");
                    return;
                }
                Comment comment = new Comment();
                comment.setContent(text(req, "content", 1000, true));
                comment.setUserId(user.getUserId());
                comment.setPostId(longParam(req, "postId"));
                String parent = optional(req, "parentId", 30);
                boolean success;
                if (parent != null && !parent.isBlank()) {
                    comment.setParentId(Long.valueOf(parent));
                    success = commentDAO.insertReply(comment);
                } else {
                    success = commentDAO.insert(comment);
                }
                if (success) {
                    notifyAboutNewComment(comment, user);
                }
                result(resp, success, "Đã bình luận");
            }
            case "toggle" -> requireModerator(resp, user, () -> {
                long id = longParam(req, "id");
                Comment comment = commentDAO.findById(id);
                if (comment == null) {
                    error(resp, HttpServletResponse.SC_NOT_FOUND, "Bình luận không tồn tại");
                    return;
                }
                String next = "VISIBLE".equals(comment.getStatus()) ? "HIDDEN" : "VISIBLE";
                result(resp, commentDAO.updateStatus(id, next), "Đã cập nhật bình luận");
            });
            case "delete" -> requireModerator(resp, user, () -> result(resp, commentDAO.delete(longParam(req, "id")), "Đã xóa bình luận"));
            default -> throw new IllegalArgumentException("Hành động bình luận không hợp lệ");
        }
    }

    private void likeComment(HttpServletRequest req, HttpServletResponse resp, User user, boolean like) throws IOException {
        requireRegularUser(resp, user, () -> {
            long id = longParam(req, "id");
            boolean alreadyLiked = commentDAO.hasLikedComment(user.getUserId(), id);
            boolean success = like ? commentDAO.likeComment(user.getUserId(), id) : commentDAO.unlikeComment(user.getUserId(), id);
            if (success && like && !alreadyLiked) {
                Comment comment = commentDAO.findById(id);
                if (comment != null && comment.getUserId() != user.getUserId()) {
                    notificationDAO.insert(comment.getUserId(), "COMMENT_LIKED", "Bình luận của bạn được thích",
                        displayName(user) + " đã thích bình luận của bạn.",
                        "post-detail.html?id=" + comment.getPostId() + "#comment-" + id);
                }
            }
            ok(resp, Map.of("success", success, "likeCount", commentDAO.countLikes(id),
                "message", like ? "Đã thích bình luận" : "Đã bỏ thích bình luận"));
        });
    }

    private void dislikeComment(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireRegularUser(resp, user, () -> {
            long id = longParam(req, "id");
            boolean success = commentDAO.dislikeComment(user.getUserId(), id);
            ok(resp, Map.of("success", success, "dislikeCount", commentDAO.countDislikes(id), "message", "Đã cập nhật"));
        });
    }

    private void likePost(HttpServletRequest req, HttpServletResponse resp, User user, boolean like) throws IOException {
        requireRegularUser(resp, user, () -> {
            long postId = longParam(req, "id");
            boolean alreadyLiked = interactionDAO.hasLiked(user.getUserId(), postId);
            boolean success = like ? interactionDAO.like(user.getUserId(), postId) : interactionDAO.unlike(user.getUserId(), postId);
            if (success && like && !alreadyLiked) {
                Post post = postDAO.findById(postId);
                if (post != null && post.getAuthorId() != user.getUserId()) {
                    notificationDAO.insert(post.getAuthorId(), "POST_LIKED", "Bài viết của bạn được thích",
                        displayName(user) + " đã thích bài “" + post.getTitle() + "”.",
                        "post-detail.html?id=" + postId);
                }
            }
            ok(resp, Map.of("success", success, "likeCount", interactionDAO.countLikes(postId),
                "message", like ? "Đã thích bài viết" : "Đã bỏ thích"));
        });
    }

    private void toggleBookmark(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireRegularUser(resp, user, () -> {
            long postId = longParam(req, "postId");
            boolean bookmarked = bookmarkDAO.isBookmarked(user.getUserId(), postId);
            boolean success = bookmarked
                ? bookmarkDAO.removeBookmark(user.getUserId(), postId)
                : bookmarkDAO.addBookmark(user.getUserId(), postId);
            ok(resp, Map.of("success", success, "bookmarked", !bookmarked,
                "message", bookmarked ? "Đã bỏ lưu bài viết" : "Đã lưu vào danh sách đọc"));
        });
    }

    private void addViewHistory(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireRegularUser(resp, user, () -> {
            viewHistoryDAO.addViewHistory(user.getUserId(), longParam(req, "postId"));
            result(resp, true, "Đã ghi nhận lịch sử xem");
        });
    }

    private void restoreComment(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireModerator(resp, user, () -> result(resp, commentDAO.restore(longParam(req, "commentId")), "Đã khôi phục bình luận"));
    }

    private void deleteOwnComment(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireRegularUser(resp, user, () -> {
            long id = longParam(req, "commentId");
            Comment comment = commentDAO.findById(id);
            if (comment == null || comment.getUserId() != user.getUserId()) {
                error(resp, HttpServletResponse.SC_FORBIDDEN, "Không có quyền xóa bình luận này");
                return;
            }
            result(resp, commentDAO.delete(id), "Đã xóa bình luận");
        });
    }

    private void updateReport(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireModerator(resp, user, () -> {
            String status = text(req, "status", 20, true).toUpperCase(Locale.ROOT);
            if (!REPORT_STATUSES.contains(status)) throw new IllegalArgumentException("Trạng thái báo cáo không hợp lệ");
            result(resp, reportDAO.updateStatus(longParam(req, "reportId"), status, user.getUserId()), "Đã cập nhật báo cáo");
        });
    }

    private void deleteReport(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireModerator(resp, user, () -> result(resp, reportDAO.deleteReport(longParam(req, "reportId")), "Đã xóa báo cáo"));
    }

    private void createSupport(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireRegularUser(resp, user, () -> {
            String message = text(req, "message", 1000, true);
            boolean created = reportDAO.insertSupportMessage(user.getUserId(), message);

            if (created) {
                notifyModeratorsAboutSupport(user, message);
            }

            result(resp, created, "Đã gửi yêu cầu hỗ trợ");
        });
    }

    private void resolveSupport(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireModerator(resp, user, () -> result(resp,
            reportDAO.markSupportResolved(longParam(req, "reportId"), user.getUserId()), "Đã xử lý yêu cầu hỗ trợ"));
    }

    private void notifyModeratorsAboutSupport(User sender, String supportMessage) {
        String senderName = displayName(sender);
        String preview = supportMessage == null ? "" : supportMessage.trim();
        if (preview.length() > 180) {
            preview = preview.substring(0, 177) + "...";
        }

        for (User moderator : userDAO.findActiveModeratorsAndAdmins()) {
            if (moderator.getUserId() == sender.getUserId()) continue;

            try {
                notificationDAO.insert(
                    moderator.getUserId(),
                    "SUPPORT_REQUEST",
                    "Có yêu cầu hỗ trợ mới",
                    senderName + " đã gửi yêu cầu hỗ trợ: " + preview,
                    "dashboard.html#support-messages"
                );
            } catch (RuntimeException ex) {
            }
        }
    }

    private void notifyModeratorsAboutPendingPost(Post post, User author, boolean resubmitted) {
        String title = resubmitted ? "Bài viết đã được gửi lại" : "Có bài viết mới cần duyệt";
        String message = displayName(author) + (resubmitted ? " đã chỉnh sửa và gửi lại bài “" : " vừa gửi bài “")
            + post.getTitle() + "” để duyệt.";
        for (User moderator : userDAO.findActiveModeratorsAndAdmins()) {
            if (moderator.getUserId() == author.getUserId()) continue;
            notificationDAO.insert(moderator.getUserId(), resubmitted ? "POST_RESUBMITTED" : "POST_PENDING",
                title, message, "dashboard.html#moderation");
        }
    }

    private void notifyAboutNewComment(Comment comment, User actor) {
        Post post = postDAO.findById(comment.getPostId());
        if (post == null) return;

        String actorName = displayName(actor);
        String link = "post-detail.html?id=" + post.getPostId();
        if (comment.getParentId() != null) link += "#comment-" + comment.getParentId();

        if (post.getAuthorId() != actor.getUserId()) {
            notificationDAO.insert(post.getAuthorId(), "POST_COMMENTED", "Bài viết có bình luận mới",
                actorName + " đã bình luận bài “" + post.getTitle() + "”.", link);
        }

        if (comment.getParentId() != null) {
            Comment parent = commentDAO.findById(comment.getParentId());
            if (parent != null && parent.getUserId() != actor.getUserId() && parent.getUserId() != post.getAuthorId()) {
                notificationDAO.insert(parent.getUserId(), "COMMENT_REPLIED", "Có người trả lời bình luận",
                    actorName + " đã trả lời bình luận của bạn trong bài “" + post.getTitle() + "”.", link);
            }
        }
    }

    private String displayName(User user) {
        if (user == null) return "Một người dùng";
        String fullName = user.getFullName();
        return fullName != null && !fullName.isBlank() ? fullName : user.getUsername();
    }

    private void manageNotifications(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireUser(resp, user, () -> {
            String action = text(req, "action", 30, true);
            boolean success = switch (action) {
                case "read" -> notificationDAO.markRead(longParam(req, "notificationId"), user.getUserId());
                case "readAll" -> notificationDAO.markAllRead(user.getUserId());
                default -> throw new IllegalArgumentException("Hành động thông báo không hợp lệ");
            };
            result(resp, success, "Đã cập nhật thông báo");
        });
    }

    private void saveSettings(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        requireUser(resp, user, () -> {
            UserSettings settings = new UserSettings();
            settings.setUserId(user.getUserId());
            settings.setTheme(colorOrText(req, "theme", "light", 20));
            settings.setPrimaryColor(hexColor(req, "primaryColor", "#667eea"));
            settings.setSecondaryColor(hexColor(req, "secondaryColor", "#764ba2"));
            settings.setBackgroundColor(hexColor(req, "backgroundColor", "#f4f6f9"));
            settings.setTextColor(hexColor(req, "textColor", "#1a1a2e"));
            settings.setFontFamily(allowedFont(optional(req, "fontFamily", 100)));
            settings.setCoverImage(optional(req, "coverImage", 255));
            settings.setCustomCss("");
            result(resp, settingsDAO.upsert(settings), "Đã cập nhật giao diện");
        });
    }

    private void uploadAvatar(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException, ServletException {
        if (user == null) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, "Chưa đăng nhập");
            return;
        }
        Part part = req.getPart("avatar");
        if (part == null || part.getSize() == 0) throw new IllegalArgumentException("Vui lòng chọn ảnh");
        if (part.getSize() > 5L * 1024 * 1024) throw new IllegalArgumentException("Ảnh không được vượt quá 5 MB");
        String contentType = part.getContentType() == null ? "" : part.getContentType().toLowerCase(Locale.ROOT);
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Chỉ chấp nhận ảnh JPG, PNG hoặc WebP");
        };
        byte[] imageBytes;
        try (var input = part.getInputStream()) {
            imageBytes = input.readAllBytes();
        }
        if (!validImageSignature(imageBytes, extension)) {
            throw new IllegalArgumentException("Nội dung tệp không đúng định dạng ảnh");
        }

        Path directory = UploadStorage.root(getServletContext()).resolve("avatars").normalize();
        Files.createDirectories(directory);
        String filename = "avatar-" + user.getUserId() + "-" + UUID.randomUUID() + extension;
        Path destination = directory.resolve(filename).normalize();
        if (!destination.startsWith(directory)) throw new IllegalArgumentException("Đường dẫn ảnh không hợp lệ");
        Files.write(destination, imageBytes);
        String avatarUrl = "/uploads/avatars/" + filename;
        boolean success = userDAO.updateAvatar(user.getUserId(), avatarUrl);
        if (success) user.setAvatar(avatarUrl);
        ok(resp, Map.of("success", success, "avatarUrl", avatarUrl, "message", "Đã cập nhật ảnh đại diện"));
    }

    private void uploadThumbnail(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException, ServletException {
        if (user == null) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, "Vui lòng đăng nhập để tải ảnh");
            return;
        }
        if (!isRegularUser(user) && !isAdmin(user)) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Moderator không có quyền tải thumbnail bài viết");
            return;
        }

        Part part = req.getPart("thumbnail");
        if (part == null || part.getSize() == 0) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh thumbnail");
        }
        if (part.getSize() > 5L * 1024 * 1024) {
            throw new IllegalArgumentException("Ảnh thumbnail không được vượt quá 5 MB");
        }

        String contentType = part.getContentType() == null
                ? ""
                : part.getContentType().toLowerCase(Locale.ROOT);
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Chỉ chấp nhận ảnh JPG, PNG hoặc WebP");
        };

        byte[] imageBytes;
        try (var input = part.getInputStream()) {
            imageBytes = input.readAllBytes();
        }
        if (!validImageSignature(imageBytes, extension)) {
            throw new IllegalArgumentException("Nội dung tệp không đúng định dạng ảnh");
        }

        Path directory = UploadStorage.root(getServletContext()).resolve("thumbnails").normalize();
        Files.createDirectories(directory);
        String filename = "thumbnail-" + user.getUserId() + "-" + UUID.randomUUID() + extension;
        Path destination = directory.resolve(filename).normalize();
        if (!destination.startsWith(directory)) {
            throw new IllegalArgumentException("Đường dẫn ảnh không hợp lệ");
        }
        Files.write(destination, imageBytes);

        String thumbnailUrl = "/uploads/thumbnails/" + filename;
        ok(resp, Map.of(
                "success", true,
                "thumbnailUrl", thumbnailUrl,
                "message", "Đã tải thumbnail lên thành công"
        ));
    }

    private void manageUsers(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        if (user == null) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, "Chưa đăng nhập");
            return;
        }
        String action = text(req, "action", 30, true);
        if ("updateProfile".equals(action)) {
            updateProfile(req, resp, user);
            return;
        }
        if (!isAdmin(user)) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Chỉ Admin được quản lý người dùng");
            return;
        }
        long id = longParam(req, "id");
        if (id == user.getUserId()) {
            error(resp, HttpServletResponse.SC_BAD_REQUEST, "Không thể tự thay đổi quyền hoặc trạng thái tài khoản của mình");
            return;
        }
        switch (action) {
            case "block", "status" -> {
                String status = text(req, "status", 20, true).toUpperCase(Locale.ROOT);
                if (!USER_STATUSES.contains(status)) throw new IllegalArgumentException("Trạng thái tài khoản không hợp lệ");
                User target = userDAO.findById(id);
                if (target == null) {
                    error(resp, HttpServletResponse.SC_NOT_FOUND, "Tài khoản không tồn tại");
                    return;
                }
                if ("RESTRICTED".equals(status) && !"USER".equals(target.getRole())) {
                    error(resp, HttpServletResponse.SC_BAD_REQUEST, "Chỉ tài khoản USER mới áp dụng trạng thái hạn chế");
                    return;
                }
                result(resp, userDAO.updateStatus(id, status), "Đã cập nhật trạng thái tài khoản");
            }
            case "changeRole" -> {
                String role = text(req, "role", 20, true).toUpperCase(Locale.ROOT);
                if (!USER_ROLES.contains(role)) throw new IllegalArgumentException("Vai trò không hợp lệ");
                result(resp, userDAO.updateRole(id, role), "Đã cập nhật vai trò");
            }
            default -> throw new IllegalArgumentException("Hành động người dùng không hợp lệ");
        }
    }

    private void updateProfile(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        String fullName = text(req, "fullName", 100, true);
        String email = email(req, "email");
        User emailOwner = userDAO.findByEmail(email);
        if (emailOwner != null && emailOwner.getUserId() != user.getUserId()) {
            conflict(resp, "Email đã được sử dụng");
            return;
        }

        String newPassword = optional(req, "newPassword", 72);
        if (newPassword != null && !newPassword.isBlank()) {
            String oldPassword = text(req, "oldPassword", 72, true);
            if (!userDAO.verifyPassword(user.getUserId(), oldPassword)) {
                error(resp, HttpServletResponse.SC_BAD_REQUEST, "Mật khẩu cũ không đúng");
                return;
            }
            PasswordUtil.validate(newPassword);
            userDAO.updatePassword(user.getUserId(), newPassword);
        }

        User updated = new User();
        updated.setUserId(user.getUserId());
        updated.setFullName(fullName);
        updated.setEmail(email);
        boolean success = userDAO.updateProfile(updated);
        if (success) {
            user.setFullName(fullName);
            user.setEmail(email);
        }
        result(resp, success, "Đã cập nhật thông tin cá nhân");
    }

    private Post postFromRequest(HttpServletRequest req) {
        Post post = new Post();
        post.setTitle(text(req, "title", 255, true));
        post.setSummary(optional(req, "summary", 500));
        post.setContent(text(req, "content", 100_000, true));
        post.setThumbnail(validThumbnail(optional(req, "thumbnail", 500)));
        post.setCategoryId(longParam(req, "categoryId"));
        post.setTags(parseTags(optional(req, "tagIds", 1000)));
        return post;
    }

    private List<Tag> parseTags(String raw) {
        List<Tag> tags = new ArrayList<>();
        if (raw == null || raw.isBlank()) return tags;
        Set<Long> unique = new java.util.LinkedHashSet<>();
        for (String value : raw.split(",")) {
            if (value.isBlank()) continue;
            long id = Long.parseLong(value.trim());
            if (id > 0) unique.add(id);
            if (unique.size() >= 10) break;
        }
        for (long id : unique) {
            if (tagDAO.findById(id) != null) {
                Tag tag = new Tag();
                tag.setTagId(id);
                tags.add(tag);
            }
        }
        return tags;
    }

    private boolean canViewPost(Post post, User user) {
        if ("APPROVED".equals(post.getStatus())) return true;
        if (user == null) return false;
        return post.getAuthorId() == user.getUserId() || isModerator(user);
    }

    private UserSettings defaultSettings(long userId) {
        UserSettings settings = new UserSettings();
        settings.setUserId(userId);
        settings.setTheme("light");
        settings.setPrimaryColor("#667eea");
        settings.setSecondaryColor("#764ba2");
        settings.setBackgroundColor("#f4f6f9");
        settings.setTextColor("#1a1a2e");
        settings.setFontFamily("system-ui");
        settings.setCustomCss("");
        return settings;
    }

    private String buildResetUrl(HttpServletRequest req, String token) {
        String configuredBase = System.getenv("BLOG_PUBLIC_URL");
        String base;
        if (configuredBase != null && !configuredBase.isBlank()) {
            base = configuredBase.trim().replaceFirst("/+$", "");
        } else {
            String scheme = req.isSecure() ? "https" : req.getScheme();
            String host = req.getServerName();
            int port = req.getServerPort();
            boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
            base = scheme + "://" + host + (defaultPort ? "" : ":" + port);
        }
        return base + req.getContextPath() + "/reset-password.html?token=" + token;
    }

    private boolean isLocalRequest(HttpServletRequest req) {
        String host = req.getServerName();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }


    private boolean validImageSignature(byte[] data, String extension) {
        if (data == null || data.length < 12) return false;
        return switch (extension) {
            case ".jpg" -> (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff;
            case ".png" -> (data[0] & 0xff) == 0x89 && data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47
                && data[4] == 0x0d && data[5] == 0x0a && data[6] == 0x1a && data[7] == 0x0a;
            case ".webp" -> data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
            default -> false;
        };
    }

    private String validThumbnail(String value) {
        if (value == null || value.isBlank()) return null;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://") || lower.startsWith("/uploads/")) return value;
        throw new IllegalArgumentException("Thumbnail phải là URL HTTPS hoặc ảnh đã tải lên");
    }

    private String allowedFont(String value) {
        if (value == null) return "system-ui";
        return switch (value) {
            case "Inter, sans-serif", "Roboto, sans-serif", "Georgia, serif", "'Courier New', monospace", "system-ui" -> value;
            default -> "system-ui";
        };
    }

    private String hexColor(HttpServletRequest req, String name, String fallback) {
        String value = optional(req, name, 7);
        return value != null && value.matches("^#[0-9a-fA-F]{6}$") ? value : fallback;
    }

    private String colorOrText(HttpServletRequest req, String name, String fallback, int max) {
        String value = optional(req, name, max);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String email(HttpServletRequest req, String name) {
        String value = text(req, name, 100, true).toLowerCase(Locale.ROOT);
        if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Email không hợp lệ");
        return value;
    }

    private String text(HttpServletRequest req, String name, int maxLength, boolean required) {
        String value = req.getParameter(name);
        if (value != null) value = value.trim();
        if (required && (value == null || value.isBlank())) throw new IllegalArgumentException("Thiếu trường " + name);
        if (value != null && value.length() > maxLength) throw new IllegalArgumentException("Trường " + name + " vượt quá " + maxLength + " ký tự");
        return value;
    }

    private String optional(HttpServletRequest req, String name, int maxLength) {
        return text(req, name, maxLength, false);
    }

    private long longParam(HttpServletRequest req, String name) {
        String value = text(req, name, 30, true);
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Tham số " + name + " không hợp lệ");
        }
    }

    private int intParam(HttpServletRequest req, String name, int fallback, int min, int max) {
        String value = optional(req, name, 20);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private User currentUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return null;
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return null;

        User freshUser = userDAO.findById(sessionUser.getUserId());
        if (freshUser == null || !"ACTIVE".equals(freshUser.getStatus())) {
            req.setAttribute("forcedLogoutStatus", freshUser == null ? "UNKNOWN" : freshUser.getStatus());
            session.invalidate();
            return null;
        }

        session.setAttribute("user", freshUser);
        return freshUser;
    }

    private String forcedLogoutMessage(Object statusValue) {
        String status = String.valueOf(statusValue);
        return "BLOCKED".equals(status)
            ? "Tài khoản của bạn đã bị khóa. Bạn đã được đăng xuất."
            : "RESTRICTED".equals(status)
                ? "Tài khoản của bạn đang bị hạn chế do đã bị báo cáo đủ 3 lần. Bạn đã được đăng xuất."
                : "Phiên đăng nhập không còn hợp lệ.";
    }

    private boolean isRegularUser(User user) {
        return user != null && "USER".equals(user.getRole());
    }

    private boolean isModerator(User user) {
        return user != null && MOD_ROLES.contains(user.getRole());
    }

    private boolean isAdmin(User user) {
        return user != null && "ADMIN".equals(user.getRole());
    }

    private String normalizePath(String value) {
        return value == null || value.isBlank() ? "/" : value;
    }

    private void prepare(HttpServletResponse resp) {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");
    }

    private void requireUser(HttpServletResponse resp, User user, IoAction action) throws IOException {
        if (user == null) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, "Chưa đăng nhập");
            return;
        }
        action.run();
    }

    private void requireRegularUser(HttpServletResponse resp, User user, IoAction action) throws IOException {
        if (user == null) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, "Chưa đăng nhập");
            return;
        }
        if (!isRegularUser(user)) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Chức năng này chỉ dành cho thành viên");
            return;
        }
        action.run();
    }

    private void requireAdmin(HttpServletResponse resp, User user, IoAction action) throws IOException {
        if (user == null) {
            error(resp, HttpServletResponse.SC_UNAUTHORIZED, "Chưa đăng nhập");
            return;
        }
        if (!isAdmin(user)) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Chỉ Admin được thực hiện chức năng này");
            return;
        }
        action.run();
    }

    private void requireModerator(HttpServletResponse resp, User user, IoAction action) throws IOException {
        if (!isModerator(user)) {
            error(resp, HttpServletResponse.SC_FORBIDDEN, "Không có quyền");
            return;
        }
        action.run();
    }

    private void ok(HttpServletResponse resp, Object payload) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(payload));
    }

    private void result(HttpServletResponse resp, boolean success, String message) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("success", success);
        payload.put("message", success ? message : "Thao tác thất bại");
        ok(resp, payload);
    }

    private void conflict(HttpServletResponse resp, String message) throws IOException {
        error(resp, HttpServletResponse.SC_CONFLICT, message);
    }

    private void error(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(gson.toJson(Map.of("success", false, "error", message, "message", message)));
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }
}