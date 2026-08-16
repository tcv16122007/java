(() => {
	const form = document.getElementById("forgotForm");
	const message = document.getElementById("formMessage");

	function showMessage(text, type = "info") {
		message.textContent = text;
		message.className = `alert alert-${type}`;
	}

	form.addEventListener("submit", async (event) => {
		event.preventDefault();
		const username = document.getElementById("username").value.trim();
		const email = document.getElementById("email").value.trim();
		if (username.length < 3 || !/^\S+@\S+\.\S+$/.test(email)) {
			showMessage("Vui lòng nhập tên đăng nhập và email hợp lệ.", "warning");
			return;
		}

		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang gửi...");
		try {
			const result = await App.api("POST", "/forgot-password", {
				username,
				email,
			});
			showMessage(
				result.message || "Nếu thông tin khớp, liên kết đã được gửi.",
				"success",
			);
			if (result.devResetUrl) {
				const panel = document.getElementById("devResetPanel");
				const link = document.getElementById("devResetLink");
				link.href = result.devResetUrl;
				panel.classList.remove("d-none");
			}
		} catch (error) {
			showMessage(error.message, "danger");
		} finally {
			App.setButtonLoading(button, false);
		}
	});
})();
