(() => {
  'use strict';

  const workspace = document.getElementById('workspace');
  const sheet = document.getElementById('sheet');
  const page = document.getElementById('page');
  const legacyZoom = document.querySelector('.zoom-group');
  const wrapBtn = document.getElementById('wrapBtn');
  const fileInput = document.getElementById('file');
  if (!workspace || !sheet || !page || !legacyZoom) return;

  const STORAGE_KEY = 'g134office-word-view-v1';
  const PAGE_WIDTH = 794;
  const MIN_ZOOM = 45;
  const MAX_ZOOM = 200;

  workspace.classList.add('word-workspace');

  const stage = document.createElement('div');
  stage.className = 'word-stage';
  sheet.parentNode.insertBefore(stage, sheet);
  stage.appendChild(sheet);

  const controls = document.createElement('div');
  controls.className = 'word-zoom-group';
  controls.setAttribute('role', 'group');
  controls.setAttribute('aria-label', 'Масштаб документа');
  controls.innerHTML = `
    <button type="button" id="wordZoomOut" aria-label="Уменьшить масштаб" title="Уменьшить масштаб">−</button>
    <input id="wordZoomSlider" class="word-zoom-slider" type="range" min="${MIN_ZOOM}" max="${MAX_ZOOM}" step="5" value="100" aria-label="Масштаб документа">
    <button type="button" id="wordZoomValue" class="word-zoom-value" title="Сбросить масштаб до 100%">100%</button>
    <button type="button" id="wordZoomIn" aria-label="Увеличить масштаб" title="Увеличить масштаб">+</button>
    <button type="button" id="wordZoomFit" class="word-zoom-fit" title="Подогнать страницу по ширине">По ширине</button>`;
  legacyZoom.replaceWith(controls);

  const zoomOut = document.getElementById('wordZoomOut');
  const zoomIn = document.getElementById('wordZoomIn');
  const zoomValue = document.getElementById('wordZoomValue');
  const zoomSlider = document.getElementById('wordZoomSlider');
  const zoomFit = document.getElementById('wordZoomFit');

  let zoom = 100;
  let mode = 'manual';
  let recentFileOpenUntil = 0;
  let resizeTimer = 0;

  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}');
    if (Number.isFinite(saved.zoom)) zoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, saved.zoom));
    if (saved.mode === 'fit' || saved.mode === 'manual') mode = saved.mode;
  } catch {}

  function persist() {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify({ zoom, mode })); } catch {}
  }

  function ensureWordWrap() {
    if (page.classList.contains('no-wrap')) {
      page.classList.remove('no-wrap');
      wrapBtn?.classList.add('on');
      try {
        const prefs = JSON.parse(localStorage.getItem('g134office-lite-prefs-v5') || '{}');
        prefs.wrap = true;
        localStorage.setItem('g134office-lite-prefs-v5', JSON.stringify(prefs));
      } catch {}
    }
  }

  function naturalHeight() {
    return Math.max(1123, sheet.scrollHeight, page.scrollHeight);
  }

  function syncStage() {
    const scale = zoom / 100;
    const height = naturalHeight();
    sheet.style.transform = `scale(${scale})`;
    sheet.style.marginBottom = '0';
    stage.style.width = `${Math.round(PAGE_WIDTH * scale)}px`;
    stage.style.height = `${Math.round(height * scale)}px`;
    zoomSlider.value = String(Math.round(zoom / 5) * 5);
    zoomValue.textContent = `${Math.round(zoom)}%`;
    zoomFit.classList.toggle('on', mode === 'fit');
  }

  function setZoom(value, nextMode = 'manual', save = true) {
    zoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Number(value) || 100));
    mode = nextMode;
    syncStage();
    if (save) persist();
  }

  function fitWidth(save = true) {
    const layout = document.body.dataset.layout || 'desktop';
    const gutter = layout === 'phone' ? 8 : layout === 'tablet' ? 36 : 72;
    const available = Math.max(320, workspace.clientWidth - gutter);
    const fitted = Math.floor((available / PAGE_WIDTH) * 100);
    setZoom(Math.min(layout === 'desktop' ? 170 : 160, fitted), 'fit', save);
  }

  function normalizeImportedPage() {
    if (Date.now() < recentFileOpenUntil) ensureWordWrap();
    page.querySelectorAll('[style*="width"]').forEach(node => {
      const width = node.style.width;
      if (!width) return;
      const numeric = parseFloat(width);
      if (width.endsWith('px') && Number.isFinite(numeric) && numeric > PAGE_WIDTH) {
        node.style.maxWidth = '100%';
        node.style.width = '100%';
      }
    });
    syncStage();
  }

  zoomOut.addEventListener('click', () => setZoom(zoom - 10));
  zoomIn.addEventListener('click', () => setZoom(zoom + 10));
  zoomValue.addEventListener('click', () => setZoom(100));
  zoomSlider.addEventListener('input', () => setZoom(Number(zoomSlider.value), 'manual', false));
  zoomSlider.addEventListener('change', persist);
  zoomFit.addEventListener('click', () => fitWidth());

  document.addEventListener('keydown', event => {
    if (!(event.ctrlKey || event.metaKey)) return;
    const key = event.key;
    if (key === '0') {
      event.preventDefault();
      event.stopImmediatePropagation();
      setZoom(100);
    } else if (key === '+' || key === '=') {
      event.preventDefault();
      event.stopImmediatePropagation();
      setZoom(zoom + 10);
    } else if (key === '-') {
      event.preventDefault();
      event.stopImmediatePropagation();
      setZoom(zoom - 10);
    }
  }, true);

  fileInput?.addEventListener('change', event => {
    const file = event.target.files?.[0];
    if (!file) return;
    if (/\.(docx|odt|rtf|txt|html?|pdf)$/i.test(file.name)) {
      recentFileOpenUntil = Date.now() + 3500;
      setTimeout(normalizeImportedPage, 0);
      setTimeout(normalizeImportedPage, 180);
      setTimeout(normalizeImportedPage, 700);
    }
  }, true);

  const observer = new MutationObserver(() => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(normalizeImportedPage, 30);
  });
  observer.observe(page, { childList: true, subtree: true, characterData: true });

  if ('ResizeObserver' in window) {
    const ro = new ResizeObserver(() => {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(syncStage, 30);
    });
    ro.observe(page);
    ro.observe(sheet);
  }

  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      if (mode === 'fit') fitWidth(false); else syncStage();
    }, 120);
  });

  window.addEventListener('orientationchange', () => setTimeout(() => {
    if (mode === 'fit') fitWidth(false); else syncStage();
  }, 180));

  ensureWordWrap();
  const firstLayout = document.body.dataset.layout || 'desktop';
  if (firstLayout === 'phone' && !localStorage.getItem(STORAGE_KEY)) mode = 'fit';
  if (mode === 'fit') fitWidth(false); else setZoom(zoom, 'manual', false);
})();