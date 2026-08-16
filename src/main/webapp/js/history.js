(() => {
	let items = [];
	const list = document.getElementById("historyList");
	const searchInput = document.getElementById("historySearch");
	const sortSelect = document.getElementById("historySort");

	function render() {
		const keyword = searchInput.value.trim().toLowerCase();
		const sort = sortSelect.value;
		const filtered = items.filter((item) =>
			[item.title, item.authorName, item.categoryName]
				.join(" ")
				.toLowerCase()
				.includes(keyword),
		);

		filtered.sort((left, right) => {
			if (sort === "oldest")
				return new Date(left.viewedAt) - new Date(right.viewedAt);
			if (sort === "title")
				return String(left.title || "").localeCompare(
					String(right.title || ""),
					"vi",
				);
			return new Date(right.viewedAt) - new Date(left.viewedAt);
		});

		if (!filtered.length) {
			list.innerHTML = App.emptyState(
				keyword ? "Không tìm thấy" : "Chưa có lịch sử",
				keyword ? "Không tìm thấy bài phù hợp." : "Bạn chưa đọc bài viết nào.",
				"bi-clock-history",
			);
			return;
		}

		list.innerHTML = filtered
			.map((post) => {
				const postId = Number(post.postId) || 0;
				const image = App.resolveAsset(post.thumbnail);
				return `
                <article class="app-card history-card">
                    ${
											image
												? `<img class="history-thumb" src="${App.escapeAttribute(image)}" alt="">`
												: '<div class="post-thumb-placeholder history-thumb"><i class="bi bi-journal-text"></i></div>'
										}
                    <div>
                        <span class="badge bg-primary mb-2">${App.escapeHtml(post.categoryName || "Bài viết")}</span>
                        <h2 class="h5">
                            <a class="text-decoration-none text-body" href="post-detail.html?id=${postId}">${App.escapeHtml(post.title || "Bài viết")}</a>
                        </h2>
                        <p class="text-muted mb-2">${App.escapeHtml(post.summary || "")}</p>
                        <div class="post-meta">
                            <span><i class="bi bi-person"></i> ${App.escapeHtml(post.authorName || "")}</span>
                            <span><i class="bi bi-clock-history"></i> Xem ${App.relativeTime(post.viewedAt)}</span>
                        </div>
                    </div>
                    <a class="btn btn-outline-primary align-self-center" href="post-detail.html?id=${postId}">Đọc lại</a>
                </article>`;
			})
			.join("");
	}

	async function initialize() {
		await App.applySettings();
		const user = await App.getCurrentUser();
		if (!user) {
			App.flash("Vui lòng đăng nhập để xem lịch sử.", "warning");
			App.redirectToLogin("history.html", true);
			return;
		}
		if (user.role !== "USER") {
			window.location.replace("dashboard.html");
			return;
		}

		list.innerHTML = App.loadingState("Đang tải lịch sử...");
		try {
			const rows = await App.api("GET", "/history/posts");
			items = (Array.isArray(rows) ? rows : []).slice(0, 50);
			render();
		} catch (error) {
			list.innerHTML = App.errorState(error.message, "reload-history");
		}
	}

	searchInput.addEventListener("input", App.debounce(render, 250));
	sortSelect.addEventListener("change", render);
	document.addEventListener("click", (event) => {
		if (event.target.closest('[data-action="reload-history"]')) initialize();
	});
	document.addEventListener("DOMContentLoaded", initialize);
})();
