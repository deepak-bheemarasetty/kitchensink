(() => {
  const apiBase = document.querySelector('meta[name="api-base"]')?.content || '/api/v1';
  const rows = document.getElementById('role-rows');
  if (!rows) return;
  const token = document.querySelector('meta[name="csrf-token"]')?.content;
  const dialog = document.getElementById('role-dialog');
  const form = document.getElementById('role-form');
  const notice = document.getElementById('role-notice');
  const csrfHeader = document.querySelector('meta[name="csrf-header"]')?.content || 'X-CSRF-TOKEN';
  const headers = () => ({'Content-Type':'application/json',...(token ? {[csrfHeader]:token} : {})});
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  let roles = [];
  let editing;
  const announce = (text, error = false) => { notice.textContent=text; notice.classList.toggle('error',error); notice.hidden=false; };
  async function load() {
    try {
      const response = await fetch(`${apiBase}/roles`);
      if (!response.ok) throw new Error();
      roles = await response.json(); render();
    } catch { rows.innerHTML='<tr><td colspan="5" class="empty-cell">Could not load roles. Please try again.</td></tr>'; }
  }
  function render() {
    rows.innerHTML = roles.map(role => `<tr><td><strong>${esc(role.name)}</strong></td><td>${esc(role.description)}</td><td>${(role.permissions || []).map(p => `<span class="role-badge">${esc(p)}</span>`).join('')}</td><td>${role.isSystem ? 'System' : 'Custom'}</td><td><div class="user-controls"><button type="button" class="small-action" data-edit="${esc(role.name)}">Edit</button>${role.isSystem ? '' : `<button type="button" class="small-action delete" data-delete="${esc(role.name)}">Delete</button>`}</div></td></tr>`).join('') || '<tr><td colspan="5" class="empty-cell">No roles configured.</td></tr>';
  }
  function open(role) {
    editing = role?.name || null; form.reset();
    document.getElementById('role-title').textContent=role?'Edit role':'Add role';
    document.getElementById('role-name').value=role?.name || '';
    document.getElementById('role-name').readOnly=Boolean(role);
    document.getElementById('role-description').value=role?.description || '';
    form.querySelectorAll('input[name="permissions"]').forEach(input => input.checked=role?.permissions?.includes(input.value) || false);
    dialog.showModal();
  }
  document.getElementById('add-role').addEventListener('click',()=>open(null));
  document.getElementById('cancel-role').addEventListener('click',()=>dialog.close());
  rows.addEventListener('click',async event=>{
    const edit=event.target.closest('[data-edit]'); const remove=event.target.closest('[data-delete]');
    if(edit) open(roles.find(role=>role.name===edit.dataset.edit));
    if(remove && window.confirm(`Delete role ${remove.dataset.delete}?`)) {
      try { const response=await fetch(`${apiBase}/roles/${encodeURIComponent(remove.dataset.delete)}`,{method:'DELETE',headers:headers()}); if(!response.ok) throw new Error('This role may be assigned to users and cannot be deleted.'); announce('Role deleted.'); await load(); }
      catch(error) { announce(error.message,true); }
    }
  });
  form.addEventListener('submit',async event=>{
    event.preventDefault();
    const permissions=[...form.querySelectorAll('input[name="permissions"]:checked')].map(input=>input.value);
    const body=JSON.stringify({name:document.getElementById('role-name').value.trim(),description:document.getElementById('role-description').value.trim(),permissions});
    try { const response=await fetch(editing?`${apiBase}/roles/${encodeURIComponent(editing)}`:`${apiBase}/roles`,{method:editing?'PUT':'POST',headers:headers(),body}); if(!response.ok) throw new Error('Could not save role. Review the name and permission choices.'); dialog.close(); announce('Role saved.'); await load(); }
    catch(error) { announce(error.message,true); }
  });
  document.querySelector('.menu-toggle')?.addEventListener('click',event=>{const nav=document.getElementById('primary-nav');const open=nav.classList.toggle('open');event.currentTarget.setAttribute('aria-expanded',String(open));});
  load();
})();
