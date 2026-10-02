(() => {
  'use strict';
  const wasmB64 = 'AGFzbQEAAAABBwFgAn9/AX8DBAMAAAAFAwEAAQc0BAZtZW1vcnkCAAtjb3VudF9jaGFycwAAC2NvdW50X2xpbmVzAAELY291bnRfd29yZHMAAgqWAQMEACABC0QBAn9BACEDQQAhAiABRQRAQQAPC0EBIQMDQCACIAFJBEAgACACai0AAEEKRgRAIANBAWohAwsgAkEBaiECDAELCyADC0oBA39BACECQQAhA0EAIQQDQCACIAFJBEAgACACai0AAEEgSwRAIARFBEAgA0EBaiEDQQEhBAsFQQAhBAsgAkEBaiECDAELCyADCw==';
  const page = document.getElementById('page');
  const engine = document.getElementById('engine');
  const status = document.getElementById('status');
  if (!page) return;
  let api = null;
  function bytes(text) { return new TextEncoder().encode(text); }
  async function bootCpp() {
    const raw = Uint8Array.from(atob(wasmB64), c => c.charCodeAt(0));
    const mod = await WebAssembly.instantiate(raw);
    api = mod.instance.exports;
    if (engine) engine.textContent = 'C++ WASM';
    refresh();
  }
  function refresh() {
    if (!api || !status) return;
    const text = page.innerText || '';
    const data = bytes(text);
    const mem = new Uint8Array(api.memory.buffer);
    mem.set(data, 0);
    const words = api.count_words(0, data.length);
    const lines = api.count_lines(0, data.length);
    status.textContent = `C++: ${words} слов, ${lines} стр`;
  }
  page.addEventListener('input', refresh);
  bootCpp().catch(() => { if (engine) engine.textContent = 'JS'; });
  const btn = document.getElementById('cpythonBtn');
  if (!btn) return;
  btn.onclick = async () => {
    btn.disabled = true;
    btn.textContent = 'CPython…';
    try {
      if (!window.loadPyodide) {
        await new Promise((ok, bad) => {
          const s = document.createElement('script');
          s.src = 'https://cdn.jsdelivr.net/pyodide/v0.26.4/full/pyodide.js';
          s.onload = ok; s.onerror = bad; document.head.appendChild(s);
        });
      }
      const py = await loadPyodide();
      const text = page.innerText || '';
      py.globals.set('src', text);
      const report = py.runPython(`
import sys
words = [w.strip('.,;:!?()"\'').lower() for w in src.split() if w.strip()]
seen, dup = {}, 0
for w in words:
    seen[w] = seen.get(w, 0) + 1
    if seen[w] == 2: dup += 1
f'CPython {sys.version.split()[0]}: {len(words)} слов, повторов {dup}'
`);
      if (status) status.textContent = report;
      btn.textContent = 'CPython';
    } catch (e) {
      if (status) status.textContent = 'CPython не загрузился';
      btn.textContent = 'CPython';
    }
    btn.disabled = false;
  };
})();
