const state = {
    token: localStorage.getItem("authz_access_token") || "",
    username: localStorage.getItem("authz_username") || "",
    captchaId: "",
    users: [],
    clientApps: [],
    permissions: [],
    roles: [],
    tableUsers: [],
    tableClientApps: [],
    tablePermissions: [],
    tableRoles: [],
    pages: {
        users: {keyword: "", page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true},
        clientApps: {keyword: "", page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true},
        permissions: {keyword: "", page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true},
        roles: {keyword: "", page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true}
    }
};

const elements = {
    loginPanel: document.querySelector("#loginPanel"),
    adminPanel: document.querySelector("#adminPanel"),
    loginForm: document.querySelector("#loginForm"),
    username: document.querySelector("#username"),
    password: document.querySelector("#password"),
    captchaCode: document.querySelector("#captchaCode"),
    captchaImage: document.querySelector("#captchaImage"),
    captchaImageBtn: document.querySelector("#captchaImageBtn"),
    refreshCaptchaBtn: document.querySelector("#refreshCaptchaBtn"),
    sessionStatus: document.querySelector("#sessionStatus"),
    logoutBtn: document.querySelector("#logoutBtn"),
    refreshBtn: document.querySelector("#refreshBtn"),
    userForm: document.querySelector("#userForm"),
    userId: document.querySelector("#userId"),
    userUsername: document.querySelector("#userUsername"),
    userPassword: document.querySelector("#userPassword"),
    userDisplayName: document.querySelector("#userDisplayName"),
    userEnabled: document.querySelector("#userEnabled"),
    resetUserFormBtn: document.querySelector("#resetUserFormBtn"),
    userRoleList: document.querySelector("#userRoleList"),
    userTable: document.querySelector("#userTable"),
    userSearchForm: document.querySelector("#userSearchForm"),
    userKeyword: document.querySelector("#userKeyword"),
    userSize: document.querySelector("#userSize"),
    userPager: document.querySelector("#userPager"),
    roleForm: document.querySelector("#roleForm"),
    roleId: document.querySelector("#roleId"),
    roleCode: document.querySelector("#roleCode"),
    roleName: document.querySelector("#roleName"),
    resetRoleFormBtn: document.querySelector("#resetRoleFormBtn"),
    roleTable: document.querySelector("#roleTable"),
    roleSearchForm: document.querySelector("#roleSearchForm"),
    roleKeyword: document.querySelector("#roleKeyword"),
    roleSize: document.querySelector("#roleSize"),
    rolePager: document.querySelector("#rolePager"),
    clientAppForm: document.querySelector("#clientAppForm"),
    clientAppId: document.querySelector("#clientAppId"),
    clientAppCode: document.querySelector("#clientAppCode"),
    clientAppName: document.querySelector("#clientAppName"),
    clientAppDescription: document.querySelector("#clientAppDescription"),
    clientAppEnabled: document.querySelector("#clientAppEnabled"),
    resetClientAppFormBtn: document.querySelector("#resetClientAppFormBtn"),
    clientAppTable: document.querySelector("#clientAppTable"),
    clientAppSearchForm: document.querySelector("#clientAppSearchForm"),
    clientAppKeyword: document.querySelector("#clientAppKeyword"),
    clientAppSize: document.querySelector("#clientAppSize"),
    clientAppPager: document.querySelector("#clientAppPager"),
    permissionForm: document.querySelector("#permissionForm"),
    permissionId: document.querySelector("#permissionId"),
    permissionAppCode: document.querySelector("#permissionAppCode"),
    permissionCode: document.querySelector("#permissionCode"),
    permissionName: document.querySelector("#permissionName"),
    resetFormBtn: document.querySelector("#resetFormBtn"),
    permissionTable: document.querySelector("#permissionTable"),
    permissionSearchForm: document.querySelector("#permissionSearchForm"),
    permissionKeyword: document.querySelector("#permissionKeyword"),
    permissionSize: document.querySelector("#permissionSize"),
    permissionPager: document.querySelector("#permissionPager"),
    roleSelect: document.querySelector("#roleSelect"),
    roleAppSelect: document.querySelector("#roleAppSelect"),
    currentRoleCard: document.querySelector("#currentRoleCard"),
    rolePermissionList: document.querySelector("#rolePermissionList"),
    saveRolePermissionsBtn: document.querySelector("#saveRolePermissionsBtn"),
    moduleNavItems: document.querySelectorAll("[data-module-target]"),
    adminModules: document.querySelectorAll("[data-module]"),
    toast: document.querySelector("#toast")
};

