/* Student Profile System - Giai doan 4
 * - Render API data with textContent/createElement only (XSS-safe output encoding).
 * - All protected API calls use the API Gateway and the current Bearer token.
 * - Student updates use PATCH for contact fields; STAFF/ADMIN use POST/PUT.
 * - Document files are uploaded as multipart/form-data and downloaded via authenticated fetch.
 */

const API_BASE_URL = (window.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const AUTH_API = `${API_BASE_URL}/api/v1/auth`;
const PROFILE_API = `${API_BASE_URL}/api/v1/profiles`;
const DOCUMENT_API = `${API_BASE_URL}/api/v1/documents`;
const AUDIT_API = `${API_BASE_URL}/api/v1/audits`;
const MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;
const ALLOWED_DOCUMENT_EXTENSIONS = new Set(['pdf', 'png', 'jpg', 'jpeg']);
const GENDER_LABELS = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' };

let currentToken = null;
let currentUser = null;
let editingStudentId = null;
let searchDebounceTimer = null;

class ApiError extends Error {
    constructor(message, status = 0, payload = null) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.payload = payload;
    }
}

function byId(id) {
    const element = document.getElementById(id);
    if (!element) throw new Error(`Không tìm thấy phần tử giao diện: ${id}`);
    return element;
}

function authHeaders(extra = {}) {
    const headers = { ...extra };
    if (currentToken) headers.Authorization = `Bearer ${currentToken}`;
    return headers;
}

async function readResponseBody(response) {
    const raw = await response.text();
    if (!raw) return {};
    try {
        return JSON.parse(raw);
    } catch {
        return { message: raw.slice(0, 500) };
    }
}

async function apiFetch(url, options = {}) {
    const response = await fetch(url, options);
    const data = await readResponseBody(response);
    if (!response.ok) {
        const message = data.message || data.error || `Yêu cầu thất bại (HTTP ${response.status}).`;
        throw new ApiError(message, response.status, data);
    }
    return { response, data };
}

function errorMessage(error) {
    if (error instanceof ApiError) return `[HTTP ${error.status}] ${error.message}`;
    if (error instanceof TypeError) return 'Không thể kết nối máy chủ. Hãy kiểm tra API Gateway và kết nối mạng.';
    return error?.message || 'Đã xảy ra lỗi không xác định.';
}

function showGlobalAlert(message, isError = false) {
    const box = byId('global-alert');
    box.style.display = 'block';
    box.className = isError ? 'alert-box toast-alert error' : 'alert-box toast-alert success';
    box.textContent = String(message ?? '');
    window.clearTimeout(showGlobalAlert.timeoutId);
    showGlobalAlert.timeoutId = window.setTimeout(() => { box.style.display = 'none'; }, 6000);
}

function showInlineAlert(boxId, message, isError = false) {
    const box = byId(boxId);
    box.style.display = 'block';
    box.className = isError ? 'alert-box error' : 'alert-box success';
    box.textContent = String(message ?? '');
}

function applyTheme(theme) {
    const validTheme = theme === 'light' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', validTheme);
    const btn = document.getElementById('theme-toggle-btn');
    if (btn) {
        btn.textContent = validTheme === 'light' ? '☀️ Sáng' : '🌙 Tối';
    }
    try {
        localStorage.setItem('sps_theme', validTheme);
    } catch {
        // Bỏ qua nếu trình duyệt chặn localStorage
    }
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme') || 'dark';
    applyTheme(current === 'dark' ? 'light' : 'dark');
}

function addCell(row, value, options = {}) {
    const cell = document.createElement('td');
    const content = value == null ? '' : String(value);
    if (options.strong) {
        const strong = document.createElement('strong');
        strong.textContent = content;
        cell.appendChild(strong);
    } else {
        cell.textContent = content;
    }
    if (options.small) {
        const small = document.createElement('small');
        small.textContent = String(options.small);
        small.className = 'cell-subtext';
        cell.appendChild(document.createElement('br'));
        cell.appendChild(small);
    }
    row.appendChild(cell);
    return cell;
}

function addTableMessage(tbody, colSpan, message) {
    tbody.replaceChildren();
    const row = document.createElement('tr');
    const cell = document.createElement('td');
    cell.colSpan = colSpan;
    cell.textContent = message;
    row.appendChild(cell);
    tbody.appendChild(row);
}

