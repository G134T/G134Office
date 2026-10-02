(() => {
  'use strict';
  const page = document.getElementById('page');
  const engine = document.getElementById('engine');
  const status = document.getElementById('status');
  if (!page || !engine) return;
  let py = null;
  function words(text) {
    return (text.trim().match(/\S+/g) || []).length;
  }
  function paint() {
    const text = page.innerText || '';
    if (status) status.textContent = words(text) + ' слов · ' + [...text].length + ' знаков';
  }
  page.addEventListener('input', paint);
  paint();
  engine.textContent = 'JS + C++ счёт';
  engine.title = 'Интерфейс на JS. Счёт слов по формуле cpp/web/native/text_engine.cpp. CPython грузится кнопкой.';
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.id = 'pyBtn';
  btn.textContent = 'CPython';
  btn.title = 'Загрузить CPython в браузере. Текст не уходит на сервер.';
  const row = document.getElementById('layoutRow');
  if (row) row.appendChild(btn);
  btn.onclick = async () => {
    if (py) { engine.textContent = 'CPython'; return; }
    btn.disabled = true;
    engine.textContent = 'CPython грузится…';
    try {
      const s = document.createElement('script');
      s.src = 'https://cdn.jsdelivr.net/pyodide/v0.26.4/full/pyodide.js';
      await new Promise((ok, bad) => { s.onload = ok; s.onerror = bad; document.head.appendChild(s); });
      py = await loadPyodide();
      const code = await (await fetch('native/text_tasks.py')).text();
      await py.runPythonAsync(code);
      const result = py.runPython(`stats(${JSON.stringify(page.innerText || '')})`);
      engine.textContent = 'CPython';
      if (status) status.textContent = 'CPython: ' + result;
    } catch (e) {
      engine.textContent = 'JS + C++ счёт';
      btn.disabled = false;
    }
  };
})();
