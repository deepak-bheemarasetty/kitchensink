(() => {
  const apiBase = document.querySelector('meta[name="api-base"]')?.content || '/api/v1';
  const rows = document.getElementById('user-rows');
  if (!rows) return;
  const token = document.querySelector('meta[name="csrf-token"]')?.content;
  const search = document.getElementById('user-search');
  const roleFilter = document.getElementById('role-filter');
  const activeFilter = document.getElementById('active-filter');
  const notice = document.getElementById('user-notice');
  const userDialog = document.getElementById('user-dialog');
  const rolesDialog = document.getElementById('roles-dialog');
  let users = [];
  let selectedUser;
  let searchTimer;
  const csrfHeader = document.querySelector('meta[name="csrf-header"]')?.content || 'X-CSRF-TOKEN';
  const headers = (json = false) => ({...(json ? {'Content-Type':'application/json'} : {}), ...(token ? {[csrfHeader]:token} : {})});
  const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  function message(text, error = false) { notice.textContent = text; notice.classList.toggle('error', error); notice.hidden = false; }
  async function load() {
    try {
      const response = await fetch(`${apiBase}/users`, {headers:headers()});
      if (!response.ok) throw new Error('Could not load user accounts. Please try again.');
      users = await response.json(); render();
    } catch (error) { rows.innerHTML = `<tr><td colspan="5" class="empty-cell">${escapeHtml(error.message)}</td></tr>`; }
  }
  function render() {
    const q = search.value.trim().toLowerCase();
    const list = users.filter(user => (!q || user.username.toLowerCase().includes(q))
      && (!roleFilter.value || user.roles.includes(roleFilter.value))
      && (!activeFilter.value || String(user.isActive) === activeFilter.value));
    if (!list.length) { rows.innerHTML = '<tr><td colspan="5" class="empty-cell">No users match these filters.</td></tr>'; return; }
    rows.innerHTML = list.map(user => {
      const mine = user.isSelf === true || user.username.toLowerCase() === document.body.dataset.username?.toLowerCase();
      const roles = user.roles.map(role => `<span class="role-badge">${escapeHtml(role)}</span>`).join('');
      const lastLogin = user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleDateString() : 'Never';
      return `<tr><td><strong>${escapeHtml(user.username)}</strong><div class="member-meta">Account email</div></td><td>${roles || '—'}</td><td><span class="status-badge ${user.isActive ? '' : 'inactive'}">${user.isActive ? 'Active' : 'Inactive'}</span></td><td>${escapeHtml(lastLogin)}</td><td><div class="user-controls">${mine ? '<span class="search-summary">Your account</span>' : `<button class="small-action" type="button" data-roles="${user.id}">Manage roles</button><button class="small-action" type="button" data-active="${user.id}" data-next="${!user.isActive}">${user.isActive ? 'Deactivate' : 'Activate'}</button>`}</div></td></tr>`;
    }).join('');
  }
  document.getElementById('show-invite').addEventListener('click', () => userDialog.showModal());
  document.getElementById('cancel-user').addEventListener('click', () => userDialog.close());
  document.getElementById('cancel-roles').addEventListener('click', () => rolesDialog.close());
  [search, roleFilter, activeFilter].forEach(element => element.addEventListener(element === search ? 'input' : 'change', () => {
    if (element === search) { clearTimeout(searchTimer); searchTimer = setTimeout(render, 200); } else render();
  }));
  document.addEventListener('click', event => {
    const roleButton = event.target.closest('[data-roles]');
    const activeButton = event.target.closest('[data-active]');
    if (roleButton) {
      selectedUser = users.find(user => user.id === roleButton.dataset.roles);
      if (!selectedUser) return;
      document.getElementById('roles-user-label').textContent = selectedUser.username;
      document.getElementById('role-options').innerHTML = Array.from(roleFilter.options).slice(1).map(option =>
        `<label><input type="checkbox" name="roles" value="${escapeHtml(option.value)}" ${selectedUser.roles.includes(option.value) ? 'checked' : ''}><span><strong>${escapeHtml(option.value)}</strong></span></label>`).join('');
      rolesDialog.showModal();
    }
    if (activeButton) setActive(activeButton.dataset.active, activeButton.dataset.next === 'true');
  });
  async function setActive(id, isActive) {
    const user = users.find(entry => entry.id === id);
    if (!user || !window.confirm(`${isActive ? 'Activate' : 'Deactivate'} ${user.username}?`)) return;
    try {
      const response = await fetch(`${apiBase}/users/${id}`, {method:'PUT',headers:headers(true),body:JSON.stringify({isActive})});
      if (!response.ok) throw new Error('The account could not be updated. Check administrator safeguards and retry.');
      message(`Account ${isActive ? 'activated' : 'deactivated'}.`); await load();
    } catch (error) { message(error.message, true); }
  }
  document.getElementById('user-form').addEventListener('submit', async event => {
    event.preventDefault();
    const checked = [...event.currentTarget.querySelectorAll('input[name="roles"]:checked')].map(input => input.value);
    const payload = {username:document.getElementById('new-username').value.trim().toLowerCase(),roles:checked};
    try {
      const response = await fetch(`${apiBase}/users/identity`, {method:'POST',headers:headers(true),body:JSON.stringify(payload)});
      if (!response.ok) throw new Error('Could not register the identity. Check that the email is valid and roles are selected.');
      userDialog.close(); event.currentTarget.reset(); message('User account created.'); await load();
    } catch (error) { message(error.message,true); }
  });
  document.getElementById('roles-form').addEventListener('submit', async event => {
    event.preventDefault();
    if (!selectedUser) return;
    const roles = [...event.currentTarget.querySelectorAll('input[name="roles"]:checked')].map(input => input.value);
    try {
      const response = await fetch(`${apiBase}/users/${selectedUser.id}/roles`, {method:'PUT',headers:headers(true),body:JSON.stringify({roles})});
      if (!response.ok) throw new Error('Could not update roles. Self-role and last-admin changes are protected.');
      rolesDialog.close(); message('Roles updated.'); await load();
    } catch (error) { message(error.message,true); }
  });
  document.querySelector('.menu-toggle')?.addEventListener('click', event => {
    const nav = document.getElementById('primary-nav');
    const open = nav.classList.toggle('open'); event.currentTarget.setAttribute('aria-expanded',String(open));
  });
  load();
})();