function actionButton(label, title, onClick, className = 'action-btn') {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = className;
    button.title = title;
    button.setAttribute('aria-label', title);
    button.textContent = label;
    button.addEventListener('click', onClick);
    return button;
}

function statusBadge(value, label = value) {
    const badge = document.createElement('span');
    const allowed = new Set(['ACTIVE', 'PENDING', 'COMPLETED', 'VERIFIED', 'SECURITY']);
    const className = allowed.has(value) ? value : 'PENDING';
    badge.className = `badge ${className}`;
    badge.textContent = label == null ? '' : String(label);
    return badge;
}

function setStatusCell(row, value, label = value) {
    const cell = document.createElement('td');
    cell.appendChild(statusBadge(value, label));
    row.appendChild(cell);
    return cell;
}

function currentRole() {
    return String(currentUser?.role || '').toUpperCase();
}

function isStaffOrAdmin() {
    return ['STAFF', 'ADMIN'].includes(currentRole());
}

function isAdmin() {
    return currentRole() === 'ADMIN';
}

function updateAuthUI() {
    const badge = byId('current-user-badge');
    const topBadge = byId('topbar-role-badge');
    const jwtBox = byId('jwt-display');
    badge.replaceChildren();

    if (currentUser && currentToken) {
        const userLine = document.createElement('strong');
        userLine.textContent = `👤 ${currentUser.username}`;
        const roleLine = document.createElement('div');
        roleLine.textContent = `Vai trò: ${currentRole()}${currentUser.studentId ? ` | Mã SV: ${currentUser.studentId}` : ''}`;
        badge.append(userLine, document.createElement('br'), roleLine);
        topBadge.textContent = `🛡️ ${currentUser.username} (ROLE_${currentRole()})`;
        if (jwtBox) jwtBox.value = currentToken;
    } else {
        badge.textContent = 'Chưa đăng nhập (Không có JWT)';
        topBadge.textContent = '🔓 Chưa đăng nhập (Guest)';
        if (jwtBox) jwtBox.value = '';
    }

    const dashboardButton = byId('tab-btn-dashboard');
    const auditButton = byId('tab-btn-audits');
    const addProfileButton = byId('btn-add-profile');
    dashboardButton.hidden = !isStaffOrAdmin();
    auditButton.hidden = !isAdmin();
    if (addProfileButton) addProfileButton.hidden = !isStaffOrAdmin();

    const studentIdInput = byId('doc-student-id');
    const canUseDocumentForm = Boolean(currentToken && currentUser);
    byId('doc-file').disabled = !canUseDocumentForm;
    byId('doc-type').disabled = !canUseDocumentForm;
    byId('btn-upload-document').disabled = !canUseDocumentForm;
    const simulationControls = ['doc-simulate-delay', 'doc-simulate-rollback'];
    if (currentRole() === 'STUDENT') {
        studentIdInput.value = currentUser.studentId || '';
        studentIdInput.disabled = true;
        simulationControls.forEach(id => { byId(id).checked = false; });
    } else {
        studentIdInput.value = '';
        studentIdInput.disabled = false;
    }
    simulationControls.forEach(id => { byId(id).disabled = !isStaffOrAdmin(); });

    // Xóa nội dung nhạy cảm đã tải ở phiên quyền cao hơn khi chuyển sang vai trò thấp hơn.
    if (!isAdmin()) addTableMessage(byId('audit-table-body'), 6, 'Chỉ ADMIN được phép xem nhật ký kiểm toán.');
    if (!isStaffOrAdmin()) byId('stats-container').replaceChildren();
    if (!isAdmin() && byId('tab-btn-audits').classList.contains('active')) switchTab('profiles');
    if (!isStaffOrAdmin() && byId('tab-btn-dashboard').classList.contains('active')) switchTab('profiles');
}