function createEmptyPageState() {
    return {keyword: "", page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true};
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#039;");
}

function setBusy(button, busy) {
    if (!button) {
        return;
    }
    button.disabled = busy;
}

function showToast(message, type = "success") {
    elements.toast.textContent = message;
    elements.toast.className = `toast ${type}`;
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => {
        elements.toast.className = "toast hidden";
    }, 2600);
}

function setSession(loggedIn) {
    elements.loginPanel.classList.toggle("hidden", loggedIn);
    elements.adminPanel.classList.toggle("hidden", !loggedIn);
    elements.logoutBtn.classList.toggle("hidden", !loggedIn);
    elements.sessionStatus.textContent = loggedIn ? `当前用户：${state.username || "已登录"}` : "未登录";
}

function switchAdminModule(moduleName) {
    elements.moduleNavItems.forEach(item => {
        item.classList.toggle("active", item.dataset.moduleTarget === moduleName);
    });
    elements.adminModules.forEach(module => {
        module.classList.toggle("active", module.dataset.module === moduleName);
    });
}

function clearSessionState() {
    state.token = "";
    state.username = "";
    state.users = [];
    state.clientApps = [];
    state.permissions = [];
    state.roles = [];
    state.tableUsers = [];
    state.tableClientApps = [];
    state.tablePermissions = [];
    state.tableRoles = [];
    state.pages.users = createEmptyPageState();
    state.pages.clientApps = createEmptyPageState();
    state.pages.permissions = createEmptyPageState();
    state.pages.roles = createEmptyPageState();
    localStorage.removeItem("authz_access_token");
    localStorage.removeItem("authz_username");
    setSession(false);
    renderUsers();
    renderClientApps();
    renderPermissions();
    renderRoles();
    renderRoleTable();
    Object.keys(listConfigs).forEach(renderPager);
}

