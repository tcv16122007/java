(() => {
	const form = document.getElementById("loginForm");
	const message = document.getElementById("formMessage");
	const params = new URLSearchParams(location.search);

	function show(text, type = "danger") {
		message.textContent = text;
		message.className = `alert alert-${type}`;
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
		const username = document.getElementById("username").value.trim();
		const password = document.getElementById("password").value;
		if (username.length < 3 || !password)
			return show("Vui lòng nhập đúng tên đăng nhập và mật khẩu.");
		const button = event.submitter;
		App.setButtonLoading(button, true, "Đang đăng nhập...");
		try {
			const result = await App.api("POST", "/login", { username, password });
			if (!result.success)
				throw new Error(result.message || "Đăng nhập thất bại");
			App.flash("Đăng nhập thành công.", "success");
			const requested = App.safeRedirectTarget(
				params.get("redirect"),
				"index.html",
			);
			window.location.replace(requested);
		} catch (error) {
			show(error.message);
		} finally {
			App.setButtonLoading(button, false);
		}
	}
	document.addEventListener("click", toggle);
	form.addEventListener("submit", submit);
})();
