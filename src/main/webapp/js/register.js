(() => {
	const message = document.getElementById("formMessage");
	const password = document.getElementById("password");
	const strengthBar = document.getElementById("strengthBar");
	const strengthText = document.getElementById("strengthText");
	function show(text, type = "danger") {
		message.textContent = text;
		message.className = `alert alert-${type}`;
	}
	function score(value) {
		let n = 0;
		if (value.length >= 8) n++;
		if (value.length >= 12) n++;
		if (/[A-Z]/.test(value) && /[a-z]/.test(value)) n++;
		if (/\d/.test(value) && /[^\w]/.test(value)) n++;
		return Math.min(n, 4);
	}
	function updateStrength() {
		const n = score(password.value);
		strengthBar.className = `strength-${n}`;
		strengthText.textContent = [
			"Từ 8–72 ký tự.",
			"Yếu",
			"Trung bình",
			"Khá",
			"Mạnh",
		][n];
	}
	function toggle(event) {
		const button = event.target.closest("[data-password-toggle]");
		if (!button) return;
		const input = document.getElementById(button.dataset.passwordToggle);
		input.type = input.type === "password" ? "text" : "password";
		button.querySelector("i").className =
			input.type === "password" ? "bi bi-eye" : "bi bi-eye-slash";
	}
	async function submit(event) {
		event.preventDefault();
		message.classList.add("d-none");
		const fullName = document.getElementById("fullName").value.trim();
		const username = document.getElementById("username").value.trim();
		const email = document.getElementById("email").value.trim();
		const confirm = document.getElementById("confirmPassword").value;
		if (fullName.length < 2) return show("Họ tên phải có ít nhất 2 ký tự.");
		if (!/^[A-Za-z0-9_.-]{3,50}$/.test(username))
			return show("Tên đăng nhập cần 3–50 ký tự, không có khoảng trắng.");
		if (!/^\S+@\S+\.\S+$/.test(email)) return show("Email không hợp lệ.");
		if (password.value.length < 8 || password.value.length > 72)
			return show("Mật khẩu cần từ 8 đến 72 ký tự.");
		if (password.value !== confirm)
			return show("Mật khẩu xác nhận không khớp.");
		if (!document.getElementById("terms").checked)
			return show("Vui lòng đồng ý quy định cộng đồng.");
		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang tạo...");
		try {
			const result = await App.api("POST", "/register", {
				fullName,
				username,
				email,
				password: password.value,
			});
			if (!result.success)
				throw new Error(result.message || "Đăng ký thất bại");
			show("Đăng ký thành công. Bạn có thể đăng nhập ngay.", "success");
			setTimeout(() => (location.href = "login.html"), 1200);
		} catch (error) {
			show(error.message);
		} finally {
			App.setButtonLoading(button, false);
		}
	}
	password.addEventListener("input", updateStrength);
	document.addEventListener("click", toggle);
	document.getElementById("registerForm").addEventListener("submit", submit);
})();