async function requestJson(url, options = {}) {
    const headers = {
        "Content-Type": "application/json",
        ...(options.headers || {})
    };
    if (state.token) {
        headers.Authorization = `Bearer ${state.token}`;
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    if (response.status === 204) {
        return null;
    }

    const text = await response.text();
    const body = text ? JSON.parse(text) : null;

    if (!response.ok) {
        const message = body?.message || body?.error || `请求失败：${response.status}`;
        if (response.status === 401 && !url.startsWith("/api/auth/login")) {
            clearSessionState();
            loadCaptcha().catch(error => showToast(error.message, "error"));
            throw new Error("登录已过期或无效，请重新登录");
        }
        throw new Error(message);
    }

    return body;
}

const listConfigs = {
    users: {
        endpoint: "/api/users",
        keywordInput: () => elements.userKeyword,
        sizeInput: () => elements.userSize,
        pager: () => elements.userPager,
        setRows: rows => state.tableUsers = rows,
        render: renderUsers
    },
    clientApps: {
        endpoint: "/api/client-apps",
        keywordInput: () => elements.clientAppKeyword,
        sizeInput: () => elements.clientAppSize,
        pager: () => elements.clientAppPager,
        setRows: rows => state.tableClientApps = rows,
        render: renderClientApps
    },
    permissions: {
        endpoint: "/api/permissions",
        keywordInput: () => elements.permissionKeyword,
        sizeInput: () => elements.permissionSize,
        pager: () => elements.permissionPager,
        setRows: rows => state.tablePermissions = rows,
        render: renderPermissions
    },
    roles: {
        endpoint: "/api/roles",
        keywordInput: () => elements.roleKeyword,
        sizeInput: () => elements.roleSize,
        pager: () => elements.rolePager,
        setRows: rows => state.tableRoles = rows,
        render: renderRoleTable
    }
};

function buildPagedUrl(type) {
    const config = listConfigs[type];
    const pageState = state.pages[type];
    const params = new URLSearchParams({
        page: String(pageState.page),
        size: String(pageState.size)
    });
    if (pageState.keyword) {
        params.set("keyword", pageState.keyword);
    }
    return `${config.endpoint}?${params.toString()}`;
}

async function loadPagedList(type) {
    const config = listConfigs[type];
    const data = await requestJson(buildPagedUrl(type));
    const pageData = Array.isArray(data)
            ? {
                content: data,
                page: 0,
                size: data.length || state.pages[type].size,
                totalElements: data.length,
                totalPages: data.length ? 1 : 0,
                first: true,
                last: true
            }
            : data;
    if ((pageData.content || []).length === 0 && pageData.totalElements > 0 && pageData.totalPages > 0 && pageData.page >= pageData.totalPages) {
        state.pages[type].page = pageData.totalPages - 1;
        return loadPagedList(type);
    }
    state.pages[type] = {
        ...state.pages[type],
        page: pageData.page,
        size: pageData.size,
        totalElements: pageData.totalElements,
        totalPages: pageData.totalPages,
        first: pageData.first,
        last: pageData.last
    };
    config.setRows(pageData.content || []);
    config.render();
    renderPager(type);
}

function renderPager(type) {
    const config = listConfigs[type];
    const pager = config.pager();
    const pageState = state.pages[type];
    const currentPage = pageState.totalPages === 0 ? 0 : pageState.page + 1;
    pager.innerHTML = `
        <span class="page-info">共 ${pageState.totalElements} 条，第 ${currentPage}/${pageState.totalPages || 0} 页</span>
        <button class="ghost" type="button" data-page-list="${type}" data-page-action="prev" ${pageState.first ? "disabled" : ""}>上一页</button>
        <button class="ghost" type="button" data-page-list="${type}" data-page-action="next" ${pageState.last ? "disabled" : ""}>下一页</button>
    `;
}

async function submitListSearch(type, event) {
    event.preventDefault();
    const config = listConfigs[type];
    const pageState = state.pages[type];
    pageState.keyword = config.keywordInput().value.trim();
    pageState.size = Number(config.sizeInput().value) || 10;
    pageState.page = 0;
    await loadPagedList(type);
}

async function resetListSearch(type) {
    const config = listConfigs[type];
    const pageState = state.pages[type];
    config.keywordInput().value = "";
    config.sizeInput().value = "10";
    pageState.keyword = "";
    pageState.size = 10;
    pageState.page = 0;
    await loadPagedList(type);
}

function focusListOn(type, keyword) {
    const config = listConfigs[type];
    const pageState = state.pages[type];
    pageState.keyword = keyword || "";
    pageState.page = 0;
    config.keywordInput().value = pageState.keyword;
}

async function handlePagerClick(event) {
    const button = event.target.closest("button[data-page-list]");
    if (!button || button.disabled) {
        return;
    }
    const type = button.dataset.pageList;
    const pageState = state.pages[type];
    pageState.page += button.dataset.pageAction === "next" ? 1 : -1;
    pageState.page = Math.max(pageState.page, 0);
    await loadPagedList(type);
}

function runListAction(action) {
    action().catch(error => showToast(error.message, "error"));
}

async function login(event) {
    event.preventDefault();
    setBusy(elements.loginForm.querySelector("button"), true);
    try {
        const data = await requestJson("/api/auth/login", {
            method: "POST",
            body: JSON.stringify({
                username: elements.username.value.trim(),
                password: elements.password.value,
                captchaId: state.captchaId,
                captchaCode: elements.captchaCode.value.trim()
            })
        });

        state.token = data.accessToken;
        state.username = data.username;
        localStorage.setItem("authz_access_token", state.token);
        localStorage.setItem("authz_username", state.username);
        setSession(true);
        await loadAll();
        showToast("登录成功");
    } catch (error) {
        showToast(error.message, "error");
        await loadCaptcha();
    } finally {
        setBusy(elements.loginForm.querySelector("button"), false);
    }
}

async function loadCaptcha() {
    const data = await requestJson("/api/auth/captcha");
    state.captchaId = data.captchaId;
    elements.captchaImage.src = data.image;
    elements.captchaCode.value = "";
}

function logout() {
    clearSessionState();
    loadCaptcha().catch(error => showToast(error.message, "error"));
}

async function loadAll() {
    const [users, clientApps, permissions, roles] = await Promise.all([
        requestJson("/api/users"),
        requestJson("/api/client-apps"),
        requestJson("/api/permissions"),
        requestJson("/api/roles")
    ]);
    state.users = users || [];
    state.clientApps = clientApps || [];
    state.permissions = permissions || [];
    state.roles = roles || [];
    renderUserRoleOptions();
    renderPermissionAppOptions();
    renderRoleAppOptions();
    renderRoles();
    await Promise.all([
        loadPagedList("users"),
        loadPagedList("clientApps"),
        loadPagedList("permissions"),
        loadPagedList("roles")
    ]);
}

function renderUserRoleOptions(selectedCodes = []) {
    if (!state.roles.length) {
        elements.userRoleList.innerHTML = '<p class="empty">暂无角色，请先创建角色</p>';
        return;
    }

    const checkedCodes = new Set(selectedCodes);
    elements.userRoleList.innerHTML = state.roles.map(role => `
        <label class="mini-check-item">
            <input type="checkbox" value="${role.id}" data-code="${escapeHtml(role.code)}" ${checkedCodes.has(role.code) ? "checked" : ""}>
            <span class="mini-check-text">
                <strong>${escapeHtml(role.name)} (${escapeHtml(role.code)})</strong>
                <span class="mini-check-meta">覆盖应用：${escapeHtml(getRoleAppSummary(role))}</span>
            </span>
        </label>
    `).join("");
}

function getRoleAppSummary(role) {
    const appNames = [...new Set((role.permissions || [])
            .map(permission => permission.appName || permission.appCode)
            .filter(Boolean))];
    return appNames.length ? appNames.join("、") : "暂无应用权限";
}

function getUserAccessibleApps(user) {
    const permissionCodes = new Set(user.permissions || []);
    const appMap = new Map();
    state.permissions
            .filter(permission => permissionCodes.has(permission.code))
            .forEach(permission => {
                const appCode = permission.appCode || "UNKNOWN";
                const appName = permission.appName || permission.appCode || "未归属应用";
                const current = appMap.get(appCode) || {name: appName, count: 0};
                current.count += 1;
                appMap.set(appCode, current);
            });
    return [...appMap.values()];
}

function renderRoleTable() {
    if (!state.tableRoles.length) {
        elements.roleTable.innerHTML = '<tr><td colspan="5" class="empty">暂无角色</td></tr>';
        return;
    }

    elements.roleTable.innerHTML = state.tableRoles.map(role => `
        <tr>
            <td>${role.id}</td>
            <td>
                <div class="stacked-cell">
                    <strong>${escapeHtml(role.name)}</strong>
                    <span class="code">${escapeHtml(role.code)}</span>
                </div>
            </td>
            <td>${escapeHtml(getRoleAppSummary(role))}</td>
            <td>${(role.permissions || []).length}</td>
            <td>
                <div class="row-actions">
                    <button class="ghost" type="button" data-action="edit" data-id="${role.id}">编辑</button>
                    <button class="ghost danger" type="button" data-action="delete" data-id="${role.id}">删除</button>
                </div>
            </td>
        </tr>
    `).join("");
}

function renderUsers() {
    if (!state.tableUsers.length) {
        elements.userTable.innerHTML = '<tr><td colspan="8" class="empty">暂无账号</td></tr>';
        renderUserRoleOptions();
        return;
    }

    elements.userTable.innerHTML = state.tableUsers.map(user => {
        const roles = [...(user.roles || [])];
        const roleTags = roles.length
                ? roles.map(role => `<span class="code">${escapeHtml(role)}</span>`).join("")
                : '<span class="empty">未分配</span>';
        const accessibleApps = getUserAccessibleApps(user);
        const appTags = accessibleApps.length
                ? accessibleApps.map(app => `<span class="code">${escapeHtml(app.name)} · ${app.count}</span>`).join("")
                : '<span class="empty">暂无应用权限</span>';
        return `
            <tr>
                <td>${user.id}</td>
                <td><span class="code">${escapeHtml(user.username)}</span></td>
                <td>${escapeHtml(user.displayName)}</td>
                <td>
                    <span class="status-pill ${user.enabled ? "enabled" : "disabled"}">
                        ${user.enabled ? "启用" : "停用"}
                    </span>
                </td>
                <td><div class="tag-list">${appTags}</div></td>
                <td><div class="tag-list">${roleTags}</div></td>
                <td>${(user.permissions || []).length}</td>
                <td>
                    <div class="row-actions">
                        <button class="ghost" type="button" data-action="edit" data-id="${user.id}">编辑</button>
                        <button class="ghost" type="button" data-action="reset-password" data-id="${user.id}">重置密码</button>
                        <button class="ghost danger" type="button" data-action="delete" data-id="${user.id}">删除</button>
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

function renderClientApps() {
    if (!state.tableClientApps.length) {
        elements.clientAppTable.innerHTML = '<tr><td colspan="5" class="empty">暂无接入应用</td></tr>';
        renderPermissionAppOptions();
        return;
    }

    elements.clientAppTable.innerHTML = state.tableClientApps.map(app => `
        <tr>
            <td>${app.id}</td>
            <td><span class="code">${escapeHtml(app.code)}</span></td>
            <td>${escapeHtml(app.name)}</td>
            <td>${app.enabled ? "启用" : "停用"}</td>
            <td>
                <div class="row-actions">
                    <button class="ghost" type="button" data-action="edit" data-id="${app.id}">编辑</button>
                    <button class="ghost danger" type="button" data-action="delete" data-id="${app.id}">删除</button>
                </div>
            </td>
        </tr>
    `).join("");
}

function renderPermissionAppOptions(selectedCode = elements.permissionAppCode.value) {
    if (!state.clientApps.length) {
        elements.permissionAppCode.innerHTML = '<option value="">暂无应用</option>';
        return;
    }

    const apps = state.clientApps;
    const value = selectedCode || apps.find(app => app.enabled)?.code || apps[0]?.code || "";
    elements.permissionAppCode.innerHTML = apps.map(app => `
        <option value="${escapeHtml(app.code)}" ${app.code === value ? "selected" : ""}>
            ${escapeHtml(app.name)} (${escapeHtml(app.code)})${app.enabled ? "" : " - 已停用"}
        </option>
    `).join("");
}

function getAvailableClientApps() {
    return state.clientApps;
}

function renderRoleAppOptions(selectedCode = elements.roleAppSelect.value) {
    if (!state.clientApps.length) {
        elements.roleAppSelect.innerHTML = '<option value="">暂无应用</option>';
        return;
    }

    const apps = getAvailableClientApps();
    const value = selectedCode || apps.find(app => app.enabled)?.code || apps[0]?.code || "";
    elements.roleAppSelect.innerHTML = apps.map(app => `
        <option value="${escapeHtml(app.code)}" ${app.code === value ? "selected" : ""}>
            ${escapeHtml(app.name)} (${escapeHtml(app.code)})${app.enabled ? "" : " - 已停用"}
        </option>
    `).join("");
}

function renderPermissions() {
    if (!state.tablePermissions.length) {
        elements.permissionTable.innerHTML = '<tr><td colspan="5" class="empty">暂无权限数据</td></tr>';
        return;
    }

    elements.permissionTable.innerHTML = state.tablePermissions.map(permission => `
        <tr>
            <td>${permission.id}</td>
            <td>${escapeHtml(permission.appName || permission.appCode || "-")}</td>
            <td><span class="code">${escapeHtml(permission.code)}</span></td>
            <td>${escapeHtml(permission.name)}</td>
            <td>
                <div class="row-actions">
                    <button class="ghost" type="button" data-action="edit" data-id="${permission.id}">编辑</button>
                    <button class="ghost danger" type="button" data-action="delete" data-id="${permission.id}">删除</button>
                </div>
            </td>
        </tr>
    `).join("");
}

function renderRoles() {
    if (!state.roles.length) {
        elements.roleSelect.innerHTML = '<option value="">暂无角色</option>';
        elements.currentRoleCard.className = "role-card empty";
        elements.currentRoleCard.textContent = "暂无可分配角色";
        elements.rolePermissionList.innerHTML = '<p class="empty">暂无可分配角色</p>';
        return;
    }

    const selectedRoleId = elements.roleSelect.value || String(state.roles[0].id);
    elements.roleSelect.innerHTML = state.roles.map(role => `
        <option value="${role.id}" ${String(role.id) === selectedRoleId ? "selected" : ""}>
            ${escapeHtml(role.name)} (${escapeHtml(role.code)})
        </option>
    `).join("");
    renderRolePermissions();
}

function renderCurrentRole(role) {
    if (!role) {
        elements.currentRoleCard.className = "role-card empty";
        elements.currentRoleCard.textContent = "请选择角色";
        return;
    }

    const permissionCount = (role.permissions || []).length;
    elements.currentRoleCard.className = "role-card";
    elements.currentRoleCard.innerHTML = `
        <strong>当前角色：${escapeHtml(role.name)}</strong>
        <div class="meta">
            <span class="tag">编码：${escapeHtml(role.code)}</span>
            <span class="tag">已分配权限：${permissionCount}</span>
        </div>
    `;
}

function renderRolePermissions() {
    const role = getSelectedRole();
    renderCurrentRole(role);
    if (!role) {
        elements.rolePermissionList.innerHTML = '<p class="empty">请先选择角色</p>';
        return;
    }

    const appCode = elements.roleAppSelect.value;
    if (!appCode) {
        elements.rolePermissionList.innerHTML = '<p class="empty">请先选择接入应用</p>';
        return;
    }

    const checkedCodes = new Set((role.permissions || []).map(permission => permission.code));
    const appPermissions = state.permissions.filter(permission => permission.appCode === appCode);
    elements.rolePermissionList.innerHTML = appPermissions.map(permission => `
        <label class="check-item">
            <input type="checkbox" value="${permission.id}" ${checkedCodes.has(permission.code) ? "checked" : ""}>
            <span>
                <strong>${escapeHtml(permission.name)}</strong>
                <span>${escapeHtml(permission.appName || permission.appCode || "-")} / ${escapeHtml(permission.code)}</span>
            </span>
        </label>
    `).join("") || '<p class="empty">当前应用暂无可分配权限</p>';
}

function getSelectedRole() {
    const roleId = Number(elements.roleSelect.value);
    return state.roles.find(role => Number(role.id) === roleId);
}

function resetPermissionForm() {
    elements.permissionId.value = "";
    renderPermissionAppOptions();
    elements.permissionCode.value = "";
    elements.permissionName.value = "";
    elements.permissionCode.focus();
}

function resetClientAppForm() {
    elements.clientAppId.value = "";
    elements.clientAppCode.value = "";
    elements.clientAppName.value = "";
    elements.clientAppDescription.value = "";
    elements.clientAppEnabled.value = "true";
    elements.clientAppCode.focus();
}

function resetUserForm() {
    elements.userId.value = "";
    elements.userUsername.value = "";
    elements.userUsername.readOnly = false;
    elements.userPassword.value = "";
    elements.userPassword.required = true;
    elements.userDisplayName.value = "";
    elements.userEnabled.value = "true";
    renderUserRoleOptions();
    elements.userUsername.focus();
}

function resetRoleForm() {
    elements.roleId.value = "";
    elements.roleCode.value = "";
    elements.roleName.value = "";
    elements.roleCode.focus();
}

function getSelectedUserRoleIds() {
    return [...elements.userRoleList.querySelectorAll("input:checked")]
            .map(input => Number(input.value));
}

async function saveUser(event) {
    event.preventDefault();
    const id = elements.userId.value;
    const roleIds = getSelectedUserRoleIds();
    const payload = {
        displayName: elements.userDisplayName.value.trim(),
        enabled: elements.userEnabled.value === "true"
    };

    if (!id) {
        payload.username = elements.userUsername.value.trim();
        payload.password = elements.userPassword.value;
        payload.roleIds = roleIds;
    }

    setBusy(elements.userForm.querySelector("button"), true);
    try {
        await requestJson(id ? `/api/users/${id}` : "/api/users", {
            method: id ? "PUT" : "POST",
            body: JSON.stringify(payload)
        });
        if (id) {
            await requestJson(`/api/users/${id}/roles`, {
                method: "PUT",
                body: JSON.stringify({roleIds})
            });
        }
        resetUserForm();
        await loadAll();
        showToast(id ? "账号已更新" : "账号已创建");
    } catch (error) {
        showToast(error.message, "error");
    } finally {
        setBusy(elements.userForm.querySelector("button"), false);
    }
}

async function saveRole(event) {
    event.preventDefault();
    const id = elements.roleId.value;
    const payload = {
        code: elements.roleCode.value.trim(),
        name: elements.roleName.value.trim()
    };

    setBusy(elements.roleForm.querySelector("button"), true);
    try {
        await requestJson(id ? `/api/roles/${id}` : "/api/roles", {
            method: id ? "PUT" : "POST",
            body: JSON.stringify(payload)
        });
        resetRoleForm();
        await loadAll();
        showToast(id ? "角色已更新" : "角色已创建");
    } catch (error) {
        showToast(error.message, "error");
    } finally {
        setBusy(elements.roleForm.querySelector("button"), false);
    }
}

async function saveClientApp(event) {
    event.preventDefault();
    const id = elements.clientAppId.value;
    const payload = {
        code: elements.clientAppCode.value.trim(),
        name: elements.clientAppName.value.trim(),
        description: elements.clientAppDescription.value.trim(),
        enabled: elements.clientAppEnabled.value === "true"
    };

    setBusy(elements.clientAppForm.querySelector("button"), true);
    try {
        const savedApp = await requestJson(id ? `/api/client-apps/${id}` : "/api/client-apps", {
            method: id ? "PUT" : "POST",
            body: JSON.stringify(payload)
        });
        resetClientAppForm();
        focusListOn("clientApps", savedApp.code);
        await loadAll();
        showToast(id ? "接入应用已更新" : "接入应用已创建");
    } catch (error) {
        showToast(error.message, "error");
    } finally {
        setBusy(elements.clientAppForm.querySelector("button"), false);
    }
}

async function savePermission(event) {
    event.preventDefault();
    const id = elements.permissionId.value;
    const payload = {
        code: elements.permissionCode.value.trim(),
        name: elements.permissionName.value.trim(),
        appCode: elements.permissionAppCode.value
    };

    setBusy(elements.permissionForm.querySelector("button"), true);
    try {
        await requestJson(id ? `/api/permissions/${id}` : "/api/permissions", {
            method: id ? "PUT" : "POST",
            body: JSON.stringify(payload)
        });
        resetPermissionForm();
        await loadAll();
        showToast(id ? "权限已更新" : "权限已创建");
    } catch (error) {
        showToast(error.message, "error");
    } finally {
        setBusy(elements.permissionForm.querySelector("button"), false);
    }
}

async function handlePermissionTableClick(event) {
    const button = event.target.closest("button[data-action]");
    if (!button) {
        return;
    }

    const permission = state.permissions.find(item => String(item.id) === button.dataset.id);
    if (!permission) {
        return;
    }

    if (button.dataset.action === "edit") {
        elements.permissionId.value = permission.id;
        renderPermissionAppOptions(permission.appCode);
        elements.permissionCode.value = permission.code;
        elements.permissionName.value = permission.name;
        elements.permissionCode.focus();
        return;
    }

    if (!window.confirm(`确认删除权限 ${permission.code} 吗？`)) {
        return;
    }

    try {
        await requestJson(`/api/permissions/${permission.id}`, {method: "DELETE"});
        await loadAll();
        showToast("权限已删除");
    } catch (error) {
        showToast(error.message, "error");
    }
}

async function handleClientAppTableClick(event) {
    const button = event.target.closest("button[data-action]");
    if (!button) {
        return;
    }

    const clientApp = state.clientApps.find(item => String(item.id) === button.dataset.id);
    if (!clientApp) {
        return;
    }

    if (button.dataset.action === "edit") {
        elements.clientAppId.value = clientApp.id;
        elements.clientAppCode.value = clientApp.code;
        elements.clientAppName.value = clientApp.name;
        elements.clientAppDescription.value = clientApp.description || "";
        elements.clientAppEnabled.value = String(Boolean(clientApp.enabled));
        elements.clientAppCode.focus();
        return;
    }

    if (!window.confirm(`确认删除接入应用 ${clientApp.code} 吗？应用下还有权限时不能删除。`)) {
        return;
    }

    try {
        await requestJson(`/api/client-apps/${clientApp.id}`, {method: "DELETE"});
        await loadAll();
        showToast("接入应用已删除");
    } catch (error) {
        showToast(error.message, "error");
    }
}

async function handleRoleTableClick(event) {
    const button = event.target.closest("button[data-action]");
    if (!button) {
        return;
    }

    const role = state.roles.find(item => String(item.id) === button.dataset.id);
    if (!role) {
        return;
    }

    if (button.dataset.action === "edit") {
        elements.roleId.value = role.id;
        elements.roleCode.value = role.code;
        elements.roleName.value = role.name;
        elements.roleCode.focus();
        return;
    }

    if (!window.confirm(`确认删除角色 ${role.code} 吗？已分配该角色的账号会同步解绑。`)) {
        return;
    }

    try {
        await requestJson(`/api/roles/${role.id}`, {method: "DELETE"});
        resetRoleForm();
        await loadAll();
        showToast("角色已删除");
    } catch (error) {
        showToast(error.message, "error");
    }
}

async function handleUserTableClick(event) {
    const button = event.target.closest("button[data-action]");
    if (!button) {
        return;
    }

    const user = state.users.find(item => String(item.id) === button.dataset.id);
    if (!user) {
        return;
    }

    if (button.dataset.action === "edit") {
        elements.userId.value = user.id;
        elements.userUsername.value = user.username;
        elements.userUsername.readOnly = true;
        elements.userPassword.value = "";
        elements.userPassword.required = false;
        elements.userDisplayName.value = user.displayName;
        elements.userEnabled.value = String(Boolean(user.enabled));
        renderUserRoleOptions(user.roles || []);
        elements.userDisplayName.focus();
        return;
    }

    if (button.dataset.action === "reset-password") {
        const newPassword = window.prompt(`请输入 ${user.username} 的新密码，至少 6 位`);
        if (newPassword === null) {
            return;
        }
        if (newPassword.length < 6) {
            showToast("新密码至少 6 位", "error");
            return;
        }
        try {
            await requestJson(`/api/auth/password/reset/${user.id}`, {
                method: "POST",
                body: JSON.stringify({newPassword})
            });
            showToast("密码已重置");
        } catch (error) {
            showToast(error.message, "error");
        }
        return;
    }

    if (!window.confirm(`确认删除账号 ${user.username} 吗？`)) {
        return;
    }

    try {
        await requestJson(`/api/users/${user.id}`, {method: "DELETE"});
        resetUserForm();
        await loadAll();
        showToast("账号已删除");
    } catch (error) {
        showToast(error.message, "error");
    }
}

async function saveRolePermissions() {
    const role = getSelectedRole();
    if (!role) {
        showToast("请先选择角色", "error");
        return;
    }

    const appCode = elements.roleAppSelect.value;
    if (!appCode) {
        showToast("请先选择接入应用", "error");
        return;
    }

    const preservedPermissionIds = (role.permissions || [])
            .filter(permission => permission.appCode !== appCode)
            .map(permission => Number(permission.id));
    const selectedPermissionIds = [...elements.rolePermissionList.querySelectorAll("input:checked")]
            .map(input => Number(input.value));
    const permissionIds = [...new Set([...preservedPermissionIds, ...selectedPermissionIds])];

    setBusy(elements.saveRolePermissionsBtn, true);
    try {
        await requestJson(`/api/roles/${role.id}/permissions`, {
            method: "PUT",
            body: JSON.stringify({permissionIds})
        });
        await loadAll();
        showToast("角色权限已保存");
    } catch (error) {
        showToast(error.message, "error");
    } finally {
        setBusy(elements.saveRolePermissionsBtn, false);
    }
}

elements.loginForm.addEventListener("submit", login);
elements.captchaImageBtn.addEventListener("click", loadCaptcha);
elements.refreshCaptchaBtn.addEventListener("click", loadCaptcha);
elements.logoutBtn.addEventListener("click", logout);
elements.refreshBtn.addEventListener("click", async () => {
    try {
        await loadAll();
        showToast("数据已刷新");
    } catch (error) {
        showToast(error.message, "error");
    }
});
elements.userForm.addEventListener("submit", saveUser);
elements.resetUserFormBtn.addEventListener("click", resetUserForm);
elements.userTable.addEventListener("click", handleUserTableClick);
elements.userSearchForm.addEventListener("submit", event => runListAction(() => submitListSearch("users", event)));
elements.userPager.addEventListener("click", event => runListAction(() => handlePagerClick(event)));
elements.roleForm.addEventListener("submit", saveRole);
elements.resetRoleFormBtn.addEventListener("click", resetRoleForm);
elements.roleTable.addEventListener("click", handleRoleTableClick);
elements.roleSearchForm.addEventListener("submit", event => runListAction(() => submitListSearch("roles", event)));
elements.rolePager.addEventListener("click", event => runListAction(() => handlePagerClick(event)));
elements.clientAppForm.addEventListener("submit", saveClientApp);
elements.resetClientAppFormBtn.addEventListener("click", resetClientAppForm);
elements.clientAppTable.addEventListener("click", handleClientAppTableClick);
elements.clientAppSearchForm.addEventListener("submit", event => runListAction(() => submitListSearch("clientApps", event)));
elements.clientAppPager.addEventListener("click", event => runListAction(() => handlePagerClick(event)));
elements.permissionForm.addEventListener("submit", savePermission);
elements.resetFormBtn.addEventListener("click", resetPermissionForm);
elements.permissionTable.addEventListener("click", handlePermissionTableClick);
elements.permissionSearchForm.addEventListener("submit", event => runListAction(() => submitListSearch("permissions", event)));
elements.permissionPager.addEventListener("click", event => runListAction(() => handlePagerClick(event)));
elements.roleSelect.addEventListener("change", renderRolePermissions);
elements.roleAppSelect.addEventListener("change", renderRolePermissions);
elements.saveRolePermissionsBtn.addEventListener("click", saveRolePermissions);
document.querySelectorAll("[data-reset-list]").forEach(button => {
    button.addEventListener("click", () => runListAction(() => resetListSearch(button.dataset.resetList)));
});
elements.moduleNavItems.forEach(item => {
    item.addEventListener("click", () => switchAdminModule(item.dataset.moduleTarget));
});

setSession(Boolean(state.token));
if (state.token) {
    loadAll().catch(error => {
        showToast(error.message, "error");
        logout();
    });
} else {
    loadCaptcha().catch(error => showToast(error.message, "error"));
}
