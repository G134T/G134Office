(() => {
  'use strict';

  const page = document.getElementById('page');
  const input = document.getElementById('size');
  const down = document.getElementById('fontSizeDown');
  const up = document.getElementById('fontSizeUp');
  if (!page || !input || !down || !up) return;

  const MIN = 8;
  const MAX = 96;
  const STEP = 1;
  let lastRange = null;

  function rememberSelection() {
    const selection = window.getSelection();
    if (!selection || !selection.rangeCount) return;
    const range = selection.getRangeAt(0);
    if (page.contains(range.commonAncestorContainer)) lastRange = range.cloneRange();
  }

  function restoreSelection() {
    if (!lastRange) return;
    const selection = window.getSelection();
    if (!selection) return;
    try {
      selection.removeAllRanges();
      selection.addRange(lastRange);
    } catch {
      lastRange = null;
    }
  }

  function normalize(value) {
    const parsed = Number.parseInt(String(value), 10);
    if (!Number.isFinite(parsed)) return 17;
    return Math.min(MAX, Math.max(MIN, parsed));
  }

  function apply(value, notify = true) {
    const next = normalize(value);
    input.value = String(next);
    restoreSelection();
    if (notify) input.dispatchEvent(new Event('change', { bubbles: true }));
    rememberSelection();
  }

  document.addEventListener('selectionchange', rememberSelection);
  page.addEventListener('keyup', rememberSelection);
  page.addEventListener('mouseup', rememberSelection);
  page.addEventListener('touchend', () => setTimeout(rememberSelection, 0), { passive: true });

  down.addEventListener('mousedown', event => event.preventDefault());
  up.addEventListener('mousedown', event => event.preventDefault());
  down.addEventListener('click', () => apply(normalize(input.value) - STEP));
  up.addEventListener('click', () => apply(normalize(input.value) + STEP));

  input.addEventListener('focus', rememberSelection);
  input.addEventListener('keydown', event => {
    if (event.key === 'Enter') {
      event.preventDefault();
      apply(input.value);
      page.focus();
    }
  });
  input.addEventListener('blur', () => apply(input.value));
  input.addEventListener('input', () => {
    const parsed = Number.parseInt(input.value, 10);
    if (Number.isFinite(parsed) && parsed >= MIN && parsed <= MAX) {
      restoreSelection();
      input.dispatchEvent(new Event('change', { bubbles: true }));
      rememberSelection();
    }
  });

  apply(input.value, false);
})();