async function loginWithCredentials(username, password) {
    if (!username || !password) {
        showGlobalAlert('Vui lòng nhập tên đăng nhập và mật khẩu.', true);
        return false;
    }

    // Không giữ phiên quyền cao nếu người dùng đang chuyển tài khoản nhưng đăng nhập thất bại.
    // Đồng thời xóa dữ liệu của phiên trước trước khi tải dữ liệu theo danh tính mới.
    currentToken = null;
    currentUser = null;
    updateAuthUI();
    addTableMessage(byId('profile-table-body'), 7, 'Đang xác thực tài khoản...');
    addTableMessage(byId('document-table-body'), 7, 'Đang xác thực tài khoản...');
    addTableMessage(byId('audit-table-body'), 6, 'Chưa đăng nhập.');
    byId('stats-container').replaceChildren();

    try {
        const { data } = await apiFetch(`${AUTH_API}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        if (!data.accessToken || !data.role || !data.username) {
            throw new ApiError('Phản hồi đăng nhập không đầy đủ.', 502, data);
        }
        currentToken = data.accessToken;
        currentUser = { username: data.username, role: data.role, studentId: data.studentId || null };
        updateAuthUI();
        byId('security-test-output').textContent = `[POST /api/v1/auth/login] Đăng nhập thành công: ${data.username} (${data.role}).`;
        showGlobalAlert(`Đăng nhập thành công: ${data.username} (${data.role}).`);
        await Promise.allSettled([loadProfiles(), loadDocuments()]);
        return true;
    } catch (error) {
        showGlobalAlert(`Đăng nhập thất bại: ${errorMessage(error)}`, true);
        byId('security-test-output').textContent = errorMessage(error);
        return false;
    }
}

function quickLogin(username) {
    byId('login-username').value = username;
    byId('login-password').value = 'DemoPassword123!';
    return loginWithCredentials(username, 'DemoPassword123!');
}

function manualLogin() {
    return loginWithCredentials(byId('login-username').value.trim(), byId('login-password').value);
}

function clearToken() {
    currentToken = null;
    currentUser = null;
    updateAuthUI();
    showGlobalAlert('Đã xóa JWT. Các API được bảo vệ sẽ từ chối yêu cầu chưa xác thực.', true);
    addTableMessage(byId('profile-table-body'), 7, 'Vui lòng đăng nhập để xem hồ sơ.');
    addTableMessage(byId('document-table-body'), 7, 'Vui lòng đăng nhập để xem tài liệu.');
}

async function useExpiredToken() {
    try {
        const { data } = await apiFetch(`${AUTH_API}/demo-expired-token`, { method: 'POST' });
        if (!data.accessToken) throw new ApiError('Máy chủ không trả về token.', 502, data);
        currentToken = data.accessToken;
        currentUser = { username: 'demo-expired-token', role: 'STUDENT', studentId: 'SV001' };
        updateAuthUI();
        showGlobalAlert('Đã nạp JWT hết hạn. Hãy gọi API để quan sát phản hồi 401.', true);
    } catch (error) {
        showGlobalAlert(errorMessage(error), true);
    }
}

async function runSecurityTest(scenario) {
    const output = byId('security-test-output');
    const testUsers = {
        OWN_PROFILE: 'student01', IDOR_PROFILE: 'student01', STUDENT_CREATE: 'student01',
        STAFF_APPROVE: 'staff01', DELETE_VERIFIED_DOC: 'student01'
    };
    try {
        if (testUsers[scenario] && !(await quickLogin(testUsers[scenario]))) return;
        let url = `${PROFILE_API}/SV001`;
        let options = { headers: authHeaders() };
        let label = '';

        switch (scenario) {
            case 'OWN_PROFILE':
                label = 'TC06 - Sinh viên xem hồ sơ của chính mình';
                break;
            case 'IDOR_PROFILE':
                url = `${PROFILE_API}/SV002`;
                label = 'TC07 - Sinh viên thử xem hồ sơ của người khác';
                break;
            case 'STUDENT_CREATE':
                url = PROFILE_API;
                options = {
                    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
                    body: JSON.stringify({ studentId: 'SV999', fullName: 'Test Sinh Vien', dateOfBirth: '2004-01-01', gender: 'MALE', email: 'test@sv.edu.vn', phone: '0901234567', major: 'CNTT' })
                };
                label = 'TC08 - STUDENT thử tạo hồ sơ';
                break;
            case 'STAFF_APPROVE':
                url = `${PROFILE_API}/SV003/approve`;
                options = { method: 'PUT', headers: authHeaders() };
                label = 'TC10 - STAFF thử duyệt hồ sơ';
                break;
            case 'NO_TOKEN':
                options = {};
                label = 'TC04 - Gọi API không kèm Bearer Token';
                break;
            case 'DELETE_VERIFIED_DOC':
                url = `${DOCUMENT_API}/1`;
                options = { method: 'DELETE', headers: authHeaders() };
                label = 'TC13 - Sinh viên thử xóa tài liệu VERIFIED';
                break;
            default:
                throw new Error('Kịch bản kiểm thử không hợp lệ.');
        }

        const response = await fetch(url, options);
        const data = await readResponseBody(response);
        output.textContent = `${label} -> HTTP ${response.status}\n${JSON.stringify(data, null, 2)}`;
        output.style.color = response.ok ? '#10b981' : '#f59e0b';
    } catch (error) {
        output.style.color = '#ef4444';
        output.textContent = `Kiểm thử thất bại: ${errorMessage(error)}`;
    }
}

function switchTab(tabName) {
    const allowedTabs = ['security', 'dashboard', 'profiles', 'documents', 'audits'];
    if (!allowedTabs.includes(tabName)) return;
    if (tabName === 'dashboard' && !isStaffOrAdmin()) {
        showGlobalAlert('Chức năng tổng quan chỉ dành cho STAFF/ADMIN.', true);
        return;
    }
    if (tabName === 'audits' && !isAdmin()) {
        showGlobalAlert('Chỉ ADMIN được phép xem nhật ký kiểm toán.', true);
        return;
    }

    allowedTabs.forEach(tab => {
        const panel = byId(`tab-${tab}`);
        const btn = byId(`tab-btn-${tab}`);
        const isActive = tab === tabName;
        panel.style.display = isActive ? 'block' : 'none';
        btn.classList.toggle('active', isActive);
        btn.setAttribute('aria-selected', String(isActive));
    });
    if (tabName === 'dashboard') loadStatistics();
    if (tabName === 'profiles') loadProfiles();
    if (tabName === 'documents') loadDocuments();
    if (tabName === 'audits') loadAudits();
}

async function loadProfiles() {
    const tbody = byId('profile-table-body');
    if (!currentToken) {
        addTableMessage(tbody, 7, 'Vui lòng đăng nhập để xem hồ sơ sinh viên.');
        return;
    }

    const params = new URLSearchParams({ page: '0', size: '20', sort: 'studentId,asc' });
    const name = byId('search-input').value.trim();
    const gender = byId('filter-gender').value;
    const status = byId('filter-status').value;
    if (name) params.set('name', name);
    if (gender) params.set('gender', gender);
    if (status) params.set('status', status);

    try {
        const { data } = await apiFetch(`${PROFILE_API}?${params}`, { headers: authHeaders() });
        tbody.replaceChildren();
        const profiles = Array.isArray(data.content) ? data.content : [];
        profiles.forEach(profile => {
            const row = document.createElement('tr');
            addCell(row, profile.studentId, { strong: true });
            addCell(row, profile.fullName);
            addCell(row, GENDER_LABELS[profile.gender] || profile.gender || '');
            addCell(row, profile.phone, { small: profile.email || '' });
            addCell(row, profile.major);
            const statusLabels = { ACTIVE: 'Đang học', COMPLETED: 'Đã hoàn thành', PENDING: 'Chờ duyệt' };
            setStatusCell(row, profile.status, statusLabels[profile.status] || 'Không xác định');

            const actions = document.createElement('td');
            const canEdit = isStaffOrAdmin() || (currentRole() === 'STUDENT' && profile.studentId === currentUser.studentId);
            if (canEdit) actions.appendChild(actionButton('✏️', 'Chỉnh sửa hồ sơ', () => editProfile(profile)));
            if (isAdmin() && profile.status === 'PENDING') {
                actions.appendChild(actionButton('✅', 'Duyệt hồ sơ', () => approveProfile(profile.studentId)));
            }
            if (isAdmin()) actions.appendChild(actionButton('🗑️', 'Xóa hồ sơ', () => deleteProfile(profile.studentId)));
            row.appendChild(actions);
            tbody.appendChild(row);
        });
        if (profiles.length === 0) addTableMessage(tbody, 7, 'Không tìm thấy hồ sơ phù hợp.');
        byId('pagination-info').textContent = `Hiển thị ${profiles.length} / ${data.totalElements || 0} sinh viên (Trang ${(data.page || 0) + 1}/${data.totalPages || 1}).`;
    } catch (error) {
        addTableMessage(tbody, 7, errorMessage(error));
        byId('pagination-info').textContent = '';
    }
}

async function loadStatistics() {
    if (!isStaffOrAdmin()) return;
    const container = byId('stats-container');
    try {
        const { data } = await apiFetch(`${PROFILE_API}/statistics`, { headers: authHeaders() });
        container.replaceChildren();
        const stats = [
            ['Tổng số sinh viên', data.total], ['Sinh viên Nam', data.male],
            ['Sinh viên Nữ', data.female], ['Đang học', data.active], ['Chờ duyệt', data.pending]
        ];
        stats.forEach(([label, value]) => {
            const card = document.createElement('div');
            card.className = 'stat-card';
            const heading = document.createElement('h4');
            heading.textContent = label;
            const number = document.createElement('div');
            number.className = 'num';
            number.textContent = String(Number.isFinite(Number(value)) ? Number(value) : 0);
            card.append(heading, number);
            container.appendChild(card);
        });
    } catch (error) {
        container.replaceChildren();
        const message = document.createElement('p');
        message.textContent = errorMessage(error);
        container.appendChild(message);
    }
}

function setStudentFormMode(studentMode) {
    ['form-studentId', 'form-dob', 'form-gender', 'form-fullName', 'form-major'].forEach(id => {
        byId(id).disabled = studentMode;
    });
}

function openModal() {
    if (!isStaffOrAdmin()) {
        showGlobalAlert('Chỉ STAFF/ADMIN được tạo hồ sơ mới.', true);
        return;
    }
    editingStudentId = null;
    byId('modal-title').textContent = 'Thêm hồ sơ sinh viên mới';
    setStudentFormMode(false);
    byId('form-studentId').disabled = false;
    ['studentId', 'fullName', 'email', 'phone', 'major', 'address'].forEach(field => { byId(`form-${field}`).value = ''; });
    byId('form-dob').value = '';
    byId('form-gender').value = 'MALE';
    byId('modal-error').style.display = 'none';
    byId('profile-modal').style.display = 'flex';
    byId('form-studentId').focus();
}

function editProfile(profile) {
    const studentMode = currentRole() === 'STUDENT';
    if (!isStaffOrAdmin() && !(studentMode && profile.studentId === currentUser?.studentId)) {
        showGlobalAlert('Bạn không có quyền sửa hồ sơ này.', true);
        return;
    }
    editingStudentId = profile.studentId;
    byId('modal-title').textContent = studentMode ? 'Cập nhật thông tin liên hệ' : `Cập nhật hồ sơ: ${profile.studentId}`;
    byId('form-studentId').value = profile.studentId || '';
    byId('form-fullName').value = profile.fullName || '';
    byId('form-dob').value = profile.dateOfBirth || '';
    byId('form-gender').value = profile.gender || 'OTHER';
    byId('form-email').value = profile.email || '';
    byId('form-phone').value = profile.phone || '';
    byId('form-major').value = profile.major || '';
    byId('form-address').value = profile.address || '';
    setStudentFormMode(studentMode);
    byId('form-studentId').disabled = true;
    byId('modal-error').style.display = 'none';
    byId('profile-modal').style.display = 'flex';
    byId(studentMode ? 'form-email' : 'form-fullName').focus();
}

function closeModal() {
    byId('profile-modal').style.display = 'none';
}

function localDateString(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function validateProfileForm(payload, isStudentPatch) {
    const errors = [];
    if (!isStudentPatch && !/^[A-Za-z0-9_-]{1,20}$/.test(payload.studentId)) errors.push('Mã sinh viên không hợp lệ.');
    if (!isStudentPatch && (payload.fullName.length < 3 || payload.fullName.length > 100)) errors.push('Họ tên phải từ 3 đến 100 ký tự.');
    if (!isStudentPatch && (!payload.dateOfBirth || payload.dateOfBirth >= localDateString(new Date()))) errors.push('Ngày sinh phải nhỏ hơn ngày hiện tại.');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email) || payload.email.length > 100) errors.push('Email không hợp lệ.');
    if (!/^\d{10}$/.test(payload.phone)) errors.push('Số điện thoại phải gồm đúng 10 chữ số.');
    if (!isStudentPatch && (!payload.major || payload.major.length > 100)) errors.push('Chuyên ngành không được để trống và tối đa 100 ký tự.');
    if (payload.address.length > 255) errors.push('Địa chỉ tối đa 255 ký tự.');
    return errors;
}

async function saveProfile() {
    const payload = {
        studentId: byId('form-studentId').value.trim(),
        fullName: byId('form-fullName').value.trim(),
        dateOfBirth: byId('form-dob').value,
        gender: byId('form-gender').value,
        email: byId('form-email').value.trim(),
        phone: byId('form-phone').value.trim(),
        major: byId('form-major').value.trim(),
        address: byId('form-address').value.trim()
    };
    const studentPatch = currentRole() === 'STUDENT' && Boolean(editingStudentId);
    const errors = validateProfileForm(payload, studentPatch);
    if (errors.length) {
        showInlineAlert('modal-error', errors.join(' '), true);
        return;
    }

    let url;
    let method;
    let body = payload;
    if (studentPatch) {
        url = `${PROFILE_API}/${encodeURIComponent(editingStudentId)}`;
        method = 'PATCH';
        body = { email: payload.email, phone: payload.phone, address: payload.address };
    } else if (editingStudentId) {
        url = `${PROFILE_API}/${encodeURIComponent(editingStudentId)}`;
        method = 'PUT';
    } else {
        url = PROFILE_API;
        method = 'POST';
    }

    const saveBtn = document.getElementById('btn-save-profile');
    if (saveBtn) saveBtn.disabled = true;
    try {
        await apiFetch(url, {
            method,
            headers: authHeaders({ 'Content-Type': 'application/json' }),
            body: JSON.stringify(body)
        });
        closeModal();
        showGlobalAlert('Lưu hồ sơ thành công.');
        await loadProfiles();
    } catch (error) {
        showInlineAlert('modal-error', errorMessage(error), true);
    } finally {
        if (saveBtn) saveBtn.disabled = false;
    }
}

async function approveProfile(studentId) {
    try {
        await apiFetch(`${PROFILE_API}/${encodeURIComponent(studentId)}/approve`, {
            method: 'PUT', headers: authHeaders()
        });
        showGlobalAlert(`Đã phê duyệt hồ sơ ${studentId}.`);
        await loadProfiles();
    } catch (error) {
        showGlobalAlert(errorMessage(error), true);
    }
}

async function deleteProfile(studentId) {
    if (!isAdmin()) return showGlobalAlert('Chỉ ADMIN được xóa hồ sơ.', true);
    if (!window.confirm(`Bạn có chắc muốn xóa hồ sơ ${studentId}?`)) return;
    try {
        await apiFetch(`${PROFILE_API}/${encodeURIComponent(studentId)}`, { method: 'DELETE', headers: authHeaders() });
        showGlobalAlert(`Đã xóa hồ sơ ${studentId}.`);
        await loadProfiles();
    } catch (error) {
        showGlobalAlert(errorMessage(error), true);
    }
}

function safeHttpsUrl(value) {
    try {
        const parsed = new URL(String(value), window.location.href);
        return parsed.protocol === 'https:' && parsed.hostname && !parsed.username && !parsed.password ? parsed.href : null;
    } catch {
        return null;
    }
}

async function loadDocuments() {
    const tbody = byId('document-table-body');
    if (!currentToken) {
        addTableMessage(tbody, 7, 'Vui lòng đăng nhập để xem tài liệu.');
        return;
    }
    try {
        const { data } = await apiFetch(DOCUMENT_API, { headers: authHeaders() });
        tbody.replaceChildren();
        const docs = Array.isArray(data) ? data : [];
        docs.forEach(doc => {
            const row = document.createElement('tr');
            addCell(row, `#${doc.id}`);
            addCell(row, doc.studentId, { strong: true });
            addCell(row, doc.studentName);
            addCell(row, doc.docType);
            setStatusCell(row, doc.docStatus, doc.docStatus);

            const linkCell = document.createElement('td');
            if (doc.storageKey) {
                const download = actionButton('⬇ Tải file', 'Tải file đã được phân quyền', () => downloadDocumentFile(doc.id, doc.originalFilename));
                linkCell.appendChild(download);
                if (doc.originalFilename) {
                    const name = document.createElement('div');
                    name.className = 'cell-subtext';
                    name.textContent = doc.originalFilename;
                    linkCell.appendChild(name);
                }
            } else {
                const safeUrl = safeHttpsUrl(doc.docUrl);
                if (safeUrl) {
                    const anchor = document.createElement('a');
                    anchor.href = safeUrl;
                    anchor.target = '_blank';
                    anchor.rel = 'noopener noreferrer';
                    anchor.textContent = 'Mở liên kết HTTPS';
                    linkCell.appendChild(anchor);
                } else {
                    linkCell.textContent = 'Không có liên kết an toàn';
                }
            }
            row.appendChild(linkCell);

            const actions = document.createElement('td');
            if (isStaffOrAdmin() && doc.docStatus === 'PENDING') {
                actions.appendChild(actionButton('✅', 'Xác thực tài liệu', () => verifyDocument(doc.id)));
            }
            actions.appendChild(actionButton('🗑️', 'Xóa tài liệu', () => deleteDocument(doc.id)));
            row.appendChild(actions);
            tbody.appendChild(row);
        });
        if (!docs.length) addTableMessage(tbody, 7, 'Chưa có tài liệu nào.');
    } catch (error) {
        addTableMessage(tbody, 7, errorMessage(error));
    }
}

