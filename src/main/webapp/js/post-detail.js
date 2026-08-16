(() => {
	const state = {
		user: null,
		post: null,
		postId: null,
		bookmarked: false,
		categories: [],
		tags: [],
		reportModal: null,
		editModal: null,
	};

	const elements = {
		content: document.getElementById("postContent"),
		related: document.getElementById("relatedPosts"),
		relatedSection: document.getElementById("relatedSection"),
		progress: document.getElementById("readingProgress"),
		backToTop: document.getElementById("backToTop"),
	};

	document.addEventListener("DOMContentLoaded", init);

	async function init() {
		bindEvents();
		state.postId = new URLSearchParams(window.location.search).get("id");
		if (!state.postId || !/^\d+$/.test(state.postId)) {
			elements.content.innerHTML = App.errorState("Mã bài viết không hợp lệ.");
			elements.relatedSection.classList.add("d-none");
			return;
		}
		elements.content.innerHTML = App.loadingState("Đang tải bài viết...");
		state.user = await App.getCurrentUser();
		if (state.user) await App.applySettings();
		await loadPost();
	}

	function bindEvents() {
		elements.content.addEventListener("click", handleContentClick);
		elements.backToTop.addEventListener("click", () =>
			window.scrollTo({ top: 0, behavior: "smooth" }),
		);
		window.addEventListener("scroll", updateReadingProgress, { passive: true });
		document
			.getElementById("reportForm")
			.addEventListener("submit", submitReport);
		document.getElementById("reportReason").addEventListener("input", () => {
			document.getElementById("reportCount").textContent =
				document.getElementById("reportReason").value.length;
		});
		document
			.getElementById("editPostForm")
			.addEventListener("submit", submitEditPost);
		document
			.getElementById("editThumbnailFile")
			.addEventListener("change", (event) =>
				uploadEditThumbnail(event.target.files?.[0]),
			);
		document
			.getElementById("editThumbnailRemove")
			.addEventListener("click", removeEditThumbnail);
		document.addEventListener("click", handleEditorToolbar);
	}

	async function loadPost() {
		try {
			const post = await App.api(
				"GET",
				`/posts?action=detail&id=${encodeURIComponent(state.postId)}`,
			);
			state.post = post;
			document.title = `${post.title} - Blog SE`;
			renderPost(post);
			await Promise.allSettled([
				loadComments(),
				loadRelated(),
				loadBookmarkState(),
				recordViewHistory(),
			]);
		} catch (error) {
			if (error.status === 403) {
				window.location.href = "403.html";
				return;
			}
			if (error.status === 404) {
				window.location.href = "404.html";
				return;
			}
			elements.content.innerHTML = App.errorState(error.message, "retry-post");
		}
	}

	function renderPost(post) {
		const markdown = App.renderMarkdown(post.content);
		const readingMinutes = Math.max(
			1,
			Math.ceil(
				String(post.content || "")
					.trim()
					.split(/\s+/)
					.filter(Boolean).length / 200,
			),
		);
		const cover = App.resolveAsset(post.thumbnail);
		const author = { fullName: post.authorName, avatar: post.authorAvatar };
		const tags = Array.isArray(post.tags) ? post.tags : [];
		const isUser = state.user?.role === "USER";
		const isAuthor =
			isUser && Number(state.user.userId) === Number(post.authorId);
		const canEdit =
			isAuthor &&
			(["DRAFT", "PENDING"].includes(post.status) ||
				(post.status === "REJECTED" && Number(post.rejectCount) < 3));
		const canDelete = isAuthor;
		const canRestore = false;
		const loginHref = App.loginUrl(
			`post-detail.html?id=${encodeURIComponent(state.postId)}`,
		);

		elements.content.innerHTML = `
            <nav aria-label="breadcrumb" class="mb-4"><ol class="breadcrumb"><li class="breadcrumb-item"><a href="index.html">Trang chủ</a></li><li class="breadcrumb-item"><a href="index.html?categoryId=${Number(post.categoryId)}">${App.escapeHtml(post.categoryName || "Chung")}</a></li><li class="breadcrumb-item active" aria-current="page">${App.escapeHtml(post.title)}</li></ol></nav>
            <div class="article-shell">
                <article class="app-card article-card" id="articleBody">
                    <div class="d-flex flex-wrap gap-2 mb-3"><span class="badge text-bg-primary">${App.escapeHtml(post.categoryName || "Chung")}</span>${tags.map((tag) => `<span class="badge text-bg-light text-primary">#${App.escapeHtml(tag.tagName)}</span>`).join("")} ${post.status !== "APPROVED" ? App.statusBadge(post.status) : ""}</div>
                    <h1 class="article-title fw-bold">${App.escapeHtml(post.title)}</h1>
                    ${post.summary ? `<p class="lead text-muted mt-3">${App.escapeHtml(post.summary)}</p>` : ""}
                    <div class="d-flex flex-column flex-md-row justify-content-between gap-3 align-items-md-center my-4">
                        <a class="article-author text-decoration-none" href="author.html?id=${Number(post.authorId)}"><img src="${App.escapeAttribute(App.resolveAvatar(author, 96))}" alt=""><span><strong class="d-block">${App.escapeHtml(post.authorName || "Ẩn danh")}</strong><small class="text-muted">Tác giả</small></span></a>
                        <div class="post-meta"><span><i class="bi bi-calendar3"></i> ${App.formatDate(post.createdAt)}</span><span><i class="bi bi-clock"></i> ${readingMinutes} phút đọc</span><span><i class="bi bi-eye"></i> ${Number(post.viewCount) || 0}</span></div>
                    </div>
                    ${cover ? `<img class="article-cover mb-4" src="${App.escapeAttribute(cover)}" alt="${App.escapeAttribute(post.title)}">` : ""}
                    <div class="article-content" id="articleContent">${markdown.html}</div>
                    <div class="article-actions mt-5 pt-4 border-top">
                        ${isUser ? `<button class="btn btn-outline-danger" type="button" data-action="toggle-like"><i class="bi bi-heart-fill"></i> Thích <span id="likeCount">${Number(post.likeCount) || 0}</span></button>` : `<span class="text-muted d-inline-flex align-items-center gap-1"><i class="bi bi-heart"></i> ${Number(post.likeCount) || 0} lượt thích</span>`}
                        ${isUser ? '<button class="btn btn-outline-primary" type="button" data-action="toggle-bookmark" id="bookmarkAction"><i class="bi bi-bookmark"></i> Lưu để đọc sau</button>' : ""}
                        <button class="btn btn-outline-secondary" type="button" data-action="share"><i class="bi bi-share"></i> Chia sẻ</button>
                        ${isUser && !isAuthor ? '<button class="btn btn-outline-warning" type="button" data-action="report-post"><i class="bi bi-flag"></i> Báo cáo</button>' : ""}
                        ${canEdit ? '<button class="btn btn-outline-primary" type="button" data-action="edit-post"><i class="bi bi-pencil"></i> Sửa</button>' : ""}
                        ${canDelete ? '<button class="btn btn-outline-danger" type="button" data-action="delete-post"><i class="bi bi-trash"></i> Xóa</button>' : ""}
                        ${canRestore ? '<button class="btn btn-outline-success" type="button" data-action="restore-post"><i class="bi bi-arrow-counterclockwise"></i> Khôi phục</button>' : ""}
                    </div>
                    ${post.rejectionReason ? `<div class="alert alert-danger mt-4"><strong>Lý do từ chối:</strong> ${App.escapeHtml(post.rejectionReason)}</div>` : ""}
                    <section class="mt-5" aria-labelledby="commentHeading"><div class="d-flex justify-content-between align-items-center mb-3"><h2 class="h4 mb-0" id="commentHeading">Bình luận <span class="text-muted" id="commentCount">(${Number(post.commentCount) || 0})</span></h2></div>
                        ${isUser ? `<form class="app-card p-3 mb-4" id="commentForm"><label class="form-label" for="commentInput">Tham gia thảo luận</label><textarea class="form-control" id="commentInput" rows="3" maxlength="1000" required></textarea><div class="d-flex justify-content-end mt-2"><button class="btn btn-primary" type="submit"><i class="bi bi-send"></i> Gửi bình luận</button></div></form>` : !state.user ? `<div class="alert alert-info"><a href="${App.escapeAttribute(loginHref)}">Đăng nhập</a> để bình luận và tương tác.</div>` : ""}
                        <div class="d-grid gap-3" id="commentList">${App.loadingState("Đang tải bình luận...")}</div>
                    </section>
                </article>
                <aside class="app-card article-toc"><h2 class="h6 text-uppercase text-muted">Mục lục</h2><nav id="tocList">${markdown.headings.length ? markdown.headings.map((item) => `<a class="${item.level === 3 ? "ms-3 small" : ""}" href="#${App.escapeAttribute(item.id)}">${App.escapeHtml(item.title)}</a>`).join("") : '<p class="small text-muted mb-0">Bài viết chưa có tiêu đề mục.</p>'}</nav></aside>
            </div>`;

		App.updateUserUI(state.user);
		document
			.getElementById("commentForm")
			?.addEventListener("submit", submitComment);
	}

	async function loadComments() {
		const list = document.getElementById("commentList");
		if (!list) return;
		try {
			const comments = await App.api(
				"GET",
				`/comments?postId=${encodeURIComponent(state.postId)}`,
			);
			list.innerHTML = comments.length
				? comments.map((comment) => renderComment(comment)).join("")
				: App.emptyState(
						"Chưa có bình luận",
						"Hãy là người đầu tiên chia sẻ ý kiến.",
						"bi-chat",
					);
			const count = countComments(comments);
			document.getElementById("commentCount").textContent = `(${count})`;
		} catch (error) {
			list.innerHTML = App.errorState(error.message, "retry-comments");
		}
	}

	function renderComment(comment, level = 0) {
		const replies = Array.isArray(comment.replies) ? comment.replies : [];
		const isOwner =
			state.user && Number(state.user.userId) === Number(comment.userId);
		const avatar = App.resolveAvatar({ fullName: comment.username }, 80);
		return `<article class="comment-box ${level ? "comment-reply" : ""}" id="comment-${Number(comment.commentId)}">
            <div class="d-flex gap-3"><img class="comment-avatar" src="${App.escapeAttribute(avatar)}" alt=""><div class="flex-grow-1"><div class="d-flex flex-wrap justify-content-between gap-2"><strong>${App.escapeHtml(comment.username || "Ẩn danh")}</strong><small class="text-muted">${App.formatDateTime(comment.createdAt)}</small></div><p class="mt-2 mb-2">${App.escapeHtml(comment.content)}</p>
            <div class="d-flex flex-wrap gap-2">${state.user?.role === "USER" ? `<button class="btn btn-sm btn-outline-primary" type="button" data-action="like-comment" data-id="${Number(comment.commentId)}"><i class="bi ${comment.likedByCurrentUser ? "bi-heart-fill text-danger" : "bi-heart"}"></i> <span data-comment-like-count>${Number(comment.likeCount) || 0}</span></button><button class="btn btn-sm btn-outline-info" type="button" data-action="reply-comment" data-id="${Number(comment.commentId)}"><i class="bi bi-reply"></i> Trả lời</button>${!isOwner ? `<button class="btn btn-sm btn-outline-warning" type="button" data-action="report-comment" data-id="${Number(comment.commentId)}"><i class="bi bi-flag"></i></button>` : ""}${isOwner ? `<button class="btn btn-sm btn-outline-danger" type="button" data-action="delete-own-comment" data-id="${Number(comment.commentId)}"><i class="bi bi-trash"></i></button>` : ""}` : ""}</div>
            <div class="mt-3 d-none" data-reply-form="${Number(comment.commentId)}"><form class="d-flex gap-2" data-reply-submit="${Number(comment.commentId)}"><input class="form-control form-control-sm" maxlength="1000" placeholder="Nhập câu trả lời..." required><button class="btn btn-sm btn-primary" type="submit">Gửi</button><button class="btn btn-sm btn-secondary" type="button" data-action="cancel-reply" data-id="${Number(comment.commentId)}">Hủy</button></form></div>
            </div></div>${replies.map((reply) => renderComment(reply, level + 1)).join("")}</article>`;
	}

	function countComments(comments) {
		return comments.reduce(
			(sum, comment) =>
				sum +
				1 +
				countComments(Array.isArray(comment.replies) ? comment.replies : []),
			0,
		);
	}

	async function loadRelated() {
		try {
			const posts = await App.api(
				"GET",
				`/posts?action=related&id=${state.postId}&categoryId=${Number(state.post.categoryId)}&limit=3`,
			);
			if (!posts.length) {
				elements.relatedSection.classList.add("d-none");
				return;
			}
			elements.related.innerHTML = posts
				.map(
					(post) =>
						`<div class="col-md-4"><article class="app-card post-card"><a href="post-detail.html?id=${Number(post.postId)}">${App.resolveAsset(post.thumbnail) ? `<img class="post-thumb" src="${App.escapeAttribute(App.resolveAsset(post.thumbnail))}" alt="${App.escapeAttribute(post.title)}">` : '<div class="post-thumb-placeholder"><i class="bi bi-file-earmark-text"></i></div>'}</a><div class="p-4"><span class="badge text-bg-primary">${App.escapeHtml(post.categoryName || "Chung")}</span><h3 class="h5 mt-2"><a class="text-decoration-none" href="post-detail.html?id=${Number(post.postId)}">${App.escapeHtml(post.title)}</a></h3><div class="post-meta"><span><i class="bi bi-eye"></i> ${Number(post.viewCount) || 0}</span><span><i class="bi bi-heart"></i> ${Number(post.likeCount) || 0}</span></div></div></article></div>`,
				)
				.join("");
		} catch {
			elements.relatedSection.classList.add("d-none");
		}
	}

	async function loadBookmarkState() {
		if (state.user?.role !== "USER") return;
		try {
			const result = await App.api("GET", `/bookmarks?postId=${state.postId}`);
			state.bookmarked = Boolean(result.bookmarked);
			updateBookmarkButton();
		} catch {
			/* bookmark state is optional */
		}
	}

	function updateBookmarkButton() {
		const button = document.getElementById("bookmarkAction");
		if (!button) return;
		button.innerHTML = state.bookmarked
			? '<i class="bi bi-bookmark-fill"></i> Đã lưu'
			: '<i class="bi bi-bookmark"></i> Lưu để đọc sau';
		button.classList.toggle("btn-primary", state.bookmarked);
		button.classList.toggle("btn-outline-primary", !state.bookmarked);
	}

	async function recordViewHistory() {
		if (state.user?.role !== "USER" || state.post.status !== "APPROVED") return;
		try {
			await App.api("POST", "/view-history", { postId: state.postId });
		} catch {
			/* non-critical */
		}
	}

	async function handleContentClick(event) {
		const target = event.target.closest("[data-action]");
		if (!target) return;
		const action = target.dataset.action;
		const id = Number(target.dataset.id);
		try {
			switch (action) {
				case "retry-post":
					await loadPost();
					break;
				case "retry-comments":
					await loadComments();
					break;
				case "toggle-like":
					await toggleLike();
					break;
				case "toggle-bookmark":
					await toggleBookmark();
					break;
				case "share":
					await sharePost();
					break;
				case "report-post":
					openReport("post", state.postId);
					break;
				case "report-comment":
					openReport("comment", id);
					break;
				case "edit-post":
					await openEditModal();
					break;
				case "delete-post":
					await deletePost();
					break;
				case "restore-post":
					await restorePost();
					break;
				case "like-comment":
					await toggleCommentLike(id, target);
					break;
				case "reply-comment":
					showReplyForm(id);
					break;
				case "cancel-reply":
					hideReplyForm(id);
					break;
				case "delete-own-comment":
					await deleteOwnComment(id);
					break;
			}
		} catch (error) {
			App.toast(error.message, "danger");
		}
	}

	async function submitComment(event) {
		event.preventDefault();
		const input = document.getElementById("commentInput");
		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang gửi...");
		try {
			const result = await App.api("POST", "/comments", {
				action: "add",
				postId: state.postId,
				content: input.value.trim(),
			});
			App.toast(result.message, "success");
			input.value = "";
			await loadComments();
		} catch (error) {
			App.toast(error.message, "danger");
		} finally {
			App.setButtonLoading(button, false);
		}
	}

	function showReplyForm(id) {
		const container = document.querySelector(`[data-reply-form="${id}"]`);
		if (!container) return;
		container.classList.remove("d-none");
		const form = container.querySelector("form");
		if (!form.dataset.bound) {
			form.dataset.bound = "true";
			form.addEventListener("submit", submitReply);
		}
		container.querySelector("input").focus();
	}

	function hideReplyForm(id) {
		const container = document.querySelector(`[data-reply-form="${id}"]`);
		if (container) container.classList.add("d-none");
	}

	async function submitReply(event) {
		event.preventDefault();
		const parentId = event.currentTarget.dataset.replySubmit;
		const input = event.currentTarget.querySelector("input");
		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang gửi...");
		try {
			const result = await App.api("POST", "/comments", {
				action: "add",
				postId: state.postId,
				parentId,
				content: input.value.trim(),
			});
			App.toast(result.message, "success");
			await loadComments();
		} catch (error) {
			App.toast(error.message, "danger");
		} finally {
			App.setButtonLoading(button, false);
		}
	}

	async function toggleLike() {
		if (!requireLogin()) return;
		const unlike = await App.api("POST", "/unlike", { id: state.postId });
		let result;
		if (unlike.success) {
			result = unlike;
			App.toast("Đã bỏ thích.", "info");
		} else {
			result = await App.api("POST", "/like", { id: state.postId });
			App.toast("Đã thích bài viết.", "success");
		}
		document.getElementById("likeCount").textContent =
			Number(result.likeCount) || 0;
	}

	async function toggleBookmark() {
		if (!requireLogin()) return;
		const result = await App.api("POST", "/bookmark/toggle", {
			postId: state.postId,
		});
		state.bookmarked = Boolean(result.bookmarked);
		updateBookmarkButton();
		App.toast(result.message, "success");
	}

	async function sharePost() {
		const shareData = {
			title: state.post.title,
			text: state.post.summary || "",
			url: window.location.href,
		};
		if (navigator.share) {
			await navigator.share(shareData);
		} else {
			await navigator.clipboard.writeText(window.location.href);
			App.toast("Đã sao chép liên kết.", "success");
		}
	}

	function openReport(target, id) {
		if (!requireLogin()) return;
		document.getElementById("reportTarget").value = target;
		document.getElementById("reportTargetId").value = id;
		document.getElementById("reportReason").value = "";
		document.getElementById("reportCount").textContent = "0";
		state.reportModal = bootstrap.Modal.getOrCreateInstance(
			document.getElementById("reportModal"),
		);
		state.reportModal.show();
	}

	async function submitReport(event) {
		event.preventDefault();
		const target = document.getElementById("reportTarget").value;
		const id = document.getElementById("reportTargetId").value;
		const reason = document.getElementById("reportReason").value.trim();
		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang gửi...");
		try {
			const endpoint = target === "post" ? "/posts/report" : "/comments/report";
			const payload =
				target === "post" ? { postId: id, reason } : { commentId: id, reason };
			const result = await App.api("POST", endpoint, payload);
			App.toast(result.message, "success");
			state.reportModal.hide();
		} catch (error) {
			App.toast(error.message, "danger");
		} finally {
			App.setButtonLoading(button, false);
		}
	}

	async function openEditModal() {
		if (!requireLogin()) return;
		const [categories, tags] = await Promise.all([
			App.api("GET", "/categories"),
			App.api("GET", "/tags"),
		]);
		state.categories = categories;
		state.tags = tags;
		document.getElementById("editPostId").value = state.post.postId;
		document.getElementById("editTitle").value = state.post.title || "";
		document.getElementById("editSummary").value = state.post.summary || "";
		document.getElementById("editContent").value = state.post.content || "";
		document.getElementById("editThumbnail").value = state.post.thumbnail || "";
		document.getElementById("editThumbnailFile").value = "";
		document.getElementById("editThumbnailStatus").textContent = "";
		updateEditThumbnailPreview();
		document.getElementById("editCategory").innerHTML = categories
			.map(
				(category) =>
					`<option value="${Number(category.categoryId)}" ${Number(category.categoryId) === Number(state.post.categoryId) ? "selected" : ""}>${App.escapeHtml(category.categoryName)}</option>`,
			)
			.join("");
		const selected = new Set(
			(state.post.tags || []).map((tag) => String(tag.tagId)),
		);
		document.getElementById("editTagPicker").innerHTML = tags
			.map(
				(tag, index) =>
					`<span><input class="tag-check" id="editTag${index}" type="checkbox" value="${Number(tag.tagId)}" ${selected.has(String(tag.tagId)) ? "checked" : ""}><label class="tag-label" for="editTag${index}">#${App.escapeHtml(tag.tagName)}</label></span>`,
			)
			.join("");
		state.editModal = bootstrap.Modal.getOrCreateInstance(
			document.getElementById("editPostModal"),
		);
		state.editModal.show();
	}

	function updateEditThumbnailPreview(temporaryUrl = "") {
		const stored = document.getElementById("editThumbnail").value.trim();
		const preview = document.getElementById("editThumbnailPreview");
		const removeButton = document.getElementById("editThumbnailRemove");
		const url = temporaryUrl || App.resolveAsset(stored);
		preview.innerHTML = url
			? `<img class="post-thumb rounded thumbnail-preview-image" src="${App.escapeAttribute(url)}" alt="Xem trước thumbnail">`
			: '<div class="thumbnail-empty small text-muted"><i class="bi bi-image"></i> Chưa chọn ảnh thumbnail.</div>';
		removeButton.classList.toggle("d-none", !stored && !temporaryUrl);
	}

	async function uploadEditThumbnail(file) {
		if (!file) return;
		const input = document.getElementById("editThumbnailFile");
		const hidden = document.getElementById("editThumbnail");
		const status = document.getElementById("editThumbnailStatus");
		const allowed = new Set(["image/jpeg", "image/png", "image/webp"]);
		if (!allowed.has(file.type)) {
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
		updateEditThumbnailPreview(localUrl);
		status.innerHTML =
			'<span class="text-primary"><span class="spinner-border spinner-border-sm me-1"></span>Đang tải ảnh lên...</span>';
		input.disabled = true;
		try {
			const formData = new FormData();
			formData.append("thumbnail", file);
			const result = await App.api("POST", "/upload-thumbnail", formData);
			hidden.value = result.thumbnailUrl || "";
			status.innerHTML =
				'<span class="text-success"><i class="bi bi-check-circle me-1"></i>Đã tải ảnh lên.</span>';
			updateEditThumbnailPreview();
		} catch (error) {
			hidden.value = state.post.thumbnail || "";
			status.innerHTML = `<span class="text-danger"><i class="bi bi-exclamation-circle me-1"></i>${App.escapeHtml(error.message)}</span>`;
			input.value = "";
			updateEditThumbnailPreview();
			App.toast(error.message, "danger");
		} finally {
			URL.revokeObjectURL(localUrl);
			input.disabled = false;
		}
	}

	function removeEditThumbnail() {
		document.getElementById("editThumbnail").value = "";
		document.getElementById("editThumbnailFile").value = "";
		document.getElementById("editThumbnailStatus").textContent = "";
		updateEditThumbnailPreview();
	}

	async function submitEditPost(event) {
		event.preventDefault();
		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang lưu...");
		try {
			const tagIds = [
				...document.querySelectorAll("#editTagPicker input:checked"),
			]
				.map((input) => input.value)
				.join(",");
			const result = await App.api("POST", "/posts/update", {
				postId: document.getElementById("editPostId").value,
				title: document.getElementById("editTitle").value.trim(),
				summary: document.getElementById("editSummary").value.trim(),
				content: document.getElementById("editContent").value.trim(),
				thumbnail: document.getElementById("editThumbnail").value.trim(),
				categoryId: document.getElementById("editCategory").value,
				tagIds,
				resubmit: state.post.status === "REJECTED",
			});
			App.toast(result.message, "success");
			state.editModal.hide();
			await loadPost();
		} catch (error) {
			App.toast(error.message, "danger");
		} finally {
			App.setButtonLoading(button, false);
		}
	}

	async function deletePost() {
		const confirmed = await App.confirmDialog({
			title: "Xóa bài viết",
			message: "Bài viết sẽ chuyển vào thùng rác.",
			confirmText: "Xóa bài",
			danger: true,
		});
		if (!confirmed) return;
		const result = await App.api("POST", "/posts", {
			action: "delete",
			id: state.postId,
		});
		App.flash(result.message, "success");
		window.location.href = "dashboard.html#posts";
	}

	async function restorePost() {
		const result = await App.api("POST", "/posts/restore", {
			postId: state.postId,
		});
		App.toast(result.message, "success");
		await loadPost();
	}

	async function toggleCommentLike(id, button) {
		requireLogin();
		const icon = button.querySelector("i");
		const liked = icon.classList.contains("bi-heart-fill");
		const result = await App.api(
			"POST",
			liked ? "/comment/unlike" : "/comment/like",
			{ id },
		);
		button.querySelector("[data-comment-like-count]").textContent =
			Number(result.likeCount) || 0;
		icon.className = liked ? "bi bi-heart" : "bi bi-heart-fill text-danger";
	}

	async function deleteOwnComment(id) {
		const confirmed = await App.confirmDialog({
			title: "Xóa bình luận",
			message: "Bình luận sẽ bị xóa.",
			confirmText: "Xóa",
			danger: true,
		});
		if (!confirmed) return;
		const result = await App.api("POST", "/comments/delete-by-user", {
			commentId: id,
		});
		App.toast(result.message, "success");
		await loadComments();
	}

	function handleEditorToolbar(event) {
		const button = event.target.closest("[data-editor-command]");
		if (!button) return;
		const textarea = document.getElementById(
			button.closest("[data-editor-target]").dataset.editorTarget,
		);
		const selected = textarea.value.slice(
			textarea.selectionStart,
			textarea.selectionEnd,
		);
		const command = button.dataset.editorCommand;
		const replacement =
			command === "heading"
				? `## ${selected || "Tiêu đề"}`
				: command === "bold"
					? `**${selected || "văn bản"}**`
					: command === "italic"
						? `*${selected || "văn bản"}*`
						: command === "code"
							? `\`${selected || "code"}\``
							: `- ${selected || "Mục danh sách"}`;
		textarea.setRangeText(
			replacement,
			textarea.selectionStart,
			textarea.selectionEnd,
			"end",
		);
		textarea.focus();
	}

	function updateReadingProgress() {
		const max = document.documentElement.scrollHeight - window.innerHeight;
		const percent =
			max > 0 ? Math.min(100, Math.max(0, (window.scrollY / max) * 100)) : 0;
		elements.progress.style.width = `${percent}%`;
		elements.backToTop.classList.toggle("d-none", window.scrollY < 600);
	}

	function requireLogin() {
		if (state.user) return true;
		App.flash("Vui lòng đăng nhập để thực hiện thao tác này.", "warning");
		App.redirectToLogin(
			`post-detail.html?id=${encodeURIComponent(state.postId)}`,
		);
		return false;
	}
})();
