(() => {
  const apiBase = document.querySelector('meta[name="api-base"]')?.content || '/api/v1';
  const rows = document.getElementById('role-rows');
  if (!rows) return;
  const token = document.querySelector('meta[name="csrf-token"]')?.content;
  const dialog = document.getElementById('role-dialog');
  const confirmRoleDialog = document.getElementById('confirm-role-dialog');
  const form = document.getElementById('role-form');
  const notice = document.getElementById('role-notice');
  const csrfHeader = document.querySelector('meta[name="csrf-header"]')?.content || 'X-CSRF-TOKEN';
  const headers = () => ({
    'Content-Type': 'application/json',
    ...(token ? { [csrfHeader]: token } : {}),
  });
  const esc = value =>
    String(value ?? '').replace(/[&<>"']/g, c =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  let roles = [];
  let editing;
  let pendingDeleteRole = null;

  // Cross-tab synchronization via BroadcastChannel with localStorage fallback
  const syncChannel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('kitchensink_sync') : null;
  function notifySync(type) {
    if (syncChannel) syncChannel.postMessage({ type, at: Date.now() });
    try { localStorage.setItem('kitchensink_sync_event', JSON.stringify({ type, at: Date.now() })); } catch (_) {}
  }
  if (syncChannel) {
    syncChannel.onmessage = event => {
      if (event?.data?.type === 'ROLES_UPDATED' || event?.data?.type === 'USERS_UPDATED') {
        load();
      }
    };
  }
  window.addEventListener('storage', event => {
    if (event.key === 'kitchensink_sync_event' && event.newValue) {
      try {
        const data = JSON.parse(event.newValue);
        if (data.type === 'ROLES_UPDATED' || data.type === 'USERS_UPDATED') {
          load();
        }
      } catch (_) {}
    }
  });

  const announce = (text, error = false) => {
    notice.textContent = text;
    notice.classList.toggle('error', error);
    notice.hidden = false;
  };

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

  async function load() {
    try {
      const response = await fetch(`${apiBase}/roles`);
      if (!response.ok) {
        const errorMsg = await parseErrorMessage(response, 'Could not load roles. Please try again.');
        throw new Error(errorMsg);
      }
      roles = await response.json();
      render();
    } catch (err) {
      rows.innerHTML = `<tr><td colspan="5" class="empty-cell">${esc(err.message)}</td></tr>`;
    }
  }

  function render() {
    rows.innerHTML =
      roles
        .map(
          role =>
            `<tr><td><strong>${esc(role.name)}</strong></td><td>${esc(role.description)}</td><td>${(role.permissions || []).map(p => `<span class="role-badge">${esc(p)}</span>`).join('')}</td><td>${role.isSystem ? 'System' : 'Custom'}</td><td><div class="user-controls"><button type="button" class="small-action" data-edit="${esc(role.name)}">Edit</button>${role.isSystem ? '' : `<button type="button" class="small-action delete" data-delete="${esc(role.name)}">Delete</button>`}</div></td></tr>`
        )
        .join('') || '<tr><td colspan="5" class="empty-cell">No roles configured.</td></tr>';
  }

  function validateRoleField(input) {
    const val = input.value.trim();
    let err = '';
    if (input.id === 'role-name') {
      if (!val) {
        err = 'Name is required';
      } else if (!/^[a-zA-Z0-9 -]{2,30}$/.test(val)) {
        err = 'Name must be 2 to 30 lowercase letters, digits, spaces, or hyphens';
      }
    } else if (input.id === 'role-description') {
      if (val.length > 200) {
        err = 'Description must be at most 200 characters';
      }
    }
    const errEl = form.querySelector(`[data-error="${input.name}"]`);
    if (errEl) errEl.textContent = err;
    return !err;
  }

  function checkRoleFormValidity() {
    const saveBtn = form.querySelector('[type="submit"]');
    if (!saveBtn) return;
    const nameVal = document.getElementById('role-name')?.value.trim();
    const hasChecked = form.querySelectorAll('input[name="permissions"]:checked').length > 0;
    if (!nameVal || !hasChecked) {
      saveBtn.disabled = true;
      return;
    }
    let hasError = false;
    ['role-name', 'role-description'].forEach(id => {
      const el = document.getElementById(id);
      if (el) {
        const errEl = form.querySelector(`[data-error="${el.name}"]`);
        if (errEl && errEl.textContent) hasError = true;
      }
    });
    saveBtn.disabled = hasError;
  }

  ['role-name', 'role-description'].forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('input', () => {
        validateRoleField(el);
        checkRoleFormValidity();
      });
      el.addEventListener('blur', () => {
        validateRoleField(el);
        checkRoleFormValidity();
      });
    }
  });

  form.querySelectorAll('input[name="permissions"]').forEach(input => {
    input.addEventListener('change', checkRoleFormValidity);
  });

  function open(role) {
    editing = role?.name || null;
    form.reset();
    form.querySelectorAll('.field-error').forEach(el => el.textContent = '');
    document.getElementById('role-title').textContent = role ? 'Edit role' : 'Add role';
    document.getElementById('role-name').value = role?.name || '';
    document.getElementById('role-name').readOnly = Boolean(role);
    document.getElementById('role-description').value = role?.description || '';
    form
      .querySelectorAll('input[name="permissions"]')
      .forEach(input => (input.checked = role?.permissions?.includes(input.value) || false));
    checkRoleFormValidity();
    dialog.showModal();
  }

  document.getElementById('add-role').addEventListener('click', () => open(null));
  document.getElementById('cancel-role').addEventListener('click', () => dialog.close());

  rows.addEventListener('click', async event => {
    const edit = event.target.closest('[data-edit]');
    const remove = event.target.closest('[data-delete]');
    if (edit) open(roles.find(role => role.name === edit.dataset.edit));
    if (remove) {
      pendingDeleteRole = remove.dataset.delete;
      document.getElementById('confirm-role-message').textContent =
        `Delete role ${remove.dataset.delete}? This cannot be undone.`;
      confirmRoleDialog.showModal();
    }
  });

  confirmRoleDialog?.addEventListener('close', async () => {
    if (confirmRoleDialog.returnValue !== 'confirm' || !pendingDeleteRole) return;
    const roleToDelete = pendingDeleteRole;
    pendingDeleteRole = null;
    try {
      const response = await fetch(`${apiBase}/roles/${encodeURIComponent(roleToDelete)}`, {
        method: 'DELETE',
        headers: headers(),
      });
      if (!response.ok) {
        const errorMsg = await parseErrorMessage(
          response,
          'This role may be assigned to users and cannot be deleted.'
        );
        throw new Error(errorMsg);
      }
      announce('Role deleted.');
      await load();
      notifySync('ROLES_UPDATED');
    } catch (error) {
      announce(error.message, true);
    }
  });

  form.addEventListener('submit', async event => {
    event.preventDefault();
    form.querySelectorAll('.field-error').forEach(el => el.textContent = '');
    const nameEl = document.getElementById('role-name');
    const descEl = document.getElementById('role-description');
    if (!validateRoleField(nameEl) || !validateRoleField(descEl)) return;

    const permissions = [...form.querySelectorAll('input[name="permissions"]:checked')].map(
      input => input.value
    );
    if (!permissions.length) {
      announce('At least one permission is required.', true);
      return;
    }
    const saveBtn = form.querySelector('[type="submit"]');
    saveBtn.disabled = true;
    saveBtn.textContent = 'Saving…';
    const body = JSON.stringify({
      name: nameEl.value.trim(),
      description: descEl.value.trim(),
      permissions,
    });
    try {
      const response = await fetch(
        editing ? `${apiBase}/roles/${encodeURIComponent(editing)}` : `${apiBase}/roles`,
        {
          method: editing ? 'PUT' : 'POST',
          headers: headers(),
          body,
        }
      );
      if (!response.ok) {
        const errorMsg = await parseErrorMessage(
          response,
          'Could not save role. Review the name and permission choices.'
        );
        throw new Error(errorMsg);
      }
      dialog.close();
      announce('Role saved.');
      await load();
      notifySync('ROLES_UPDATED');
    } catch (error) {
      announce(error.message, true);
    } finally {
      saveBtn.textContent = 'Save role';
      checkRoleFormValidity();
    }
  });

  document.querySelector('.menu-toggle')?.addEventListener('click', event => {
    const nav = document.getElementById('primary-nav');
    const open = nav.classList.toggle('open');
    event.currentTarget.setAttribute('aria-expanded', String(open));
  });

  load();
})();
