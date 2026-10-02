(() => {
  'use strict';

  const cfg = window.G134OfficeAuth || {};
  const SESSION_KEY = 'g134office-github-session';
  const authBase = String(cfg.baseUrl || '').replace(/\/$/, '');

  const css = document.createElement('style');
  css.textContent = `
    #ghAccountBtn{display:inline-flex;align-items:center;gap:7px;white-space:nowrap}
    #ghAccountBtn img{width:22px;height:22px;border-radius:50%}
    #ghAccountMask{position:fixed;inset:0;background:rgba(0,0,0,.46);display:none;z-index:20}
    #ghAccountMask.open{display:block}
    #ghAccountPanel{position:absolute;right:12px;top:64px;width:min(390px,calc(100vw - 24px));max-height:calc(100vh - 86px);overflow:auto;background:var(--bar);color:var(--ink);border:1px solid var(--line);border-radius:14px;box-shadow:0 18px 55px rgba(0,0,0,.28);padding:14px;display:grid;gap:10px}
    #ghAccountProfile{display:flex;align-items:center;gap:11px}
    #ghAccountAvatar{width:52px;height:52px;border-radius:50%;background:var(--desk)}
    #ghAccountName{font-weight:700}
    #ghAccountLogin,#ghAccountInfo{font-size:12px;color:var(--muted)}
    #ghAccountActions{display:grid;grid-template-columns:1fr 1fr;gap:8px}
    #ghAccountList{display:grid;gap:6px}
    .gh-item{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:9px;border:1px solid var(--line);border-radius:9px;background:var(--button);text-decoration:none;color:var(--ink)}
    .gh-item-main{min-width:0}.gh-item-title{font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.gh-item-meta{font-size:11px;color:var(--muted);margin-top:2px}
    #ghAccountError{display:none;padding:9px;border-radius:8px;background:rgba(200,50,50,.12);font-size:12px;line-height:1.4}
    #ghAccountSetup{display:none;padding:10px;border:1px dashed var(--line);border-radius:9px;color:var(--muted);font-size:12px;line-height:1.45}
    @media(max-width:620px){#ghAccountPanel{left:0;right:0;top:auto;bottom:0;width:100%;max-height:84vh;border-radius:16px 16px 0 0;border-left:0;border-right:0;border-bottom:0}body[data-layout="phone"] #ghAccountBtn .gh-label{display:none}}
  `;
  document.head.appendChild(css);

  const topRow = document.querySelector('header .row');
  if (!topRow) return;
  const nameEl = document.getElementById('name');
  const btn = document.createElement('button');
  btn.id = 'ghAccountBtn';
  btn.type = 'button';
  btn.innerHTML = '<span aria-hidden="true">◉</span><span class="gh-label">GitHub</span>';
  topRow.insertBefore(btn, nameEl || null);

  const mask = document.createElement('div');
  mask.id = 'ghAccountMask';
  mask.innerHTML = `
    <section id="ghAccountPanel" role="dialog" aria-modal="true" aria-label="Аккаунт GitHub">
      <div id="ghAccountProfile" hidden>
        <img id="ghAccountAvatar" alt="Аватар GitHub">
        <div>
          <div id="ghAccountName"></div>
          <div id="ghAccountLogin"></div>
        </div>
      </div>
      <div id="ghAccountInfo">GitHub не подключён</div>
      <div id="ghAccountError"></div>
      <div id="ghAccountSetup">OAuth backend ещё не подключён к опубликованной странице. После деплоя backend и задания G134_AUTH_BASE_URL эта кнопка начнёт обычный вход через GitHub без PAT.</div>
      <div id="ghAccountActions">
        <button id="ghLoginBtn" type="button">Войти через GitHub</button>
        <button id="ghLogoutBtn" type="button" hidden>Выйти</button>
        <button id="ghReposBtn" type="button" hidden>Репозитории</button>
        <button id="ghGistsBtn" type="button" hidden>Gist</button>
        <button id="ghSaveGistBtn" type="button" hidden>Сохранить в Gist</button>
        <button id="ghCloseBtn" type="button">Закрыть</button>
      </div>
      <div id="ghAccountList"></div>
    </section>`;
  document.body.appendChild(mask);

  const el = id => document.getElementById(id);
  const profile = el('ghAccountProfile');
  const avatar = el('ghAccountAvatar');
  const accountName = el('ghAccountName');
  const login = el('ghAccountLogin');
  const info = el('ghAccountInfo');
  const errorBox = el('ghAccountError');
  const setupBox = el('ghAccountSetup');
  const loginBtn = el('ghLoginBtn');
  const logoutBtn = el('ghLogoutBtn');
  const reposBtn = el('ghReposBtn');
  const gistsBtn = el('ghGistsBtn');
  const saveGistBtn = el('ghSaveGistBtn');
  const list = el('ghAccountList');

  function session() { return sessionStorage.getItem(SESSION_KEY) || ''; }
  function showError(message) {
    errorBox.textContent = message || '';
    errorBox.style.display = message ? 'block' : 'none';
  }
  function setDisconnected() {
    profile.hidden = true;
    loginBtn.hidden = false;
    logoutBtn.hidden = true;
    reposBtn.hidden = true;
    gistsBtn.hidden = true;
    saveGistBtn.hidden = true;
    info.textContent = 'GitHub не подключён';
    btn.innerHTML = '<span aria-hidden="true">◉</span><span class="gh-label">GitHub</span>';
    list.innerHTML = '';
  }
  function setConnected(user) {
    profile.hidden = false;
    avatar.src = user.avatar_url || '';
    accountName.textContent = user.name || user.login || 'GitHub';
    login.textContent = '@' + (user.login || '');
    info.textContent = 'Аккаунт подключён' + (user.email ? ' · ' + user.email : '');
    loginBtn.hidden = true;
    logoutBtn.hidden = false;
    reposBtn.hidden = false;
    gistsBtn.hidden = false;
    saveGistBtn.hidden = false;
    btn.innerHTML = `${user.avatar_url ? `<img src="${user.avatar_url}" alt="">` : '<span aria-hidden="true">◉</span>'}<span class="gh-label">${user.login || 'GitHub'}</span>`;
  }
  async function api(path, options = {}) {
    if (!authBase) throw new Error('OAuth backend не настроен');
    const token = session();
    const headers = Object.assign({'Accept':'application/json'}, options.headers || {});
    if (token) headers.Authorization = 'Bearer ' + token;
    if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
    const res = await fetch(authBase + path, Object.assign({}, options, {headers}));
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || data.message || `HTTP ${res.status}`);
    return data;
  }
  function consumeCallback() {
    const hash = new URLSearchParams(location.hash.replace(/^#/, ''));
    const token = hash.get('github_session');
    const oauthError = hash.get('github_error');
    if (token) sessionStorage.setItem(SESSION_KEY, token);
    if (token || oauthError) history.replaceState(null, '', location.pathname + location.search);
    if (oauthError) showError(oauthError);
  }
  async function refreshAccount() {
    if (!session()) { setDisconnected(); return; }
    try {
      const user = await api('/api/me');
      setConnected(user);
    } catch (e) {
      sessionStorage.removeItem(SESSION_KEY);
      setDisconnected();
      showError('Сессия GitHub завершена: ' + e.message);
    }
  }
  function renderItems(items, kind) {
    list.innerHTML = '';
    if (!items.length) {
      list.innerHTML = '<div id="ghAccountInfo">Ничего не найдено.</div>';
      return;
    }
    for (const item of items) {
      const a = document.createElement('a');
      a.className = 'gh-item';
      a.target = '_blank';
      a.rel = 'noopener';
      a.href = item.html_url || '#';
      const title = kind === 'repo' ? item.full_name : (item.description || 'Gist без описания');
      const meta = kind === 'repo'
        ? `${item.private ? 'private' : 'public'}${item.language ? ' · ' + item.language : ''}`
        : `${item.public ? 'public' : 'secret'} · ${new Date(item.updated_at).toLocaleDateString()}`;
      a.innerHTML = `<div class="gh-item-main"><div class="gh-item-title"></div><div class="gh-item-meta"></div></div><span>↗</span>`;
      a.querySelector('.gh-item-title').textContent = title;
      a.querySelector('.gh-item-meta').textContent = meta;
      list.appendChild(a);
    }
  }

  btn.onclick = () => {
    mask.classList.add('open');
    setupBox.style.display = authBase ? 'none' : 'block';
    refreshAccount();
  };
  mask.onclick = e => { if (e.target === mask) mask.classList.remove('open'); };
  el('ghCloseBtn').onclick = () => mask.classList.remove('open');
  loginBtn.onclick = () => {
    showError('');
    if (!authBase) { setupBox.style.display = 'block'; return; }
    const returnTo = location.origin + location.pathname;
    location.href = `${authBase}/api/login?return_to=${encodeURIComponent(returnTo)}`;
  };
  logoutBtn.onclick = () => {
    sessionStorage.removeItem(SESSION_KEY);
    setDisconnected();
    showError('');
  };
  reposBtn.onclick = async () => {
    try { info.textContent = 'Загрузка репозиториев…'; const data = await api('/api/repos'); renderItems(data.items || [], 'repo'); info.textContent = `Репозитории: ${(data.items || []).length}`; }
    catch (e) { showError(e.message); }
  };
  gistsBtn.onclick = async () => {
    try { info.textContent = 'Загрузка Gist…'; const data = await api('/api/gists'); renderItems(data.items || [], 'gist'); info.textContent = `Gist: ${(data.items || []).length}`; }
    catch (e) { showError(e.message); }
  };
  saveGistBtn.onclick = async () => {
    try {
      const editor = document.getElementById('page');
      const currentName = (document.getElementById('name')?.textContent || 'document').replace(/\s\*$/, '').replace(/\.[^.]+$/, '') || 'document';
      const data = await api('/api/gists', {
        method: 'POST',
        body: JSON.stringify({
          filename: currentName + '.html',
          description: 'G134Office: ' + currentName,
          content: editor?.innerHTML || ''
        })
      });
      info.textContent = 'Сохранено в GitHub Gist';
      if (data.html_url) window.open(data.html_url, '_blank', 'noopener');
    } catch (e) { showError(e.message); }
  };

  consumeCallback();
  refreshAccount();
})();