function validateDocumentFile(file) {
    if (!file) return 'Vui lòng chọn file tài liệu.';
    if (file.size <= 0) return 'File được chọn đang trống.';
    if (file.size > MAX_DOCUMENT_SIZE) return 'Dung lượng file tối đa là 10 MB.';
    const extension = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : '';
    if (!ALLOWED_DOCUMENT_EXTENSIONS.has(extension)) return 'Chỉ hỗ trợ PDF, PNG, JPG hoặc JPEG.';
    const acceptedTypes = new Set(['application/pdf', 'image/png', 'image/jpeg', '']);
    if (!acceptedTypes.has(file.type)) return 'Kiểu nội dung file không được hỗ trợ.';
    return null;
}

async function uploadDocument() {
    const studentId = byId('doc-student-id').value.trim();
    const docType = byId('doc-type').value.trim();
    const file = byId('doc-file').files[0];
    const simulateDelay = byId('doc-simulate-delay').checked;
    const simulateRollback = byId('doc-simulate-rollback').checked;

    if (!currentToken) return showInlineAlert('doc-alert', 'Bạn cần đăng nhập trước khi nộp tài liệu.', true);
    if (!/^[A-Za-z0-9_-]{1,20}$/.test(studentId)) return showInlineAlert('doc-alert', 'Mã sinh viên không hợp lệ.', true);
    if (docType.length < 2 || docType.length > 100) return showInlineAlert('doc-alert', 'Loại tài liệu phải từ 2 đến 100 ký tự.', true);
    const fileError = validateDocumentFile(file);
    if (fileError) return showInlineAlert('doc-alert', fileError, true);

    const params = new URLSearchParams({ simulateDelay: String(simulateDelay), simulateRollback: String(simulateRollback) });
    const formData = new FormData();
    formData.append('docType', docType);
    formData.append('file', file, file.name);

    const submitButton = document.getElementById('btn-upload-document');
    if (submitButton) submitButton.disabled = true;
    try {
        const { data } = await apiFetch(`${DOCUMENT_API}/${encodeURIComponent(studentId)}/upload?${params}`, {
            method: 'POST',
            headers: authHeaders(),
            body: formData
        });
        showInlineAlert('doc-alert', `Đã tải lên tài liệu #${data.id} (${data.originalFilename || file.name}) cho ${data.studentId}.`);
        byId('doc-file').value = '';
        await loadDocuments();
    } catch (error) {
        showInlineAlert('doc-alert', errorMessage(error), true);
    } finally {
        if (submitButton) submitButton.disabled = false;
    }
}

