(() => {
  'use strict';
  const page = document.getElementById('page');
  const nameEl = document.getElementById('name');
  const toast = document.getElementById('toast');
  if (!page || !nameEl) return;
  page.lang = 'ru';
  page.spellcheck = true;
  function note(text) {
    if (!toast) return;
    toast.textContent = text;
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 1600);
  }
  const copy = document.getElementById('copyBtn');
  if (copy) copy.onclick = async () => {
    const text = page.innerText || '';
    try { await navigator.clipboard.writeText(text); note('Текст скопирован'); }
    catch { note('Не удалось скопировать'); }
  };
  nameEl.title = 'Нажмите, чтобы переименовать';
  nameEl.style.cursor = 'pointer';
  nameEl.onclick = () => {
    const current = nameEl.textContent.replace(/\s\*$/, '') || 'без имени';
    const next = prompt('Имя документа', current);
    if (!next) return;
    nameEl.textContent = next.trim();
    const saveName = document.getElementById('saveName');
    if (saveName) saveName.value = next.trim().replace(/\.[^.]+$/, '');
    document.title = 'G134Office Lite — ' + next.trim();
  };
})();
