const AUTH_BASE = '/auth';
const ADMIN_API = `${AUTH_BASE}/admin/api`;
const SYSTEM_API_ID = '11111111-1111-1111-1111-111111111111';

let session = JSON.parse(sessionStorage.getItem('darmoz_admin_session') || 'null');
if (!session || !session.accessToken) {
  window.location.href = 'index.html';
}

document.getElementById('session-email').textContent = session.email || '';

function goToLogin() {
  sessionStorage.removeItem('darmoz_admin_session');
  window.location.href = 'index.html';
}

document.getElementById('logout-btn').addEventListener('click', goToLogin);

function showGlobalError(message) {
  const el = document.getElementById('global-error');
  el.textContent = message || '';
}

// El accessToken dura 12 min (default); si el admin deja el dashboard
// abierto y después borra/edita algo, expira antes de que se haga el
// request. Un solo refresh en vuelo compartido entre requests concurrentes
// (no disparar /auth/refresh en paralelo con el mismo refreshToken: el
// servidor lo trata como reuso y revoca todas las sesiones).
let refreshPromise = null;

async function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const response = await fetch(`${AUTH_BASE}/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'API_ID': SYSTEM_API_ID },
          body: JSON.stringify({ refreshToken: session.refreshToken }),
        });
        if (!response.ok) return false;
        const auth = await response.json();
        session = { ...session, accessToken: auth.accessToken, refreshToken: auth.refreshToken };
        sessionStorage.setItem('darmoz_admin_session', JSON.stringify(session));
        return true;
      } catch (err) {
        return false;
      } finally {
        refreshPromise = null;
      }
    })();
  }
  return refreshPromise;
}

async function authFetch(path, options = {}, isRetry = false) {
  const response = await fetch(path, {
    ...options,
    headers: {
      ...(options.headers || {}),
      Authorization: `Bearer ${session.accessToken}`,
    },
  });

  // 401 = token ausente/inválido/expirado (posible arreglar con refresh).
  // 403 = autenticado pero sin el rol SUPER: un refresh no lo va a arreglar.
  if (response.status === 401 && !isRetry) {
    const refreshed = await refreshSession();
    if (refreshed) {
      return authFetch(path, options, true);
    }
  }

  if (response.status === 401 || response.status === 403) {
    goToLogin();
    throw new Error('sesion invalida');
  }
  return response;
}

async function authFetchJson(path, options = {}) {
  const response = await authFetch(path, options);
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message || `Error ${response.status}`);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

let rolesCache = [];
let applicationsCache = [];

document.querySelectorAll('.tab-btn').forEach((btn) => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.tab-btn').forEach((b) => b.classList.remove('active'));
    document.querySelectorAll('.panel').forEach((p) => p.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById(`panel-${btn.dataset.tab}`).classList.add('active');
  });
});

function escapeHtml(value) {
  const div = document.createElement('div');
  div.textContent = value ?? '';
  return div.innerHTML;
}

async function loadRoles() {
  rolesCache = await authFetchJson(`${ADMIN_API}/roles`);
  renderRolesTable();
  renderRoleDropdowns();
}

function renderRoleDropdowns() {
  renderPermissionRoleDropdown();
  filterUserRolesByApplication();
}

function renderPermissionRoleDropdown() {
  const permissionRoleSelect = document.getElementById('permission-role');
  permissionRoleSelect.innerHTML = rolesCache
    .map((role) => `<option value="${role.id}">${escapeHtml(role.applicationName)} / ${escapeHtml(role.name)}</option>`)
    .join('');
}

function filterUserRolesByApplication() {
  const userAppSelect = document.getElementById('user-application');
  const userRolesSelect = document.getElementById('user-roles');
  const selectedAppId = userAppSelect.value;
  userRolesSelect.innerHTML = rolesCache
    .filter((role) => role.applicationId === selectedAppId)
    .map((role) => `<option value="${escapeHtml(role.name)}">${escapeHtml(role.name)}</option>`)
    .join('');
}

document.getElementById('user-application').addEventListener('change', filterUserRolesByApplication);

function renderRolesTable() {
  const body = document.getElementById('roles-table-body');
  body.innerHTML = rolesCache.map((role) => `
    <tr>
      <td>${escapeHtml(role.applicationName)}</td>
      <td>${escapeHtml(role.name)}</td>
      <td>${escapeHtml(role.description)}</td>
      <td class="actions">
        <button type="button" class="danger" data-delete-role="${role.id}">Borrar</button>
      </td>
    </tr>
  `).join('');

  body.querySelectorAll('[data-delete-role]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      if (!confirm('¿Borrar este rol?')) return;
      try {
        await authFetchJson(`${ADMIN_API}/roles/${btn.dataset.deleteRole}`, { method: 'DELETE' });
        showGlobalError('');
        await loadRoles();
      } catch (err) {
        showGlobalError(err.message);
      }
    });
  });
}

document.getElementById('role-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const name = document.getElementById('role-name').value;
  const description = document.getElementById('role-description').value;
  const applicationId = document.getElementById('role-application').value;
  try {
    await authFetchJson(`${ADMIN_API}/roles`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, description, applicationId }),
    });
    document.getElementById('role-form').reset();
    showGlobalError('');
    await loadRoles();
  } catch (err) {
    showGlobalError(err.message);
  }
});

async function loadUsers() {
  const users = await authFetchJson(`${ADMIN_API}/users`);
  renderUsersTable(users);
}

function renderUsersTable(users) {
  const body = document.getElementById('users-table-body');
  body.innerHTML = users.map((user) => `
    <tr data-user-row="${user.id}" data-application-id="${user.applicationId}">
      <td>${escapeHtml(user.email)}</td>
      <td>${escapeHtml(user.applicationName)}</td>
      <td>${user.enabled ? 'Habilitado' : 'Deshabilitado'}</td>
      <td>${user.roles.map((r) => `<span class="badge">${escapeHtml(r)}</span>`).join('')}</td>
      <td class="actions">
        <button type="button" data-toggle="${user.id}" data-enabled="${user.enabled}">
          ${user.enabled ? 'Deshabilitar' : 'Habilitar'}
        </button>
        <button type="button" data-edit-roles="${user.id}">Roles</button>
        <button type="button" class="danger" data-delete-user="${user.id}">Borrar</button>
      </td>
    </tr>
  `).join('');

  body.querySelectorAll('[data-toggle]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      try {
        const nextEnabled = btn.dataset.enabled !== 'true';
        await authFetchJson(`${ADMIN_API}/users/${btn.dataset.toggle}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ enabled: nextEnabled }),
        });
        showGlobalError('');
        await loadUsers();
      } catch (err) {
        showGlobalError(err.message);
      }
    });
  });

  body.querySelectorAll('[data-delete-user]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      if (!confirm('¿Borrar este usuario?')) return;
      try {
        await authFetchJson(`${ADMIN_API}/users/${btn.dataset.deleteUser}`, { method: 'DELETE' });
        showGlobalError('');
        await loadUsers();
      } catch (err) {
        showGlobalError(err.message);
      }
    });
  });

  body.querySelectorAll('[data-edit-roles]').forEach((btn) => {
    btn.addEventListener('click', () => startRoleEdit(btn.dataset.editRoles));
  });
}

