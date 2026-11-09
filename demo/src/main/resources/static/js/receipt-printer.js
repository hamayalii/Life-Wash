/**
 * ReceiptPrinter — animated thermal receipt printer (vanilla JS, no dependencies).
 *
 * Re-implementation of the "payment receipt printer" pattern in plain JS + CSS.
 * Everything is namespaced under `ReceiptPrinter` and all markup is prefixed `.rp-`.
 *
 * Usage:
 *   ReceiptPrinter.open({ merchant: 'Shop', orderNumber: '#123', items: [...], total: 100 })
 *   new ReceiptPrinter(containerEl, { ... }) -> { update, replay, printCopy, destroy }
 *
 * All dynamic strings are inserted with textContent (or escaped in the print
 * document) to prevent XSS. `merchantLogo` accepts a DOM Node or an image URL.
 */
(function (global) {
  'use strict';

  var REDUCED_MOTION = typeof matchMedia === 'function' &&
    matchMedia('(prefers-reduced-motion: reduce)').matches;

  var DEFAULT_LABELS = {
    statusIdle: 'Idle',
    statusPrinting: 'Printing',
    statusCompleted: 'Completed',
    orderLabel: 'Order',
    dateLabel: 'Date',
    paymentLabel: 'Payment',
    customerLabel: 'Customer',
    phoneLabel: 'Phone',
    addressLabel: 'Address',
    notesLabel: 'Notes',
    subtotalLabel: 'Subtotal',
    taxLabel: 'Tax',
    discountLabel: 'Discount',
    totalLabel: 'Total',
    replay: 'Replay',
    printCopy: 'Print Copy',
    close: 'Close'
  };

  var DEFAULT_OPTIONS = {
    status: 'idle',
    merchant: '',
    merchantSubtext: '',
    merchantLogo: null,
    orderNumber: '',
    date: null,
    items: null,
    item: null,
    subtotal: null,
    tax: null,
    discount: null,
    total: null,
    currency: 'IQD',
    paymentMethod: '',
    customer: null,
    message: '',
    autoPrint: true,
    printDuration: 2.4,
    showStatusCard: true,
    statusTitle: 'Payment Complete',
    statusSubtitle: 'Receipt has been issued',
    showActions: true,
    showBarcode: true,
    showCutEffect: true,
    paperTheme: 'light',
    className: '',
    labels: null,
    onPrintStart: null,
    onPrintComplete: null,
    onReplay: null
  };

  function toNum(value) {
    var n = Number(value);
    return isFinite(n) ? n : 0;
  }

  function fmtQty(q) {
    var n = toNum(q);
    return Number.isInteger(n) ? n.toLocaleString('en-US') : String(Number(n.toFixed(2)));
  }

  function fmtMoney(value, currency) {
    var n = toNum(value);
    var s = Number.isInteger(n) ? n.toLocaleString('en-US') : n.toFixed(2);
    return currency ? (s + ' ' + currency) : s;
  }

  function esc(str) {
    return String(str == null ? '' : str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function ltrSpan(text) {
    var s = el('span');
    s.setAttribute('dir', 'ltr');
    s.textContent = text == null ? '' : text;
    return s;
  }

  function resolveItems(options) {
    var raw = options.items;
    if ((!raw || raw.length === 0) && options.item) raw = [options.item];
    if (!raw) raw = [];
    return raw.map(function (it) {
      var qty = toNum(it.qty);
      var unit = toNum(it.price);
      var total = it.total != null ? toNum(it.total) : (qty * unit);
      return {
        name: it.name != null ? String(it.name) : '',
        description: it.description != null ? String(it.description) : '',
        qty: qty,
        unit: unit,
        total: total
      };
    });
  }

  function ReceiptPrinter(container, options) {
    if (!container) throw new Error('ReceiptPrinter: container element is required');
    this.container = container;
    this.options = mergeOptions(options);
    this.labels = Object.assign({}, DEFAULT_LABELS, this.options.labels || {});
    this.items = resolveItems(this.options);

    var st = this.options.subtotal;
    this.subtotal = st != null ? toNum(st) : this.items.reduce(function (s, it) { return s + it.total; }, 0);
    var tt = this.options.total;
    this.total = tt != null ? toNum(tt) : this.subtotal;
    this.tax = this.options.tax != null ? toNum(this.options.tax) : null;
    this.discount = this.options.discount != null ? toNum(this.options.discount) : null;

    this.root = null;
    this.paper = null;
    this.paperHolder = null;
    this.statusBadge = null;
    this.statusTitleEl = null;
    this.statusSubtitleEl = null;
    this.statusCardEl = null;
    this.actionsEl = null;
    this.printBtnEl = null;
    this.replayBtnEl = null;
    this._timer = null;
    this._status = 'idle';
    this._closed = false;
    this._overlay = null;
    this._onKey = null;
    this._onBackdrop = null;
    this._onTransitionEnd = null;
    this._previouslyFocused = null;

    this._renderAll();
    this._setStatus(this.options.status || 'idle');
    if (this.options.autoPrint) this.print();
  }

  function mergeOptions(options) {
    var o = Object.assign({}, DEFAULT_OPTIONS, options || {});
    if (o.labels && typeof o.labels === 'object') {
      o.labels = Object.assign({}, DEFAULT_LABELS, o.labels);
    }
    return o;
  }

  ReceiptPrinter.prototype._renderAll = function () {
    var self = this;
    var o = this.options;
    var root = el('div', 'rp-root');
    if (o.className) root.className += ' ' + o.className;

    if (o.showStatusCard) {
      var card = el('div', 'rp-status-card');
      var textWrap = el('div', 'rp-status-text');
      this.statusTitleEl = el('div', 'rp-status-title', o.statusTitle);
      this.statusSubtitleEl = el('div', 'rp-status-subtitle', o.statusSubtitle);
      textWrap.appendChild(this.statusTitleEl);
      textWrap.appendChild(this.statusSubtitleEl);
      this.statusBadge = el('span', 'rp-status-badge');
      this.statusBadge.setAttribute('role', 'status');
      this.statusBadge.setAttribute('aria-live', 'polite');
      card.appendChild(textWrap);
      card.appendChild(this.statusBadge);
      root.appendChild(card);
      this.statusCardEl = card;
    } else {
      this.statusCardEl = null;
      this.statusBadge = null;
    }

    var wrap = el('div', 'rp-printer-wrap');
    var printer = el('div', 'rp-printer');
    var brandRow = el('div', 'rp-printer-brand');
    brandRow.appendChild(el('span', 'rp-printer-name', 'POS-8000'));
    var led = el('span', 'rp-printer-led');
    led.setAttribute('aria-hidden', 'true');
    brandRow.appendChild(led);
    printer.appendChild(brandRow);
    printer.appendChild(el('div', 'rp-printer-slot'));
    wrap.appendChild(printer);

    this.paperHolder = el('div', 'rp-paper-holder');
    this.paper = el('div', 'rp-paper rp-theme-' + (o.paperTheme || 'light'));
    this.paper.setAttribute('dir', 'rtl');
    this.paperHolder.appendChild(this.paper);
    wrap.appendChild(this.paperHolder);
    root.appendChild(wrap);

    this._onTransitionEnd = function (e) {
      if (e.target !== self.paper || e.propertyName !== 'transform') return;
      if (self._status === 'completed') return;
      self._clearTimer();
      self._setStatus('completed');
      if (typeof self.options.onPrintComplete === 'function') self.options.onPrintComplete.call(self);
    };
    this.paper.addEventListener('transitionend', this._onTransitionEnd);

    if (o.showActions) {
      var actions = el('div', 'rp-actions');

      var printBtn = el('button', 'rp-btn rp-btn-primary');
      printBtn.type = 'button';
      printBtn.appendChild(this._btnIcon('fa-solid fa-print'));
      printBtn.appendChild(el('span', null, this.labels.printCopy));
      printBtn.addEventListener('click', function () { self.printCopy(); });

      var replayBtn = el('button', 'rp-btn rp-btn-secondary');
      replayBtn.type = 'button';
      replayBtn.appendChild(this._btnIcon('fa-solid fa-rotate-right'));
      replayBtn.appendChild(el('span', null, this.labels.replay));
      replayBtn.addEventListener('click', function () { self.replay(); });

      actions.appendChild(printBtn);
      actions.appendChild(replayBtn);
      root.appendChild(actions);
      this.actionsEl = actions;
      this.printBtnEl = printBtn;
      this.replayBtnEl = replayBtn;
    } else {
      this.actionsEl = null;
      this.printBtnEl = null;
      this.replayBtnEl = null;
    }

    root.style.setProperty('--rp-duration', ((o.printDuration && o.printDuration > 0) ? o.printDuration : 2.4) + 's');

    this.container.appendChild(root);
    this.root = root;
    this._renderPaperContent();
  };

  ReceiptPrinter.prototype._appendLogo = function (parent, logo) {
    if (!logo) return;
    if (logo.nodeType) {
      parent.appendChild(logo);
      return;
    }
    if (typeof logo === 'string') {
      var t = logo.trim();
      if (/^(https?:|data:image\/|\/|\.{1,2}\/)/i.test(t) || /\.(png|jpe?g|gif|svg|webp)$/i.test(t)) {
        var img = document.createElement('img');
        img.className = 'rp-paper-logo';
        img.alt = '';
        img.src = t;
        parent.appendChild(img);
      } else {
        parent.appendChild(el('div', 'rp-paper-logo-text', t));
      }
    }
  };

  ReceiptPrinter.prototype._btnIcon = function (iconClass) {
    var i = el('i');
    i.className = iconClass;
    i.setAttribute('aria-hidden', 'true');
    return i;
  };

  ReceiptPrinter.prototype._dateStr = function () {
    var d = this.options.date;
    if (!d) return '';
    if (d instanceof Date) {
      if (isNaN(d.getTime())) return '';
      var day = d.toLocaleDateString('en-GB', { day: '2-digit', month: '2-digit', year: 'numeric' });
      var time = d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
      return day + ' - ' + time;
    }
    return String(d);
  };

  ReceiptPrinter.prototype._renderPaperContent = function () {
    var o = this.options;
    var L = this.labels;
    var paper = this.paper;
    paper.textContent = '';

    if (o.merchant) {
      var head = el('div', 'rp-paper-merchant');
      this._appendLogo(head, o.merchantLogo);
      head.appendChild(el('div', 'rp-paper-merchant-name', o.merchant));
      if (o.merchantSubtext) head.appendChild(el('div', 'rp-paper-merchant-sub', o.merchantSubtext));
      paper.appendChild(head);
    }

    var meta = el('div', 'rp-paper-meta');
    if (o.orderNumber != null && o.orderNumber !== '') {
      var orow = el('div', 'rp-paper-row');
      orow.appendChild(el('span', 'rp-paper-label', L.orderLabel));
      orow.appendChild(ltrSpan(o.orderNumber));
      meta.appendChild(orow);
    }
    var dateStr = this._dateStr();
    if (dateStr) {
      var drow = el('div', 'rp-paper-row');
      drow.appendChild(el('span', 'rp-paper-label', L.dateLabel));
      drow.appendChild(ltrSpan(dateStr));
      meta.appendChild(drow);
    }
    if (o.paymentMethod) {
      var prow = el('div', 'rp-paper-row');
      prow.appendChild(el('span', 'rp-paper-label', L.paymentLabel));
      prow.appendChild(ltrSpan(o.paymentMethod));
      meta.appendChild(prow);
    }
    if (meta.childNodes.length) paper.appendChild(meta);

    var cust = o.customer || {};
    var custHas = cust.name || cust.phone || cust.address || cust.notes;
    if (custHas) {
      var cbox = el('div', 'rp-paper-customer');
      cbox.appendChild(el('div', 'rp-paper-section-title', L.customerLabel));
      if (cust.name) cbox.appendChild(this._kvRow(L.customerLabel, cust.name));
      if (cust.phone) cbox.appendChild(this._kvRow(L.phoneLabel, cust.phone));
      if (cust.address) cbox.appendChild(this._kvRow(L.addressLabel, cust.address));
      if (cust.notes) cbox.appendChild(this._kvRow(L.notesLabel, cust.notes));
      paper.appendChild(cbox);
    }

    if (this.items.length) {
      var itemsBox = el('div', 'rp-paper-items');
      this.items.forEach(function (it) {
        itemsBox.appendChild(this._itemRow(it));
      }, this);
      paper.appendChild(itemsBox);
    }

    var totals = el('div', 'rp-paper-totals');
    totals.appendChild(this._kvRow(L.subtotalLabel, fmtMoney(this.subtotal, o.currency)));
    if (this.tax != null) totals.appendChild(this._kvRow(L.taxLabel, fmtMoney(this.tax, o.currency)));
    if (this.discount != null) totals.appendChild(this._kvRow(L.discountLabel, fmtMoney(this.discount, o.currency)));
    var totalRow = this._kvRow(L.totalLabel, fmtMoney(this.total, o.currency));
    totalRow.className += ' rp-paper-row-total';
    totals.appendChild(totalRow);
    paper.appendChild(totals);

    if (o.showBarcode && o.orderNumber) paper.appendChild(this._buildBarcode(o.orderNumber));

    if (o.message) {
      var footer = el('div', 'rp-paper-footer', o.message);
      paper.appendChild(footer);
    }

    if (o.showCutEffect) paper.appendChild(el('div', 'rp-cut'));
  };

  ReceiptPrinter.prototype._kvRow = function (label, value) {
    var row = el('div', 'rp-paper-row');
    row.appendChild(el('span', 'rp-paper-label', label));
    row.appendChild(ltrSpan(value));
    return row;
  };

  ReceiptPrinter.prototype._itemRow = function (it) {
    var row = el('div', 'rp-item');
    var info = el('div', 'rp-item-info');
    if (it.name) info.appendChild(el('div', 'rp-item-name', it.name));
    if (it.description) info.appendChild(el('div', 'rp-item-desc', it.description));

    var right = el('div', 'rp-item-right');
    var qtyText = fmtQty(it.qty) + (it.description ? '' : '') + ' × ' + fmtMoney(it.unit, '');
    right.appendChild(el('div', 'rp-item-qty', qtyText));
    right.appendChild(el('div', 'rp-item-line', fmtMoney(it.total, this.options.currency)));

    row.appendChild(info);
    row.appendChild(right);
    return row;
  };

  ReceiptPrinter.prototype._barcodeBars = function (orderNumber) {
    var bars = [];
    var seed = String(orderNumber || '000000');
    for (var i = 0; i < 40; i++) {
      var ch = seed.charCodeAt(i % seed.length) || 90;
      bars.push({ w: 1 + (ch % 3), h: 18 + (ch % 14) });
    }
    return bars;
  };

  ReceiptPrinter.prototype._buildBarcode = function (orderNumber) {
    var box = el('div', 'rp-barcode');
    var barsEl = el('div', 'rp-barcode-bars');
    this._barcodeBars(orderNumber).forEach(function (b) {
      var bar = el('span');
      bar.style.width = b.w + 'px';
      bar.style.height = b.h + 'px';
      barsEl.appendChild(bar);
    });
    var num = el('div', 'rp-barcode-text');
    num.setAttribute('dir', 'ltr');
    num.textContent = orderNumber;
    box.appendChild(barsEl);
    box.appendChild(num);
    return box;
  };

  ReceiptPrinter.prototype._statusLabel = function (s) {
    if (s === 'success') s = 'completed';
    var map = { idle: 'statusIdle', printing: 'statusPrinting', completed: 'statusCompleted' };
    return this.labels[map[s]] || s;
  };

  ReceiptPrinter.prototype._setStatus = function (s) {
    if (s === 'success') s = 'completed';
    this._status = s;
    if (this.root) {
      this.root.classList.remove('rp-idle', 'rp-printing', 'rp-completed');
      this.root.classList.add('rp-' + s);
    }
    if (this.statusBadge) {
      this.statusBadge.classList.remove('rp-badge-printing', 'rp-badge-completed');
      if (s === 'printing') this.statusBadge.classList.add('rp-badge-printing');
      else if (s === 'completed') this.statusBadge.classList.add('rp-badge-completed');
      this.statusBadge.textContent = this._statusLabel(s);
    }
    if (this.replayBtnEl) {
      this.replayBtnEl.disabled = (s === 'printing');
    }
  };

  ReceiptPrinter.prototype._clearTimer = function () {
    if (this._timer) {
      clearTimeout(this._timer);
      this._timer = null;
    }
  };

  ReceiptPrinter.prototype.print = function () {
    if (this._closed) return;
    this._clearTimer();
    var o = this.options;
    var duration = (o.printDuration && o.printDuration > 0) ? o.printDuration : 2.4;
    this.root.style.setProperty('--rp-duration', duration + 's');

    var self = this;
    var paper = this.paper;

    // Collapse instantly, then animate open. The forced reflows commit the
    // collapsed state so the transition actually runs (fixes "instantly full").
    this.root.classList.remove('rp-printing', 'rp-completed');
    this._setStatus('idle');
    paper.style.transition = 'none';
    void paper.offsetWidth; // commit collapsed (translateY(-100%))
    paper.style.transition = '';
    void paper.offsetWidth; // commit restored transition
    this._setStatus('printing');
    if (typeof o.onPrintStart === 'function') o.onPrintStart.call(this);

    var fallbackMs = (REDUCED_MOTION ? 0.6 : duration) * 1000 + 250;
    this._timer = setTimeout(function () {
      if (self._closed || self._status === 'completed') return;
      self._setStatus('completed');
      if (typeof o.onPrintComplete === 'function') o.onPrintComplete.call(self);
    }, fallbackMs);
  };

  ReceiptPrinter.prototype.replay = function () {
    if (this._closed) return;
    if (typeof this.options.onReplay === 'function') this.options.onReplay.call(this);
    this.print();
  };

  ReceiptPrinter.prototype.update = function (options) {
    if (this._closed) return;
    this.options = mergeOptions(options);
    this.labels = Object.assign({}, DEFAULT_LABELS, this.options.labels || {});
    this.items = resolveItems(this.options);

    var st = this.options.subtotal;
    this.subtotal = st != null ? toNum(st) : this.items.reduce(function (s, it) { return s + it.total; }, 0);
    var tt = this.options.total;
    this.total = tt != null ? toNum(tt) : this.subtotal;
    this.tax = this.options.tax != null ? toNum(this.options.tax) : null;
    this.discount = this.options.discount != null ? toNum(this.options.discount) : null;

    // Rebuild the whole subtree (safe DOM, keeps root mount point intact).
    var parent = this.root.parentNode;
    if (parent) parent.removeChild(this.root);
    this.root = null;
    this._renderAll();
    this._setStatus(this.options.status || 'idle');
    if (this.options.autoPrint) this.print();
  };

  ReceiptPrinter.prototype.destroy = function () {
    if (this._closed) return;
    this._closed = true;
    this._clearTimer();
    if (this._onKey) {
      document.removeEventListener('keydown', this._onKey);
      this._onKey = null;
    }
    if (this._onBackdrop && this._overlay) {
      this._overlay.removeEventListener('mousedown', this._onBackdrop);
      this._onBackdrop = null;
    }
    if (this._previouslyFocused) {
      try { this._previouslyFocused.focus(); } catch (e) { /* noop */ }
      this._previouslyFocused = null;
    }
    if (this._overlay) {
      if (this._overlay.parentNode) this._overlay.parentNode.removeChild(this._overlay);
      this._overlay = null;
    } else if (this.root && this.root.parentNode) {
      this.root.parentNode.removeChild(this.root);
    }
    this.root = null;
  };

  ReceiptPrinter.prototype.close = function () {
    this.destroy();
  };

  ReceiptPrinter.prototype._buildPrintHtml = function () {
    var o = this.options;
    var L = this.labels;
    var html = '';

    html += '<!DOCTYPE html><html dir="rtl"><head><meta charset="utf-8"><title>Receipt</title><style>';
    html += '@page{size:80mm auto;margin:0;}';
    html += '*{box-sizing:border-box;}';
    html += 'html,body{margin:0;padding:0;}';
    html += 'body{width:80mm;font-family:"Courier New",monospace;font-size:12px;line-height:1.45;color:#000;background:#fff;}';
    html += '.wrap{padding:5mm 4mm;}';
    html += '.center{text-align:center;}';
    html += '.merchant{font-weight:bold;font-size:14px;}';
    html += '.sub{font-size:11px;}';
    html += '.row{display:flex;justify-content:space-between;margin:1.5px 0;}';
    html += '.row .l{text-align:right;}';
    html += '.row .v{text-align:left;white-space:nowrap;}';
    html += '.items{border-top:1px dashed #000;border-bottom:1px dashed #000;margin:4mm 0;padding:2mm 0;}';
    html += '.item{display:flex;justify-content:space-between;margin:1.5mm 0;}';
    html += '.item .l{text-align:right;}';
    html += '.item .r{text-align:left;white-space:nowrap;}';
    html += '.item .d{font-size:10px;color:#333;}';
    html += '.total{font-weight:bold;font-size:13px;border-top:1px solid #000;margin-top:1mm;padding-top:1mm;}';
    html += '.barcode{text-align:center;margin:3mm 0;}';
    html += '.barcode .bars{display:inline-block;white-space:nowrap;}';
    html += '.barcode .bars span{display:inline-block;background:#000;vertical-align:bottom;margin-right:1px;}';
    html += '.barcode .txt{font-size:11px;letter-spacing:2px;margin-top:1mm;}';
    html += '.footer{text-align:center;font-size:10px;margin-top:3mm;white-space:pre-line;}';
    html += '</style></head><body><div class="wrap">';

    if (o.merchant) html += '<div class="center"><div class="merchant">' + esc(o.merchant) + '</div>' +
      (o.merchantSubtext ? '<div class="sub">' + esc(o.merchantSubtext) + '</div>' : '') + '</div>';

    if (o.orderNumber != null && o.orderNumber !== '') {
      html += '<div class="row"><span class="l">' + esc(L.orderLabel) + '</span><span class="v">' + esc(o.orderNumber) + '</span></div>';
    }
    var dateStr = this._dateStr();
    if (dateStr) html += '<div class="row"><span class="l">' + esc(L.dateLabel) + '</span><span class="v">' + esc(dateStr) + '</span></div>';
    if (o.paymentMethod) html += '<div class="row"><span class="l">' + esc(L.paymentLabel) + '</span><span class="v">' + esc(o.paymentMethod) + '</span></div>';

    var cust = o.customer || {};
    if (cust.name) html += '<div class="row"><span class="l">' + esc(L.customerLabel) + '</span><span class="v">' + esc(cust.name) + '</span></div>';
    if (cust.phone) html += '<div class="row"><span class="l">' + esc(L.phoneLabel) + '</span><span class="v">' + esc(cust.phone) + '</span></div>';
    if (cust.address) html += '<div class="row"><span class="l">' + esc(L.addressLabel) + '</span><span class="v">' + esc(cust.address) + '</span></div>';

    if (this.items.length) {
      html += '<div class="items">';
      this.items.forEach(function (it) {
        html += '<div class="item"><div class="l">' + esc(it.name);
        if (it.description) html += '<div class="d">' + esc(it.description) + '</div>';
        html += '</div><div class="r">' + esc(fmtQty(it.qty) + ' × ' + fmtMoney(it.unit, '')) +
          '<br>' + esc(fmtMoney(it.total, o.currency)) + '</div></div>';
      });
      html += '</div>';
    }

    html += '<div class="row"><span class="l">' + esc(L.subtotalLabel) + '</span><span class="v">' + esc(fmtMoney(this.subtotal, o.currency)) + '</span></div>';
    if (this.tax != null) html += '<div class="row"><span class="l">' + esc(L.taxLabel) + '</span><span class="v">' + esc(fmtMoney(this.tax, o.currency)) + '</span></div>';
    if (this.discount != null) html += '<div class="row"><span class="l">' + esc(L.discountLabel) + '</span><span class="v">' + esc(fmtMoney(this.discount, o.currency)) + '</span></div>';
    html += '<div class="row total"><span class="l">' + esc(L.totalLabel) + '</span><span class="v">' + esc(fmtMoney(this.total, o.currency)) + '</span></div>';

    if (o.showBarcode && o.orderNumber) {
      html += '<div class="barcode"><div class="bars">';
      this._barcodeBars(o.orderNumber).forEach(function (b) {
        html += '<span style="width:' + b.w + 'px;height:' + b.h + 'px;"></span>';
      });
      html += '</div><div class="txt">' + esc(o.orderNumber) + '</div></div>';
    }

    if (o.message) html += '<div class="footer">' + esc(o.message) + '</div>';

    html += '</div></body></html>';
    return html;
  };

  ReceiptPrinter.prototype.printCopy = function () {
    var html = this._buildPrintHtml();
    var iframe = document.createElement('iframe');
    iframe.setAttribute('aria-hidden', 'true');
    iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden;';
    document.body.appendChild(iframe);

    var printed = false;
    var win = iframe.contentWindow;
    var doc = win.document;

    var doPrint = function () {
      if (printed) return;
      printed = true;
      try { win.focus(); win.print(); } catch (e) { /* noop */ }
      setTimeout(function () {
        if (iframe.parentNode) iframe.parentNode.removeChild(iframe);
      }, 1000);
    };

    iframe.onload = doPrint;
    doc.open();
    doc.write(html);
    doc.close();
    setTimeout(doPrint, 500); // fallback if onload never fires
  };

  ReceiptPrinter.open = function (options) {
    var overlay = el('div', 'rp-overlay');
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');
    overlay.setAttribute('aria-label', 'Receipt');

    var dialog = el('div', 'rp-dialog');

    var closeBtn = el('button', 'rp-close');
    closeBtn.type = 'button';
    var closeLabel = (options && options.labels && options.labels.close) || DEFAULT_LABELS.close;
    closeBtn.setAttribute('aria-label', closeLabel);
    closeBtn.textContent = '✕';

    dialog.appendChild(closeBtn);
    overlay.appendChild(dialog);
    document.body.appendChild(overlay);

    var instance = new ReceiptPrinter(dialog, options);
    instance._overlay = overlay;
    instance._closeBtn = closeBtn;
    instance._previouslyFocused = document.activeElement;

    instance._onKey = function (e) {
      if (e.key === 'Escape' || e.key === 'Esc') instance.close();
    };
    instance._onBackdrop = function (e) {
      if (e.target === overlay) instance.close();
    };

    closeBtn.addEventListener('click', function () { instance.close(); });
    document.addEventListener('keydown', instance._onKey);
    overlay.addEventListener('mousedown', instance._onBackdrop);

    closeBtn.focus();
    return instance;
  };

  global.ReceiptPrinter = ReceiptPrinter;
})(window);
