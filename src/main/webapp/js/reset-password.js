(() => {
	const token = new URLSearchParams(window.location.search).get("token") || "";
	const form = document.getElementById("resetForm");
	const message = document.getElementById("formMessage");
	const status = document.getElementById("tokenStatus");
	const password = document.getElementById("newPassword");

	function showMessage(text, type = "danger") {
		message.textContent = text;
		message.className = `alert alert-${type}`;
	}

	function passwordScore(value) {
		let score = 0;
		if (value.length >= 8) score += 1;
		if (value.length >= 12) score += 1;
		if (/[A-Z]/.test(value) && /[a-z]/.test(value)) score += 1;
		if (/\d/.test(value) && /[^\w]/.test(value)) score += 1;
		return Math.min(score, 4);
	}

	function updateStrength() {
		const score = passwordScore(password.value);
		document.getElementById("strengthBar").className = `strength-${score}`;
		document.getElementById("strengthText").textContent = [
			"Từ 8–72 ký tự.",
			"Yếu",
			"Trung bình",
			"Khá",
			"Mạnh",
		][score];
	}

	function togglePassword(event) {
		const button = event.target.closest("[data-password-toggle]");
		if (!button) return;
		const input = document.getElementById(button.dataset.passwordToggle);
		input.type = input.type === "password" ? "text" : "password";
		button.querySelector("i").className =
			input.type === "password" ? "bi bi-eye" : "bi bi-eye-slash";
	}

	async function validateToken() {
		if (!token) {
			status.textContent = "Liên kết không hợp lệ.";
			return;
		}
		try {
			const result = await App.api(
				"GET",
				`/reset-password/validate?token=${encodeURIComponent(token)}`,
			);
			if (!result.valid)
				throw new Error("Liên kết đã hết hạn hoặc đã được sử dụng.");
			status.textContent = "Liên kết hợp lệ. Hãy tạo mật khẩu mới.";
			form.classList.remove("d-none");
		} catch (error) {
			status.textContent = error.message;
			showMessage(error.message, "danger");
		}
	}

	form.addEventListener("submit", async (event) => {
		event.preventDefault();
		const confirmation = document.getElementById("confirmPassword").value;
		if (password.value.length < 8 || password.value.length > 72) {
			showMessage("Mật khẩu cần từ 8 đến 72 ký tự.", "warning");
			return;
		}
		if (password.value !== confirmation) {
			showMessage("Mật khẩu xác nhận không khớp.", "warning");
			return;
		}

		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang đổi...");
		try {
			const result = await App.api("POST", "/reset-password", {
				token,
				newPassword: password.value,
			});
			if (!result.success)
				throw new Error(result.message || "Đặt lại thất bại");
			showMessage(
				"Đổi mật khẩu thành công. Đang chuyển tới đăng nhập...",
				"success",
			);
			form.reset();
			window.setTimeout(() => {
				window.location.href = "login.html";
			}, 1300);
		} catch (error) {
			showMessage(error.message);
		} finally {
			App.setButtonLoading(button, false);
		}
	});

	password.addEventListener("input", updateStrength);
	document.addEventListener("click", togglePassword);
	validateToken();
})();
