const App = (() => {
	let notificationTimer = null;
	let accountStatusTimer = null;
	let forcedLogoutInProgress = false;
	const toastTimers = new WeakMap();

	function contextPath() {
		const first = window.location.pathname.split("/").filter(Boolean)[0] || "";
		return first && !first.includes(".") ? `/${first}` : "";
	}

	function apiBase() {
		return `${contextPath()}/api`;
	}

	async function api(method, endpoint, data = null, options = {}) {
		const fetchOptions = {
			method,
			credentials: "include",
			headers: { Accept: "application/json" },
			signal: options.signal,
		};
		let url = `${apiBase()}${endpoint}`;
		if (method === "GET") {
			url += `${url.includes("?") ? "&" : "?"}_t=${Date.now()}`;
		}
		if (data instanceof FormData) {
			fetchOptions.body = data;
		} else if (data !== null && data !== undefined) {
			fetchOptions.headers["Content-Type"] =
				"application/x-www-form-urlencoded;charset=UTF-8";
			fetchOptions.body = new URLSearchParams(data).toString();
		}

		let response;
		try {
			response = await fetch(url, fetchOptions);
		} catch (error) {
			if (error.name === "AbortError") throw error;
			throw new Error("Không thể kết nối đến máy chủ.");
		}
		const raw = await response.text();
		let payload = {};
		if (raw) {
			try {
				payload = JSON.parse(raw);
			} catch {
				throw new Error("Máy chủ trả về dữ liệu không hợp lệ.");
			}
		}
		if (!response.ok) {
			const error = new Error(
				payload.message || payload.error || `Lỗi HTTP ${response.status}`,
			);
			error.status = response.status;
			error.payload = payload;
			if (
				response.status === 401 &&
				endpoint !== "/login" &&
				!options.skipAuthRedirect
			) {
				forceLogout(
					payload.message ||
						"Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.",
				);
			}
			throw error;
		}
		return payload;
	}

	function escapeHtml(value) {
		if (value === null || value === undefined) return "";
		return String(value)
			.replaceAll("&", "&amp;")
			.replaceAll("<", "&lt;")
			.replaceAll(">", "&gt;")
			.replaceAll('"', "&quot;")
			.replaceAll("'", "&#039;");
	}

	function escapeAttribute(value) {
		return escapeHtml(value)
			.replaceAll("`", "&#096;")
			.replaceAll("=", "&#061;");
	}

	function safeUrl(value, allowRelative = true) {
		if (!value) return "";
		try {
			const url = new URL(String(value), window.location.origin);
			if (!["http:", "https:"].includes(url.protocol)) return "";
			if (
				!allowRelative &&
				url.origin === window.location.origin &&
				!String(value).startsWith("http")
			)
				return "";
			return url.href;
		} catch {
			return "";
		}
	}

	function resolveAsset(value) {
		if (!value) return "";
		const text = String(value).trim();
		if (text.startsWith("/uploads/")) return `${contextPath()}${text}`;
		return safeUrl(text);
	}

	function slugify(value, index = 0) {
		const slug = String(value || "")
			.normalize("NFD")
			.replace(/[\u0300-\u036f]/g, "")
			.toLowerCase()
			.replace(/[^a-z0-9]+/g, "-")
			.replace(/(^-|-$)/g, "");
		return slug || `section-${index + 1}`;
	}

	function inlineMarkdown(text) {
		let output = escapeHtml(text);
		output = output.replace(/`([^`]+)`/g, "<code>$1</code>");
		output = output.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
		output = output.replace(/__([^_]+)__/g, "<strong>$1</strong>");
		output = output.replace(/\*([^*]+)\*/g, "<em>$1</em>");
		output = output.replace(/~~([^~]+)~~/g, "<del>$1</del>");
		output = output.replace(/\+\+([^+]+)\+\+/g, "<u>$1</u>");
		output = output.replace(
			/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
			(_all, label, url) => {
				const safe = safeUrl(url, false);
				return safe
					? `<a href="${escapeAttribute(safe)}" target="_blank" rel="noopener noreferrer">${label}</a>`
					: label;
			},
		);
		output = output.replace(
			/(^|\s)(https?:\/\/[^\s<]+)/g,
			(_all, prefix, url) => {
				const safe = safeUrl(url, false);
				return safe
					? `${prefix}<a href="${escapeAttribute(safe)}" target="_blank" rel="noopener noreferrer">${escapeHtml(url)}</a>`
					: `${prefix}${escapeHtml(url)}`;
			},
		);
		return output;
	}

	function youtubeEmbedUrl(value) {
		try {
			const url = new URL(String(value), window.location.origin);
			const host = url.hostname.replace(/^www\./, "").toLowerCase();
			let videoId = "";
			if (host === "youtu.be")
				videoId = url.pathname.split("/").filter(Boolean)[0] || "";
			if (host === "youtube.com" || host === "m.youtube.com") {
				if (url.pathname === "/watch")
					videoId = url.searchParams.get("v") || "";
				else if (url.pathname.startsWith("/embed/"))
					videoId = url.pathname.split("/")[2] || "";
			}
			return /^[A-Za-z0-9_-]{6,20}$/.test(videoId)
				? `https://www.youtube.com/embed/${videoId}`
				: "";
		} catch {
			return "";
		}
	}

	function renderMediaLine(line) {
		const trimmed = String(line || "").trim();
		const imageMarkdown = /^!\[([^\]]*)\]\(([^)]+)\)$/.exec(trimmed);
		const candidate = imageMarkdown ? imageMarkdown[2].trim() : trimmed;
		const resolved = resolveAsset(candidate);
		if (!resolved) return "";

		const embed = youtubeEmbedUrl(resolved);
		if (embed) {
			return `<div class="article-media ratio ratio-16x9"><iframe src="${escapeAttribute(embed)}" title="Video YouTube" loading="lazy" allowfullscreen></iframe></div>`;
		}

		const pathname = (() => {
			try {
				return new URL(resolved).pathname.toLowerCase();
			} catch {
				return "";
			}
		})();
		if (/\.(?:jpg|jpeg|png|gif|webp)$/.test(pathname)) {
			const alt = imageMarkdown?.[1] || "Ảnh trong bài viết";
			return `<figure class="article-media"><img src="${escapeAttribute(resolved)}" alt="${escapeAttribute(alt)}" loading="lazy"></figure>`;
		}
		if (/\.(?:mp4|webm)$/.test(pathname)) {
			return `<div class="article-media"><video controls preload="metadata"><source src="${escapeAttribute(resolved)}">Trình duyệt không hỗ trợ video.</video></div>`;
		}
		return "";
	}

	function renderMarkdown(source) {
		const lines = String(source || "")
			.replace(/\r/g, "")
			.split("\n");
		const html = [];
		const headings = [];
		let inCode = false;
		let codeLines = [];
		let listType = null;
		const closeList = () => {
			if (listType) html.push(`</${listType}>`);
			listType = null;
		};

		lines.forEach((line, lineIndex) => {
			if (line.trim().startsWith("```")) {
				closeList();
				if (inCode) {
					html.push(
						`<pre><code>${escapeHtml(codeLines.join("\n"))}</code></pre>`,
					);
					codeLines = [];
				}
				inCode = !inCode;
				return;
			}
			if (inCode) {
				codeLines.push(line);
				return;
			}

			const media = renderMediaLine(line);
			if (media) {
				closeList();
				html.push(media);
				return;
			}

			const heading = /^(#{2,3})\s+(.+)$/.exec(line.trim());
			if (heading) {
				closeList();
				const level = heading[1].length;
				const title = heading[2].trim();
				const id = `${slugify(title, headings.length)}-${headings.length + 1}`;
				headings.push({ level, title, id });
				html.push(
					`<h${level} id="${escapeAttribute(id)}">${inlineMarkdown(title)}</h${level}>`,
				);
				return;
			}

			const bullet = /^\s*[-*]\s+(.+)$/.exec(line);
			const ordered = /^\s*\d+[.)]\s+(.+)$/.exec(line);
			if (bullet || ordered) {
				const desired = bullet ? "ul" : "ol";
				if (listType !== desired) {
					closeList();
					listType = desired;
					html.push(`<${listType}>`);
				}
				html.push(`<li>${inlineMarkdown((bullet || ordered)[1])}</li>`);
				return;
			}

			closeList();
			if (!line.trim()) return;
			if (line.trim().startsWith(">")) {
				html.push(
					`<blockquote>${inlineMarkdown(line.trim().replace(/^>\s?/, ""))}</blockquote>`,
				);
			} else {
				html.push(`<p>${inlineMarkdown(line)}</p>`);
			}
		});
		if (inCode)
			html.push(`<pre><code>${escapeHtml(codeLines.join("\n"))}</code></pre>`);
		closeList();
		return { html: html.join("\n"), headings };
	}

	function formatDate(value) {
		if (!value) return "";
		const date = new Date(String(value).replace(" ", "T"));
		if (Number.isNaN(date.getTime())) return String(value).slice(0, 10);
		return date.toLocaleDateString("vi-VN");
	}

	function formatDateTime(value) {
		if (!value) return "";
		const date = new Date(String(value).replace(" ", "T"));
		if (Number.isNaN(date.getTime())) return String(value);
		return date.toLocaleString("vi-VN", {
			dateStyle: "short",
			timeStyle: "short",
		});
	}

	function relativeTime(value) {
		const date = new Date(String(value || "").replace(" ", "T"));
		if (Number.isNaN(date.getTime())) return formatDate(value);
		const seconds = Math.floor((Date.now() - date.getTime()) / 1000);
		if (seconds < 60) return "Vừa xong";
		if (seconds < 3600) return `${Math.floor(seconds / 60)} phút trước`;
		if (seconds < 86400) return `${Math.floor(seconds / 3600)} giờ trước`;
		if (seconds < 604800) return `${Math.floor(seconds / 86400)} ngày trước`;
		return formatDate(value);
	}

	function statusBadge(status) {
		const map = {
			APPROVED: ["success", "Đã duyệt"],
			PENDING: ["warning", "Chờ duyệt"],
			REJECTED: ["danger", "Bị từ chối"],
			DRAFT: ["secondary", "Bản nháp"],
			DELETED: ["dark", "Đã xóa"],
			ACTIVE: ["success", "Hoạt động"],
			BLOCKED: ["danger", "Đã khóa"],
			RESTRICTED: ["warning", "Hạn chế"],
			VISIBLE: ["success", "Hiển thị"],
			HIDDEN: ["warning", "Đã ẩn"],
			PROCESSING: ["info", "Đang xử lý"],
			RESOLVED: ["success", "Đã xử lý"],
		};
		const [color, label] = map[status] || ["secondary", status || "Không rõ"];
		return `<span class="badge bg-${color}">${escapeHtml(label)}</span>`;
	}

	function toast(message, type = "success", options = {}) {
		let container = document.getElementById("toastContainer");
		if (!container) {
			container = document.createElement("div");
			container.id = "toastContainer";
			container.className = "app-toast-container";
			container.setAttribute("aria-live", "polite");
			document.body.appendChild(container);
		}

		const config = {
			success: ["bi-check-circle-fill", "Thành công"],
			danger: ["bi-x-circle-fill", "Có lỗi"],
			warning: ["bi-exclamation-triangle-fill", "Lưu ý"],
			info: ["bi-info-circle-fill", "Thông báo"],
		}[type] || ["bi-bell-fill", "Thông báo"];

		const title = options.title || config[1];
		const duration = Number(options.duration) || 4500;
		const toastKey = String(options.key || `${type}|${title}|${message || ""}`);
		const existing = Array.from(container.children).find(
			(item) => item.dataset.toastKey === toastKey,
		);

		const restartTimer = (element) => {
			const oldTimer = toastTimers.get(element);
			if (oldTimer) window.clearTimeout(oldTimer);

			const progress = element.querySelector(".app-toast-progress");
			if (progress) {
				progress.style.animation = "none";
				void progress.offsetWidth;
				progress.style.animation = "";
				progress.style.animationDuration = `${duration}ms`;
			}

			const timer = window.setTimeout(() => {
				toastTimers.delete(element);
				element.remove();
			}, duration);
			toastTimers.set(element, timer);
		};

		// Không tạo thêm nhiều thông báo giống hệt nhau khi người dùng bấm liên tục.
		if (existing) {
			restartTimer(existing);
			return existing;
		}

		const element = document.createElement("div");
		element.className = `app-toast app-toast-${type}`;
		element.dataset.toastKey = toastKey;
		element.innerHTML = `
            <div><i class="bi ${config[0]}"></i></div>
            <div><strong>${escapeHtml(title)}</strong><div>${escapeHtml(message || "")}</div></div>
            <button type="button" class="app-toast-close" aria-label="Đóng"><i class="bi bi-x-lg"></i></button>
            <div class="app-toast-progress"></div>`;

		const remove = () => {
			const timer = toastTimers.get(element);
			if (timer) window.clearTimeout(timer);
			toastTimers.delete(element);
			element.remove();
		};

		element.querySelector(".app-toast-close").addEventListener("click", remove);
		container.appendChild(element);
		restartTimer(element);
		return element;
	}

	function flash(message, type = "success") {
		sessionStorage.setItem("blogSeFlash", JSON.stringify({ message, type }));
	}

	function showFlash() {
		const raw = sessionStorage.getItem("blogSeFlash");
		if (!raw) return;
		sessionStorage.removeItem("blogSeFlash");
		try {
			const value = JSON.parse(raw);
			toast(value.message, value.type);
		} catch {
			/* ignore malformed flash */
		}
	}

	function setButtonLoading(button, loading, label = "Đang xử lý...") {
		if (!button) return;
		if (loading) {
			button.dataset.originalHtml = button.innerHTML;
			button.disabled = true;
			button.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span>${escapeHtml(label)}`;
		} else {
			button.disabled = false;
			if (button.dataset.originalHtml)
				button.innerHTML = button.dataset.originalHtml;
			delete button.dataset.originalHtml;
		}
	}

	function loadingState(message = "Đang tải dữ liệu...") {
		return `<div class="state-box"><div><div class="spinner-border text-primary"></div><p class="mt-3 mb-0 text-muted">${escapeHtml(message)}</p></div></div>`;
	}

	function emptyState(title, message, icon = "bi-inbox") {
		return `<div class="state-box"><div><i class="bi ${icon} state-icon"></i><h5 class="mt-3">${escapeHtml(title)}</h5><p class="text-muted mb-0">${escapeHtml(message)}</p></div></div>`;
	}

	function errorState(message, retryAction = "") {
		const button = retryAction
			? `<button type="button" class="btn btn-outline-danger mt-3" data-action="${escapeAttribute(retryAction)}"><i class="bi bi-arrow-clockwise"></i> Thử lại</button>`
			: "";
		return `<div class="state-box"><div><i class="bi bi-exclamation-triangle state-icon text-danger"></i><h5 class="mt-3">Không thể tải dữ liệu</h5><p class="text-muted mb-0">${escapeHtml(message)}</p>${button}</div></div>`;
	}

	function debounce(fn, wait = 350) {
		let timer;
		return (...args) => {
			window.clearTimeout(timer);
			timer = window.setTimeout(() => fn(...args), wait);
		};
	}

	function resolveAvatar(user, size = 64) {
		const avatar = String(user?.avatar || "").trim();
		if (avatar) {
			if (avatar.startsWith("/uploads/")) {
				return `${contextPath()}${avatar}?v=${Date.now()}`;
			}
			const safe = safeUrl(avatar);
			if (safe) return safe;
		}
		const name = encodeURIComponent(user?.fullName || user?.username || "User");
		return `https://ui-avatars.com/api/?name=${name}&background=667eea&color=fff&size=${size}`;
	}

	function safeInternalHref(value, fallback = "#") {
		if (!value) return fallback;
		try {
			const url = new URL(String(value), window.location.href);
			if (url.origin !== window.location.origin) return fallback;
			const appRoot = `${window.location.origin}${contextPath()}/`;
			if (!url.href.startsWith(appRoot)) return fallback;
			return `${url.pathname}${url.search}${url.hash}`;
		} catch {
			return fallback;
		}
	}

	function currentPageTarget() {
		const root = `${contextPath()}/`;
		let path = window.location.pathname;
		if (path.startsWith(root)) path = path.slice(root.length);
		else path = path.split("/").filter(Boolean).pop() || "index.html";
		if (!path || path.endsWith("/")) path = "index.html";
		return `${path}${window.location.search}${window.location.hash}`;
	}

	function safeRedirectTarget(value, fallback = "index.html") {
		const text = String(value || "").trim();
		if (!text) return fallback;
		// Chỉ cho phép điều hướng tới một trang HTML nội bộ trong chính ứng dụng.
		if (!/^[a-z0-9_-]+\.html(?:[?#].*)?$/i.test(text)) return fallback;
		if (/^login\.html(?:[?#].*)?$/i.test(text)) return fallback;
		return text;
	}

	function loginUrl(redirect = currentPageTarget()) {
		return `login.html?redirect=${encodeURIComponent(safeRedirectTarget(redirect, "index.html"))}`;
	}

	function redirectToLogin(
		redirect = currentPageTarget(),
		replaceHistory = false,
	) {
		const target = loginUrl(redirect);
		if (replaceHistory) window.location.replace(target);
		else window.location.assign(target);
	}

	function applyThemeIcon() {
		document
			.querySelectorAll('[data-action="toggle-theme"] i')
			.forEach((icon) => {
				icon.className = document.body.classList.contains("dark-theme")
					? "bi bi-sun-fill"
					: "bi bi-moon-stars";
			});
	}

	function toggleTheme() {
		const dark = document.body.classList.toggle("dark-theme");
		localStorage.setItem("theme", dark ? "dark" : "light");
		applyThemeIcon();
	}

	function loadTheme() {
		document.body.classList.toggle(
			"dark-theme",
			localStorage.getItem("theme") === "dark",
		);
		applyThemeIcon();
	}

	function hexToRgb(value, fallback) {
		const match = /^#([0-9a-f]{6})$/i.exec(value || "");
		if (!match) return fallback;
		const number = Number.parseInt(match[1], 16);
		return `${(number >> 16) & 255}, ${(number >> 8) & 255}, ${number & 255}`;
	}

	function applySettingsValues(settings = {}) {
		const root = document.documentElement;
		const dark = settings.theme === "dark";
		const primary = settings.primaryColor || "#667eea";
		const secondary = settings.secondaryColor || "#764ba2";
		const background =
			settings.backgroundColor || (dark ? "#111827" : "#f5f7fb");
		const text = settings.textColor || (dark ? "#f4f6ff" : "#172033");

		root.style.setProperty("--primary-color", primary);
		root.style.setProperty("--secondary-color", secondary);
		root.style.setProperty("--primary-rgb", hexToRgb(primary, "102, 126, 234"));
		root.style.setProperty(
			"--secondary-rgb",
			hexToRgb(secondary, "118, 75, 162"),
		);
		root.style.setProperty("--bg-body", background);
		root.style.setProperty("--text-color", text);
		document.body.style.fontFamily = settings.fontFamily || "system-ui";
		document.body.classList.toggle("dark-theme", dark);
		localStorage.setItem("theme", dark ? "dark" : "light");
		applyThemeIcon();

		// Không tự thay màu chữ theo độ tương phản. Người dùng chọn màu nào thì giữ màu đó.
		return { ...settings, backgroundColor: background, textColor: text };
	}

	async function applySettings() {
		try {
			const settings = await api("GET", "/settings");
			return applySettingsValues(settings);
		} catch (error) {
			if (error.status !== 401)
				console.warn("Không thể áp dụng cài đặt:", error);
			return null;
		}
	}

	function updateUserUI(user) {
		document.querySelectorAll("[data-user-name]").forEach((el) => {
			el.textContent = user ? user.fullName || user.username : "";
			el.classList.toggle("d-none", !user);
		});
		document.querySelectorAll("[data-user-avatar]").forEach((el) => {
			if (user) el.src = resolveAvatar(user, 96);
			el.classList.toggle("d-none", !user);
		});
		document
			.querySelectorAll('[data-auth="guest"]')
			.forEach((el) => el.classList.toggle("d-none", Boolean(user)));
		document
			.querySelectorAll('[data-auth="user"]')
			.forEach((el) => el.classList.toggle("d-none", !user));
		document.querySelectorAll("[data-role]").forEach((el) => {
			const rule = el.dataset.role;
			const visible =
				Boolean(user) &&
				((rule === "USER" && user.role === "USER") ||
					(rule === "MOD" && ["MODERATOR", "ADMIN"].includes(user.role)) ||
					(rule === "ADMIN" && user.role === "ADMIN"));
			el.classList.toggle("d-none", !visible);
		});
		if (user) {
			setupNotifications();
			startAccountStatusWatch();
		} else {
			stopNotifications();
			stopAccountStatusWatch();
		}
	}

	function forceLogout(message) {
		if (
			forcedLogoutInProgress ||
			/(?:^|\/)login\.html$/i.test(window.location.pathname)
		)
			return;
		forcedLogoutInProgress = true;
		stopNotifications();
		stopAccountStatusWatch();
		flash(
			message || "Tài khoản không còn hoạt động. Bạn đã được đăng xuất.",
			"warning",
		);
		window.location.replace(loginUrl(currentPageTarget()));
	}

	function startAccountStatusWatch() {
		if (accountStatusTimer) return;
		accountStatusTimer = window.setInterval(async () => {
			try {
				const response = await api("GET", "/current-user", null, {
					skipAuthRedirect: true,
				});
				if (!response.success) {
					if (response.forcedLogout) forceLogout(response.message);
					else
						forceLogout("Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại.");
				}
			} catch (error) {
				if (error.status === 401) forceLogout(error.message);
			}
		}, 5000);
	}

	function stopAccountStatusWatch() {
		if (accountStatusTimer) window.clearInterval(accountStatusTimer);
		accountStatusTimer = null;
	}

	async function getCurrentUser() {
		try {
			const response = await api("GET", "/current-user", null, {
				skipAuthRedirect: true,
			});
			if (!response.success && response.forcedLogout) {
				forceLogout(response.message);
				return null;
			}
			const user = response.success ? response.user : null;
			updateUserUI(user);
			return user;
		} catch {
			updateUserUI(null);
			return null;
		}
	}

	function setupNotifications() {
		const actions = document.querySelector("[data-navbar-actions]");
		if (!actions) return;
		let center = document.getElementById("notificationCenter");
		if (!center) {
			center = document.createElement("div");
			center.id = "notificationCenter";
			center.className = "dropdown notification-center";
			center.innerHTML = `
                <button class="btn btn-outline-light notification-button" type="button" data-bs-toggle="dropdown" aria-expanded="false" aria-label="Thông báo">
                    <i class="bi bi-bell"></i><span id="notificationBadge" class="notification-badge d-none">0</span>
                </button>
                <div class="dropdown-menu dropdown-menu-end notification-menu">
                    <div class="notification-header"><strong>Thông báo</strong><button class="btn btn-link btn-sm p-0" type="button" data-action="notifications-read-all">Đọc tất cả</button></div>
                    <div id="notificationList" class="notification-list"></div>
                    <div class="p-2 text-center"><a class="btn btn-sm btn-outline-primary" href="dashboard.html#notifications">Xem tất cả</a></div>
                </div>`;
			const theme = actions.querySelector('[data-action="toggle-theme"]');
			actions.insertBefore(center, theme || actions.firstChild);
			center.addEventListener("show.bs.dropdown", loadNotifications);
			center.addEventListener("click", handleNotificationClick);
		}
		loadNotifications();
		if (!notificationTimer)
			notificationTimer = window.setInterval(loadNotifications, 60000);
	}

	function stopNotifications() {
		document.getElementById("notificationCenter")?.remove();
		if (notificationTimer) window.clearInterval(notificationTimer);
		notificationTimer = null;
	}

	async function loadNotifications() {
		const list = document.getElementById("notificationList");
		const badge = document.getElementById("notificationBadge");
		if (!list || !badge) return;
		try {
			const data = await api("GET", "/notifications?limit=20");
			const items = Array.isArray(data.items) ? data.items : [];
			const unread = Number(data.unreadCount) || 0;
			badge.textContent = unread > 99 ? "99+" : String(unread);
			badge.classList.toggle("d-none", unread === 0);
			list.innerHTML = items.length
				? items
						.map(
							(item) => `
                <a class="notification-item ${item.read ? "" : "is-unread"}" href="${escapeAttribute(safeInternalHref(item.link, "dashboard.html#notifications"))}" data-notification-id="${Number(item.notificationId) || 0}">
                    <div class="notification-title">${escapeHtml(item.title)}</div>
                    <div class="small mt-1">${escapeHtml(item.message)}</div>
                    <div class="notification-time mt-1">${escapeHtml(relativeTime(item.createdAt))}</div>
                </a>`,
						)
						.join("")
				: `<div class="p-4 text-center text-muted">Chưa có thông báo.</div>`;
		} catch (error) {
			list.innerHTML = `<div class="p-4 text-center text-danger">${escapeHtml(error.message)}</div>`;
		}
	}

	async function handleNotificationClick(event) {
		const action = event.target.closest("[data-action]")?.dataset.action;
		if (action === "notifications-read-all") {
			event.preventDefault();
			await api("POST", "/notifications", { action: "readAll" });
			await loadNotifications();
			return;
		}
		const item = event.target.closest("[data-notification-id]");
		if (!item) return;
		event.preventDefault();
		const id = Number(item.dataset.notificationId);
		const href = item.getAttribute("href");
		try {
			await api("POST", "/notifications", {
				action: "read",
				notificationId: id,
			});
		} finally {
			window.location.href =
				href && href !== "#" ? href : "dashboard.html#notifications";
		}
	}

	async function logout() {
		try {
			await api("POST", "/logout");
		} finally {
			localStorage.removeItem("theme");
			flash("Bạn đã đăng xuất.", "info");
			window.location.href = "index.html";
		}
	}

	function bindGlobalEvents() {
		document.addEventListener("click", (event) => {
			const target = event.target.closest("[data-action]");
			if (!target) return;
			if (target.dataset.action === "toggle-theme") {
				event.preventDefault();
				toggleTheme();
			} else if (target.dataset.action === "logout") {
				event.preventDefault();
				logout();
			}
		});
	}

	function confirmDialog({
		title = "Xác nhận",
		message = "Bạn có chắc chắn?",
		confirmText = "Xác nhận",
		danger = false,
	} = {}) {
		return new Promise((resolve) => {
			let modal = document.getElementById("appConfirmModal");
			if (!modal) {
				modal = document.createElement("div");
				modal.id = "appConfirmModal";
				modal.className = "modal fade";
				modal.tabIndex = -1;
				modal.innerHTML = `<div class="modal-dialog modal-dialog-centered"><div class="modal-content"><div class="modal-header"><h5 class="modal-title"></h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div><div class="modal-body"></div><div class="modal-footer"><button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Hủy</button><button type="button" class="btn" data-confirm></button></div></div></div>`;
				document.body.appendChild(modal);
			}
			modal.querySelector(".modal-title").textContent = title;
			modal.querySelector(".modal-body").textContent = message;
			const confirmButton = modal.querySelector("[data-confirm]");
			confirmButton.textContent = confirmText;
			confirmButton.className = `btn ${danger ? "btn-danger" : "btn-primary"}`;
			const instance = bootstrap.Modal.getOrCreateInstance(modal);
			const cleanup = (value) => {
				confirmButton.removeEventListener("click", onConfirm);
				modal.removeEventListener("hidden.bs.modal", onHidden);
				resolve(value);
			};
			const onConfirm = () => {
				instance.hide();
				cleanup(true);
			};
			const onHidden = () => cleanup(false);
			confirmButton.addEventListener("click", onConfirm, { once: true });
			modal.addEventListener("hidden.bs.modal", onHidden, { once: true });
			instance.show();
		});
	}

	document.addEventListener("DOMContentLoaded", () => {
		loadTheme();
		showFlash();
		bindGlobalEvents();
	});

	return {
		api,
		contextPath,
		escapeHtml,
		escapeAttribute,
		safeUrl,
		safeInternalHref,
		currentPageTarget,
		safeRedirectTarget,
		loginUrl,
		redirectToLogin,
		resolveAsset,
		renderMarkdown,
		formatDate,
		formatDateTime,
		relativeTime,
		statusBadge,
		toast,
		flash,
		setButtonLoading,
		loadingState,
		emptyState,
		errorState,
		debounce,
		resolveAvatar,
		applySettings,
		applySettingsValues,
		updateUserUI,
		getCurrentUser,
		confirmDialog,
		loadNotifications,
	};
})();

window.App = App;
