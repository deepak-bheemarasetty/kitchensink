(() => {
  const apiBase = document.querySelector('meta[name="api-base"]')?.content || '/api/v1';
  const rows = document.getElementById('user-rows');
  if (!rows) return;
  const token = document.querySelector('meta[name="csrf-token"]')?.content;
  const csrfHeader = document.querySelector('meta[name="csrf-header"]')?.content || 'X-CSRF-TOKEN';
  const search = document.getElementById('user-search');
  const roleFilter = document.getElementById('role-filter');
  const activeFilter = document.getElementById('active-filter');
  const notice = document.getElementById('user-notice');
  const userDialog = document.getElementById('user-dialog');
  const rolesDialog = document.getElementById('roles-dialog');
  const confirmUserDialog = document.getElementById('confirm-user-dialog');

  let users = [];
  let allRoles = [];
  let selectedUser;
  let pendingUserAction = null;
  let searchTimer;

  // Cross-tab synchronization via BroadcastChannel with localStorage fallback
  const syncChannel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('kitchensink_sync') : null;
  function notifySync(type) {
    if (syncChannel) syncChannel.postMessage({ type, at: Date.now() });
    try { localStorage.setItem('kitchensink_sync_event', JSON.stringify({ type, at: Date.now() })); } catch (_) {}
  }
  if (syncChannel) {
    syncChannel.onmessage = event => {
      if (event?.data?.type === 'USERS_UPDATED' || event?.data?.type === 'ROLES_UPDATED') {
        load();
      }
    };
  }
  window.addEventListener('storage', event => {
    if (event.key === 'kitchensink_sync_event' && event.newValue) {
      try {
        const data = JSON.parse(event.newValue);
        if (data.type === 'USERS_UPDATED' || data.type === 'ROLES_UPDATED') {
          load();
        }
      } catch (_) {}
    }
  });

  const headers = (json = false) => ({
    ...(json ? { 'Content-Type': 'application/json' } : {}),
    ...(token ? { [csrfHeader]: token } : {}),
  });

  const escapeHtml = value =>
    String(value ?? '').replace(/[&<>"']/g, c =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  function message(text, error = false) {
    notice.textContent = text;
    notice.classList.toggle('error', error);
    notice.hidden = false;
  }

  async function parseErrorMessage(response, defaultMsg) {
    try {
      const contentType = response.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        const body = await response.json();
        const err = body.error || body;
        if (err.message) return err.message;
        if (err.fields && Array.isArray(err.fields) && err.fields.length > 0) {
          return err.fields.map(f => `${f.field}: ${f.message}`).join(', ');
        }
      } else {
        const text = await response.text();
        if (text && text.trim().length > 0) return text.trim();
      }
    } catch (_) {}
    return defaultMsg || `Request failed with status ${response.status}`;
  }

  /* ── Load users + roles in parallel ──────────────────────────────────── */
  async function load() {
    try {
      const [userResp, roleResp] = await Promise.all([
        fetch(`${apiBase}/users`, { headers: headers() }),
        fetch(`${apiBase}/roles`, { headers: headers() }),
      ]);
      if (!userResp.ok) {
        const errText = await parseErrorMessage(userResp, 'Could not load user accounts. Please try again.');
        throw new Error(errText);
      }
      if (!roleResp.ok) {
        const errText = await parseErrorMessage(roleResp, 'Could not load roles.');
        throw new Error(errText);
      }
      users = await userResp.json();
      allRoles = await roleResp.json();
      render();
    } catch (err) {
      rows.innerHTML = `<tr><td colspan="5" class="empty-cell">${escapeHtml(err.message)}</td></tr>`;
    }
  }

  /* ── Render user table ────────────────────────────────────────────────── */
  function render() {
    const q = search.value.trim().toLowerCase();
    const list = users.filter(u =>
      (!q || u.username.toLowerCase().includes(q)) &&
      (!roleFilter.value || u.roles.includes(roleFilter.value)) &&
      (!activeFilter.value || String(u.isActive) === activeFilter.value));

    if (!list.length) {
      rows.innerHTML = '<tr><td colspan="5" class="empty-cell">No users match these filters.</td></tr>';
      return;
    }

    rows.innerHTML = list.map(u => {
      const id = u.id || String(u._id);
      const mine = u.isSelf === true ||
        u.username.toLowerCase() === document.body.dataset.username?.toLowerCase();
      const roleBadges = u.roles.map(r => `<span class="role-badge">${escapeHtml(r)}</span>`).join('');
      const lastLogin = u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleDateString() : 'Never';
      const actions = mine
        ? '<span class="search-summary">Your account</span>'
        : `<button class="small-action" type="button" data-roles="${escapeHtml(id)}">Manage roles</button>
           <button class="small-action" type="button"
             data-active="${escapeHtml(id)}"
             data-next="${!u.isActive}">${u.isActive ? 'Deactivate' : 'Activate'}</button>`;
      return `<tr>
        <td><strong>${escapeHtml(u.username)}</strong><div class="member-meta">Account email</div></td>
        <td>${roleBadges || '—'}</td>
        <td><span class="status-badge ${u.isActive ? '' : 'inactive'}">${u.isActive ? 'Active' : 'Inactive'}</span></td>
        <td>${escapeHtml(lastLogin)}</td>
        <td><div class="user-controls">${actions}</div></td>
      </tr>`;
    }).join('');
  }

  /* ── Filter / search events ───────────────────────────────────────────── */
  search.addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(render, 200); });
  roleFilter.addEventListener('change', render);
  activeFilter.addEventListener('change', render);

  /* ── Add-user dialog & validation ─────────────────────────────────────── */
  const userForm = document.getElementById('user-form');
  const newUsernameInput = document.getElementById('new-username');
  const userSubmitBtn = userForm.querySelector('[type="submit"]');

  function validateUserEmail() {
    const val = newUsernameInput.value.trim();
    let err = '';
    if (!val) {
      err = 'Email Address is required';
    } else if (!/^[A-Za-z0-9_+\-]+(?:\.[A-Za-z0-9_+\-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$/.test(val)) {
      err = "Email Address isn't valid";
    }
    const errEl = userForm.querySelector('[data-error="username"]');
    if (errEl) errEl.textContent = err;
    return !err;
  }

  function checkUserFormValidity() {
    const val = newUsernameInput.value.trim();
    const hasChecked = userForm.querySelectorAll('input[name="roles"]:checked').length > 0;
    const errEl = userForm.querySelector('[data-error="username"]');
    const hasError = errEl && Boolean(errEl.textContent);
    userSubmitBtn.disabled = !val || !hasChecked || hasError;
  }

  newUsernameInput.addEventListener('input', () => {
    validateUserEmail();
    checkUserFormValidity();
  });
  newUsernameInput.addEventListener('blur', () => {
    validateUserEmail();
    checkUserFormValidity();
  });
  userForm.querySelectorAll('input[name="roles"]').forEach(cb => {
    cb.addEventListener('change', checkUserFormValidity);
  });

  const rolesForm = document.getElementById('roles-form');
  const rolesSubmitBtn = rolesForm.querySelector('[type="submit"]');
  function checkRolesFormValidity() {
    const hasChecked = rolesForm.querySelectorAll('input[name="roles"]:checked').length > 0;
    rolesSubmitBtn.disabled = !hasChecked;
  }

  document.getElementById('show-invite').addEventListener('click', () => {
    userForm.reset();
    userForm.querySelectorAll('.field-error').forEach(el => el.textContent = '');
    checkUserFormValidity();
    userDialog.showModal();
  });
  document.getElementById('cancel-user').addEventListener('click', () => userDialog.close());

  /* ── Roles panel ──────────────────────────────────────────────────────── */
  document.getElementById('cancel-roles').addEventListener('click', () => rolesDialog.close());

  /* ── Delegated click for Manage-roles / Activate / Deactivate ────────── */
  document.addEventListener('click', event => {
    const roleBtn = event.target.closest('[data-roles]');
    const activeBtn = event.target.closest('[data-active]');

    if (roleBtn) {
      const id = roleBtn.dataset.roles;
      selectedUser = users.find(u => (u.id || String(u._id)) === id);
      if (!selectedUser) return;

      document.getElementById('roles-user-label').textContent = selectedUser.username;
      document.getElementById('role-options').innerHTML = allRoles.map(r => `
        <label>
          <input type="checkbox" name="roles" value="${escapeHtml(r.name)}"
            ${selectedUser.roles.includes(r.name) ? 'checked' : ''}>
          <span>
            <strong>${escapeHtml(r.name)}</strong>
            ${r.description ? `<small>${escapeHtml(r.description)}</small>` : ''}
            ${r.permissions?.length ? `<small class="role-permissions">${escapeHtml(r.permissions.join(', '))}</small>` : ''}
          </span>
        </label>`).join('');
      document.getElementById('role-options').querySelectorAll('input[name="roles"]').forEach(cb => {
        cb.addEventListener('change', checkRolesFormValidity);
      });
      checkRolesFormValidity();
      rolesDialog.showModal();
    }

    if (activeBtn) {
      const id = activeBtn.dataset.active;
      const next = activeBtn.dataset.next === 'true';
      const user = users.find(u => (u.id || String(u._id)) === id);
      if (!user) return;
      pendingUserAction = { id, isActive: next };
      const actionTitle = next ? 'Activate user?' : 'Deactivate user?';
      const actionBtnText = next ? 'Activate' : 'Deactivate';
      document.getElementById('confirm-user-title').textContent = actionTitle;
      document.getElementById('confirm-user-message').textContent = `${next ? 'Activate' : 'Deactivate'} account for ${user.username}?`;
      const confirmBtn = document.getElementById('confirm-user-action-btn');
      confirmBtn.textContent = actionBtnText;
      confirmBtn.className = next ? 'primary' : 'danger-button';
      confirmUserDialog.showModal();
    }
  });

  confirmUserDialog?.addEventListener('close', async () => {
    if (confirmRoleDialogOrDefault(confirmUserDialog) !== 'confirm' || !pendingUserAction) return;
    const { id, isActive } = pendingUserAction;
    pendingUserAction = null;
    try {
      const response = await fetch(`${apiBase}/users/${id}`, {
        method: 'PUT',
        headers: headers(true),
        body: JSON.stringify({ isActive }),
      });
      if (!response.ok) {
        const errorMsg = await parseErrorMessage(response, 'The account could not be updated.');
        throw new Error(errorMsg);
      }
      message(`Account ${isActive ? 'activated' : 'deactivated'}.`);
      await load();
      notifySync('USERS_UPDATED');
    } catch (err) { message(err.message, true); }
  });

  function confirmRoleDialogOrDefault(dlg) {
    return dlg.returnValue;
  }

  /* ── Create new user  →  POST /api/v1/users/identity ─────────────────── */
  document.getElementById('user-form').addEventListener('submit', async event => {
    event.preventDefault();
    const checked = [...event.currentTarget.querySelectorAll('input[name="roles"]:checked')].map(i => i.value);
    const payload = {
      username: document.getElementById('new-username').value.trim().toLowerCase(),
      roles: checked,
    };
    try {
      const response = await fetch(`${apiBase}/users/identity`, {
        method: 'POST', headers: headers(true), body: JSON.stringify(payload),
      });
      if (!response.ok) {
        const errorMsg = await parseErrorMessage(response, 'Could not register the identity. Check that the email is valid and roles are selected.');
        throw new Error(errorMsg);
      }
      userDialog.close();
      event.currentTarget.reset();
      message('User account created.');
      await load();
      notifySync('USERS_UPDATED');
    } catch (err) { message(err.message, true); }
  });

  /* ── Save role changes  →  PUT /api/v1/users/{id}/roles ──────────────── */
  document.getElementById('roles-form').addEventListener('submit', async event => {
    event.preventDefault();
    if (!selectedUser) return;
    const id = selectedUser.id || String(selectedUser._id);
    const roles = [...event.currentTarget.querySelectorAll('input[name="roles"]:checked')].map(i => i.value);
    try {
      const response = await fetch(`${apiBase}/users/${id}/roles`, {
        method: 'PUT', headers: headers(true), body: JSON.stringify({ roles }),
      });
      if (!response.ok) {
        const errorMsg = await parseErrorMessage(response, 'Could not update roles. Self-role and last-admin changes are protected.');
        throw new Error(errorMsg);
      }
      rolesDialog.close();
      message('Roles updated.');
      await load();
      notifySync('USERS_UPDATED');
    } catch (err) { message(err.message, true); }
  });

  /* ── Mobile nav ───────────────────────────────────────────────────────── */
  document.querySelector('.menu-toggle')?.addEventListener('click', event => {
    const nav = document.getElementById('primary-nav');
    const open = nav.classList.toggle('open');
    event.currentTarget.setAttribute('aria-expanded', String(open));
  });

  load();
})();
