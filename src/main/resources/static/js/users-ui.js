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

  /* ── Load users + roles in parallel ──────────────────────────────────── */
  async function load() {
    try {
      const [userResp, roleResp] = await Promise.all([
        fetch(`${apiBase}/users`, { headers: headers() }),
        fetch(`${apiBase}/roles`, { headers: headers() }),
      ]);
      if (!userResp.ok) throw new Error('Could not load user accounts. Please try again.');
      if (!roleResp.ok) throw new Error('Could not load roles.');
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
      // 'id' is the plain hex string added by the server; fall back to stringifying _id
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

  /* ── Add-user dialog ──────────────────────────────────────────────────── */
  document.getElementById('show-invite').addEventListener('click', () => userDialog.showModal());
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
    if (confirmUserDialog.returnValue !== 'confirm' || !pendingUserAction) return;
    const { id, isActive } = pendingUserAction;
    pendingUserAction = null;
    try {
      const response = await fetch(`${apiBase}/users/${id}`, {
        method: 'PUT',
        headers: headers(true),
        body: JSON.stringify({ isActive }),
      });
      if (!response.ok) {
        const body = await response.text().catch(() => '');
        throw new Error(body || 'The account could not be updated.');
      }
      message(`Account ${isActive ? 'activated' : 'deactivated'}.`);
      await load();
    } catch (err) { message(err.message, true); }
  });

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
      if (!response.ok) throw new Error('Could not register the identity. Check that the email is valid and roles are selected.');
      userDialog.close();
      event.currentTarget.reset();
      message('User account created.');
      await load();
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
        const body = await response.text().catch(() => '');
        throw new Error(body || 'Could not update roles. Self-role and last-admin changes are protected.');
      }
      rolesDialog.close();
      message('Roles updated.');
      await load();
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