function startRoleEdit(userId) {
  const parentRow = document.querySelector(`tr[data-user-row="${userId}"]`);
  const applicationId = parentRow.dataset.applicationId;
  const row = parentRow.querySelector('td:nth-child(4)');
  const options = rolesCache
    .filter((role) => role.applicationId === applicationId)
    .map((role) => `<option value="${escapeHtml(role.name)}">${escapeHtml(role.name)}</option>`)
    .join('');
  row.innerHTML = `
    <select multiple id="edit-roles-${userId}">${options}</select>
    <button type="button" data-save-roles="${userId}">Guardar</button>
  `;
  document.getElementById(`edit-roles-${userId}`).addEventListener('change', () => {});
  row.querySelector(`[data-save-roles="${userId}"]`).addEventListener('click', async () => {
    const select = document.getElementById(`edit-roles-${userId}`);
    const roles = Array.from(select.selectedOptions).map((o) => o.value);
    if (roles.length === 0) {
      showGlobalError('Elegí al menos un rol.');
      return;
    }
    try {
      await authFetchJson(`${ADMIN_API}/users/${userId}/roles`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ roles }),
      });
      showGlobalError('');
      await loadUsers();
    } catch (err) {
      showGlobalError(err.message);
    }
  });
}

document.getElementById('user-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const email = document.getElementById('user-email').value;
  const password = document.getElementById('user-password').value;
  const applicationId = document.getElementById('user-application').value;
  const roles = Array.from(document.getElementById('user-roles').selectedOptions).map((o) => o.value);
  if (roles.length === 0) {
    showGlobalError('Elegí al menos un rol.');
    return;
  }
  try {
    await authFetchJson(`${ADMIN_API}/users`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, applicationId, roles }),
    });
    document.getElementById('user-form').reset();
    showGlobalError('');
    await loadUsers();
    filterUserRolesByApplication();
  } catch (err) {
    showGlobalError(err.message);
  }
});

async function loadPermissions() {
  const permissions = await authFetchJson(`${ADMIN_API}/role-permissions`);
  renderPermissionsTable(permissions);
}

