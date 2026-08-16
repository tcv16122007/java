(() => {
	const defaults = {
		theme: "light",
		primaryColor: "#667eea",
		secondaryColor: "#764ba2",
		backgroundColor: "#f5f7fb",
		textColor: "#172033",
		fontFamily: "system-ui",
		coverImage: "",
	};
	let user = null;
	let settings = { ...defaults };
	let settingsSaving = false;
	const $ = (id) => document.getElementById(id);

	function clearPasswords() {
		["oldPassword", "newPassword", "confirmPassword"].forEach((id) => {
			if ($(id)) $(id).value = "";
		});
	}

	function protectPasswordFields() {
		clearPasswords();
		["oldPassword", "newPassword", "confirmPassword"].forEach((id) => {
			const input = $(id);
			const unlock = () => {
				input.readOnly = false;
				input.value = "";
			};
			input.addEventListener("focus", unlock, { once: true });
			input.addEventListener("pointerdown", unlock, { once: true });
		});
		setTimeout(clearPasswords, 80);
		setTimeout(clearPasswords, 450);
	}

	function populateUser() {
		$("profileName").textContent = user.fullName || user.username;
		$("profileMeta").textContent =
			`${user.role || "USER"} · Tham gia ${App.formatDate(user.createdAt) || "gần đây"}`;
		$("profileAvatar").src = App.resolveAvatar(user, 256);
		$("usernameDisplay").value = user.username || "";
		$("roleDisplay").value = user.role || "USER";
		$("fullName").value = user.fullName || "";
		$("email").value = user.email || "";
		clearPasswords();
	}

	function populateSettings() {
		[
			"primaryColor",
			"secondaryColor",
			"backgroundColor",
			"textColor",
			"fontFamily",
		].forEach((key) => {
			if ($(key)) $(key).value = settings[key] || defaults[key];
		});
		$("themeMode").value = settings.theme === "dark" ? "dark" : "light";
		previewSettings();
	}

	function previewSettings() {
		const preview = $("themePreview");
		const primary = $("primaryColor").value;
		const secondary = $("secondaryColor").value;
		const background = $("backgroundColor").value;
		const text = $("textColor").value;
		preview.style.setProperty("--preview-primary", primary);
		preview.style.setProperty("--preview-secondary", secondary);
		preview.style.setProperty("--preview-background", background);
		preview.style.setProperty("--preview-text", text);
		preview.style.fontFamily = $("fontFamily").value;
	}

	async function saveSettings(event) {
		event.preventDefault();
		if (settingsSaving) return;

		const button =
			event.submitter ||
			$("settingsForm").querySelector('button[type="submit"]');
		const nextSettings = {
			theme: $("themeMode").value,
			primaryColor: $("primaryColor").value,
			secondaryColor: $("secondaryColor").value,
			backgroundColor: $("backgroundColor").value,
			textColor: $("textColor").value,
			fontFamily: $("fontFamily").value,
			coverImage: "",
		};

		settingsSaving = true;
		App.setButtonLoading(button, true, "Đang lưu...");
		try {
			const result = await App.api("POST", "/settings", nextSettings);
			if (!result.success)
				throw new Error(result.message || "Không thể lưu giao diện");
			settings = { ...nextSettings };
			App.applySettingsValues(nextSettings);
			populateSettings();
		} catch (error) {
			console.error("Không thể lưu giao diện:", error);
		} finally {
			settingsSaving = false;
			App.setButtonLoading(button, false);
		}
	}

	async function resetTheme() {
		const confirmed = await App.confirmDialog({
			title: "Khôi phục giao diện",
			message: "Đưa toàn bộ màu sắc và phông chữ về mặc định?",
			confirmText: "Khôi phục",
		});
		if (!confirmed) return;
		settings = { ...defaults };
		populateSettings();
		$("settingsForm").requestSubmit();
	}

	async function saveProfile(event) {
		event.preventDefault();
		const fullName = $("fullName").value.trim();
		const email = $("email").value.trim();
		if (fullName.length < 2)
			return App.toast("Họ tên phải có ít nhất 2 ký tự.", "warning");
		if (!/^\S+@\S+\.\S+$/.test(email))
			return App.toast("Email không hợp lệ.", "warning");
		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang lưu...");
		try {
			const result = await App.api("POST", "/users", {
				action: "updateProfile",
				fullName,
				email,
			});
			if (!result.success)
				throw new Error(result.message || "Cập nhật thất bại");
			user = { ...user, fullName, email };
			populateUser();
			App.updateUserUI(user);
			App.toast("Đã cập nhật thông tin cá nhân.", "success");
		} catch (error) {
			App.toast(error.message, "danger");
		} finally {
			App.setButtonLoading(button, false);
		}
	}

	function passwordScore(value) {
		let score = 0;
		if (value.length >= 8) score++;
		if (value.length >= 12) score++;
		if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score++;
		if (/\d/.test(value)) score++;
		if (/[^A-Za-z0-9]/.test(value)) score++;
		return Math.min(score, 4);
	}

	function updateStrength() {
		const value = $("newPassword").value;
		const score = passwordScore(value);
		const labels = [
			"Chưa nhập mật khẩu mới.",
			"Yếu",
			"Trung bình",
			"Khá",
			"Mạnh",
		];
		$("passwordStrengthBar").className = `strength-${score}`;
		$("passwordStrengthText").textContent = labels[score];
	}

	async function changePassword(event) {
		event.preventDefault();
		const form = event.currentTarget;
		const oldPassword = $("oldPassword").value;
		const newPassword = $("newPassword").value;
		const confirmPassword = $("confirmPassword").value;
		if (!oldPassword)
			return App.toast("Vui lòng nhập mật khẩu hiện tại.", "warning");
		if (newPassword.length < 8 || newPassword.length > 72)
			return App.toast("Mật khẩu mới cần từ 8 đến 72 ký tự.", "warning");
		if (newPassword !== confirmPassword)
			return App.toast("Mật khẩu xác nhận không khớp.", "warning");
		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang đổi...");
		try {
			const result = await App.api("POST", "/users", {
				action: "updateProfile",
				fullName: user.fullName,
				email: user.email,
				oldPassword,
				newPassword,
			});
			if (!result.success)
				throw new Error(result.message || "Đổi mật khẩu thất bại");
			App.toast("Đã đổi mật khẩu.", "success");
			form.reset();
			protectPasswordFields();
			updateStrength();
		} catch (error) {
			App.toast(error.message, "danger");
		} finally {
			App.setButtonLoading(button, false);
		}
	}

	function previewAvatar() {
		const file = $("avatarFile").files?.[0];
		const preview = $("avatarPreview");
		if (!file) {
			preview.classList.add("d-none");
			return;
		}
		if (
			!["image/jpeg", "image/png", "image/webp"].includes(file.type) ||
			file.size > 5 * 1024 * 1024
		) {
			$("avatarFile").value = "";
			preview.classList.add("d-none");
			App.toast("Chỉ chọn ảnh JPG, PNG hoặc WebP không quá 5 MB.", "warning");
			return;
		}
		preview.src = URL.createObjectURL(file);
		preview.classList.remove("d-none");
	}

	async function uploadAvatar(event) {
		event.preventDefault();
		const file = $("avatarFile").files?.[0];
		if (!file) return App.toast("Vui lòng chọn ảnh.", "warning");
		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang tải...");
		try {
			const form = new FormData();
			form.append("avatar", file);
			const result = await App.api("POST", "/upload-avatar", form);
			if (!result.success)
				throw new Error(result.message || "Tải ảnh thất bại");
			user.avatar = result.avatarUrl;
			$("profileAvatar").src = App.resolveAvatar(user, 256);
			App.updateUserUI(user);
			$("avatarFile").value = "";
			$("avatarPreview").classList.add("d-none");
			App.toast("Đã cập nhật ảnh đại diện.", "success");
		} catch (error) {
			App.toast(error.message, "danger");
		} finally {
			App.setButtonLoading(button, false);
		}
	}

	function togglePassword(event) {
		const button = event.target.closest("[data-toggle-password]");
		if (!button) return;
		const input = $(button.dataset.togglePassword);
		input.readOnly = false;
		input.type = input.type === "password" ? "text" : "password";
		button.querySelector("i").className =
			input.type === "password" ? "bi bi-eye" : "bi bi-eye-slash";
	}

	async function init() {
		protectPasswordFields();
		user = await App.getCurrentUser();
		if (!user) {
			App.flash("Vui lòng đăng nhập để xem hồ sơ.", "warning");
			App.redirectToLogin("profile.html", true);
			return;
		}
		populateUser();
		try {
			settings = { ...defaults, ...(await App.api("GET", "/settings")) };
			settings = { ...settings, ...App.applySettingsValues(settings) };
			populateSettings();
		} catch (error) {
			console.error("Không thể tải giao diện cá nhân:", error);
		}
		$("avatarFile").addEventListener("change", previewAvatar);
		$("avatarForm").addEventListener("submit", uploadAvatar);
		$("profileForm").addEventListener("submit", saveProfile);
		$("passwordForm").addEventListener("submit", changePassword);
		$("settingsForm").addEventListener("submit", saveSettings);
		$("resetThemeButton").addEventListener("click", resetTheme);
		$("newPassword").addEventListener("input", updateStrength);
		document.addEventListener("click", togglePassword);
		[
			"primaryColor",
			"secondaryColor",
			"backgroundColor",
			"textColor",
			"fontFamily",
		].forEach((id) => $(id).addEventListener("input", previewSettings));
	}

	document.addEventListener("DOMContentLoaded", init);
	window.addEventListener("pageshow", clearPasswords);
})();