async function downloadDocumentFile(docId, filename) {
    try {
        const response = await fetch(`${DOCUMENT_API}/${encodeURIComponent(docId)}/file`, { headers: authHeaders() });
        if (!response.ok) {
            const data = await readResponseBody(response);
            throw new ApiError(data.message || `Không thể tải file (HTTP ${response.status}).`, response.status, data);
        }
        const blob = await response.blob();
        const objectUrl = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = objectUrl;
        anchor.download = (filename || `student-document-${docId}`).replace(/[\\/\r\n]/g, '_');
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60000);
    } catch (error) {
        showGlobalAlert(errorMessage(error), true);
    }
}

async function verifyDocument(docId) {
    try {
        await apiFetch(`${DOCUMENT_API}/${encodeURIComponent(docId)}/verify`, { method: 'PUT', headers: authHeaders() });
        showGlobalAlert(`Tài liệu #${docId} đã được xác thực.`);
        await loadDocuments();
    } catch (error) {
        showGlobalAlert(errorMessage(error), true);
    }
}

async function deleteDocument(docId) {
    if (!window.confirm(`Bạn có chắc muốn xóa tài liệu #${docId}?`)) return;
    try {
        await apiFetch(`${DOCUMENT_API}/${encodeURIComponent(docId)}`, { method: 'DELETE', headers: authHeaders() });
        showGlobalAlert(`Đã xóa tài liệu #${docId}.`);
        await loadDocuments();
    } catch (error) {
        showInlineAlert('doc-alert', errorMessage(error), true);
    }
}

