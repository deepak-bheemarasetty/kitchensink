(() => {
  const apiBase = document.querySelector('meta[name="api-base"]')?.content || '/api/v1';
  const token = document.querySelector('meta[name="csrf-token"]')?.content;
  const csrfHeader = document.querySelector('meta[name="csrf-header"]')?.content || 'X-CSRF-TOKEN';
  const rows = document.getElementById('member-rows');
  if (!rows) return;
  const cards = document.getElementById('member-cards');
  const search = document.getElementById('search');
  const status = document.getElementById('status');
  const loadMore = document.getElementById('load-more');
  const note = document.getElementById('page-note');
  const count = document.getElementById('result-count');
  const dialog = document.getElementById('member-dialog');
  const form = document.getElementById('member-form');
  const confirmDialog = document.getElementById('confirm-dialog');
  const notice = document.getElementById('notice');
  const canUpdate = document.body.dataset.canUpdate === 'true';
  const canDelete = document.body.dataset.canDelete === 'true';
  let cursor = null;
  let loading = false;
  let hasMore = false;
  let total = 0;
  let searchTimer;
  let pendingDelete;
  let deletedMemberId;

  // Cross-tab synchronization via BroadcastChannel with localStorage fallback
  const syncChannel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('kitchensink_sync') : null;
  function notifySync(type) {
    if (syncChannel) syncChannel.postMessage({ type, at: Date.now() });
    try { localStorage.setItem('kitchensink_sync_event', JSON.stringify({ type, at: Date.now() })); } catch (_) {}
  }
  if (syncChannel) {
    syncChannel.onmessage = event => {
      if (event?.data?.type === 'MEMBERS_UPDATED') {
        load(true);
      }
    };
  }
  window.addEventListener('storage', event => {
    if (event.key === 'kitchensink_sync_event' && event.newValue) {
      try {
        const data = JSON.parse(event.newValue);
        if (data.type === 'MEMBERS_UPDATED') {
          load(true);
        }
      } catch (_) {}
    }
  });

  function headers(json = false) {
    const result = {};
    if (json) result['Content-Type'] = 'application/json';
    if (token) result[csrfHeader] = token;
    return result;
  }
  function showNotice(message, error = false) {
    notice.textContent = message;
    notice.classList.toggle('error', error);
    notice.hidden = false;
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  }
  function memberActions(member) {
    const actions = [];
    if (canUpdate) actions.push(`<button type="button" class="small-action" data-edit="${member.id}">Edit</button>`);
    if (canDelete) actions.push(`<button type="button" class="small-action delete" data-delete="${member.id}" data-name="${escapeHtml(member.name || (member.firstName + ' ' + (member.lastName || '')))}">Delete</button>`);
    return actions.length ? `<div class="row-actions">${actions.join('')}</div>` : '';
  }
  function memberRow(member) {
    const name = escapeHtml(member.name || `${member.firstName} ${member.lastName || ''}`.trim());
    const badge = `<span class="status-badge ${escapeHtml(member.status)}">${escapeHtml(member.status)}</span>`;
    return `<tr><td><span class="member-name">${name}</span></td><td class="hide-medium">${escapeHtml(member.memberId)}</td><td>${escapeHtml(member.email)}</td><td class="hide-medium">${escapeHtml(member.phone || '')}</td><td>${badge}</td>${(canUpdate || canDelete) ? `<td>${memberActions(member)}</td>` : ''}</tr>`;
  }
  function memberCard(member) {
    const name = escapeHtml(member.name || `${member.firstName} ${member.lastName || ''}`.trim());
    return `<article class="member-card"><div class="member-card-top"><strong class="member-name">${name}</strong><span class="status-badge ${escapeHtml(member.status)}">${escapeHtml(member.status)}</span></div><span class="member-meta">${escapeHtml(member.memberId)}</span><span class="member-email">${escapeHtml(member.email)}</span><span class="member-phone">${escapeHtml(member.phone || '')}</span>${memberActions(member)}</article>`;
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

  async function load(reset = false) {
    if (loading) return;
    loading = true;
    if (reset) {
      cursor = null; total = 0; rows.innerHTML = '<tr><td colspan="6" class="loading-cell">Loading members…</td></tr>'; cards.innerHTML = '';
    }
    const params = new URLSearchParams({limit:'50',sort:'name'});
    if (search.value.trim()) params.set('q', search.value.trim());
    if (status.value) params.set('status', status.value);
    if (cursor) params.set('cursor', cursor);
    const url = `${apiBase}/members?${params}`;
    try {
      const response = await fetch(url, {headers: headers()});
      if (!response.ok) {
        const errText = await parseErrorMessage(
          response,
          response.status === 401 ? 'Your session expired. Sign in again.' : 'Could not load the member directory. Please try again.'
        );
        throw new Error(errText);
      }
      const result = await response.json();
      if (reset) rows.innerHTML = '';
      if (!result.data.length && reset) {
        rows.innerHTML = `<tr><td colspan="6" class="empty-cell">No members found. Try another search or clear the filters.</td></tr>`;
        cards.innerHTML = '<p class="empty-cell">No members found. Try another search or clear the filters.</p>';
      } else {
        rows.insertAdjacentHTML('beforeend', result.data.map(memberRow).join(''));
        cards.insertAdjacentHTML('beforeend', result.data.map(memberCard).join(''));
      }
      total += result.data.length;
      cursor = result.page.nextCursor;
      hasMore = result.page.hasMore;
      loadMore.hidden = !hasMore;
      count.textContent = `${total} ${total === 1 ? 'member' : 'members'}`;
      note.textContent = hasMore ? `Showing ${total} members` : `Showing ${total} ${total === 1 ? 'member' : 'members'}`;
      const urlParams = new URLSearchParams({limit:'50',sort:'name'});
      if (search.value.trim()) urlParams.set('q', search.value.trim());
      if (status.value) urlParams.set('status', status.value);
      history.replaceState(null, '', `${location.pathname}?${urlParams.toString()}`);
    } catch (error) {
      if (reset) rows.innerHTML = `<tr><td colspan="6" class="empty-cell">${escapeHtml(error.message)}</td></tr>`;
      showNotice(error.message, true);
    } finally { loading = false; }
  }
  function openForm(member) {
    form.reset();
    form.querySelectorAll('.field-error').forEach(el => el.textContent = '');
    document.getElementById('member-id').value = member?.id || '';
    document.getElementById('member-version').value = member?.version ?? '';
    document.getElementById('firstName').value = member?.firstName || '';
    document.getElementById('lastName').value = member?.lastName || '';
    document.getElementById('email').value = member?.email || '';
    document.getElementById('phone').value = member?.rawPhone || member?.phone || '';
    document.getElementById('countryCode').value = member?.countryCode || '';
    document.getElementById('member-status').value = member?.status || 'active';
    document.querySelector('.status-field').classList.toggle('sr-only', !member);
    document.getElementById('drawer-title').textContent = member ? 'Edit member' : 'Add member';
    if (member) {
      document.getElementById('email').readOnly = false;
    }
    checkFormValidity();
    dialog.showModal();
    document.getElementById('firstName').focus();
  }
  async function fetchMember(id) {
    const response = await fetch(`${apiBase}/members/${encodeURIComponent(id)}`, {headers:headers()});
    if (!response.ok) {
      const errText = await parseErrorMessage(response, 'Unable to open this member. Refresh the page and try again.');
      throw new Error(errText);
    }
    return response.json();
  }
  document.getElementById('add-member')?.addEventListener('click', () => openForm(null));
  document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => dialog.close()));
  search.addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => load(true), 300); });
  status.addEventListener('change', () => load(true));
  document.getElementById('clear-filters').addEventListener('click', () => { search.value = ''; status.value = ''; load(true); });
  loadMore.addEventListener('click', () => load(false));
  document.querySelector('.menu-toggle')?.addEventListener('click', event => {
    const nav = document.getElementById('primary-nav');
    const open = nav.classList.toggle('open');
    event.currentTarget.setAttribute('aria-expanded', String(open));
  });
  document.addEventListener('click', async event => {
    const edit = event.target.closest('[data-edit]');
    const remove = event.target.closest('[data-delete]');
    if (edit) {
      try { openForm(await fetchMember(edit.dataset.edit)); }
      catch (error) { showNotice(error.message, true); }
    }
    if (remove) {
      pendingDelete = remove.dataset.delete;
      document.getElementById('confirm-message').textContent = `Delete ${remove.dataset.name.trim()}? The record will be soft deleted.`;
      confirmDialog.showModal();
    }
  });
  function validateField(input) {
    const name = input.name;
    const val = input.value.trim();
    let err = '';
    if (name === 'firstName') {
      if (!val) {
        err = 'First Name is required';
      } else if (/\d/.test(val) || !/^[\p{L}\p{M}][\p{L}\p{M} .'-]*$/u.test(val)) {
        err = 'First Name contains Invalid Characters, Please Enter a Valid FirstName';
      } else if (val.length > 60) {
        err = 'First Name must be at most 60 characters';
      }
    } else if (name === 'lastName') {
      if (val && (/\d/.test(val) || !/^[\p{L}\p{M}][\p{L}\p{M} .'-]*$/u.test(val))) {
        err = 'Last Name contains Invalid Characters, Please Enter a Valid LastName';
      } else if (val.length > 60) {
        err = 'Last Name must be at most 60 characters';
      }
    } else if (name === 'email') {
      if (!val) {
        err = 'Email Address isn\'t valid';
      } else if (!/^[A-Za-z0-9_+\-]+(?:\.[A-Za-z0-9_+\-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$/.test(val)) {
        err = 'Email Address isn\'t valid';
      }
    } else if (name === 'countryCode') {
      if (val) {
        if (val.startsWith('0')) {
          err = 'Country Code Cannot start with a 0';
        } else if (!/^[1-9][0-9]{0,3}$/.test(val)) {
          err = 'Country Code must contain 1 to 4 digits and cannot start with a 0';
        }
      }
    } else if (name === 'phone') {
      if (!val) {
        err = 'Phone Number is required';
      } else if (val.startsWith('0')) {
        err = 'Phone Number Cannot start with a 0';
      } else if (!/^[1-9][0-9]{5,13}$/.test(val.replace(/[\s()-]/g, ''))) {
        err = 'Phone Number must contain 6 to 14 digits and cannot start with a 0';
      }
    }
    const errEl = form.querySelector(`[data-error="${name}"]`);
    if (errEl) errEl.textContent = err;
    return !err;
  }

  function checkFormValidity() {
    const saveBtn = form.querySelector('[type="submit"]');
    if (!saveBtn) return;
    const fn = document.getElementById('firstName')?.value.trim();
    const em = document.getElementById('email')?.value.trim();
    const ph = document.getElementById('phone')?.value.trim();
    if (!fn || !em || !ph) {
      saveBtn.disabled = true;
      return;
    }
    const inputs = ['firstName', 'lastName', 'email', 'countryCode', 'phone'].map(id => document.getElementById(id)).filter(Boolean);
    let hasError = false;
    inputs.forEach(input => {
      const errEl = form.querySelector(`[data-error="${input.name}"]`);
      if (errEl && errEl.textContent) hasError = true;
    });
    saveBtn.disabled = hasError;
  }

  ['firstName', 'lastName', 'email', 'countryCode', 'phone'].forEach(fieldName => {
    const el = document.getElementById(fieldName);
    if (el) {
      el.addEventListener('input', () => {
        validateField(el);
        checkFormValidity();
      });
      el.addEventListener('blur', () => {
        validateField(el);
        checkFormValidity();
      });
    }
  });

  form.addEventListener('submit', async event => {
    event.preventDefault();
    form.querySelectorAll('.field-error').forEach(el => el.textContent = '');

    const inputs = ['firstName', 'lastName', 'email', 'countryCode', 'phone'].map(id => document.getElementById(id)).filter(Boolean);
    let valid = true;
    inputs.forEach(input => {
      if (!validateField(input)) valid = false;
    });
    if (!valid) return;

    const id = document.getElementById('member-id').value;
    const payload = Object.fromEntries(new FormData(form).entries());
    delete payload.id;
    delete payload.version;
    payload.firstName = payload.firstName.trim();
    payload.lastName = payload.lastName.trim();
    payload.email = payload.email.trim().toLowerCase();
    payload.phone = payload.phone.trim();
    payload.countryCode = payload.countryCode.trim();
    if (!payload.countryCode) delete payload.countryCode;
    if (!id) delete payload.status;
    else payload.version = Number(document.getElementById('member-version').value);
    const submit = form.querySelector('[type="submit"]');
    submit.disabled = true; submit.textContent = 'Saving…';
    try {
      const requestHeaders = headers(true);
      if (!id) {
        const cryptoApi = window.crypto;
        if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
          requestHeaders['Idempotency-Key'] = cryptoApi.randomUUID();
        } else if (cryptoApi && typeof cryptoApi.getRandomValues === 'function') {
          const bytes = new Uint8Array(16);
          cryptoApi.getRandomValues(bytes);
          const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
          requestHeaders['Idempotency-Key'] = `${Date.now()}-${hex}`;
        } else {
          throw new Error('Secure random number generation is unavailable in this browser.');
        }
      }
      const response = await fetch(id ? `${apiBase}/members/${encodeURIComponent(id)}` : `${apiBase}/members`, {
        method:id ? 'PATCH' : 'POST', headers:requestHeaders, body:JSON.stringify(payload)
      });
      const body = response.status === 204 ? null : await response.json().catch(() => null);
      if (!response.ok) {
        const errorBody = body?.error || body;
        if (errorBody?.fields) errorBody.fields.forEach(field => {
          const fieldSelector = window.CSS && CSS.escape ? `[data-error="${CSS.escape(field.field)}"]` : `[data-error="${field.field}"]`;
          const target = form.querySelector(fieldSelector);
          if (target) target.textContent = field.message;
        });
        const msg = response.status === 409
          ? 'This member changed since you opened the form. Reload the latest record and try again.'
          : errorBody?.message || 'Could not save the member. Check the fields and try again.';
        throw new Error(msg);
      }
      dialog.close();
      showNotice(id ? 'Member updated.' : 'Member added.');
      await load(true);
      notifySync('MEMBERS_UPDATED');
    } catch (error) { showNotice(error.message, true); }
    finally { submit.disabled = false; submit.textContent = 'Save member'; }
  });
  confirmDialog.addEventListener('close', async () => {
    if (confirmDialog.returnValue !== 'confirm' || !pendingDelete) return;
    try {
      const response = await fetch(`${apiBase}/members/${encodeURIComponent(pendingDelete)}`, {method:'DELETE',headers:headers()});
      if (!response.ok) {
        const errText = await parseErrorMessage(response, 'Could not delete this member. Refresh the page and try again.');
        throw new Error(errText);
      }
      deletedMemberId = pendingDelete;
      notice.textContent = 'Member deleted. ';
      const undoBtn = document.createElement('button');
      undoBtn.type = 'button';
      undoBtn.className = 'undo-button';
      undoBtn.textContent = 'Undo';
      notice.appendChild(undoBtn);
      notice.hidden = false;
      await load(true);
      notifySync('MEMBERS_UPDATED');
    } catch (error) { showNotice(error.message, true); }
    pendingDelete = null;
  });
  notice.addEventListener('click', async event => {
    if (!event.target.closest('.undo-button') || !deletedMemberId) return;
    try {
      const response = await fetch(`${apiBase}/members/${encodeURIComponent(deletedMemberId)}/restore`, {method:'POST',headers:headers()});
      if (!response.ok) {
        const errText = await parseErrorMessage(response, 'Could not restore this member.');
        throw new Error(errText);
      }
      deletedMemberId = null;
      showNotice('Member restored.');
      await load(true);
      notifySync('MEMBERS_UPDATED');
    } catch (error) { showNotice(error.message,true); }
  });
  const query = new URLSearchParams(location.search);
  search.value = query.get('q') || '';
  status.value = query.get('status') || '';
  load(true);
})();
