(() => {
  'use strict';

  const workspace = document.getElementById('workspace');
  if (!workspace) return;

  let pendingX = 0;
  let pendingY = 0;
  let frame = 0;

  function pixelsFromWheel(event) {
    let x = event.deltaX;
    let y = event.deltaY;

    if (event.deltaMode === WheelEvent.DOM_DELTA_LINE) {
      x *= 18;
      y *= 18;
    } else if (event.deltaMode === WheelEvent.DOM_DELTA_PAGE) {
      const page = Math.max(320, workspace.clientHeight * 0.88);
      x *= page;
      y *= page;
    }

    if (event.shiftKey && Math.abs(y) > Math.abs(x)) {
      x = y;
      y = 0;
    }

    return { x, y };
  }

  function flushWheel() {
    frame = 0;
    if (!pendingX && !pendingY) return;

    workspace.scrollLeft += pendingX;
    workspace.scrollTop += pendingY;
    pendingX = 0;
    pendingY = 0;
  }

  workspace.addEventListener('wheel', event => {
    // Ctrl/Cmd + wheel belongs to browser/page zoom and must not be hijacked.
    if (event.ctrlKey || event.metaKey) return;

    const { x, y } = pixelsFromWheel(event);
    if (!x && !y) return;

    event.preventDefault();
    pendingX += x;
    pendingY += y;

    if (!frame) frame = requestAnimationFrame(flushWheel);
  }, { passive: false });

  // The workspace must be scrollable immediately under the pointer. No click/focus required.
  workspace.dataset.wheelScroll = 'ready';
})();
