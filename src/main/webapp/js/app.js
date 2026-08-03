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

// ====== TOAST (hiển thị trên cùng, giữa màn hình) ======
function showToast(message, type = 'success') {
    // Tạo container nếu chưa có
    const container = document.getElementById('toastContainer') || (() => {
        const div = document.createElement('div');
        div.id = 'toastContainer';
        div.className = 'toast-container';
        // Đưa lên trên cùng, giữa màn hình
        div.style.position = 'fixed';
        div.style.top = '20px';
        div.style.left = '50%';
        div.style.transform = 'translateX(-50%)';
        div.style.zIndex = '9999';
        div.style.display = 'flex';
        div.style.flexDirection = 'column';
        div.style.alignItems = 'center';
        div.style.gap = '8px';
        div.style.width = 'auto';
        div.style.maxWidth = '90%';
        div.style.pointerEvents = 'none'; // để click xuyên qua container
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
    toast.style.pointerEvents = 'auto'; // cho phép click vào toast
    toast.style.minWidth = '200px';
    toast.style.maxWidth = '500px';
    toast.style.width = 'auto';
    toast.style.boxShadow = '0 8px 30px rgba(0,0,0,0.2)';
    toast.style.borderRadius = '12px';
    toast.style.margin = '0 auto';
    toast.innerHTML = `
        <div class="d-flex align-items-center p-2">
            <div class="toast-body fw-semibold text-center flex-grow-1">${message}</div>
            <button type="button" class="btn-close btn-close-white me-2" onclick="this.parentElement.parentElement.remove()" style="flex-shrink:0;"></button>
        </div>
    `;
    container.appendChild(toast);

    // Tự động xóa sau 4 giây
    setTimeout(() => {
        if (toast.parentElement) toast.remove();
    }, 4000);
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

// ====== ESCAPE HTML (chống XSS) ======
function escapeHtml(text) {
    if (!text) return '';
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, function (m) { return map[m]; });
}

// ====== BACKGROUND EFFECTS ======
function initBackgroundEffects() {
    if (document.getElementById('bgLayer')) return; // đã tồn tại

    const bgLayer = document.createElement('div');
    bgLayer.id = 'bgLayer';
    bgLayer.className = 'bg-layer';

    // Thêm các gradient orb (3 quả cầu)
    for (let i = 1; i <= 3; i++) {
        const orb = document.createElement('div');
        orb.className = 'gradient-orb';
        bgLayer.appendChild(orb);
    }

    // Grid lines
    const grid = document.createElement('div');
    grid.className = 'grid-lines';
    bgLayer.appendChild(grid);

    // Tạo sparkle (hạt sáng)
    const sparklePositions = [
        { left: '10%', top: '20%', duration: 18, delay: 0 },
        { left: '80%', top: '60%', duration: 22, delay: -4 },
        { left: '30%', top: '80%', duration: 20, delay: -8 },
        { left: '60%', top: '10%', duration: 25, delay: -12 },
        { left: '90%', top: '30%', duration: 16, delay: -2 },
        { left: '20%', top: '70%', duration: 24, delay: -6 },
        { left: '50%', top: '40%', duration: 19, delay: -10 },
        { left: '70%', top: '90%', duration: 21, delay: -14 }
    ];

    sparklePositions.forEach((pos, i) => {
        const sparkle = document.createElement('div');
        sparkle.className = 'sparkle';
        sparkle.style.left = pos.left;
        sparkle.style.top = pos.top;
        sparkle.style.animationDuration = pos.duration + 's';
        sparkle.style.animationDelay = pos.delay + 's';
        sparkle.style.setProperty('--i', i);
        bgLayer.appendChild(sparkle);
    });

    // Light sweeps (2 vệt)
    for (let i = 9; i <= 10; i++) {
        const sweep = document.createElement('div');
        sweep.className = 'light-sweep';
        bgLayer.appendChild(sweep);
    }

    document.body.prepend(bgLayer);
}

document.addEventListener('DOMContentLoaded', function () {
    initBackgroundEffects();
    loadTheme();
    // Các khởi tạo khác có thể thêm vào đây nếu cần
});