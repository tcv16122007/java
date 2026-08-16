(() => {
	const state = {
		user: null,
		page: 1,
		limit: 9,
		categories: [],
		tags: [],
		suggestionIndex: -1,
		suggestionItems: [],
		suggestionController: null,
	};

	const elements = {};

	document.addEventListener("DOMContentLoaded", init);

	async function init() {
		cacheElements();
		bindEvents();
		elements.featuredPosts.innerHTML = App.loadingState(
			"Đang chọn bài viết nổi bật...",
		);
		elements.postList.innerHTML = skeletonCards(6);
		elements.trendingList.innerHTML =
			'<div class="text-muted py-3">Đang tải...</div>';

		state.user = await App.getCurrentUser();
		if (state.user) await App.applySettings();
		await Promise.allSettled([loadFilters(), loadFeatured(), loadTrending()]);
		await loadPosts(1);
	}

	function cacheElements() {
		Object.assign(elements, {
			featuredPosts: document.getElementById("featuredPosts"),
			categoryChips: document.getElementById("categoryChips"),
			filterCategory: document.getElementById("filterCategory"),
			filterTag: document.getElementById("filterTag"),
			filterSort: document.getElementById("filterSort"),
			filterKeyword: document.getElementById("filterKeyword"),
			filterSearchButton: document.getElementById("filterSearchButton"),
			heroSearchInput: document.getElementById("heroSearchInput"),
			suggestions: document.getElementById("searchSuggestions"),
			postList: document.getElementById("postList"),
			pagination: document.getElementById("paginationContainer"),
			resultSummary: document.getElementById("resultSummary"),
			trendingList: document.getElementById("trendingList"),
		});
	}

	function bindEvents() {
		elements.filterCategory.addEventListener("change", () => {
			syncCategoryChip(elements.filterCategory.value);
			loadPosts(1);
		});
		elements.filterTag.addEventListener("change", () => loadPosts(1));
		elements.filterSort.addEventListener("change", () => loadPosts(1));
		elements.filterSearchButton.addEventListener("click", () => loadPosts(1));
		elements.filterKeyword.addEventListener("keydown", (event) => {
			if (event.key === "Enter") loadPosts(1);
		});
		elements.categoryChips.addEventListener("click", (event) => {
			const chip = event.target.closest("[data-category-id]");
			if (!chip) return;
			elements.filterCategory.value = chip.dataset.categoryId;
			syncCategoryChip(chip.dataset.categoryId);
			loadPosts(1);
		});
		elements.pagination.addEventListener("click", (event) => {
			const button = event.target.closest("[data-page]");
			if (!button || button.disabled) return;
			loadPosts(Number(button.dataset.page));
		});
		elements.postList.addEventListener("click", handleListAction);
		elements.heroSearchInput.addEventListener(
			"input",
			App.debounce(loadSuggestions, 350),
		);
		elements.heroSearchInput.addEventListener("keydown", handleSuggestionKeys);
		elements.suggestions.addEventListener("mousemove", (event) => {
			const item = event.target.closest("[data-suggestion-index]");
			if (item) setSuggestionIndex(Number(item.dataset.suggestionIndex));
		});
		document.addEventListener("click", (event) => {
			if (!event.target.closest(".hero-search")) hideSuggestions();
		});
	}

	async function loadFilters() {
		try {
			const [categories, tags] = await Promise.all([
				App.api("GET", "/categories"),
				App.api("GET", "/tags"),
			]);
			state.categories = Array.isArray(categories) ? categories : [];
			state.tags = Array.isArray(tags) ? tags : [];
			elements.filterCategory.insertAdjacentHTML(
				"beforeend",
				state.categories
					.map(
						(category) =>
							`<option value="${Number(category.categoryId)}">${App.escapeHtml(category.categoryName)}</option>`,
					)
					.join(""),
			);
			elements.filterTag.insertAdjacentHTML(
				"beforeend",
				state.tags
					.map(
						(tag) =>
							`<option value="${Number(tag.tagId)}">#${App.escapeHtml(tag.tagName)}</option>`,
					)
					.join(""),
			);
			elements.categoryChips.innerHTML = [
				'<button type="button" class="category-chip is-active" data-category-id="">Tất cả</button>',
				...state.categories.map(
					(category) =>
						`<button type="button" class="category-chip" data-category-id="${Number(category.categoryId)}">${App.escapeHtml(category.categoryName)}</button>`,
				),
			].join("");
		} catch (error) {
			App.toast(error.message, "danger");
		}
	}

	async function loadFeatured() {
		try {
			const result = await App.api(
				"GET",
				"/posts?action=filter&sort=most_viewed&page=1&limit=3",
			);
			const posts = Array.isArray(result.posts) ? result.posts : [];
			if (!posts.length) {
				elements.featuredPosts.innerHTML = App.emptyState(
					"Chưa có bài nổi bật",
					"Bài viết được xem nhiều sẽ xuất hiện tại đây.",
				);
				return;
			}
			const [main, ...others] = posts;
			elements.featuredPosts.innerHTML = `
                <div class="featured-layout">
                    ${featuredCard(main, true)}
                    <div class="featured-stack">${others.map((post) => featuredCard(post, false)).join("")}</div>
                </div>`;
		} catch (error) {
			elements.featuredPosts.innerHTML = App.errorState(
				error.message,
				"retry-featured",
			);
			elements.featuredPosts
				.querySelector('[data-action="retry-featured"]')
				?.addEventListener("click", loadFeatured);
		}
	}

	function featuredCard(post, main) {
		const image = App.resolveAsset(post.thumbnail);
		const imageHtml = image
			? `<img class="featured-image" src="${App.escapeAttribute(image)}" alt="" loading="lazy">`
			: '<div class="featured-image post-thumb-placeholder"><i class="bi bi-journal-code"></i></div>';
		return `
            <article class="${main ? "featured-main" : "featured-small"}">
                ${imageHtml}<div class="featured-overlay"></div>
                <div class="featured-content">
                    <span class="badge text-bg-primary mb-2">${App.escapeHtml(post.categoryName || "Chung")}</span>
                    <h3 class="${main ? "display-6" : "h5"} fw-bold"><a class="text-white text-decoration-none" href="post-detail.html?id=${Number(post.postId)}">${App.escapeHtml(post.title)}</a></h3>
                    <div class="small text-white-50"><i class="bi bi-eye"></i> ${Number(post.viewCount) || 0} · ${App.formatDate(post.createdAt)}</div>
                </div>
            </article>`;
	}

	async function loadTrending() {
		try {
			const result = await App.api(
				"GET",
				"/posts?action=filter&sort=most_viewed&page=1&limit=5",
			);
			const posts = Array.isArray(result.posts) ? result.posts : [];
			elements.trendingList.innerHTML = posts.length
				? posts
						.map(
							(post) => `
                <article class="trending-item">
                    <div>
                        <a class="fw-semibold text-decoration-none" href="post-detail.html?id=${Number(post.postId)}">${App.escapeHtml(post.title)}</a>
                        <div class="small text-muted mt-1"><i class="bi bi-eye"></i> ${Number(post.viewCount) || 0}</div>
                    </div>
                </article>`,
						)
						.join("")
				: '<p class="text-muted mb-0">Chưa có dữ liệu.</p>';
		} catch (error) {
			elements.trendingList.innerHTML = `<p class="text-danger mb-0">${App.escapeHtml(error.message)}</p>`;
		}
	}

	async function loadPosts(page = 1) {
		state.page = Math.max(1, page);
		elements.postList.innerHTML = skeletonCards(6);
		elements.pagination.innerHTML = "";
		const params = new URLSearchParams({
			action: "filter",
			page: state.page,
			limit: state.limit,
			sort: elements.filterSort.value || "newest",
		});
		if (elements.filterCategory.value)
			params.set("categoryId", elements.filterCategory.value);
		if (elements.filterTag.value) params.set("tagId", elements.filterTag.value);
		if (elements.filterKeyword.value.trim())
			params.set("keyword", elements.filterKeyword.value.trim());

		try {
			const result = await App.api("GET", `/posts?${params}`);
			const posts = Array.isArray(result.posts) ? result.posts : [];
			const total = Number(result.total) || 0;
			const pages = Math.max(1, Number(result.totalPages) || 1);
			elements.resultSummary.textContent = `${total} bài viết`;
			elements.postList.innerHTML = posts.length
				? posts.map(renderPostCard).join("")
				: `<div class="col-12">${App.emptyState("Không tìm thấy bài viết", "Hãy thử thay đổi bộ lọc hoặc từ khóa.", "bi-search")}</div>`;
			renderPagination(state.page, pages);
		} catch (error) {
			elements.resultSummary.textContent = "";
			elements.postList.innerHTML = `<div class="col-12">${App.errorState(error.message, "retry-posts")}</div>`;
		}
	}

	function renderPostCard(post) {
		const thumbnail = App.resolveAsset(post.thumbnail);
		const tags = Array.isArray(post.tags) ? post.tags.slice(0, 3) : [];
		const image = thumbnail
			? `<img class="post-thumb" src="${App.escapeAttribute(thumbnail)}" alt="${App.escapeAttribute(post.title)}" loading="lazy">`
			: '<div class="post-thumb-placeholder"><i class="bi bi-file-earmark-code"></i></div>';
		return `
            <div class="col-md-6 col-lg-4">
                <article class="app-card post-card">
                    <a href="post-detail.html?id=${Number(post.postId)}">${image}</a>
                    <div class="p-4 d-flex flex-column h-100">
                        <div class="d-flex flex-wrap gap-2 mb-2">
                            <span class="badge text-bg-primary">${App.escapeHtml(post.categoryName || "Chung")}</span>
                            ${tags.map((tag) => `<span class="badge text-bg-light text-primary">#${App.escapeHtml(tag.tagName)}</span>`).join("")}
                        </div>
                        <h3 class="h5 post-card-title"><a class="text-decoration-none" href="post-detail.html?id=${Number(post.postId)}">${App.escapeHtml(post.title)}</a></h3>
                        <p class="text-muted post-card-summary">${App.escapeHtml(post.summary || "Bài viết chưa có phần tóm tắt.")}</p>
                        <div class="post-meta mt-auto"><span><i class="bi bi-heart"></i> ${Number(post.likeCount) || 0}</span><span><i class="bi bi-chat"></i> ${Number(post.commentCount) || 0}</span><span><i class="bi bi-eye"></i> ${Number(post.viewCount) || 0}</span></div>
                    </div>
                    <footer class="px-4 py-3 border-top d-flex justify-content-between gap-2 small">
                        <a class="text-muted text-decoration-none text-truncate" href="author.html?id=${Number(post.authorId)}"><i class="bi bi-person-circle"></i> ${App.escapeHtml(post.authorName || "Ẩn danh")}</a>
                        <span class="text-muted flex-shrink-0">${App.formatDate(post.createdAt)}</span>
                    </footer>
                </article>
            </div>`;
	}

	function renderPagination(current, total) {
		if (total <= 1) return;
		const start = Math.max(1, current - 2);
		const end = Math.min(total, current + 2);
		const buttons = [];
		buttons.push(pageButton("«", current - 1, current <= 1));
		if (start > 1) buttons.push(pageButton("1", 1, false, current === 1));
		if (start > 2)
			buttons.push(
				'<li class="page-item disabled"><span class="page-link">…</span></li>',
			);
		for (let number = start; number <= end; number++)
			buttons.push(
				pageButton(String(number), number, false, number === current),
			);
		if (end < total - 1)
			buttons.push(
				'<li class="page-item disabled"><span class="page-link">…</span></li>',
			);
		if (end < total)
			buttons.push(pageButton(String(total), total, false, current === total));
		buttons.push(pageButton("»", current + 1, current >= total));
		elements.pagination.innerHTML = `<ul class="pagination flex-wrap">${buttons.join("")}</ul>`;
	}

	function pageButton(label, page, disabled = false, active = false) {
		return `<li class="page-item ${disabled ? "disabled" : ""} ${active ? "active" : ""}"><button class="page-link" type="button" data-page="${page}" ${disabled ? "disabled" : ""}>${label}</button></li>`;
	}

	function syncCategoryChip(value) {
		elements.categoryChips
			.querySelectorAll("[data-category-id]")
			.forEach((chip) => {
				chip.classList.toggle(
					"is-active",
					chip.dataset.categoryId === String(value),
				);
			});
	}

	async function loadSuggestions() {
		const keyword = elements.heroSearchInput.value.trim();
		if (keyword.length < 2) {
			hideSuggestions();
			return;
		}
		state.suggestionController?.abort();
		state.suggestionController = new AbortController();
		elements.suggestions.classList.remove("d-none");
		elements.suggestions.innerHTML =
			'<div class="p-3 text-muted">Đang tìm...</div>';
		try {
			const params = new URLSearchParams({
				action: "filter",
				keyword,
				page: 1,
				limit: 5,
			});
			const result = await App.api("GET", `/posts?${params}`, null, {
				signal: state.suggestionController.signal,
			});
			state.suggestionItems = Array.isArray(result.posts) ? result.posts : [];
			state.suggestionIndex = -1;
			elements.suggestions.innerHTML = state.suggestionItems.length
				? state.suggestionItems
						.map(
							(post, index) =>
								`<a class="suggestion-item" role="option" data-suggestion-index="${index}" href="post-detail.html?id=${Number(post.postId)}"><strong>${App.escapeHtml(post.title)}</strong><div class="small text-muted">${App.escapeHtml(post.categoryName || "Chung")} · ${App.escapeHtml(post.authorName || "Ẩn danh")}</div></a>`,
						)
						.join("") +
					`<button class="suggestion-item w-100 text-start border-0" type="button" data-suggestion-all>Xem tất cả kết quả cho “${App.escapeHtml(keyword)}”</button>`
				: '<div class="p-3 text-muted">Không tìm thấy bài viết phù hợp.</div>';
			elements.suggestions
				.querySelector("[data-suggestion-all]")
				?.addEventListener("click", () => {
					elements.filterKeyword.value = keyword;
					hideSuggestions();
					document.getElementById("latestPosts").scrollIntoView();
					loadPosts(1);
				});
		} catch (error) {
			if (error.name !== "AbortError")
				elements.suggestions.innerHTML = `<div class="p-3 text-danger">${App.escapeHtml(error.message)}</div>`;
		}
	}

	function handleSuggestionKeys(event) {
		if (elements.suggestions.classList.contains("d-none")) return;
		if (event.key === "ArrowDown") {
			event.preventDefault();
			setSuggestionIndex(
				Math.min(state.suggestionItems.length - 1, state.suggestionIndex + 1),
			);
		} else if (event.key === "ArrowUp") {
			event.preventDefault();
			setSuggestionIndex(Math.max(0, state.suggestionIndex - 1));
		} else if (event.key === "Enter" && state.suggestionIndex >= 0) {
			event.preventDefault();
			window.location.href = `post-detail.html?id=${Number(state.suggestionItems[state.suggestionIndex].postId)}`;
		} else if (event.key === "Enter") {
			elements.filterKeyword.value = elements.heroSearchInput.value.trim();
			hideSuggestions();
			document.getElementById("latestPosts").scrollIntoView();
			loadPosts(1);
		} else if (event.key === "Escape") hideSuggestions();
	}

	function setSuggestionIndex(index) {
		state.suggestionIndex = index;
		elements.suggestions
			.querySelectorAll("[data-suggestion-index]")
			.forEach((item) => {
				item.classList.toggle(
					"is-active",
					Number(item.dataset.suggestionIndex) === index,
				);
			});
	}

	function hideSuggestions() {
		elements.suggestions.classList.add("d-none");
		state.suggestionIndex = -1;
	}

	function handleListAction(event) {
		const retry = event.target.closest('[data-action="retry-posts"]');
		if (retry) loadPosts(state.page);
	}

	function skeletonCards(count) {
		return Array.from(
			{ length: count },
			() => `
            <div class="col-md-6 col-lg-4"><div class="app-card overflow-hidden"><div class="skeleton post-thumb"></div><div class="p-4"><div class="skeleton skeleton-label rounded mb-3"></div><div class="skeleton skeleton-title rounded mb-2"></div><div class="skeleton skeleton-line rounded mb-2"></div><div class="skeleton skeleton-line skeleton-line-short rounded"></div></div></div></div>`,
		).join("");
	}
})();
