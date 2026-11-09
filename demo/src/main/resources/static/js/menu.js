/**
 * Shared admin navigation menu — single source of truth.
 *
 * Renders the same menu items (in the same order) on every page, and marks the
 * current page with `.active` + aria-current="page" (it never hides an item).
 * Loaded synchronously at the end of <body> so it renders before first paint
 * and before the page's own scripts attach listeners.
 */
(function () {
  'use strict';

  var MENU_ITEMS = [
    { label: 'سەرەکی', href: '/index.html' },
    { label: 'داشبۆرد', href: '/dashboard.html' },
    { label: 'خاڵی داواکردن (POS)', href: '/pos.html' },
    { label: 'ئامار', href: '/report.html' },
    { label: 'ڕێکخستنی نرخەکان', href: '/settings.html' }
  ];

  function normalizePath(path) {
    var p = String(path || '');
    p = p.split('?')[0].split('#')[0];
    p = p.replace(/\/+$/, '');
    if (!p) return '/';
    return p;
  }

  function currentPath() {
    var p = normalizePath(window.location.pathname);
    if (p === '/' || p.toLowerCase() === '/index.html') return '/index.html';
    return p;
  }

  function isActive(href) {
    var cur = currentPath();
    var target = normalizePath(href);
    if (target.toLowerCase() === '/index.html') {
      return cur === '/' || cur.toLowerCase() === '/index.html';
    }
    // Exact match, or sub-route match (e.g. /orders/123 highlights /orders)
    return cur === target || cur.indexOf(target + '/') === 0;
  }

  function render() {
    var dropdown = document.querySelector('#adminMenu .admin-dropdown');
    if (!dropdown) return;

    dropdown.textContent = '';

    MENU_ITEMS.forEach(function (item) {
      var a = document.createElement('a');
      a.href = item.href;
      a.textContent = item.label;
      if (isActive(item.href)) {
        a.classList.add('active');
        a.setAttribute('aria-current', 'page');
      }
      dropdown.appendChild(a);
    });

    var logoutBtn = document.createElement('button');
    logoutBtn.type = 'button';
    logoutBtn.id = 'logoutBtn';
    logoutBtn.className = 'logout-btn';

    var icon = document.createElement('i');
    icon.className = 'fa-solid fa-right-from-bracket';
    icon.setAttribute('aria-hidden', 'true');
    logoutBtn.appendChild(icon);
    logoutBtn.appendChild(document.createTextNode(' چوونەدەرەوە'));

    dropdown.appendChild(logoutBtn);
  }

  // Keep the dropdown anchored to the toggle and fully inside the viewport.
  // Runs after each page's own toggle handler (via the observer below), so it
  // is the single authority for the final position on every page.
  function positionDropdown() {
    var toggle = document.getElementById('adminToggle');
    var dropdown = document.querySelector('#adminMenu .admin-dropdown');
    if (!toggle || !dropdown || dropdown.classList.contains('hidden')) return;

    var tr = toggle.getBoundingClientRect();
    var vw = window.innerWidth;
    dropdown.style.top = (tr.bottom + 4) + 'px';
    dropdown.style.right = (vw - tr.right) + 'px';

    var r = dropdown.getBoundingClientRect();
    var shift = 0;
    if (r.left < 4) shift = 4 - r.left;
    else if (r.right > vw - 4) shift = (vw - 4) - r.right;
    if (shift !== 0) {
      var cur = parseFloat(dropdown.style.right) || 0;
      dropdown.style.right = (cur - shift) + 'px';
    }
  }

  var dropdownEl = document.querySelector('#adminMenu .admin-dropdown');
  if (dropdownEl && typeof MutationObserver === 'function') {
    var observer = new MutationObserver(positionDropdown);
    // Observe only the open/close class toggle (not style), so our own inline
    // top/right writes can't re-trigger this observer into an infinite loop.
    observer.observe(dropdownEl, { attributes: true, attributeFilter: ['class'] });
  }
  window.addEventListener('resize', positionDropdown);

  render();
})();
