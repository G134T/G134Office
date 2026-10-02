(() => {
  'use strict';

  const $ = id => document.getElementById(id);
  const page = $('page');
  const sheet = $('sheet');
  const pdf = $('pdf');
  const fileInput = $('file');
  const statusEl = $('status');
  const cursorEl = $('cursor');
  const autosaveEl = $('autosave');
  const nameEl = $('name');
  const engineEl = $('engine');
  const themeSelect = $('theme');
  const wrapBtn = $('wrapBtn');
  const zoomReset = $('zoomReset');
  const autoLayout = $('autoLayout');
  const deviceState = $('deviceState');
  const layoutFooter = $('layoutFooter');
  const layoutButtons = [...document.querySelectorAll('[data-layout-mode]')];
  const saveDialog = $('saveDialog');
  const saveName = $('saveName');
  const toastEl = $('toast');

  const DRAFT_KEY = 'g134office-lite-draft-v4';
  const PREF_KEY = 'g134office-lite-prefs-v5';

  let fileName = 'без имени';
  let dirty = false;
  let pdfUrl = '';
  let zoom = 100;
  let wrap = true;
  let wasm = null;
  let saveTimer = 0;
  let toastTimer = 0;
  let layoutMode = 'auto';
  let detectedLayout = 'desktop';

  function toast(message) {
    clearTimeout(toastTimer);
    toastEl.textContent = message;
    toastEl.classList.add('show');
    toastTimer = setTimeout(() => toastEl.classList.remove('show'), 2400);
  }

  function safeBaseName(value = fileName) {
    const raw = String(value || 'document').replace(/\.[^.]+$/, '').trim() || 'document';
    return raw.replace(/[\\/:*?"<>|]+/g, '_').slice(0, 120) || 'document';
  }

  function mark() {
    document.title = `G134Office Lite — ${fileName}${dirty ? ' *' : ''}`;
    nameEl.textContent = `${fileName}${dirty ? ' *' : ''}`;
  }

  function jsWords(text) {
    const t = text.trim();
    return t ? t.split(/\s+/u).length : 0;
  }

  function stats(text) {
    if (wasm) {
      return {
        words: wasm.ccall('g134_word_count', 'number', ['string'], [text]),
        chars: wasm.ccall('g134_char_count', 'number', ['string'], [text]),
        lines: wasm.ccall('g134_line_count', 'number', ['string'], [text]),
        seconds: wasm.ccall('g134_reading_seconds', 'number', ['string'], [text])
      };
    }
    const words = jsWords(text);
    return { words, chars: [...text].length, lines: (text.match(/\n/g) || []).length + 1, seconds: Math.ceil(words * 60 / 200) };
  }

  function updateStatus() {
    if (document.body.classList.contains('pdf')) return;
    const text = page.innerText || '';
    const s = stats(text);
    const read = s.seconds < 60 ? '<1 мин' : `${Math.ceil(s.seconds / 60)} мин`;
    statusEl.textContent = `Слов ${s.words} · знаков ${s.chars} · строк ${s.lines} · чтение ${read}`;
  }

  function updateCursor() {
    const sel = getSelection();
    if (!sel || !sel.rangeCount || !page.contains(sel.anchorNode)) return;
    try {
      const range = document.createRange();
      range.selectNodeContents(page);
      range.setEnd(sel.anchorNode, sel.anchorOffset);
      const before = range.toString();
      const lines = before.split('\n');
      const selected = sel.toString().length;
      cursorEl.textContent = `Стр ${lines.length} · Стлб ${lines[lines.length - 1].length + 1}${selected ? ` · выделено ${selected}` : ''}`;
    } catch {}
  }

  function showDoc() { document.body.classList.remove('pdf'); updateStatus(); }
  function showPdf() { document.body.classList.add('pdf'); statusEl.textContent = pdfUrl ? 'PDF открыт' : 'PDF не открыт'; }
  function setDirty(value = true) { dirty = value; mark(); }

  function savePrefs() {
    localStorage.setItem(PREF_KEY, JSON.stringify({ theme: themeSelect.value, zoom, wrap, layoutMode }));
  }

  function saveDraft(now = false) {
    clearTimeout(saveTimer);
    const run = () => {
      try {
        localStorage.setItem(DRAFT_KEY, JSON.stringify({ html: page.innerHTML, fileName, ts: Date.now() }));
        autosaveEl.textContent = `Сохранено локально · ${new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
      } catch (error) {
        autosaveEl.textContent = 'Автосохранение недоступно';
        console.warn(error);
      }
    };
    if (now) run(); else saveTimer = setTimeout(run, 700);
  }

  function restoreDraft() {
    try {
      const draft = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null');
      if (draft?.html) { page.innerHTML = draft.html; fileName = draft.fileName || 'авточерновик'; }
    } catch (error) { console.warn(error); }
  }

  function detectDeviceLayout() {
    const viewport = innerWidth || document.documentElement.clientWidth || 9999;
    const screenShort = Math.min(screen.width || viewport, screen.height || viewport);
    const coarse = matchMedia('(pointer: coarse)').matches;
    const touch = (navigator.maxTouchPoints || 0) > 0;
    if ((coarse || touch) && screenShort <= 640) return 'phone';
    if ((coarse || touch) && screenShort <= 1100) return 'tablet';
    if (viewport <= 680) return 'phone';
    if (viewport <= 1100) return 'tablet';
    return 'desktop';
  }

  function layoutTitle(mode) { return mode === 'phone' ? 'телефон' : mode === 'tablet' ? 'планшет' : 'компьютер'; }

  function applyLayout(mode, persist = true) {
    detectedLayout = detectDeviceLayout();
    layoutMode = mode === 'auto' ? 'auto' : mode;
    const actual = layoutMode === 'auto' ? detectedLayout : layoutMode;
    document.body.dataset.layout = actual;
    autoLayout.checked = layoutMode === 'auto';
    layoutButtons.forEach(button => button.classList.toggle('on', layoutMode !== 'auto' && button.dataset.layoutMode === actual));
    deviceState.textContent = `Устройство: ${layoutTitle(detectedLayout)} · ${layoutMode === 'auto' ? 'авто' : `вручную: ${layoutTitle(actual)}`}`;
    layoutFooter.textContent = `Вид: ${layoutMode === 'auto' ? 'авто → ' : ''}${layoutTitle(actual)}`;
    if (persist) savePrefs();
  }

  function applyTheme(value) {
    let actual = value;
    if (value === 'system') actual = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    document.body.dataset.theme = actual;
    $('themeMeta').content = actual === 'dark' ? '#20242b' : actual === 'sepia' ? '#f5eddc' : '#f8fafc';
    savePrefs();
  }

  function applyZoom(value) {
    zoom = Math.min(160, Math.max(60, Number(value) || 100));
    sheet.style.transform = `scale(${zoom / 100})`;
    sheet.style.marginBottom = `${Math.max(0, (zoom - 100) * 8)}px`;
    zoomReset.textContent = `${zoom}%`;
    savePrefs();
  }

  function applyWrap(value) {
    wrap = Boolean(value);
    page.classList.toggle('no-wrap', !wrap);
    wrapBtn.classList.toggle('on', wrap);
    savePrefs();
  }

  function loadPrefs() {
    try {
      const prefs = JSON.parse(localStorage.getItem(PREF_KEY) || '{}');
      themeSelect.value = prefs.theme || 'system';
      zoom = Number(prefs.zoom) || 100;
      wrap = prefs.wrap !== false;
      layoutMode = prefs.layoutMode || 'auto';
    } catch {
      themeSelect.value = 'system'; zoom = 100; wrap = true; layoutMode = 'auto';
    }
    applyTheme(themeSelect.value); applyZoom(zoom); applyWrap(wrap); applyLayout(layoutMode, false);
  }

  function openSaveDialog() {
    saveName.value = safeBaseName();
    saveDialog.classList.add('open');
    saveDialog.setAttribute('aria-hidden', 'false');
    setTimeout(() => saveName.select(), 20);
  }

  function closeSaveDialog() {
    saveDialog.classList.remove('open');
    saveDialog.setAttribute('aria-hidden', 'true');
  }

  async function saveBlob(name, mime, data) {
    const blob = data instanceof Blob ? data : new Blob([data], { type: mime });
    if ('showSaveFilePicker' in window) {
      try {
        const ext = `.${name.split('.').pop()}`;
        const handle = await showSaveFilePicker({ suggestedName: name, types: [{ description: ext.slice(1).toUpperCase(), accept: { [mime.split(';')[0]]: [ext] } }] });
        const writable = await handle.createWritable();
        await writable.write(blob); await writable.close();
        fileName = name; setDirty(false); saveDraft(true); toast(`Сохранено: ${name}`); return;
      } catch (error) {
        if (error?.name === 'AbortError') return;
        console.warn('File picker failed, using download fallback', error);
      }
    }
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url; anchor.download = name; document.body.appendChild(anchor); anchor.click(); anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2500);
    fileName = name; setDirty(false); saveDraft(true); toast(`Файл создан: ${name}`);
  }

  function xml(value) { return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  const encoder = new TextEncoder(); let crcTable = null;

  function crc32(bytes) {
    if (!crcTable) {
      crcTable = new Uint32Array(256);
      for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = (c & 1) ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crcTable[n] = c >>> 0; }
    }
    let c = 0xffffffff; for (const byte of bytes) c = crcTable[(c ^ byte) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0;
  }

  function u16(value, array, offset) { array[offset] = value & 255; array[offset + 1] = (value >>> 8) & 255; }
  function u32(value, array, offset) { u16(value & 65535, array, offset); u16((value >>> 16) & 65535, array, offset + 2); }

  function zipStore(entries, mime = 'application/zip') {
    const local = [], central = []; let offset = 0;
    for (const entry of entries) {
      const name = encoder.encode(entry.name), data = entry.data instanceof Uint8Array ? entry.data : encoder.encode(entry.data), crc = crc32(data);
      const localHeader = new Uint8Array(30 + name.length);
      u32(0x04034b50, localHeader, 0); u16(20, localHeader, 4); u16(0, localHeader, 6); u16(0, localHeader, 8); u32(crc, localHeader, 14); u32(data.length, localHeader, 18); u32(data.length, localHeader, 22); u16(name.length, localHeader, 26); localHeader.set(name, 30);
      local.push(localHeader, data);
      const centralHeader = new Uint8Array(46 + name.length);
      u32(0x02014b50, centralHeader, 0); u16(20, centralHeader, 4); u16(20, centralHeader, 6); u32(crc, centralHeader, 16); u32(data.length, centralHeader, 20); u32(data.length, centralHeader, 24); u16(name.length, centralHeader, 28); u32(offset, centralHeader, 42); centralHeader.set(name, 46);
      central.push(centralHeader); offset += localHeader.length + data.length;
    }
    const centralSize = central.reduce((sum, item) => sum + item.length, 0), end = new Uint8Array(22);
    u32(0x06054b50, end, 0); u16(entries.length, end, 8); u16(entries.length, end, 10); u32(centralSize, end, 12); u32(offset, end, 16);
    return new Blob([...local, ...central, end], { type: mime });
  }

  function paragraphNodes() {
    const blocks = [];
    for (const node of page.childNodes) {
      if (node.nodeType === Node.TEXT_NODE) { if (node.textContent) blocks.push({ node, align: 'left', kind: 'p' }); continue; }
      const tag = node.tagName?.toLowerCase() || 'p';
      if (tag === 'ul' || tag === 'ol') [...node.children].forEach((li, index) => blocks.push({ node: li, prefix: tag === 'ul' ? '• ' : `${index + 1}. `, align: getComputedStyle(li).textAlign || 'left', kind: 'p' }));
      else blocks.push({ node, align: getComputedStyle(node).textAlign || 'left', kind: tag });
    }
    return blocks.length ? blocks : [{ node: page, align: 'left', kind: 'p' }];
  }

  function collectRuns(node, prefix = '') {
    const runs = prefix ? [{ text: prefix }] : [];
    const walk = (current, style) => {
      if (current.nodeType === Node.TEXT_NODE) { if (current.nodeValue) runs.push({ text: current.nodeValue, ...style }); return; }
      if (current.nodeType !== Node.ELEMENT_NODE) return;
      const tag = current.tagName.toLowerCase(), next = { ...style };
      if (tag === 'b' || tag === 'strong' || current.style.fontWeight === 'bold' || Number(current.style.fontWeight) >= 600) next.bold = true;
      if (tag === 'i' || tag === 'em' || current.style.fontStyle === 'italic') next.italic = true;
      if (tag === 'u' || current.style.textDecoration.includes('underline')) next.underline = true;
      if (tag === 'br') { runs.push({ text: '\n', ...next }); return; }
      for (const child of current.childNodes) walk(child, next);
    };
    walk(node, {});
    return runs.length ? runs : [{ text: node.textContent || '' }];
  }

  function docxBlob() {
    const paragraphs = paragraphNodes().map(block => {
      const align = block.align === 'center' ? 'center' : block.align === 'right' ? 'right' : block.align === 'justify' ? 'both' : 'left';
      const runs = collectRuns(block.node, block.prefix || '').map(run => {
        const props = `${run.bold ? '<w:b/>' : ''}${run.italic ? '<w:i/>' : ''}${run.underline ? '<w:u w:val="single"/>' : ''}`;
        const pieces = run.text.split('\n').map((piece, index) => `${index ? '<w:br/>' : ''}<w:t xml:space="preserve">${xml(piece)}</w:t>`).join('');
        return `<w:r>${props ? `<w:rPr>${props}</w:rPr>` : ''}${pieces}</w:r>`;
      }).join('');
      const heading = block.kind === 'h1' ? '<w:pStyle w:val="Heading1"/>' : block.kind === 'h2' ? '<w:pStyle w:val="Heading2"/>' : '';
      return `<w:p><w:pPr>${heading}<w:jc w:val="${align}"/></w:pPr>${runs}</w:p>`;
    }).join('');
    return zipStore([
      { name: '[Content_Types].xml', data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>' },
      { name: '_rels/.rels', data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>' },
      { name: 'word/_rels/document.xml.rels', data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>' },
      { name: 'word/styles.xml', data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:sz w:val="26"/></w:rPr></w:style></w:styles>' },
      { name: 'word/document.xml', data: `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>${paragraphs}<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/></w:sectPr></w:body></w:document>` }
    ], 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
  }

  function odtBlob() {
    const body = paragraphNodes().map(block => {
      const runs = collectRuns(block.node, block.prefix || '').map(run => {
        let style = '';
        if (run.bold && run.italic && run.underline) style = 'BIU'; else if (run.bold && run.italic) style = 'BI'; else if (run.bold && run.underline) style = 'BU'; else if (run.italic && run.underline) style = 'IU'; else if (run.bold) style = 'B'; else if (run.italic) style = 'I'; else if (run.underline) style = 'U';
        const text = xml(run.text).replace(/\n/g, '<text:line-break/>');
        return style ? `<text:span text:style-name="${style}">${text}</text:span>` : text;
      }).join('');
      if (block.kind === 'h1' || block.kind === 'h2') return `<text:h text:outline-level="${block.kind === 'h1' ? 1 : 2}">${runs}</text:h>`;
      return `<text:p>${runs}</text:p>`;
    }).join('');
    const autoStyles = '<office:automatic-styles><style:style style:name="B" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style><style:style style:name="I" style:family="text"><style:text-properties fo:font-style="italic"/></style:style><style:style style:name="U" style:family="text"><style:text-properties style:text-underline-style="solid"/></style:style><style:style style:name="BI" style:family="text"><style:text-properties fo:font-weight="bold" fo:font-style="italic"/></style:style><style:style style:name="BU" style:family="text"><style:text-properties fo:font-weight="bold" style:text-underline-style="solid"/></style:style><style:style style:name="IU" style:family="text"><style:text-properties fo:font-style="italic" style:text-underline-style="solid"/></style:style><style:style style:name="BIU" style:family="text"><style:text-properties fo:font-weight="bold" fo:font-style="italic" style:text-underline-style="solid"/></style:style></office:automatic-styles>';
    const content = `<?xml version="1.0" encoding="UTF-8"?><office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" office:version="1.3">${autoStyles}<office:body><office:text>${body}</office:text></office:body></office:document-content>`;
    const manifest = '<?xml version="1.0" encoding="UTF-8"?><manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.3"><manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text"/><manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/></manifest:manifest>';
    return zipStore([{ name: 'mimetype', data: 'application/vnd.oasis.opendocument.text' }, { name: 'content.xml', data: content }, { name: 'META-INF/manifest.xml', data: manifest }], 'application/vnd.oasis.opendocument.text');
  }

  function rtfText() {
    const text = page.innerText || ''; let out = '{\\rtf1\\ansi\\uc1\\deff0{\\fonttbl{\\f0 Calibri;}}\\f0\\fs24 ';
    for (const ch of text) { const code = ch.charCodeAt(0); if (ch === '\\') out += '\\\\'; else if (ch === '{') out += '\\{'; else if (ch === '}') out += '\\}'; else if (ch === '\n') out += '\\par\n'; else if (code >= 32 && code < 127) out += ch; else out += `\\u${code > 32767 ? code - 65536 : code}?`; }
    return `${out}}`;
  }

  function printPdf() {
    const win = window.open('', '_blank');
    if (!win) { toast('Разрешите всплывающие окна для PDF'); return; }
    win.document.open();
    win.document.write(`<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>${xml(safeBaseName())}</title><style>body{font-family:Calibri,Arial,sans-serif;margin:0;color:#000}main{width:170mm;margin:auto;padding:18mm 20mm;font-size:12pt;line-height:1.5}h1{font-size:24pt}h2{font-size:18pt}@page{size:A4;margin:0}</style></head><body><main id="printRoot"></main></body></html>`);
    win.document.close();
    const root = win.document.getElementById('printRoot'); if (root) root.innerHTML = page.innerHTML;
    setTimeout(() => { win.focus(); win.print(); }, 250);
  }

  async function exportFormat(kind) {
    const base = safeBaseName(saveName.value || fileName); closeSaveDialog(); showDoc();
    try {
      if (kind === 'txt') await saveBlob(`${base}.txt`, 'text/plain;charset=utf-8', page.innerText || '');
      else if (kind === 'rtf') await saveBlob(`${base}.rtf`, 'application/rtf;charset=utf-8', rtfText());
      else if (kind === 'docx') await saveBlob(`${base}.docx`, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', docxBlob());
      else if (kind === 'odt') await saveBlob(`${base}.odt`, 'application/vnd.oasis.opendocument.text', odtBlob());
      else if (kind === 'pdf') printPdf();
      else if (kind === 'share') {
        const text = page.innerText || ''; if (!navigator.share) return toast('На этом устройстве нет системного меню «Поделиться»');
        const shareFile = new File([text], `${base}.txt`, { type: 'text/plain;charset=utf-8' });
        if (navigator.canShare?.({ files: [shareFile] })) await navigator.share({ title: base, files: [shareFile] }); else await navigator.share({ title: base, text });
      }
    } catch (error) { if (error?.name !== 'AbortError') { console.error(error); toast(`Не удалось сохранить: ${error?.message || error}`); } }
  }

  const readU16 = (view, offset) => view.getUint16(offset, true);
  const readU32 = (view, offset) => view.getUint32(offset, true);

  async function unzipEntries(file) {
    const bytes = new Uint8Array(await file.arrayBuffer()), view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    let eocd = -1;
    for (let i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) { if (readU32(view, i) === 0x06054b50) { eocd = i; break; } }
    if (eocd < 0) throw new Error('ZIP-контейнер не найден');
    const count = readU16(view, eocd + 10); let offset = readU32(view, eocd + 16); const entries = new Map();
    for (let index = 0; index < count; index++) {
      if (readU32(view, offset) !== 0x02014b50) throw new Error('Повреждён ZIP-каталог');
      const method = readU16(view, offset + 10), compressedSize = readU32(view, offset + 20), nameLength = readU16(view, offset + 28), extraLength = readU16(view, offset + 30), commentLength = readU16(view, offset + 32), localOffset = readU32(view, offset + 42);
      const name = new TextDecoder().decode(bytes.subarray(offset + 46, offset + 46 + nameLength));
      if (readU32(view, localOffset) !== 0x04034b50) throw new Error('Повреждена ZIP-запись');
      const localNameLength = readU16(view, localOffset + 26), localExtraLength = readU16(view, localOffset + 28), dataStart = localOffset + 30 + localNameLength + localExtraLength, compressed = bytes.slice(dataStart, dataStart + compressedSize);
      let data;
      if (method === 0) data = compressed;
      else if (method === 8) {
        if (!('DecompressionStream' in window)) throw new Error('Браузер не умеет распаковывать DOCX/ODT');
        const stream = new Blob([compressed]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
        data = new Uint8Array(await new Response(stream).arrayBuffer());
      } else throw new Error(`ZIP-сжатие ${method} не поддерживается`);
      entries.set(name, data); offset += 46 + nameLength + extraLength + commentLength;
    }
    return entries;
  }

  function docxToHtml(xmlText) {
    const doc = new DOMParser().parseFromString(xmlText, 'application/xml');
    if (doc.querySelector('parsererror')) throw new Error('Некорректный document.xml');
    const out = document.createElement('div');
    for (const p of [...doc.getElementsByTagNameNS('*', 'p')]) {
      const styleEl = [...p.getElementsByTagNameNS('*', 'pStyle')][0], styleVal = styleEl?.getAttribute('w:val') || styleEl?.getAttributeNS('http://schemas.openxmlformats.org/wordprocessingml/2006/main', 'val');
      const block = document.createElement(styleVal === 'Heading1' ? 'h1' : styleVal === 'Heading2' ? 'h2' : 'div');
      const jc = [...p.getElementsByTagNameNS('*', 'jc')][0], align = jc?.getAttribute('w:val') || jc?.getAttributeNS('http://schemas.openxmlformats.org/wordprocessingml/2006/main', 'val');
      if (align === 'center') block.style.textAlign = 'center'; else if (align === 'right') block.style.textAlign = 'right'; else if (align === 'both') block.style.textAlign = 'justify';
      for (const r of [...p.getElementsByTagNameNS('*', 'r')]) {
        const span = document.createElement('span'), rPr = [...r.children].find(el => el.localName === 'rPr');
        if (rPr) { if ([...rPr.children].some(el => el.localName === 'b')) span.style.fontWeight = 'bold'; if ([...rPr.children].some(el => el.localName === 'i')) span.style.fontStyle = 'italic'; if ([...rPr.children].some(el => el.localName === 'u')) span.style.textDecoration = 'underline'; }
        for (const child of r.children) { if (child.localName === 't') span.append(document.createTextNode(child.textContent || '')); if (child.localName === 'br') span.append(document.createElement('br')); }
        block.append(span);
      }
      out.append(block);
    }
    return out.innerHTML;
  }

  function odtToHtml(xmlText) {
    const doc = new DOMParser().parseFromString(xmlText, 'application/xml'); if (doc.querySelector('parsererror')) throw new Error('Некорректный content.xml');
    const out = document.createElement('div'), body = [...doc.getElementsByTagNameNS('*', 'text')][0]; if (!body) return '';
    const render = (node, parent) => {
      if (node.nodeType === Node.TEXT_NODE) { parent.append(document.createTextNode(node.nodeValue || '')); return; }
      if (node.nodeType !== Node.ELEMENT_NODE) return;
      const name = node.localName; let el = parent;
      if (name === 'p') { el = document.createElement('div'); parent.append(el); }
      else if (name === 'h') { el = document.createElement((node.getAttribute('text:outline-level') || '2') === '1' ? 'h1' : 'h2'); parent.append(el); }
      else if (name === 'span') { el = document.createElement('span'); const styleName = node.getAttribute('text:style-name') || ''; if (styleName.includes('B')) el.style.fontWeight = 'bold'; if (styleName.includes('I')) el.style.fontStyle = 'italic'; if (styleName.includes('U')) el.style.textDecoration = 'underline'; parent.append(el); }
      else if (name === 'line-break') { parent.append(document.createElement('br')); return; }
      for (const child of node.childNodes) render(child, el);
    };
    for (const child of body.childNodes) render(child, out); return out.innerHTML;
  }

  function stripRtf(input) {
    return input.replace(/\\u(-?\d+)\??/g, (_, n) => String.fromCharCode(Number(n) < 0 ? Number(n) + 65536 : Number(n))).replace(/\\par[d]?\b/g, '\n').replace(/\\'[0-9a-fA-F]{2}/g, '').replace(/\\[a-zA-Z]+-?\d* ?/g, '').replace(/[{}]/g, '').replace(/\\([\\{}])/g, '$1').trim();
  }

  function openPdf(file) {
    if (pdfUrl) URL.revokeObjectURL(pdfUrl); pdfUrl = URL.createObjectURL(file); pdf.src = pdfUrl; fileName = file.name; setDirty(false); showPdf();
  }

  async function openAny(file) {
    if (!file) return; const lower = file.name.toLowerCase();
    try {
      if (file.type === 'application/pdf' || lower.endsWith('.pdf')) return openPdf(file);
      if (lower.endsWith('.doc')) return toast('Старый .doc не поддерживается. Используйте .docx');
      if (lower.endsWith('.docx')) { const entries = await unzipEntries(file), data = entries.get('word/document.xml'); if (!data) throw new Error('В DOCX нет word/document.xml'); page.innerHTML = docxToHtml(new TextDecoder().decode(data)); }
      else if (lower.endsWith('.odt')) { const entries = await unzipEntries(file), data = entries.get('content.xml'); if (!data) throw new Error('В ODT нет content.xml'); page.innerHTML = odtToHtml(new TextDecoder().decode(data)); }
      else if (lower.endsWith('.rtf')) page.innerText = stripRtf(await file.text());
      else { const text = await file.text(); if (/\.(html|htm)$/i.test(lower)) page.innerHTML = text; else page.innerText = text; }
      fileName = file.name; setDirty(false); showDoc(); updateStatus(); saveDraft(true); toast(`Открыт: ${file.name}`);
    } catch (error) { console.error(error); toast(`Не удалось открыть файл: ${error?.message || error}`); }
    finally { fileInput.value = ''; }
  }

  function exec(command, value = null) { page.focus(); document.execCommand(command, false, value); setDirty(true); updateStatus(); saveDraft(); }
  function applyBlockStyle(tag) { page.focus(); document.execCommand('formatBlock', false, tag); setDirty(true); saveDraft(); }

  function replaceOne() {
    const q = $('q').value, replacement = $('replaceText').value; if (!q) return;
    const sel = getSelection();
    if (sel?.toString() && sel.toString().toLocaleLowerCase() === q.toLocaleLowerCase()) { document.execCommand('insertText', false, replacement); setDirty(true); updateStatus(); saveDraft(); }
    window.find(q, false, false, true, false, false, false);
  }

  function replaceAll() {
    const q = $('q').value; if (!q) return; const replacement = $('replaceText').value, text = page.innerText || '', escaped = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), next = text.replace(new RegExp(escaped, 'giu'), replacement);
    if (next === text) return toast('Совпадений нет'); page.innerText = next; setDirty(true); updateStatus(); saveDraft(); toast('Все совпадения заменены');
  }

  async function initWasm() {
    try { if (typeof G134LiteCore !== 'function') throw new Error('loader missing'); wasm = await G134LiteCore(); engineEl.textContent = 'C++ WASM: активен'; updateStatus(); }
    catch (error) { engineEl.textContent = 'C++: JS fallback'; console.warn(error); }
  }

  document.querySelectorAll('[data-cmd]').forEach(button => button.addEventListener('click', () => exec(button.dataset.cmd)));
  document.querySelectorAll('[data-save]').forEach(button => button.addEventListener('click', () => exportFormat(button.dataset.save)));
  page.addEventListener('input', () => { setDirty(true); updateStatus(); updateCursor(); saveDraft(); });
  page.addEventListener('keyup', updateCursor); page.addEventListener('mouseup', updateCursor); document.addEventListener('selectionchange', updateCursor);
  $('font').addEventListener('change', event => exec('fontName', event.target.value));
  $('size').addEventListener('change', event => { page.focus(); document.execCommand('fontSize', false, '4'); page.querySelectorAll('font[size="4"]').forEach(node => { node.removeAttribute('size'); node.style.fontSize = `${event.target.value}px`; }); setDirty(true); saveDraft(); });
  $('blockStyle').addEventListener('change', event => applyBlockStyle(event.target.value)); $('clearFormat').addEventListener('click', () => exec('removeFormat')); $('undo').addEventListener('click', () => exec('undo')); $('redo').addEventListener('click', () => exec('redo'));
  $('open').addEventListener('click', () => fileInput.click()); fileInput.addEventListener('change', event => openAny(event.target.files[0])); $('saveBtn').addEventListener('click', openSaveDialog); $('closeSave').addEventListener('click', closeSaveDialog); saveDialog.addEventListener('click', event => { if (event.target === saveDialog) closeSaveDialog(); });
  $('new').addEventListener('click', () => { if (dirty && !confirm('Сбросить несохранённые изменения?')) return; page.innerHTML = ''; fileName = 'без имени'; setDirty(false); showDoc(); updateStatus(); saveDraft(true); toast('Новый документ'); });
  themeSelect.addEventListener('change', event => applyTheme(event.target.value)); matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => { if (themeSelect.value === 'system') applyTheme('system'); }); wrapBtn.addEventListener('click', () => applyWrap(!wrap));
  $('zoomOut').addEventListener('click', () => applyZoom(zoom - 10)); $('zoomIn').addEventListener('click', () => applyZoom(zoom + 10)); zoomReset.addEventListener('click', () => applyZoom(100));
  $('focusBtn').addEventListener('click', () => { document.body.classList.toggle('focus'); $('focusBtn').classList.toggle('on'); });
  $('fullBtn').addEventListener('click', async () => { try { if (!document.fullscreenElement) await document.documentElement.requestFullscreen?.(); else await document.exitFullscreen?.(); } catch { toast('Полноэкранный режим недоступен'); } });
  autoLayout.addEventListener('change', () => applyLayout(autoLayout.checked ? 'auto' : detectedLayout)); layoutButtons.forEach(button => button.addEventListener('click', () => applyLayout(button.dataset.layoutMode)));
  addEventListener('resize', () => { clearTimeout(window.__g134Resize); window.__g134Resize = setTimeout(() => { if (layoutMode === 'auto') applyLayout('auto', false); }, 120); });
  addEventListener('orientationchange', () => setTimeout(() => { if (layoutMode === 'auto') applyLayout('auto', false); }, 180));
  $('find').addEventListener('click', () => { const q = $('q').value; if (q && !window.find(q)) toast('Совпадений больше нет'); }); $('replaceOne').addEventListener('click', replaceOne); $('replaceAll').addEventListener('click', replaceAll);
  addEventListener('keydown', event => { if (event.key === 'Escape') { closeSaveDialog(); $('searchBox').open = false; } if (!(event.ctrlKey || event.metaKey)) return; const key = event.key.toLowerCase(); if (key === 's') { event.preventDefault(); openSaveDialog(); } else if (key === 'o') { event.preventDefault(); fileInput.click(); } else if (key === 'n') { event.preventDefault(); $('new').click(); } else if (key === 'f') { event.preventDefault(); $('searchBox').open = true; $('q').focus(); } else if (key === '0') { event.preventDefault(); applyZoom(100); } else if (key === '+' || key === '=') { event.preventDefault(); applyZoom(zoom + 10); } else if (key === '-') { event.preventDefault(); applyZoom(zoom - 10); } });
  addEventListener('dragover', event => { event.preventDefault(); sheet.classList.add('drop-target'); }); addEventListener('dragleave', event => { if (event.relatedTarget === null) sheet.classList.remove('drop-target'); }); addEventListener('drop', event => { event.preventDefault(); sheet.classList.remove('drop-target'); openAny(event.dataTransfer.files[0]); }); addEventListener('beforeunload', event => { if (dirty) { saveDraft(true); event.preventDefault(); } });

  restoreDraft(); loadPrefs(); mark(); showDoc(); updateCursor(); initWasm();
})();
