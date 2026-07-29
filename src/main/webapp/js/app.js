// ====== API HELPER (có timestamp chống cache) ======
async function callApi(method, endpoint, data = null) {
    const opts = {
        method,
        headers: {},
        credentials: 'include'
    };
    // Thêm timestamp để tránh cache
    const timestamp = Date.now();
    const separator = endpoint.includes('?') ? '&' : '?';
    const url = endpoint + separator + '_t=' + timestamp;

    if (data) {
        opts.headers['Content-Type'] = 'application/x-www-form-urlencoded';
        opts.body = new URLSearchParams(data);
    }
    const contextPath = window.location.pathname.split('/')[1] || '';
    const base = contextPath ? '/' + contextPath + '/api' : '/api';
    const res = await fetch(base + url, opts);
    return res.json();
}

// ====== TOAST ======
function showToast(message, type = 'success') {
    const container = document.getElementById('toastContainer') || (() => {
        const div = document.createElement('div');
        div.id = 'toastContainer';
        div.className = 'toast-container';
        document.body.appendChild(div);
        return div;
    })();
    const colors = {
        success: 'bg-success text-white',
        danger: 'bg-danger text-white',
        warning: 'bg-warning text-dark',
        info: 'bg-info text-white'
    };
    const toast = document.createElement('div');
    toast.className = `toast align-items-center ${colors[type] || 'bg-secondary text-white'} border-0 show`;
    toast.innerHTML = `
        <div class="d-flex">
            <div class="toast-body">${message}</div>
            <button class="btn-close btn-close-white me-2 m-auto" onclick="this.parentElement.parentElement.remove()"></button>
        </div>
    `;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 4000);
}

// ====== THEME TOGGLE ======
function toggleTheme() {
    const isDark = document.body.classList.toggle('dark-theme');
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
}

function loadTheme() {
    const theme = localStorage.getItem('theme');
    if (theme === 'dark') document.body.classList.add('dark-theme');
}

// ====== APPLY GLOBAL SETTINGS (chỉ set body background, text color, primary) ======
async function applyGlobalSettings() {
    try {
        const res = await callApi('GET', '/settings');
        if (res && !res.error) {
            const root = document.documentElement;
            // Chỉ set các biến liên quan đến body, không ảnh hưởng đến card/input
            root.style.setProperty('--primary-color', res.primaryColor || '#667eea');
            root.style.setProperty('--bg-body', res.backgroundColor || '#f4f6f9');
            root.style.setProperty('--text-color', res.textColor || '#1a1a2e');
            document.body.style.fontFamily = res.fontFamily || 'system-ui';
            // Custom CSS
            const styleEl = document.getElementById('customGlobalStyle') || (() => {
                const el = document.createElement('style');
                el.id = 'customGlobalStyle';
                document.head.appendChild(el);
                return el;
            })();
            styleEl.textContent = res.customCss || '';
        }
    } catch (e) { console.warn('Apply settings error', e); }
}

// ====== GLOBAL UI UPDATE ======
function updateUI(user) {
    console.log('updateUI called with user:', user);
    const userInfo = document.getElementById('userInfo');
    const avatarImg = document.getElementById('avatarImg');
    const avatarImgBig = document.getElementById('avatarImgBig');
    const loginBtn = document.getElementById('loginBtn');
    const registerBtn = document.getElementById('registerBtn');
    const logoutBtn = document.getElementById('logoutBtn');
    const dashboardBtn = document.getElementById('dashboardBtn');

    if (user) {
        if (userInfo) {
            userInfo.textContent = '👤 ' + user.fullName;
            userInfo.classList.remove('d-none');
        }
        if (loginBtn) loginBtn.classList.add('d-none');
        if (registerBtn) registerBtn.classList.add('d-none');
        if (logoutBtn) logoutBtn.classList.remove('d-none');
        if (dashboardBtn) dashboardBtn.classList.remove('d-none');

        if (user.avatar) {
            const url = '/java' + user.avatar + '?t=' + Date.now();
            if (avatarImg) {
                avatarImg.src = url;
                avatarImg.style.display = 'inline';
            }
            if (avatarImgBig) {
                avatarImgBig.src = url;
            }
        } else {
            if (avatarImg) avatarImg.style.display = 'none';
        }
    } else {
        if (userInfo) {
            userInfo.textContent = '';
            userInfo.classList.add('d-none');
        }
        if (loginBtn) loginBtn.classList.remove('d-none');
        if (registerBtn) registerBtn.classList.remove('d-none');
        if (logoutBtn) logoutBtn.classList.add('d-none');
        if (dashboardBtn) dashboardBtn.classList.add('d-none');
        if (avatarImg) avatarImg.style.display = 'none';
    }
}

// ====== FORMAT DATE ======
function formatDate(dateStr) {
    if (!dateStr) return '';
    try {
        const d = new Date(dateStr);
        return d.toLocaleDateString('vi-VN');
    } catch { return dateStr.slice(0, 10); }
}

// ====== GET STATUS BADGE ======
function getStatusBadge(status) {
    const map = {
        'APPROVED': 'success',
        'PENDING': 'warning',
        'REJECTED': 'danger',
        'DRAFT': 'secondary',
        'DELETED': 'dark',
        'ACTIVE': 'success',
        'BLOCKED': 'danger',
        'RESTRICTED': 'warning',
        'VISIBLE': 'success',
        'HIDDEN': 'warning',
        'DELETED': 'danger'
    };
    return `<span class="badge bg-${map[status] || 'secondary'}">${status}</span>`;
}