(() => {
	const authorId = Number(
		new URLSearchParams(window.location.search).get("id"),
	);
	const pageSize = 9;
	let currentPage = 1;
	let author = null;

	const stateBox = document.getElementById("authorState");
	const content = document.getElementById("authorContent");
	const postsContainer = document.getElementById("authorPosts");
	const pagination = document.getElementById("authorPagination");

	function renderPostCard(post) {
		const postId = Number(post.postId) || 0;
		const title = App.escapeHtml(post.title || "Không có tiêu đề");
		const image = App.resolveAsset(post.thumbnail);

		return `
            <div class="col-md-6 col-xl-4">
                <article class="app-card post-card">
                    <a href="post-detail.html?id=${postId}">
                        ${
													image
														? `<img class="post-thumb" src="${App.escapeAttribute(image)}" alt="${App.escapeAttribute(title)}">`
														: '<div class="post-thumb-placeholder"><i class="bi bi-journal-code"></i></div>'
												}
                    </a>
                    <div class="p-3">
                        <span class="badge bg-primary mb-2">${App.escapeHtml(post.categoryName || "Chung")}</span>
                        <h3 class="h5 post-card-title">
                            <a class="text-decoration-none text-body" href="post-detail.html?id=${postId}">${title}</a>
                        </h3>
                        <p class="post-card-summary text-muted">${App.escapeHtml(post.summary || "")}</p>
                        <div class="post-meta">
                            <span><i class="bi bi-eye"></i> ${Number(post.viewCount) || 0}</span>
                            <span><i class="bi bi-heart"></i> ${Number(post.likeCount) || 0}</span>
                            <span>${App.formatDate(post.createdAt)}</span>
                        </div>
                    </div>
                </article>
            </div>`;
	}

	function renderPagination(totalPages) {
		if (totalPages <= 1) {
			pagination.innerHTML = "";
			return;
		}

		const start = Math.max(1, currentPage - 2);
		const end = Math.min(totalPages, currentPage + 2);
		let html = '<ul class="pagination">';
		for (let page = start; page <= end; page += 1) {
			html += `
                <li class="page-item ${page === currentPage ? "active" : ""}">
                    <button class="page-link" type="button" data-page="${page}">${page}</button>
                </li>`;
		}
		pagination.innerHTML = `${html}</ul>`;
	}

	async function loadPosts() {
		postsContainer.innerHTML = `<div class="col-12">${App.loadingState("Đang tải bài viết...")}</div>`;
		try {
			const sort = document.getElementById("authorSort").value;
			const result = await App.api(
				"GET",
				`/posts?action=filter&authorId=${authorId}&sort=${encodeURIComponent(sort)}&page=${currentPage}&limit=${pageSize}`,
			);
			const posts = Array.isArray(result.posts) ? result.posts : [];
			postsContainer.innerHTML = posts.length
				? posts.map(renderPostCard).join("")
				: `<div class="col-12">${App.emptyState("Chưa có bài viết", "Tác giả chưa có bài viết công khai.", "bi-journal-x")}</div>`;
			renderPagination(Number(result.totalPages) || 1);
		} catch (error) {
			postsContainer.innerHTML = `<div class="col-12">${App.errorState(error.message, "load-author-posts")}</div>`;
		}
	}

	async function initialize() {
		const currentUser = await App.getCurrentUser();
		if (currentUser) await App.applySettings();
		if (!authorId) {
			window.location.replace("404.html");
			return;
		}

		stateBox.innerHTML = App.loadingState("Đang tải hồ sơ tác giả...");
		try {
			author = await App.api("GET", `/authors?id=${authorId}`);
			document.getElementById("authorAvatar").src = App.resolveAvatar(
				author,
				256,
			);
			document.getElementById("authorName").textContent =
				author.fullName || author.username || "Tác giả";
			document.getElementById("authorUsername").textContent =
				`@${author.username || "author"} · Tham gia ${App.formatDate(author.createdAt)}`;

			const stats = author.stats || {};
			document.getElementById("authorStats").innerHTML = [
				["Bài viết", stats.postCount],
				["Lượt xem", stats.viewCount],
				["Lượt thích", stats.likeCount],
				["Bình luận", stats.commentCount],
			]
				.map(
					([label, value]) => `
                <div><strong>${Number(value) || 0}</strong><span>${App.escapeHtml(label)}</span></div>`,
				)
				.join("");

			stateBox.innerHTML = "";
			content.classList.remove("d-none");
			await loadPosts();
		} catch (error) {
			if (error.status === 404) {
				window.location.replace("404.html");
				return;
			}
			stateBox.innerHTML = App.errorState(error.message, "reload-author");
		}
	}

	document.addEventListener("click", async (event) => {
		const pageButton = event.target.closest("[data-page]");
		if (pageButton) {
			currentPage = Number(pageButton.dataset.page) || 1;
			await loadPosts();
			window.scrollTo({ top: 0, behavior: "smooth" });
			return;
		}
		if (event.target.closest('[data-action="reload-author"]'))
			await initialize();
		if (event.target.closest('[data-action="load-author-posts"]'))
			await loadPosts();
	});

	document.getElementById("authorSort").addEventListener("change", async () => {
		currentPage = 1;
		await loadPosts();
	});

	document
		.getElementById("shareAuthorButton")
		.addEventListener("click", async () => {
			if (!author) return;
			try {
				if (navigator.share) {
					await navigator.share({
						title: author.fullName || author.username,
						url: window.location.href,
					});
				} else {
					await navigator.clipboard.writeText(window.location.href);
					App.toast("Đã sao chép liên kết hồ sơ.", "success");
				}
			} catch (error) {
				if (error.name !== "AbortError")
					App.toast("Không thể chia sẻ hồ sơ.", "warning");
			}
		});

	document.addEventListener("DOMContentLoaded", initialize);
})();
