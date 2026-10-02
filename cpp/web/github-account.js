(() => {
  'use strict';
  const cfg = window.G134OfficeAuth || {};
  const SESSION_KEY = 'g134office-github-session';
  const TOKEN_KEY = 'g134office-github-token';
  const authBase = String(cfg.baseUrl || '').replace(/\/$/, '');
  const css = document.createElement('style');
  css.textContent = `#ghAccountBtn{display:inline-flex;align-items:center;gap:7px}#ghAccountBtn img{width:22px;height:22px;border-radius:50%}#ghAccountMask{position:fixed;inset:0;background:rgba(0,0,0,.46);display:none;z-index:20}#ghAccountMask.open{display:block}#ghAccountPanel{position:absolute;right:12px;top:64px;width:min(420px,calc(100vw - 24px));max-height:calc(100vh - 86px);overflow:auto;background:var(--bar,#fff);color:var(--ink,#111);border:1px solid var(--line,#ddd);border-radius:14px;padding:14px;display:grid;gap:10px}#ghAccountProfile{display:flex;align-items:center;gap:11px}#ghAccountAvatar{width:52px;height:52px;border-radius:50%}#ghToken{width:100%;min-height:42px;border:1px solid var(--line,#ddd);border-radius:8px;padding:8px}#ghAccountActions{display:grid;grid-template-columns:1fr 1fr;gap:8px}#ghAccountList{display:grid;gap:6px}.gh-item{display:flex;justify-content:space-between;gap:8px;padding:9px;border:1px solid var(--line,#ddd);border-radius:9px;text-decoration:none;color:inherit}@media(max-width:620px){#ghAccountPanel{left:0;right:0;top:auto;bottom:0;width:100%;border-radius:16px 16px 0 0}}`;
  document.head.appendChild(css);
  const topRow = document.querySelector('header .row') || document.querySelector('header');
  if (!topRow) return;
  const btn = document.createElement('button');
  btn.id = 'ghAccountBtn';
  btn.type = 'button';
  btn.innerHTML = '<span class="gh-label">GitHub</span>';
  topRow.appendChild(btn);
  const mask = document.createElement('div');
  mask.id = 'ghAccountMask';
  mask.innerHTML = `<section id="ghAccountPanel"><div id="ghAccountProfile" hidden><img id="ghAccountAvatar" alt=""><div><div id="ghAccountName"></div><div id="ghAccountLogin"></div></div></div><div id="ghAccountInfo">Вход не обязателен</div><a id="ghTokenLink" href="https://github.com/settings/tokens/new?scopes=read:user,gist&description=G134Office" target="_blank" rel="noopener">Создать токен GitHub</a><input id="ghToken" type="password" placeholder="ghp_... или github_pat_..." autocomplete="off"><div id="ghAccountError"></div><div id="ghAccountActions"><button id="ghLoginBtn" type="button">Войти</button><button id="ghLogoutBtn" type="button" hidden>Выйти</button><button id="ghReposBtn" type="button" hidden>Репозитории</button><button id="ghGistsBtn" type="button" hidden>Gist</button><button id="ghSaveGistBtn" type="button" hidden>Сохранить в Gist</button><button id="ghCloseBtn" type="button">Закрыть</button></div><div id="ghAccountList"></div></section>`;
  document.body.appendChild(mask);
  const el = id => document.getElementById(id);
  const tokenInput = el('ghToken');
  function token() { return sessionStorage.getItem(TOKEN_KEY) || localStorage.getItem(TOKEN_KEY) || ''; }
  function saveToken(value) { sessionStorage.setItem(TOKEN_KEY, value); localStorage.setItem(TOKEN_KEY, value); }
  function clearToken() { sessionStorage.removeItem(TOKEN_KEY); localStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(SESSION_KEY); }
  function showError(message) { el('ghAccountError').textContent = message || ''; }
  function setDisconnected() {
    el('ghAccountProfile').hidden = true;
    el('ghLoginBtn').hidden = false;
    el('ghLogoutBtn').hidden = true;
    el('ghReposBtn').hidden = true;
    el('ghGistsBtn').hidden = true;
    el('ghSaveGistBtn').hidden = true;
    el('ghAccountInfo').textContent = 'Вход не обязателен';
    btn.innerHTML = '<span class="gh-label">GitHub</span>';
    el('ghAccountList').innerHTML = '';
  }
  function setConnected(user) {
    el('ghAccountProfile').hidden = false;
    el('ghAccountAvatar').src = user.avatar_url || '';
    el('ghAccountName').textContent = user.name || user.login;
    el('ghAccountLogin').textContent = '@' + user.login;
    el('ghAccountInfo').textContent = 'Аккаунт подключён';
    el('ghLoginBtn').hidden = true;
    tokenInput.hidden = true;
    el('ghTokenLink').hidden = true;
    el('ghLogoutBtn').hidden = false;
    el('ghReposBtn').hidden = false;
    el('ghGistsBtn').hidden = false;
    el('ghSaveGistBtn').hidden = false;
    btn.innerHTML = `${user.avatar_url ? `<img src="${user.avatar_url}" alt="">` : ''}<span class="gh-label">${user.login}</span>`;
  }
  async function gh(path, options = {}) {
    const headers = {Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', Authorization: 'Bearer ' + token()};
    if (options.body) headers['Content-Type'] = 'application/json';
    const res = await fetch('https://api.github.com' + path, Object.assign({}, options, {headers}));
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.message || 'GitHub не принял токен');
    return data;
  }
  async function loginWithToken(value) {
    saveToken(value.trim());
    const user = await gh('/user');
    setConnected(user);
    showError('');
  }
  function renderItems(items, kind) {
    const list = el('ghAccountList');
    list.innerHTML = '';
    items.forEach(item => {
      const a = document.createElement('a');
      a.className = 'gh-item';
      a.href = item.html_url;
      a.target = '_blank';
      a.rel = 'noopener';
      a.textContent = kind === 'repo' ? item.full_name : (item.description || 'Gist');
      list.appendChild(a);
    });
  }
  btn.onclick = () => mask.classList.add('open');
  mask.onclick = e => { if (e.target === mask) mask.classList.remove('open'); };
  el('ghCloseBtn').onclick = () => mask.classList.remove('open');
  el('ghLoginBtn').onclick = async () => {
    showError('');
    if (authBase && !tokenInput.value.trim()) {
      location.href = `${authBase}/api/login?return_to=${encodeURIComponent(location.origin + location.pathname)}`;
      return;
    }
    if (!tokenInput.value.trim()) { showError('Вставьте токен из ссылки выше.'); return; }
    try { await loginWithToken(tokenInput.value); tokenInput.value = ''; }
    catch (e) { clearToken(); showError(e.message); }
  };
  el('ghLogoutBtn').onclick = () => { clearToken(); setDisconnected(); tokenInput.hidden = false; el('ghTokenLink').hidden = false; };
  el('ghReposBtn').onclick = async () => { try { renderItems(await gh('/user/repos?per_page=8&sort=updated'), 'repo'); } catch (e) { showError(e.message); } };
  el('ghGistsBtn').onclick = async () => { try { renderItems(await gh('/gists?per_page=8'), 'gist'); } catch (e) { showError(e.message); } };
  el('ghSaveGistBtn').onclick = async () => {
    try {
      const page = document.getElementById('page');
      const name = (document.getElementById('name')?.textContent || 'document').replace(/\s\*$/, '') || 'document';
      const data = await gh('/gists', {method: 'POST', body: JSON.stringify({description: 'G134Office: ' + name, public: false, files: {[name + '.html']: {content: page?.innerHTML || ' '}}})});
      el('ghAccountInfo').textContent = 'Сохранено в Gist';
      if (data.html_url) window.open(data.html_url, '_blank', 'noopener');
    } catch (e) { showError(e.message); }
  };
  const hash = new URLSearchParams(location.hash.replace(/^#/, ''));
  if (hash.get('github_session')) { sessionStorage.setItem(SESSION_KEY, hash.get('github_session')); history.replaceState(null, '', location.pathname); }
  if (token()) loginWithToken(token()).catch(() => { clearToken(); setDisconnected(); });
  else setDisconnected();
})();