async function loadAudits() {
    const tbody = byId('audit-table-body');
    if (!isAdmin()) {
        addTableMessage(tbody, 6, 'Chỉ ADMIN được phép xem nhật ký kiểm toán.');
        return;
    }
    try {
        const { data } = await apiFetch(AUDIT_API, { headers: authHeaders() });
        tbody.replaceChildren();
        const logs = Array.isArray(data) ? data : [];
        logs.forEach(log => {
            const row = document.createElement('tr');
            addCell(row, log.id);
            const actionCell = document.createElement('td');
            const securityEvent = String(log.action || '').includes('DENIED');
            actionCell.appendChild(statusBadge(securityEvent ? 'SECURITY' : 'ACTIVE', log.action || 'UNKNOWN'));
            row.appendChild(actionCell);
            addCell(row, log.studentId, { strong: true });
            addCell(row, log.fullName);
            addCell(row, log.description);
            addCell(row, log.timestamp);
            tbody.appendChild(row);
        });
        if (!logs.length) addTableMessage(tbody, 6, 'Chưa có nhật ký hoạt động.');
    } catch (error) {
        addTableMessage(tbody, 6, errorMessage(error));
    }
}

// Không tự động đăng nhập bằng tài khoản ADMIN khi mở trang.
document.addEventListener('DOMContentLoaded', () => {
    try {
        const savedTheme = localStorage.getItem('sps_theme');
        if (savedTheme) applyTheme(savedTheme);
    } catch {
        // Mặc định giữ giao diện tối
    }

    byId('form-dob').max = localDateString(new Date());
    updateAuthUI();
    addTableMessage(byId('profile-table-body'), 7, 'Vui lòng đăng nhập để xem hồ sơ sinh viên.');
    addTableMessage(byId('document-table-body'), 7, 'Vui lòng đăng nhập để xem tài liệu.');
    addTableMessage(byId('audit-table-body'), 6, 'Chỉ ADMIN được phép xem nhật ký kiểm toán.');

    // Debounce 300ms cho ô tìm kiếm để tránh gọi API liên tục khi đang gõ
    const searchInput = byId('search-input');
    searchInput.addEventListener('input', () => {
        window.clearTimeout(searchDebounceTimer);
        searchDebounceTimer = window.setTimeout(() => {
            loadProfiles();
        }, 300);
    });

    // Đóng Modal khi nhấn phím Escape hoặc click vào vùng nền mờ
    const modalBackdrop = byId('profile-modal');
    modalBackdrop.addEventListener('click', (event) => {
        if (event.target === modalBackdrop) closeModal();
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && modalBackdrop.style.display !== 'none') {
            closeModal();
        }
    });
});