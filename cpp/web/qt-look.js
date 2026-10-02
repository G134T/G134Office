(() => {
  'use strict';
  const css = document.createElement('style');
  css.textContent = `body.qt-fusion{--bar:#f0f0f0;--ink:#202020;--line:#b8b8b8;--button:#e6e6e6;--desk:#d8d8d8}body.qt-fusion header,body.qt-fusion .status-bar{font-family:"Segoe UI",sans-serif}body.qt-fusion button,body.qt-fusion select,body.qt-fusion input{border-radius:3px;border:1px solid #9a9a9a;background:#f4f4f4;color:#202020}body.qt-fusion #qtNote{position:fixed;right:12px;bottom:36px;z-index:8;max-width:280px;padding:10px 12px;background:#f6f6f6;color:#202020;border:1px solid #a0a0a0;border-radius:4px;box-shadow:0 8px 24px rgba(0,0,0,.18);font-size:12px}`;
  document.head.appendChild(css);
  const row = document.getElementById('layoutRow');
  if (!row) return;
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.id = 'qtLookBtn';
  btn.textContent = 'Вид Qt';
  row.appendChild(btn);
  const note = document.createElement('div');
  note.id = 'qtNote';
  note.hidden = true;
  note.textContent = 'Это только внешний вид Qt Fusion. PySide6 и Qt5 в браузере не запускаются: у страницы нет библиотек Qt.';
  document.body.appendChild(note);
  btn.onclick = () => {
    const on = document.body.classList.toggle('qt-fusion');
    note.hidden = !on;
    btn.textContent = on ? 'Qt вкл' : 'Вид Qt';
  };
})();
