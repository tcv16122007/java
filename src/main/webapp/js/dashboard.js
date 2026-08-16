(() => {
	const state = {
		user: null,
		section: "overview",
		categories: [],
		tags: [],
		postEditorModal: null,
		rejectModal: null,
		entityModal: null,
		previewModal: null,
	};

	const content = document.getElementById("dashboardContent");
	const sidebar = document.getElementById("dashboardSidebar");
	const nav = document.getElementById("dashboardNav");

	document.addEventListener("DOMContentLoaded", init);

	async function init() {
		bindStaticEvents();
		state.user = await App.getCurrentUser();
		if (!state.user) {
			App.flash("Vui lòng đăng nhập để mở Dashboard.", "warning");
			App.redirectToLogin(`dashboard.html${window.location.hash || ""}`, true);
			return;
		}
		await App.applySettings();
		configureRoleMenu();
		await Promise.allSettled([ensureCategories(), ensureTags()]);
		const requested = normalizeSection(window.location.hash.slice(1));
		await loadSection(requested);
	}

	function bindStaticEvents() {
		document
			.getElementById("sidebarToggle")
			.addEventListener("click", () => sidebar.classList.toggle("is-open"));
		nav.addEventListener("click", (event) => {
			const link = event.target.closest("[data-section]");
			if (!link) return;
			event.preventDefault();
			window.location.hash = link.dataset.section;
			sidebar.classList.remove("is-open");
		});
		window.addEventListener("hashchange", () =>
			loadSection(normalizeSection(window.location.hash.slice(1))),
		);
		content.addEventListener("click", handleContentClick);
		content.addEventListener("change", handleContentChange);
		content.addEventListener("input", handleContentInput);
		document
			.getElementById("postEditorForm")
			.addEventListener("submit", submitPostEditor);
		document
			.getElementById("rejectForm")
			.addEventListener("submit", submitReject);
		document
			.getElementById("entityForm")
			.addEventListener("submit", submitEntity);
		document
			.getElementById("editorPreviewButton")
			.addEventListener("click", () => previewEditor("editor"));
		document
			.getElementById("editorThumbnailFile")
			.addEventListener("change", (event) =>
				handleThumbnailSelection("editor", event.target.files?.[0]),
			);
		document
			.getElementById("editorTitle")
			.addEventListener("input", () =>
				updateCounter("editorTitle", "editorTitleCount"),
			);
		document
			.getElementById("editorSummary")
			.addEventListener("input", () =>
				updateCounter("editorSummary", "editorSummaryCount"),
			);
		document
			.getElementById("rejectReason")
			.addEventListener("input", () =>
				updateCounter("rejectReason", "rejectReasonCount"),
			);
		document.addEventListener("click", handleEditorToolbar);
	}

	function configureRoleMenu() {
		document.querySelectorAll("[data-role]").forEach((item) => {
			const rule = item.dataset.role;
			const visible =
				rule === "USER"
					? state.user.role === "USER"
					: rule === "MOD"
						? ["MODERATOR", "ADMIN"].includes(state.user.role)
						: rule === "ADMIN" && state.user.role === "ADMIN";
			item.classList.toggle("d-none", !visible);
		});
	}

	function normalizeSection(section) {
		const requested = section || "overview";
		const allowed = new Set(["overview", "notifications"]);

		if (state.user?.role === "USER") {
			[
				"posts",
				"write",
				"bookmarks",
				"history-posts",
				"history-comments",
				"support",
			].forEach((value) => allowed.add(value));
		}

		if (state.user?.role === "MODERATOR") {
			["moderation", "comments", "reports", "support-messages"].forEach(
				(value) => allowed.add(value),
			);
		}

		if (state.user?.role === "ADMIN") {
			[
				"moderation",
				"comments",
				"reports",
				"support-messages",
				"users",
				"all-posts",
				"categories",
				"tags",
			].forEach((value) => allowed.add(value));
		}

		return allowed.has(requested) ? requested : "overview";
	}

	async function loadSection(section) {
		state.section = normalizeSection(section);
		if (window.location.hash !== `#${state.section}`)
			history.replaceState(null, "", `#${state.section}`);
		nav
			.querySelectorAll("[data-section]")
			.forEach((link) =>
				link.classList.toggle("active", link.dataset.section === state.section),
			);
		content.innerHTML = App.loadingState("Đang tải Dashboard...");
		content.focus({ preventScroll: true });
		window.scrollTo({ top: 0, behavior: "smooth" });

		try {
			switch (state.section) {
				case "overview":
					await renderOverview();
					break;
				case "posts":
					await renderMyPosts();
					break;
				case "write":
					await renderWrite();
					break;
				case "categories":
					await renderCategories();
					break;
				case "tags":
					await renderTags();
					break;
				case "bookmarks":
					await renderBookmarks();
					break;
				case "history-posts":
					await renderPostHistory();
					break;
				case "history-comments":
					await renderCommentHistory();
					break;
				case "moderation":
					await renderModeration();
					break;
				case "comments":
					await renderAllComments();
					break;
				case "reports":
					await renderReports();
					break;
				case "users":
					await renderUsers();
					break;
				case "all-posts":
					await renderAllPosts();
					break;
				case "support":
					renderSupport();
					break;
				case "support-messages":
					await renderSupportMessages();
					break;
				case "notifications":
					await renderNotifications();
					break;
				default:
					await renderOverview();
			}
		} catch (error) {
			content.innerHTML = App.errorState(error.message, "retry-section");
		}
	}

	async function renderOverview() {
		const isUser = state.user.role === "USER";
		const isAdmin = state.user.role === "ADMIN";
		if (isUser) {
			const [posts, bookmarks, history] = await Promise.all([
				App.api("GET", "/posts?action=my"),
				App.api("GET", "/bookmarks?action=list"),
				App.api("GET", "/history/posts"),
			]);
			const list = Array.isArray(posts) ? posts : [];
			const cards = [
				["Tổng bài viết", list.length, "bi-file-earmark-text"],
				[
					"Đã duyệt",
					list.filter((p) => p.status === "APPROVED").length,
					"bi-check-circle",
				],
				[
					"Chờ duyệt",
					list.filter((p) => p.status === "PENDING").length,
					"bi-hourglass-split",
				],
				[
					"Bị từ chối",
					list.filter((p) => p.status === "REJECTED").length,
					"bi-exclamation-circle",
				],
				[
					"Bản nháp",
					list.filter((p) => p.status === "DRAFT").length,
					"bi-file-earmark",
				],
				[
					"Tổng lượt xem",
					list.reduce((sum, p) => sum + (Number(p.viewCount) || 0), 0),
					"bi-eye",
				],
				[
					"Danh sách đọc",
					Array.isArray(bookmarks) ? bookmarks.length : 0,
					"bi-bookmark",
				],
				[
					"Đã xem",
					Array.isArray(history) ? history.length : 0,
					"bi-clock-history",
				],
			];
			content.innerHTML = overviewMarkup(
				"Tổng quan cá nhân",
				"Theo dõi hoạt động viết và đọc của bạn.",
				cards,
				[
					["Viết bài mới", "write", "bi-pencil-square"],
					["Bài viết của tôi", "posts", "bi-journal-text"],
					["Danh sách đọc", "bookmarks", "bi-bookmark"],
				],
			);
			return;
		}

		const requests = [
			App.api("GET", "/posts?action=pending"),
			App.api("GET", "/comments?action=all"),
			App.api("GET", "/reports"),
			App.api("GET", "/support/messages"),
		];
		if (isAdmin) requests.push(App.api("GET", "/users?action=list"));
		const [pending, comments, reports, supports, users = []] =
			await Promise.all(requests);
		const cards = [
			["Bài chờ duyệt", pending.length, "bi-hourglass-split"],
			["Tổng bình luận", comments.length, "bi-chat-square-text"],
			[
				"Bình luận ẩn",
				comments.filter((c) => c.status === "HIDDEN").length,
				"bi-eye-slash",
			],
			[
				"Báo cáo chờ xử lý",
				reports.filter((r) => r.status === "PENDING").length,
				"bi-flag",
			],
			[
				"Yêu cầu hỗ trợ",
				supports.filter((s) => s.status !== "RESOLVED").length,
				"bi-headset",
			],
		];
		if (isAdmin) {
			cards.push(["Tổng người dùng", users.length, "bi-people"]);
			cards.push([
				"Tài khoản bị khóa",
				users.filter((u) => u.status === "BLOCKED").length,
				"bi-person-lock",
			]);
			cards.push([
				"Tài khoản hạn chế",
				users.filter((u) => u.status === "RESTRICTED").length,
				"bi-person-exclamation",
			]);
		}
		const quickActions = isAdmin
			? [
					["Người dùng", "users", "bi-people"],
					["Quản lý bài viết", "all-posts", "bi-journals"],
					["Danh mục", "categories", "bi-folder"],
					["Duyệt bài", "moderation", "bi-check2-circle"],
				]
			: [
					["Duyệt bài", "moderation", "bi-check2-circle"],
					["Báo cáo", "reports", "bi-flag"],
					["Bình luận", "comments", "bi-chat-square-text"],
					["Hỗ trợ", "support-messages", "bi-envelope"],
				];

		content.innerHTML = overviewMarkup(
			isAdmin ? "Tổng quan quản trị" : "Tổng quan kiểm duyệt",
			isAdmin
				? "Admin quản lý toàn bộ nội dung và tài khoản trong hệ thống."
				: "Moderator tập trung vào kiểm duyệt và xử lý nội dung.",
			cards,
			quickActions,
		);
	}

	function overviewMarkup(title, subtitle, cards, actions) {
		return `
            <header class="dashboard-header"><div><h1 class="h3 mb-1">${App.escapeHtml(title)}</h1><p class="text-muted mb-0">${App.escapeHtml(subtitle)}</p></div></header>
            <div class="dashboard-grid mb-4">${cards
							.map(
								([label, value, icon]) => `
                <article class="app-card stat-card"><div class="d-flex justify-content-between align-items-start gap-3"><div><div class="text-muted small">${App.escapeHtml(label)}</div><div class="display-6 fw-bold mt-1">${Number(value) || 0}</div></div><div class="stat-icon"><i class="bi ${icon}"></i></div></div></article>`,
							)
							.join("")}</div>
            <div class="app-card p-4"><h2 class="h5 mb-3">Truy cập nhanh</h2><div class="quick-actions">${actions.map(([label, section, icon]) => `<button class="btn btn-outline-primary text-start p-3" type="button" data-action="open-section" data-section="${section}"><i class="bi ${icon} me-2"></i>${App.escapeHtml(label)}</button>`).join("")}</div></div>`;
	}

	async function renderMyPosts() {
		if (state.user.role !== "USER") return loadSection("overview");
		const posts = await App.api("GET", "/posts?action=my");
		const rows = Array.isArray(posts) ? posts : [];
		content.innerHTML =
			sectionHeader(
				"Bài viết của tôi",
				"Quản lý bản nháp, bài chờ duyệt và bài bị từ chối",
				state.user.role === "USER"
					? '<button class="btn btn-primary" type="button" data-action="open-section" data-section="write"><i class="bi bi-plus-lg"></i> Viết bài</button>'
					: "",
			) +
			(rows.length
				? `<div class="app-card table-responsive"><table class="table dashboard-table align-middle mb-0"><thead><tr><th>Tiêu đề</th><th>Trạng thái</th><th>Lượt xem</th><th>Ngày tạo</th><th>Hành động</th></tr></thead><tbody>${rows
						.map(
							(post) => `
                <tr><td><div class="fw-semibold">${App.escapeHtml(post.title)}</div>${post.rejectionReason ? `<div class="rejection-box small mt-2"><strong>Lý do:</strong> ${App.escapeHtml(post.rejectionReason)}</div>` : ""}</td><td>${App.statusBadge(post.status)}${Number(post.rejectCount) > 0 ? `<div class="small text-muted mt-1">Từ chối: ${Number(post.rejectCount)}/3</div>` : ""}</td><td>${Number(post.viewCount) || 0}</td><td>${App.formatDate(post.createdAt)}</td><td><div class="d-flex flex-wrap gap-2">
                    <a class="btn btn-sm btn-outline-info" href="post-detail.html?id=${Number(post.postId)}"><i class="bi bi-eye"></i></a>
                    ${["DRAFT", "PENDING"].includes(post.status) || (post.status === "REJECTED" && Number(post.rejectCount) < 3) ? `<button class="btn btn-sm btn-outline-primary" type="button" data-action="edit-post" data-id="${Number(post.postId)}" data-resubmit="${post.status === "REJECTED"}"><i class="bi bi-pencil"></i> ${post.status === "REJECTED" ? "Sửa & gửi lại" : "Sửa"}</button>` : ""}
                    ${post.status === "REJECTED" && Number(post.rejectCount) >= 3 ? '<span class="small text-danger align-self-center">Đã đủ 3 lần từ chối</span>' : ""}
                    <button class="btn btn-sm btn-outline-danger" type="button" data-action="delete-post" data-id="${Number(post.postId)}"><i class="bi bi-trash"></i></button>
                </div></td></tr>`,
						)
						.join("")}</tbody></table></div>`
				: App.emptyState(
						"Chưa có bài viết",
						"Bắt đầu bằng bài viết đầu tiên của bạn.",
						"bi-journal-plus",
					));
	}

	async function renderWrite() {
		if (state.user.role !== "USER") return loadSection("overview");
		await Promise.all([ensureCategories(), ensureTags()]);
		content.innerHTML =
			sectionHeader(
				"Viết bài mới",
				"Soạn bằng Markdown, chọn thumbnail và thẻ trước khi gửi duyệt",
			) + editorFormMarkup("write");
		fillEditorOptions("write");
		restoreDraft();
		bindWriteForm();
	}

	function editorFormMarkup(prefix) {
		return `<form class="app-card p-4" id="${prefix}PostForm">
            <div class="row g-4"><div class="col-lg-8">
                <div class="mb-3"><label class="form-label" for="${prefix}Title">Tiêu đề</label><input class="form-control" id="${prefix}Title" maxlength="255" required><div class="char-count"><span id="${prefix}TitleCount">0</span>/255</div></div>
                <div class="mb-3"><label class="form-label" for="${prefix}Summary">Tóm tắt</label><textarea class="form-control" id="${prefix}Summary" rows="3" maxlength="500"></textarea><div class="char-count"><span id="${prefix}SummaryCount">0</span>/500</div></div>
                <label class="form-label" for="${prefix}Content">Nội dung Markdown</label>
                <div class="editor-toolbar" data-editor-target="${prefix}Content"><button class="btn btn-sm btn-outline-secondary" type="button" data-editor-command="heading"><i class="bi bi-type-h2"></i></button><button class="btn btn-sm btn-outline-secondary" type="button" data-editor-command="bold"><i class="bi bi-type-bold"></i></button><button class="btn btn-sm btn-outline-secondary" type="button" data-editor-command="italic" title="In nghiêng"><i class="bi bi-type-italic"></i></button><button class="btn btn-sm btn-outline-secondary" type="button" data-editor-command="underline" title="Gạch chân"><i class="bi bi-type-underline"></i></button><button class="btn btn-sm btn-outline-secondary" type="button" data-editor-command="strike" title="Gạch ngang"><i class="bi bi-type-strikethrough"></i></button><button class="btn btn-sm btn-outline-secondary" type="button" data-editor-command="code"><i class="bi bi-code-slash"></i></button><button class="btn btn-sm btn-outline-secondary" type="button" data-editor-command="list"><i class="bi bi-list-ul"></i></button><button class="btn btn-sm btn-outline-secondary" type="button" data-editor-command="link"><i class="bi bi-link-45deg"></i></button></div>
                <textarea class="form-control editor-textarea" id="${prefix}Content" maxlength="100000" required></textarea>
                <div class="form-text">Dùng ## cho tiêu đề, **đậm**, *nghiêng*, ++gạch chân++, ~~gạch ngang~~. Đặt URL ảnh/video/YouTube ở một dòng riêng để nhúng media.</div>
            </div><div class="col-lg-4">
                <div class="app-card p-3 mb-3"><label class="form-label" for="${prefix}Category">Danh mục</label><select class="form-select" id="${prefix}Category" required></select></div>
                <div class="app-card p-3 mb-3">
                    <label class="form-label" for="${prefix}ThumbnailFile">Ảnh thumbnail</label>
                    <input type="hidden" id="${prefix}Thumbnail">
                    <input class="form-control" id="${prefix}ThumbnailFile" type="file" accept="image/jpeg,image/png,image/webp">
                    <div class="form-text">JPG, PNG hoặc WebP, tối đa 5 MB.</div>
                    <div class="thumbnail-upload-status small mt-2" id="${prefix}ThumbnailStatus" aria-live="polite"></div>
                    <div class="mt-3" id="${prefix}ThumbnailPreview"></div>
                    <button class="btn btn-sm btn-outline-danger mt-2 d-none" id="${prefix}ThumbnailRemove" type="button" data-action="remove-thumbnail" data-prefix="${prefix}"><i class="bi bi-trash"></i> Xóa ảnh</button>
                </div>
                <div class="app-card p-3"><div class="form-label">Thẻ bài viết</div><div class="tag-picker" id="${prefix}TagPicker"></div></div>
            </div></div>
            <div class="d-flex flex-wrap justify-content-end gap-2 mt-4"><button class="btn btn-outline-secondary" type="button" data-action="clear-write"><i class="bi bi-arrow-counterclockwise"></i> Làm mới</button><button class="btn btn-outline-primary" type="button" data-action="preview-write"><i class="bi bi-eye"></i> Xem trước</button><button class="btn btn-secondary" type="button" data-action="save-draft"><i class="bi bi-save"></i> Lưu nháp</button><button class="btn btn-primary" type="submit"><i class="bi bi-send"></i> Gửi duyệt</button></div>
        </form>`;
	}

	function bindWriteForm() {
		const form = document.getElementById("writePostForm");
		form.addEventListener("submit", (event) => submitNewPost(event, false));
		["writeTitle", "writeSummary", "writeContent"].forEach((id) =>
			document
				.getElementById(id)
				.addEventListener("input", App.debounce(saveLocalDraft, 400)),
		);
		document
			.getElementById("writeTitle")
			.addEventListener("input", () =>
				updateCounter("writeTitle", "writeTitleCount"),
			);
		document
			.getElementById("writeSummary")
			.addEventListener("input", () =>
				updateCounter("writeSummary", "writeSummaryCount"),
			);
		document
			.getElementById("writeCategory")
			.addEventListener("change", saveLocalDraft);
		document
			.getElementById("writeTagPicker")
			.addEventListener("change", saveLocalDraft);
	}

	async function renderAllPosts() {
		if (state.user.role !== "ADMIN") return loadSection("overview");
		const posts = await App.api("GET", "/posts?action=all");
		const rows = Array.isArray(posts) ? posts : [];

		content.innerHTML =
			sectionHeader(
				"Quản lý bài viết",
				"Admin xem và quản lý toàn bộ bài viết trong hệ thống.",
			) +
			(rows.length
				? `<div class="app-card table-responsive"><table class="table dashboard-table align-middle mb-0">
                <thead><tr><th>Tiêu đề</th><th>Tác giả</th><th>Danh mục</th><th>Trạng thái</th><th>Ngày tạo</th><th>Hành động</th></tr></thead>
                <tbody>${rows
									.map(
										(post) => `<tr>
                    <td><div class="fw-semibold">${App.escapeHtml(post.title)}</div></td>
                    <td>${App.escapeHtml(post.authorName || "")}</td>
                    <td>${App.escapeHtml(post.categoryName || "Chung")}</td>
                    <td>${App.statusBadge(post.status)}</td>
                    <td>${App.formatDate(post.createdAt)}</td>
                    <td><div class="d-flex flex-wrap gap-2">
                        <a class="btn btn-sm btn-outline-info" href="post-detail.html?id=${Number(post.postId)}"><i class="bi bi-eye"></i> Xem</a>
                        ${
													post.status === "DELETED"
														? `<button class="btn btn-sm btn-outline-success" type="button" data-action="restore-post-admin" data-id="${Number(post.postId)}"><i class="bi bi-arrow-counterclockwise"></i> Khôi phục</button>`
														: `<button class="btn btn-sm btn-outline-danger" type="button" data-action="delete-post" data-id="${Number(post.postId)}"><i class="bi bi-trash"></i> Xóa</button>`
												}
                    </div></td>
                </tr>`,
									)
									.join("")}</tbody>
            </table></div>`
				: App.emptyState(
						"Chưa có bài viết",
						"Hệ thống hiện chưa có bài viết nào.",
						"bi-journals",
					));
	}

	async function renderCategories() {
		if (state.user.role !== "ADMIN") return loadSection("overview");
		const categories = await ensureCategories(true);
		const canEdit = state.user.role === "ADMIN";
		content.innerHTML =
			sectionHeader(
				"Danh mục",
				"Khám phá và tổ chức nội dung theo chủ đề",
				canEdit
					? '<button class="btn btn-primary" type="button" data-action="add-entity" data-type="category"><i class="bi bi-plus-lg"></i> Thêm danh mục</button>'
					: "",
			) +
			(categories.length
				? `<div class="row g-3">${categories.map((category) => `<div class="col-md-6 col-xl-4"><article class="app-card p-4 h-100"><h3 class="h5">${App.escapeHtml(category.categoryName)}</h3><p class="text-muted">${App.escapeHtml(category.description || "Chưa có mô tả.")}</p>${canEdit ? `<div class="d-flex gap-2"><button class="btn btn-sm btn-outline-primary" type="button" data-action="edit-entity" data-type="category" data-id="${Number(category.categoryId)}"><i class="bi bi-pencil"></i> Sửa</button><button class="btn btn-sm btn-outline-danger" type="button" data-action="delete-entity" data-type="category" data-id="${Number(category.categoryId)}"><i class="bi bi-trash"></i></button></div>` : ""}</article></div>`).join("")}</div>`
				: App.emptyState("Chưa có danh mục", "Admin có thể tạo danh mục mới."));
	}

	async function renderTags() {
		if (state.user.role !== "ADMIN") return loadSection("overview");
		const tags = await ensureTags(true);
		content.innerHTML =
			sectionHeader(
				"Quản lý thẻ",
				"Thẻ giúp bài viết dễ tìm và liên kết với nhau",
				'<button class="btn btn-primary" type="button" data-action="add-entity" data-type="tag"><i class="bi bi-plus-lg"></i> Thêm thẻ</button>',
			) +
			(tags.length
				? `<div class="app-card table-responsive"><table class="table dashboard-table align-middle mb-0"><thead><tr><th>Tên thẻ</th><th>Mô tả</th><th>Hành động</th></tr></thead><tbody>${tags.map((tag) => `<tr><td><span class="badge text-bg-light text-primary">#${App.escapeHtml(tag.tagName)}</span></td><td>${App.escapeHtml(tag.description || "")}</td><td><div class="d-flex gap-2"><button class="btn btn-sm btn-outline-primary" type="button" data-action="edit-entity" data-type="tag" data-id="${Number(tag.tagId)}"><i class="bi bi-pencil"></i></button><button class="btn btn-sm btn-outline-danger" type="button" data-action="delete-entity" data-type="tag" data-id="${Number(tag.tagId)}"><i class="bi bi-trash"></i></button></div></td></tr>`).join("")}</tbody></table></div>`
				: App.emptyState(
						"Chưa có thẻ",
						"Tạo thẻ đầu tiên để phân loại bài viết.",
					));
	}

	async function renderBookmarks() {
		if (state.user.role !== "USER") return loadSection("overview");
		const posts = await App.api("GET", "/bookmarks?action=list");
		content.innerHTML =
			sectionHeader("Danh sách đọc", "Những bài viết bạn đã lưu để đọc sau") +
			(posts.length
				? `<div class="row g-4">${posts.map((post) => `<div class="col-md-6 col-xl-4">${compactPostCard(post, `<button class="btn btn-sm btn-outline-danger" type="button" data-action="remove-bookmark" data-id="${Number(post.postId)}"><i class="bi bi-bookmark-x"></i> Bỏ lưu</button>`)}</div>`).join("")}</div>`
				: App.emptyState(
						"Danh sách đọc trống",
						"Bấm Bookmark tại trang chi tiết để lưu bài.",
						"bi-bookmark",
					));
	}

	async function renderPostHistory() {
		if (state.user.role !== "USER") return loadSection("overview");
		const history = await App.api("GET", "/history/posts");
		content.innerHTML =
			sectionHeader(
				"Lịch sử đọc",
				"Các bài viết bạn đã mở gần đây",
				'<a class="btn btn-outline-primary" href="history.html">Mở trang lịch sử</a>',
			) +
			(history.length
				? `<div class="app-card table-responsive"><table class="table dashboard-table align-middle mb-0"><thead><tr><th>Bài viết</th><th>Tác giả</th><th>Đã xem lúc</th></tr></thead><tbody>${history.map((item) => `<tr><td><a class="fw-semibold text-decoration-none" href="post-detail.html?id=${Number(item.postId)}">${App.escapeHtml(item.title)}</a></td><td>${App.escapeHtml(item.authorName || "")}</td><td>${App.formatDateTime(item.viewedAt)}</td></tr>`).join("")}</tbody></table></div>`
				: App.emptyState(
						"Chưa có lịch sử đọc",
						"Mở một bài viết để bắt đầu ghi nhận lịch sử.",
						"bi-clock-history",
					));
	}

	async function renderCommentHistory() {
		if (state.user.role !== "USER") return loadSection("overview");
		const comments = await App.api("GET", "/history/comments");
		content.innerHTML =
			sectionHeader(
				"Bình luận của tôi",
				"Theo dõi những nội dung bạn đã trao đổi",
			) +
			(comments.length
				? `<div class="d-grid gap-3">${comments.map((comment) => `<article class="app-card p-4"><div class="d-flex justify-content-between gap-3"><div><p class="mb-2">${App.escapeHtml(comment.content)}</p><a class="small text-decoration-none" href="post-detail.html?id=${Number(comment.postId)}">Mở bài viết</a></div><div>${App.statusBadge(comment.status)}</div></div><div class="small text-muted mt-2">${App.formatDateTime(comment.createdAt)}</div>${comment.status !== "DELETED" ? `<button class="btn btn-sm btn-outline-danger mt-3" type="button" data-action="delete-own-comment" data-id="${Number(comment.commentId)}"><i class="bi bi-trash"></i> Xóa</button>` : ""}</article>`).join("")}</div>`
				: App.emptyState(
						"Chưa có bình luận",
						"Bình luận của bạn sẽ xuất hiện tại đây.",
						"bi-chat-dots",
					));
	}

	async function renderModeration() {
		const posts = await App.api("GET", "/posts?action=pending");
		content.innerHTML =
			sectionHeader("Duyệt bài", "Kiểm tra nội dung trước khi xuất bản") +
			(posts.length
				? `<div class="d-grid gap-3">${posts
						.map((post) => {
							const rejectCount = Number(post.rejectCount) || 0;
							const canReject = rejectCount < 3;
							return `<article class="app-card p-4"><div class="d-flex flex-column flex-lg-row justify-content-between gap-3"><div><div class="d-flex flex-wrap gap-2 mb-2"><span class="badge text-bg-warning">Chờ duyệt</span><span class="badge ${canReject ? "text-bg-light" : "text-bg-danger"}">Từ chối ${rejectCount}/3</span></div><h3 class="h5"><a class="text-decoration-none" href="post-detail.html?id=${Number(post.postId)}" target="_blank" rel="noopener">${App.escapeHtml(post.title)}</a></h3><p class="text-muted mb-1">${App.escapeHtml(post.summary || "")}</p><small class="text-muted">${App.escapeHtml(post.authorName || "")} · ${App.formatDate(post.createdAt)}</small>${!canReject ? '<div class="small text-danger mt-2">Bài đã đủ 3 lần từ chối. Chỉ có thể duyệt hoặc xử lý theo quyền quản trị.</div>' : ""}</div><div class="d-flex flex-wrap align-items-start gap-2"><a class="btn btn-outline-info" href="post-detail.html?id=${Number(post.postId)}" target="_blank" rel="noopener"><i class="bi bi-eye"></i> Xem</a><button class="btn btn-success" type="button" data-action="approve-post" data-id="${Number(post.postId)}"><i class="bi bi-check-lg"></i> Duyệt</button>${canReject ? `<button class="btn btn-danger" type="button" data-action="reject-post" data-id="${Number(post.postId)}"><i class="bi bi-x-lg"></i> Từ chối</button>` : ""}</div></div></article>`;
						})
						.join("")}</div>`
				: App.emptyState(
						"Không có bài chờ duyệt",
						"Danh sách kiểm duyệt hiện đã trống.",
						"bi-check2-all",
					));
	}

	async function renderAllComments() {
		const comments = await App.api("GET", "/comments?action=all");
		content.innerHTML =
			sectionHeader(
				"Quản lý bình luận",
				"Ẩn, khôi phục hoặc xóa nội dung vi phạm",
			) +
			(comments.length
				? `<div class="app-card table-responsive"><table class="table dashboard-table align-middle mb-0"><thead><tr><th>Nội dung</th><th>Bài viết</th><th>Người dùng</th><th>Trạng thái</th><th>Hành động</th></tr></thead><tbody>${comments.map((comment) => `<tr><td>${App.escapeHtml(comment.content)}</td><td>${App.escapeHtml(comment.postTitle || `#${comment.postId}`)}</td><td>${App.escapeHtml(comment.username || "")}</td><td>${App.statusBadge(comment.status)}</td><td><div class="d-flex flex-wrap gap-2">${comment.status === "VISIBLE" ? `<button class="btn btn-sm btn-outline-warning" type="button" data-action="toggle-comment" data-id="${Number(comment.commentId)}"><i class="bi bi-eye-slash"></i> Ẩn</button>` : comment.status === "HIDDEN" ? `<button class="btn btn-sm btn-outline-success" type="button" data-action="toggle-comment" data-id="${Number(comment.commentId)}"><i class="bi bi-eye"></i> Hiện</button>` : `<button class="btn btn-sm btn-outline-success" type="button" data-action="restore-comment" data-id="${Number(comment.commentId)}"><i class="bi bi-arrow-counterclockwise"></i> Khôi phục</button>`}<button class="btn btn-sm btn-outline-danger" type="button" data-action="delete-comment" data-id="${Number(comment.commentId)}"><i class="bi bi-trash"></i></button></div></td></tr>`).join("")}</tbody></table></div>`
				: App.emptyState(
						"Chưa có bình luận",
						"Bình luận sẽ xuất hiện tại đây.",
					));
	}

	async function renderReports() {
		const reports = await App.api("GET", "/reports");
		content.innerHTML =
			sectionHeader("Báo cáo", "Xem xét và cập nhật trạng thái báo cáo") +
			(reports.length
				? `<div class="app-card table-responsive"><table class="table dashboard-table align-middle mb-0"><thead><tr><th>Đối tượng</th><th>Người báo cáo</th><th>Lý do</th><th>Trạng thái</th><th>Ngày</th><th>Hành động</th></tr></thead><tbody>${reports.map((report) => `<tr><td>${report.postId ? `<a href="post-detail.html?id=${Number(report.postId)}">Bài viết #${Number(report.postId)}</a>` : `Bình luận #${Number(report.commentId)}`}</td><td>${App.escapeHtml(report.reporterName || report.reporterId)}</td><td>${App.escapeHtml(report.reason)}</td><td>${App.statusBadge(report.status)}</td><td>${App.formatDate(report.createdAt)}</td><td><div class="d-flex flex-wrap gap-2"><button class="btn btn-sm btn-outline-info" type="button" data-action="report-status" data-id="${Number(report.reportId)}" data-status="PROCESSING">Đang xử lý</button><button class="btn btn-sm btn-outline-success" type="button" data-action="report-status" data-id="${Number(report.reportId)}" data-status="RESOLVED">Hoàn thành</button><button class="btn btn-sm btn-outline-warning" type="button" data-action="report-status" data-id="${Number(report.reportId)}" data-status="REJECTED">Bác bỏ</button><button class="btn btn-sm btn-outline-danger" type="button" data-action="delete-report" data-id="${Number(report.reportId)}"><i class="bi bi-trash"></i></button></div></td></tr>`).join("")}</tbody></table></div>`
				: App.emptyState(
						"Không có báo cáo",
						"Không có nội dung nào cần xử lý.",
						"bi-shield-check",
					));
	}

	async function renderUsers(keyword = "") {
		const endpoint = keyword
			? `/users?action=search&keyword=${encodeURIComponent(keyword)}`
			: "/users?action=list";
		const users = await App.api("GET", endpoint);
		content.innerHTML =
			sectionHeader(
				"Quản lý người dùng",
				"Phân quyền và kiểm soát trạng thái tài khoản",
				`<div class="input-group"><input class="form-control" id="userSearch" value="${App.escapeAttribute(keyword)}" placeholder="Tìm người dùng..."><button class="btn btn-outline-primary" type="button" data-action="search-users"><i class="bi bi-search"></i></button></div>`,
			) +
			(users.length
				? `<div class="app-card table-responsive"><table class="table dashboard-table align-middle mb-0"><thead><tr><th>Người dùng</th><th>Email</th><th>Vai trò</th><th>Trạng thái</th><th>Báo cáo</th><th>Hành động</th></tr></thead><tbody>${users
						.map((user) => {
							const isSelf = Number(user.userId) === Number(state.user.userId);
							const reportCount = Number(user.warningCount) || 0;
							let actions =
								'<span class="text-muted small">Tài khoản hiện tại</span>';
							if (!isSelf) {
								if (user.status === "BLOCKED") {
									actions = `<button class="btn btn-sm btn-outline-success" type="button" data-action="user-status" data-id="${Number(user.userId)}" data-next-status="ACTIVE">Mở khóa</button>`;
								} else if (user.status === "RESTRICTED") {
									actions = `<div class="d-flex flex-wrap gap-2"><button class="btn btn-sm btn-outline-success" type="button" data-action="user-status" data-id="${Number(user.userId)}" data-next-status="ACTIVE">Gỡ hạn chế</button><button class="btn btn-sm btn-outline-danger" type="button" data-action="user-status" data-id="${Number(user.userId)}" data-next-status="BLOCKED">Khóa</button></div>`;
								} else {
									actions = `<div class="d-flex flex-wrap gap-2">${user.role === "USER" ? `<button class="btn btn-sm btn-outline-warning" type="button" data-action="user-status" data-id="${Number(user.userId)}" data-next-status="RESTRICTED">Hạn chế</button>` : ""}<button class="btn btn-sm btn-outline-danger" type="button" data-action="user-status" data-id="${Number(user.userId)}" data-next-status="BLOCKED">Khóa</button></div>`;
								}
							}
							return `<tr><td><strong>${App.escapeHtml(user.fullName)}</strong><div class="small text-muted">@${App.escapeHtml(user.username)}</div></td><td>${App.escapeHtml(user.email)}</td><td><select class="form-select form-select-sm" data-action="change-role" data-id="${Number(user.userId)}" ${isSelf ? "disabled" : ""}><option value="USER" ${user.role === "USER" ? "selected" : ""}>USER</option><option value="MODERATOR" ${user.role === "MODERATOR" ? "selected" : ""}>MODERATOR</option><option value="ADMIN" ${user.role === "ADMIN" ? "selected" : ""}>ADMIN</option></select></td><td>${App.statusBadge(user.status)}</td><td>${user.role === "USER" ? `<span class="badge ${reportCount >= 3 ? "text-bg-warning" : "text-bg-light"}">${reportCount}/3</span>` : '<span class="text-muted">-</span>'}</td><td>${actions}</td></tr>`;
						})
						.join("")}</tbody></table></div>`
				: App.emptyState(
						"Không tìm thấy người dùng",
						"Thử từ khóa khác.",
						"bi-search",
					));
		document
			.getElementById("userSearch")
			?.addEventListener("keydown", (event) => {
				if (event.key === "Enter")
					renderUsers(event.currentTarget.value.trim());
			});
	}

	function renderSupport() {
		if (state.user.role !== "USER") {
			loadSection("overview");
			return;
		}
		content.innerHTML =
			sectionHeader("Hỗ trợ", "Gửi câu hỏi tới đội ngũ kiểm duyệt") +
			`<div class="app-card p-4"><form id="supportForm"><label class="form-label" for="supportMessage">Nội dung cần hỗ trợ</label><textarea class="form-control" id="supportMessage" rows="6" maxlength="1000" required></textarea><div class="char-count"><span id="supportCount">0</span>/1000</div><button class="btn btn-primary mt-3" type="submit"><i class="bi bi-send"></i> Gửi yêu cầu</button></form></div>`;
		document
			.getElementById("supportMessage")
			.addEventListener("input", () =>
				updateCounter("supportMessage", "supportCount"),
			);
		document
			.getElementById("supportForm")
			.addEventListener("submit", submitSupport);
	}

	async function renderSupportMessages() {
		const response = await App.api("GET", "/support/messages");
		const messages = Array.isArray(response) ? response : [];

		content.innerHTML =
			sectionHeader(
				"Tin nhắn hỗ trợ",
				"Yêu cầu do người dùng gửi tới đội ngũ kiểm duyệt",
			) +
			(messages.length
				? `<div class="d-grid gap-3">${messages
						.map((item) => {
							const senderName =
								item.fullName ||
								item.reporterName ||
								item.username ||
								`User #${Number(item.reporterId)}`;
							const message = item.message || item.reason || "";
							const resolved = item.status === "RESOLVED";

							return `<article class="app-card p-4">
                    <div class="d-flex flex-column flex-md-row justify-content-between gap-3">
                        <div class="flex-grow-1">
                            <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
                                <h3 class="h6 mb-0">${App.escapeHtml(senderName)}</h3>
                                ${App.statusBadge(item.status || "PENDING")}
                            </div>
                            <p class="mb-2 text-break">${App.escapeHtml(message)}</p>
                            <small class="text-muted">
                                <i class="bi bi-clock me-1"></i>${App.formatDateTime(item.createdAt)}
                            </small>
                        </div>
                        <div class="flex-shrink-0">
                            ${
															resolved
																? '<span class="text-success small"><i class="bi bi-check-circle me-1"></i>Đã xử lý</span>'
																: `<button class="btn btn-outline-success" type="button" data-action="resolve-support" data-id="${Number(item.reportId)}">
                                    <i class="bi bi-check2"></i> Đánh dấu đã xử lý
                                </button>`
														}
                        </div>
                    </div>
                </article>`;
						})
						.join("")}</div>`
				: App.emptyState(
						"Không có yêu cầu hỗ trợ",
						"Chưa có người dùng nào gửi yêu cầu hỗ trợ.",
						"bi-headset",
					));
	}

	async function renderNotifications() {
		const result = await App.api("GET", "/notifications?limit=50");
		const items = Array.isArray(result.items) ? result.items : [];
		content.innerHTML =
			sectionHeader(
				"Thông báo",
				`${Number(result.unreadCount) || 0} thông báo chưa đọc`,
				items.length
					? '<button class="btn btn-outline-primary" type="button" data-action="read-all-notifications">Đánh dấu đã đọc</button>'
					: "",
			) +
			(items.length
				? `<div class="d-grid gap-2">${items.map((item) => `<a class="app-card p-3 text-decoration-none ${item.read ? "" : "border-primary"}" href="${App.escapeAttribute(App.safeInternalHref(item.link, "dashboard.html#notifications"))}"><div class="d-flex justify-content-between gap-3"><div><strong>${App.escapeHtml(item.title)}</strong><p class="text-muted mb-0 mt-1">${App.escapeHtml(item.message)}</p></div><small class="text-muted flex-shrink-0">${App.relativeTime(item.createdAt)}</small></div></a>`).join("")}</div>`
				: App.emptyState(
						"Chưa có thông báo",
						"Thông báo về bài viết và tài khoản sẽ xuất hiện tại đây.",
						"bi-bell",
					));
	}

	function sectionHeader(title, subtitle, action = "") {
		return `<header class="dashboard-header"><div><h1 class="h3 mb-1">${App.escapeHtml(title)}</h1><p class="text-muted mb-0">${App.escapeHtml(subtitle)}</p></div>${action}</header>`;
	}

	function compactPostCard(post, action = "") {
		const image = App.resolveAsset(post.thumbnail);
		return `<article class="app-card post-card"><a href="post-detail.html?id=${Number(post.postId)}">${image ? `<img class="post-thumb" src="${App.escapeAttribute(image)}" alt="${App.escapeAttribute(post.title)}">` : '<div class="post-thumb-placeholder"><i class="bi bi-file-earmark-text"></i></div>'}</a><div class="p-4"><span class="badge text-bg-primary">${App.escapeHtml(post.categoryName || "Chung")}</span><h3 class="h5 mt-2"><a class="text-decoration-none" href="post-detail.html?id=${Number(post.postId)}">${App.escapeHtml(post.title)}</a></h3><p class="text-muted">${App.escapeHtml(post.summary || "")}</p>${action}</div></article>`;
	}

	async function handleContentClick(event) {
		const target = event.target.closest("[data-action]");
		if (!target) return;
		const action = target.dataset.action;
		const id = Number(target.dataset.id);
		try {
			switch (action) {
				case "retry-section":
					await loadSection(state.section);
					break;
				case "open-section":
					window.location.hash = target.dataset.section;
					break;
				case "save-draft":
					await submitNewPost(
						{
							preventDefault() {},
							submitter: target,
							currentTarget: document.getElementById("writePostForm"),
						},
						true,
					);
					break;
				case "preview-write":
					previewEditor("write");
					break;
				case "clear-write":
					await clearWriteForm();
					break;
				case "remove-thumbnail":
					removeThumbnail(target.dataset.prefix);
					break;
				case "edit-post":
					await openPostEditor(id, target.dataset.resubmit === "true");
					break;
				case "delete-post":
					await deletePost(id);
					break;
				case "restore-post-admin":
					await restorePostAdmin(id);
					break;
				case "approve-post":
					await approvePost(id);
					break;
				case "reject-post":
					openRejectModal(id);
					break;
				case "add-entity":
					openEntityModal(target.dataset.type);
					break;
				case "edit-entity":
					openEntityModal(target.dataset.type, id);
					break;
				case "delete-entity":
					await deleteEntity(target.dataset.type, id);
					break;
				case "remove-bookmark":
					await removeBookmark(id);
					break;
				case "delete-own-comment":
					await deleteOwnComment(id);
					break;
				case "toggle-comment":
					await updateComment(id, "toggle");
					break;
				case "restore-comment":
					await restoreComment(id);
					break;
				case "delete-comment":
					await deleteComment(id);
					break;
				case "report-status":
					await updateReportStatus(id, target.dataset.status);
					break;
				case "delete-report":
					await deleteReport(id);
					break;
				case "search-users":
					await renderUsers(document.getElementById("userSearch").value.trim());
					break;
				case "user-status":
					await setUserStatus(id, target.dataset.nextStatus);
					break;
				case "toggle-user":
					await toggleUser(id, target.dataset.status);
					break;
				case "resolve-support":
					await resolveSupport(id);
					break;
				case "read-all-notifications":
					await App.api("POST", "/notifications", { action: "readAll" });
					await renderNotifications();
					await App.loadNotifications();
					break;
			}
		} catch (error) {
			App.toast(error.message, "danger");
		}
	}

	async function handleContentChange(event) {
		const target = event.target;
		if (target.id === "writeThumbnailFile") {
			await handleThumbnailSelection("write", target.files?.[0]);
			return;
		}
		if (target.matches('[data-action="change-role"]')) {
			const confirmed = await App.confirmDialog({
				title: "Đổi quyền",
				message: `Đổi người dùng thành ${target.value}?`,
				confirmText: "Đổi quyền",
			});
			if (!confirmed) {
				await renderUsers();
				return;
			}
			try {
				await App.api("POST", "/users", {
					action: "changeRole",
					id: target.dataset.id,
					role: target.value,
				});
				App.toast("Đã cập nhật vai trò.", "success");
			} catch (error) {
				App.toast(error.message, "danger");
				await renderUsers();
			}
		}
	}

	function handleContentInput(event) {
		if (event.target.id === "writeTitle")
			updateCounter("writeTitle", "writeTitleCount");
		if (event.target.id === "writeSummary")
			updateCounter("writeSummary", "writeSummaryCount");
	}

	function handleEditorToolbar(event) {
		const button = event.target.closest("[data-editor-command]");
		if (!button) return;
		const toolbar = button.closest("[data-editor-target]");
		const textarea = document.getElementById(toolbar.dataset.editorTarget);
		if (!textarea) return;
		const command = button.dataset.editorCommand;
		const selected = textarea.value.slice(
			textarea.selectionStart,
			textarea.selectionEnd,
		);
		const templates = {
			heading: [
				`## ${selected || "Tiêu đề"}`,
				3,
				selected ? 3 + selected.length : 10,
			],
			bold: [
				`**${selected || "văn bản"}**`,
				2,
				selected ? 2 + selected.length : 9,
			],
			italic: [
				`*${selected || "văn bản"}*`,
				1,
				selected ? 1 + selected.length : 8,
			],
			underline: [
				`++${selected || "văn bản"}++`,
				2,
				selected ? 2 + selected.length : 9,
			],
			strike: [
				`~~${selected || "văn bản"}~~`,
				2,
				selected ? 2 + selected.length : 9,
			],
			code: [
				`\`${selected || "code"}\``,
				1,
				selected ? 1 + selected.length : 5,
			],
			list: [
				`- ${selected || "Mục danh sách"}`,
				2,
				selected ? 2 + selected.length : 14,
			],
			link: [
				`[${selected || "liên kết"}](https://)`,
				1,
				selected ? 1 + selected.length : 9,
			],
		};
		const [text, selectStart, selectEnd] = templates[command];
		textarea.setRangeText(
			text,
			textarea.selectionStart,
			textarea.selectionEnd,
			"end",
		);
		const base = textarea.selectionStart - text.length;
		textarea.setSelectionRange(base + selectStart, base + selectEnd);
		textarea.focus();
		textarea.dispatchEvent(new Event("input", { bubbles: true }));
	}

	async function submitNewPost(event, draft) {
		event.preventDefault();
		const button =
			event.submitter ||
			event.currentTarget.querySelector('button[type="submit"]');
		const payload = collectEditor("write");
		if (!validatePostPayload(payload, draft)) return;
		App.setButtonLoading(button, true, draft ? "Đang lưu..." : "Đang gửi...");
		try {
			const result = await App.api("POST", "/posts", {
				action: draft ? "saveDraft" : "add",
				...payload,
			});
			App.toast(result.message, "success");
			localStorage.removeItem("blogSePostDraft");
			window.location.hash = "posts";
			await loadSection("posts");
		} catch (error) {
			App.toast(error.message, "danger");
		} finally {
			App.setButtonLoading(button, false);
		}
	}

	function collectEditor(prefix) {
		return {
			title: document.getElementById(`${prefix}Title`).value.trim(),
			summary: document.getElementById(`${prefix}Summary`).value.trim(),
			content: document.getElementById(`${prefix}Content`).value.trim(),
			categoryId: document.getElementById(`${prefix}Category`).value,
			thumbnail: document.getElementById(`${prefix}Thumbnail`).value.trim(),
			tagIds: [
				...document.querySelectorAll(`#${prefix}TagPicker input:checked`),
			]
				.map((input) => input.value)
				.join(","),
		};
	}

	function validatePostPayload(payload, draft = false) {
		if (!payload.title) {
			App.toast("Vui lòng nhập tiêu đề.", "warning");
			return false;
		}
		if (!payload.categoryId) {
			App.toast("Vui lòng chọn danh mục.", "warning");
			return false;
		}
		if (!draft && !payload.content) {
			App.toast("Vui lòng nhập nội dung.", "warning");
			return false;
		}
		if (draft && !payload.content) payload.content = "Nội dung bản nháp";
		return true;
	}

	function saveLocalDraft() {
		if (!document.getElementById("writePostForm")) return;
		localStorage.setItem(
			"blogSePostDraft",
			JSON.stringify(collectEditor("write")),
		);
	}

	function restoreDraft() {
		const raw = localStorage.getItem("blogSePostDraft");
		if (!raw) return;
		try {
			const draft = JSON.parse(raw);
			document.getElementById("writeTitle").value = draft.title || "";
			document.getElementById("writeSummary").value = draft.summary || "";
			document.getElementById("writeContent").value = draft.content || "";
			document.getElementById("writeCategory").value = draft.categoryId || "";
			document.getElementById("writeThumbnail").value = draft.thumbnail || "";
			const selected = new Set(String(draft.tagIds || "").split(","));
			document.querySelectorAll("#writeTagPicker input").forEach((input) => {
				input.checked = selected.has(input.value);
			});
			updateCounter("writeTitle", "writeTitleCount");
			updateCounter("writeSummary", "writeSummaryCount");
			updateThumbnailPreview("write");
		} catch {
			localStorage.removeItem("blogSePostDraft");
		}
	}

	async function clearWriteForm() {
		const confirmed = await App.confirmDialog({
			title: "Làm mới trình soạn thảo",
			message: "Nội dung đang nhập sẽ bị xóa.",
			confirmText: "Xóa nội dung",
			danger: true,
		});
		if (!confirmed) return;
		localStorage.removeItem("blogSePostDraft");
		document.getElementById("writePostForm").reset();
		document.getElementById("writeThumbnail").value = "";
		document.querySelectorAll("#writeTagPicker input").forEach((input) => {
			input.checked = false;
		});
		updateCounter("writeTitle", "writeTitleCount");
		updateCounter("writeSummary", "writeSummaryCount");
		updateThumbnailPreview("write");
	}

	function previewEditor(prefix) {
		const payload = collectEditor(prefix);
		const rendered = App.renderMarkdown(payload.content);
		document.getElementById("previewContent").innerHTML =
			`<h1>${App.escapeHtml(payload.title || "Không có tiêu đề")}</h1><p class="lead text-muted">${App.escapeHtml(payload.summary || "")}</p><hr>${rendered.html}`;
		state.previewModal = bootstrap.Modal.getOrCreateInstance(
			document.getElementById("previewModal"),
		);
		state.previewModal.show();
	}

	async function openPostEditor(id, resubmit) {
		const modalElement = document.getElementById("postEditorModal");
		const form = document.getElementById("postEditorForm");
		const categorySelect = document.getElementById("editorCategory");
		const thumbnailFile = document.getElementById("editorThumbnailFile");
		const thumbnailStatus = document.getElementById("editorThumbnailStatus");
		const saveButton = document.getElementById("editorSaveButton");
		const reviewButton = document.getElementById("editorSubmitReviewButton");

		if (
			!modalElement ||
			!form ||
			!categorySelect ||
			!saveButton ||
			!reviewButton
		) {
			throw new Error("Không tìm thấy biểu mẫu chỉnh sửa bài viết.");
		}

		form.reset();
		if (thumbnailFile) thumbnailFile.value = "";
		if (thumbnailStatus) thumbnailStatus.textContent = "";

		await Promise.all([ensureCategories(true), ensureTags(true)]);
		fillEditorOptions("editor");

		const post = await App.api("GET", `/posts?action=detail&id=${id}`);
		const originalStatus = String(post.status || "");
		const shouldResubmit = Boolean(resubmit) || originalStatus === "REJECTED";

		document.getElementById("editorPostId").value = post.postId;
		document.getElementById("editorResubmit").value = String(shouldResubmit);
		document.getElementById("editorOriginalStatus").value = originalStatus;
		document.getElementById("editorTitle").value = post.title || "";
		document.getElementById("editorSummary").value = post.summary || "";
		document.getElementById("editorContent").value = post.content || "";
		document.getElementById("editorThumbnail").value = post.thumbnail || "";

		const categoryId = String(post.categoryId || "");
		categorySelect.value = categoryId;
		if (categoryId && categorySelect.value !== categoryId) {
			const fallbackOption = document.createElement("option");
			fallbackOption.value = categoryId;
			fallbackOption.textContent = post.categoryName || "Danh mục hiện tại";
			fallbackOption.dataset.fallback = "true";
			categorySelect.appendChild(fallbackOption);
			categorySelect.value = categoryId;
		}

		const isDraft = originalStatus === "DRAFT";
		const isRejected = originalStatus === "REJECTED";

		document.getElementById("postEditorTitle").textContent = isRejected
			? "Sửa và gửi lại bài viết"
			: isDraft
				? "Chỉnh sửa bản nháp"
				: "Chỉnh sửa bài viết";
		document.getElementById("postEditorSubtitle").textContent = isRejected
			? `Lý do từ chối: ${post.rejectionReason || "Không có"}`
			: isDraft
				? "Bạn có thể tiếp tục lưu nháp hoặc gửi bài để kiểm duyệt."
				: "Cập nhật nội dung bài viết.";

		reviewButton.classList.toggle("d-none", !isDraft);
		saveButton.innerHTML = isRejected
			? '<i class="bi bi-send-check"></i> Gửi lại để duyệt'
			: isDraft
				? '<i class="bi bi-save"></i> Lưu bản nháp'
				: '<i class="bi bi-save"></i> Lưu thay đổi';

		const selectedTags = new Set(
			(Array.isArray(post.tags) ? post.tags : []).map((tag) =>
				String(tag.tagId),
			),
		);
		document
			.querySelectorAll('#editorTagPicker input[type="checkbox"]')
			.forEach((input) => {
				input.checked = selectedTags.has(input.value);
			});

		updateCounter("editorTitle", "editorTitleCount");
		updateCounter("editorSummary", "editorSummaryCount");
		updateThumbnailPreview("editor");

		state.postEditorModal = bootstrap.Modal.getOrCreateInstance(modalElement);
		state.postEditorModal.show();

		modalElement.addEventListener(
			"shown.bs.modal",
			() => {
				const body = modalElement.querySelector(".modal-body");
				if (body) body.scrollTop = 0;
			},
			{ once: true },
		);
	}

	async function submitPostEditor(event) {
		event.preventDefault();
		const submitButton =
			event.submitter || document.getElementById("editorSaveButton");
		const submitMode = submitButton?.dataset.submitMode || "save";
		const originalStatus = document.getElementById(
			"editorOriginalStatus",
		).value;
		const submitForReview =
			originalStatus === "DRAFT" && submitMode === "review";
		const resubmit = originalStatus === "REJECTED";
		const payload = collectEditor("editor");

		if (
			!validatePostPayload(
				payload,
				!submitForReview && originalStatus === "DRAFT",
			)
		)
			return;

		App.setButtonLoading(
			submitButton,
			true,
			submitForReview || resubmit ? "Đang gửi..." : "Đang lưu...",
		);

		try {
			const result = await App.api("POST", "/posts/update", {
				postId: document.getElementById("editorPostId").value,
				resubmit: String(resubmit),
				submitForReview: String(submitForReview),
				...payload,
			});
			App.toast(result.message, "success");
			state.postEditorModal.hide();
			await loadSection("posts");
		} catch (error) {
			App.toast(error.message, "danger");
		} finally {
			App.setButtonLoading(submitButton, false);
		}
	}

	function fillEditorOptions(prefix) {
		const categorySelect = document.getElementById(`${prefix}Category`);
		const tagPicker = document.getElementById(`${prefix}TagPicker`);

		if (!categorySelect || !tagPicker) {
			throw new Error(
				"Không tìm thấy danh mục hoặc thẻ của biểu mẫu bài viết.",
			);
		}

		categorySelect.replaceChildren();
		const placeholder = document.createElement("option");
		placeholder.value = "";
		placeholder.textContent = "Chọn danh mục";
		categorySelect.appendChild(placeholder);

		(Array.isArray(state.categories) ? state.categories : []).forEach(
			(item) => {
				const option = document.createElement("option");
				option.value = String(item.categoryId);
				option.textContent = item.categoryName || "Danh mục";
				categorySelect.appendChild(option);
			},
		);

		tagPicker.replaceChildren();
		(Array.isArray(state.tags) ? state.tags : []).forEach((tag, index) => {
			const wrapper = document.createElement("span");
			const input = document.createElement("input");
			const label = document.createElement("label");
			const inputId = `${prefix}Tag${index}`;

			input.className = "tag-check";
			input.id = inputId;
			input.type = "checkbox";
			input.value = String(tag.tagId);

			label.className = "tag-label";
			label.htmlFor = inputId;
			label.textContent = `#${tag.tagName || "tag"}`;

			wrapper.append(input, label);
			tagPicker.appendChild(wrapper);
		});
	}

	function updateThumbnailPreview(prefix, temporaryUrl = "") {
		const storedValue = document
			.getElementById(`${prefix}Thumbnail`)
			?.value.trim();
		const preview = document.getElementById(`${prefix}ThumbnailPreview`);
		const removeButton = document.getElementById(`${prefix}ThumbnailRemove`);
		if (!preview) return;
		const url = temporaryUrl || App.resolveAsset(storedValue);
		preview.innerHTML = url
			? `<img class="post-thumb rounded thumbnail-preview-image" src="${App.escapeAttribute(url)}" alt="Xem trước thumbnail">`
			: '<div class="thumbnail-empty small text-muted"><i class="bi bi-image"></i> Chưa chọn ảnh thumbnail.</div>';
		removeButton?.classList.toggle("d-none", !storedValue && !temporaryUrl);
	}

	async function handleThumbnailSelection(prefix, file) {
		if (!file) return;
		const input = document.getElementById(`${prefix}ThumbnailFile`);
		const hidden = document.getElementById(`${prefix}Thumbnail`);
		const status = document.getElementById(`${prefix}ThumbnailStatus`);
		const allowedTypes = new Set(["image/jpeg", "image/png", "image/webp"]);

		if (!allowedTypes.has(file.type)) {
			input.value = "";
			App.toast("Chỉ chấp nhận ảnh JPG, PNG hoặc WebP.", "warning");
			return;
		}
		if (file.size > 5 * 1024 * 1024) {
			input.value = "";
			App.toast("Ảnh thumbnail không được vượt quá 5 MB.", "warning");
			return;
		}

		const localUrl = URL.createObjectURL(file);
		updateThumbnailPreview(prefix, localUrl);
		if (status)
			status.innerHTML =
				'<span class="text-primary"><span class="spinner-border spinner-border-sm me-1"></span>Đang tải ảnh lên...</span>';
		input.disabled = true;

		try {
			const formData = new FormData();
			formData.append("thumbnail", file);
			const result = await App.api("POST", "/upload-thumbnail", formData);
			hidden.value = result.thumbnailUrl || "";
			if (status)
				status.innerHTML =
					'<span class="text-success"><i class="bi bi-check-circle me-1"></i>Đã tải ảnh lên.</span>';
			updateThumbnailPreview(prefix);
			if (prefix === "write") saveLocalDraft();
		} catch (error) {
			hidden.value = "";
			if (status)
				status.innerHTML = `<span class="text-danger"><i class="bi bi-exclamation-circle me-1"></i>${App.escapeHtml(error.message)}</span>`;
			updateThumbnailPreview(prefix);
			input.value = "";
			App.toast(error.message, "danger");
		} finally {
			URL.revokeObjectURL(localUrl);
			input.disabled = false;
		}
	}

	function removeThumbnail(prefix) {
		const hidden = document.getElementById(`${prefix}Thumbnail`);
		const input = document.getElementById(`${prefix}ThumbnailFile`);
		const status = document.getElementById(`${prefix}ThumbnailStatus`);
		if (hidden) hidden.value = "";
		if (input) input.value = "";
		if (status) status.textContent = "";
		updateThumbnailPreview(prefix);
		if (prefix === "write") saveLocalDraft();
	}

	function updateCounter(inputId, counterId) {
		const input = document.getElementById(inputId);
		const counter = document.getElementById(counterId);
		if (input && counter) counter.textContent = input.value.length;
	}

	async function deletePost(id) {
		const confirmed = await App.confirmDialog({
			title: "Xóa bài viết",
			message: "Bài viết sẽ được chuyển vào thùng rác.",
			confirmText: "Xóa bài",
			danger: true,
		});
		if (!confirmed) return;
		const result = await App.api("POST", "/posts", { action: "delete", id });
		App.toast(result.message, "success");
		if (state.user.role === "ADMIN" && state.section === "all-posts")
			await renderAllPosts();
		else await renderMyPosts();
	}

	async function restorePostAdmin(id) {
		if (state.user.role !== "ADMIN") return;
		const confirmed = await App.confirmDialog({
			title: "Khôi phục bài viết",
			message: "Bài viết sẽ được đưa ra khỏi thùng rác.",
			confirmText: "Khôi phục",
		});
		if (!confirmed) return;
		const result = await App.api("POST", "/posts/restore", { postId: id });
		App.toast(result.message, "success");
		await renderAllPosts();
	}

	async function approvePost(id) {
		const confirmed = await App.confirmDialog({
			title: "Duyệt bài viết",
			message: "Bài viết sẽ được công khai trên trang chủ.",
			confirmText: "Duyệt bài",
		});
		if (!confirmed) return;
		const result = await App.api("POST", "/posts", { action: "approve", id });
		App.toast(result.message, "success");
		await renderModeration();
	}

	function openRejectModal(id) {
		document.getElementById("rejectPostId").value = id;
		document.getElementById("rejectReason").value = "";
		updateCounter("rejectReason", "rejectReasonCount");
		state.rejectModal = bootstrap.Modal.getOrCreateInstance(
			document.getElementById("rejectModal"),
		);
		state.rejectModal.show();
	}

	async function submitReject(event) {
		event.preventDefault();
		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang xử lý...");
		try {
			const result = await App.api("POST", "/posts", {
				action: "reject",
				id: document.getElementById("rejectPostId").value,
				reason: document.getElementById("rejectReason").value.trim(),
			});
			App.toast(result.message, "success");
			state.rejectModal.hide();
			await renderModeration();
		} catch (error) {
			App.toast(error.message, "danger");
		} finally {
			App.setButtonLoading(button, false);
		}
	}

	async function openEntityModal(type, id = 0) {
		const collection =
			type === "category" ? await ensureCategories() : await ensureTags();
		const entity = id
			? collection.find(
					(item) =>
						Number(type === "category" ? item.categoryId : item.tagId) === id,
				)
			: null;
		document.getElementById("entityType").value = type;
		document.getElementById("entityId").value = id || "";
		document.getElementById("entityName").value = entity
			? type === "category"
				? entity.categoryName
				: entity.tagName
			: "";
		document.getElementById("entityDescription").value =
			entity?.description || "";
		document.getElementById("entityModalTitle").textContent =
			`${id ? "Sửa" : "Thêm"} ${type === "category" ? "danh mục" : "thẻ"}`;
		state.entityModal = bootstrap.Modal.getOrCreateInstance(
			document.getElementById("entityModal"),
		);
		state.entityModal.show();
	}

	async function submitEntity(event) {
		event.preventDefault();
		const type = document.getElementById("entityType").value;
		const id = document.getElementById("entityId").value;
		const endpoint = type === "category" ? "/categories" : "/tags";
		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang lưu...");
		try {
			const result = await App.api("POST", endpoint, {
				action: id ? "update" : "add",
				id,
				name: document.getElementById("entityName").value.trim(),
				description: document.getElementById("entityDescription").value.trim(),
			});
			App.toast(result.message, "success");
			state.entityModal.hide();
			if (type === "category") {
				state.categories = [];
				await renderCategories();
			} else {
				state.tags = [];
				await renderTags();
			}
		} catch (error) {
			App.toast(error.message, "danger");
		} finally {
			App.setButtonLoading(button, false);
		}
	}

	async function deleteEntity(type, id) {
		const confirmed = await App.confirmDialog({
			title: `Ẩn ${type === "category" ? "danh mục" : "thẻ"}`,
			message: "Mục này sẽ không còn xuất hiện trong danh sách chọn.",
			confirmText: "Ẩn mục",
			danger: true,
		});
		if (!confirmed) return;
		const endpoint = type === "category" ? "/categories" : "/tags";
		const result = await App.api("POST", endpoint, { action: "delete", id });
		App.toast(result.message, "success");
		if (type === "category") {
			state.categories = [];
			await renderCategories();
		} else {
			state.tags = [];
			await renderTags();
		}
	}

	async function removeBookmark(id) {
		const result = await App.api("POST", "/bookmark/toggle", { postId: id });
		App.toast(result.message, "success");
		await renderBookmarks();
	}

	async function deleteOwnComment(id) {
		const confirmed = await App.confirmDialog({
			title: "Xóa bình luận",
			message: "Bình luận sẽ không còn hiển thị.",
			confirmText: "Xóa",
			danger: true,
		});
		if (!confirmed) return;
		const result = await App.api("POST", "/comments/delete-by-user", {
			commentId: id,
		});
		App.toast(result.message, "success");
		await renderCommentHistory();
	}

	async function updateComment(id, action) {
		const result = await App.api("POST", "/comments", { action, id });
		App.toast(result.message, "success");
		await renderAllComments();
	}

	async function restoreComment(id) {
		const result = await App.api("POST", "/comments/restore", {
			commentId: id,
		});
		App.toast(result.message, "success");
		await renderAllComments();
	}

	async function deleteComment(id) {
		const confirmed = await App.confirmDialog({
			title: "Xóa bình luận",
			message: "Bình luận sẽ chuyển sang trạng thái đã xóa.",
			confirmText: "Xóa",
			danger: true,
		});
		if (!confirmed) return;
		const result = await App.api("POST", "/comments", { action: "delete", id });
		App.toast(result.message, "success");
		await renderAllComments();
	}

	async function updateReportStatus(id, status) {
		const result = await App.api("POST", "/reports/update", {
			reportId: id,
			status,
		});
		App.toast(result.message, "success");
		await renderReports();
	}

	async function deleteReport(id) {
		const confirmed = await App.confirmDialog({
			title: "Xóa báo cáo",
			message: "Báo cáo sẽ bị xóa khỏi danh sách.",
			confirmText: "Xóa",
			danger: true,
		});
		if (!confirmed) return;
		const result = await App.api("POST", "/reports/delete", { reportId: id });
		App.toast(result.message, "success");
		await renderReports();
	}

	async function setUserStatus(id, next) {
		const labels = {
			ACTIVE: "Hoạt động",
			RESTRICTED: "Hạn chế",
			BLOCKED: "Khóa",
		};
		const titles = {
			ACTIVE: "Khôi phục tài khoản",
			RESTRICTED: "Hạn chế tài khoản",
			BLOCKED: "Khóa tài khoản",
		};
		const confirmed = await App.confirmDialog({
			title: titles[next] || "Đổi trạng thái",
			message: `Trạng thái mới: ${labels[next] || next}. Tài khoản đang đăng nhập sẽ bị đăng xuất nếu chuyển sang Hạn chế hoặc Khóa.`,
			confirmText: "Xác nhận",
			danger: next === "BLOCKED",
		});
		if (!confirmed) return;
		const result = await App.api("POST", "/users", {
			action: "status",
			id,
			status: next,
		});
		App.toast(result.message, "success");
		await renderUsers();
	}

	async function toggleUser(id, status) {
		await setUserStatus(id, status === "BLOCKED" ? "ACTIVE" : "BLOCKED");
	}

	async function submitSupport(event) {
		event.preventDefault();
		const form = event.currentTarget;
		if (!form) {
			App.toast("Không tìm thấy biểu mẫu hỗ trợ.", "danger");
			return;
		}

		const button =
			event.submitter || form.querySelector('button[type="submit"]');
		const messageField = form.querySelector("#supportMessage");
		const message = messageField?.value.trim() || "";

		if (!message) {
			App.toast("Vui lòng nhập nội dung cần hỗ trợ.", "warning");
			return;
		}

		App.setButtonLoading(button, true, "Đang gửi...");
		try {
			const result = await App.api("POST", "/support", { message });
			App.toast(result.message || "Đã gửi yêu cầu hỗ trợ.", "success");

			if (form.isConnected) {
				form.reset();
				const counter = document.getElementById("supportCount");
				if (counter) counter.textContent = "0";
			}
		} catch (error) {
			App.toast(error.message, "danger");
		} finally {
			App.setButtonLoading(button, false);
		}
	}

	async function resolveSupport(id) {
		if (!id) {
			App.toast("Không xác định được yêu cầu hỗ trợ.", "danger");
			return;
		}
		try {
			const result = await App.api("POST", "/support/resolve", {
				reportId: id,
			});
			App.toast(result.message || "Đã xử lý yêu cầu hỗ trợ.", "success");
			await renderSupportMessages();
		} catch (error) {
			App.toast(error.message, "danger");
		}
	}

	async function ensureCategories(force = false) {
		if (force || !state.categories.length)
			state.categories = await App.api("GET", "/categories");
		return state.categories;
	}

	async function ensureTags(force = false) {
		if (force || !state.tags.length) state.tags = await App.api("GET", "/tags");
		return state.tags;
	}
})();