function renderPermissionsTable(permissions) {
  const body = document.getElementById('permissions-table-body');
  body.innerHTML = permissions.map((permission) => `
    <tr>
      <td>${escapeHtml(permission.applicationName)}</td>
      <td><span class="badge">${escapeHtml(permission.role)}</span></td>
      <td>${escapeHtml(permission.service)}</td>
      <td>${escapeHtml(permission.httpMethod)}</td>
      <td>${escapeHtml(permission.endpointPattern)}</td>
      <td class="actions">
        <button type="button" class="danger" data-delete-permission="${permission.id}">Borrar</button>
      </td>
    </tr>
  `).join('');

  body.querySelectorAll('[data-delete-permission]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      if (!confirm('¿Borrar este permiso?')) return;
      try {
        await authFetchJson(`${ADMIN_API}/role-permissions/${btn.dataset.deletePermission}`, { method: 'DELETE' });
        showGlobalError('');
        await loadPermissions();
      } catch (err) {
        showGlobalError(err.message);
      }
    });
  });
}

document.getElementById('permission-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const roleId = document.getElementById('permission-role').value;
  const service = document.getElementById('permission-service').value;
  const httpMethod = document.getElementById('permission-method').value;
  const endpointPattern = document.getElementById('permission-pattern').value;
  try {
    await authFetchJson(`${ADMIN_API}/role-permissions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ roleId, service, httpMethod, endpointPattern }),
    });
    document.getElementById('permission-form').reset();
    showGlobalError('');
    await loadPermissions();
  } catch (err) {
    showGlobalError(err.message);
  }
});

async function loadApplications() {
  applicationsCache = await authFetchJson(`${ADMIN_API}/applications`);
  renderApplicationsTable();
  renderApplicationDropdowns();
}

function renderApplicationDropdowns() {
  const options = applicationsCache
    .map((app) => `<option value="${app.id}">${escapeHtml(app.name)}</option>`)
    .join('');
  document.getElementById('user-application').innerHTML = options;
  document.getElementById('role-application').innerHTML = options;
}

function renderApplicationsTable() {
  const body = document.getElementById('applications-table-body');
  body.innerHTML = applicationsCache.map((app) => `
    <tr data-application-row="${app.id}">
      <td>${escapeHtml(app.serviceName)}</td>
      <td>${escapeHtml(app.name)}</td>
      <td>${escapeHtml(app.description)}</td>
      <td class="actions">
        <button type="button" data-edit-application="${app.id}">Editar</button>
        <button type="button" class="danger" data-delete-application="${app.id}">Borrar</button>
      </td>
    </tr>
  `).join('');

  body.querySelectorAll('[data-delete-application]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      if (!confirm('¿Borrar esta aplicación?')) return;
      try {
        await authFetchJson(`${ADMIN_API}/applications/${btn.dataset.deleteApplication}`, { method: 'DELETE' });
        showGlobalError('');
        await loadApplications();
      } catch (err) {
        showGlobalError(err.message);
      }
    });
  });

  body.querySelectorAll('[data-edit-application]').forEach((btn) => {
    btn.addEventListener('click', () => startApplicationEdit(btn.dataset.editApplication));
  });
}

function startApplicationEdit(appId) {
  const app = applicationsCache.find((a) => a.id === appId);
  const row = document.querySelector(`tr[data-application-row="${appId}"]`);
  row.innerHTML = `
    <td><input type="text" id="edit-app-service-${appId}" value="${escapeHtml(app.serviceName)}"></td>
    <td><input type="text" id="edit-app-name-${appId}" value="${escapeHtml(app.name)}"></td>
    <td><input type="text" id="edit-app-description-${appId}" value="${escapeHtml(app.description)}"></td>
    <td class="actions">
      <button type="button" data-save-application="${appId}">Guardar</button>
      <button type="button" class="secondary" data-cancel-application="${appId}">Cancelar</button>
    </td>
  `;
  row.querySelector(`[data-cancel-application="${appId}"]`).addEventListener('click', () => renderApplicationsTable());
  row.querySelector(`[data-save-application="${appId}"]`).addEventListener('click', async () => {
    const serviceName = document.getElementById(`edit-app-service-${appId}`).value;
    const name = document.getElementById(`edit-app-name-${appId}`).value;
    const description = document.getElementById(`edit-app-description-${appId}`).value;
    try {
      await authFetchJson(`${ADMIN_API}/applications/${appId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ serviceName, name, description }),
      });
      showGlobalError('');
      await loadApplications();
    } catch (err) {
      showGlobalError(err.message);
    }
  });
}

document.getElementById('application-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const serviceName = document.getElementById('application-service-name').value;
  const name = document.getElementById('application-name').value;
  const description = document.getElementById('application-description').value;
  try {
    await authFetchJson(`${ADMIN_API}/applications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ serviceName, name, description }),
    });
    document.getElementById('application-form').reset();
    showGlobalError('');
    await loadApplications();
  } catch (err) {
    showGlobalError(err.message);
  }
});

(async function init() {
  try {
    await loadApplications();
    await loadRoles();
    await loadUsers();
    await loadPermissions();
  } catch (err) {
    showGlobalError(err.message);
  }
})();
