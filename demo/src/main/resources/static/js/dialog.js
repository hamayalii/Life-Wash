/**
 * AppDialog — unified modal dialog (vanilla JS, no dependencies).
 *
 * Promise-based replacement for native alert()/confirm()/prompt():
 *   await AppDialog.alert({ title, message, type })           -> Promise<void>
 *   await AppDialog.confirm({ title, message, danger, ... })  -> Promise<boolean>
 *   await AppDialog.prompt({ title, message, placeholder })   -> Promise<string|null>
 *
 * type: 'info' | 'success' | 'warning' | 'error'.
 * Dialogs are queued (shown one after another) and never stacked.
 * All dynamic text is inserted with textContent (XSS-safe).
 */
(function (global) {
  'use strict';

  var ICONS = {
    info: 'fa-solid fa-circle-info',
    success: 'fa-solid fa-circle-check',
    warning: 'fa-solid fa-triangle-exclamation',
    error: 'fa-solid fa-circle-xmark'
  };

  var DEFAULTS = {
    ok: 'باشە',
    cancel: 'پاشگەزبوونەوە',
    confirm: 'دڵنیام'
  };

  var FOCUSABLE = 'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

  var queue = Promise.resolve();

  function enqueue(fn) {
    var run = queue.then(function () { return fn(); });
    queue = run.then(function () {}, function () {});
    return run;
  }

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function typeIcon(type) {
    return ICONS[type] || ICONS.info;
  }

  function focusable(dialog) {
    return Array.prototype.slice.call(dialog.querySelectorAll(FOCUSABLE));
  }

  function show(kind, opts) {
    return new Promise(function (resolve) {
      opts = opts || {};
      var type = opts.type || (kind === 'confirm' ? 'warning' : 'info');
      var isConfirm = kind === 'confirm';
      var isPrompt = kind === 'prompt';

      var previouslyFocused = document.activeElement;
      var prevOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';

      var overlay = el('div', 'ad-overlay');
      var dialog = el('div', 'ad-dialog');
      dialog.setAttribute('role', isPrompt ? 'dialog' : 'alertdialog');
      dialog.setAttribute('aria-modal', 'true');
      dialog.setAttribute('aria-labelledby', 'ad-title-' + Date.now());
      dialog.setAttribute('aria-describedby', 'ad-msg-' + Date.now());

      var titleId = dialog.getAttribute('aria-labelledby');
      var msgId = dialog.getAttribute('aria-describedby');

      // Icon
      var iconWrap = el('div', 'ad-icon ad-icon-' + type);
      iconWrap.appendChild(el('i', typeIcon(type)));
      dialog.appendChild(iconWrap);

      // Title
      var title = el('h2', 'ad-title', opts.title || '');
      title.id = titleId;
      dialog.appendChild(title);

      // Message
      var msg = el('div', 'ad-msg', opts.message || '');
      msg.id = msgId;
      dialog.appendChild(msg);

      // Prompt input
      var input = null;
      if (isPrompt) {
        input = el('input', 'ad-input');
        input.type = 'text';
        input.placeholder = opts.placeholder || '';
        if (opts.defaultValue != null) input.value = opts.defaultValue;
        input.setAttribute('dir', 'auto');
        dialog.appendChild(input);
      }

      // Actions
      var actions = el('div', 'ad-actions');
      var primaryText = isConfirm ? (opts.confirmText || DEFAULTS.confirm)
        : (opts.okText || DEFAULTS.ok);

      var primary = el('button', 'ad-btn ad-btn-primary' + (opts.danger ? ' ad-btn-danger' : ''), primaryText);
      primary.type = 'button';

      var cancel = null;
      if (isConfirm || isPrompt) {
        cancel = el('button', 'ad-btn ad-btn-cancel', opts.cancelText || DEFAULTS.cancel);
        cancel.type = 'button';
      }

      // RTL: primary on the right (first child), cancel on the left.
      actions.appendChild(primary);
      if (cancel) actions.appendChild(cancel);
      dialog.appendChild(actions);

      overlay.appendChild(dialog);
      document.body.appendChild(overlay);

      var closed = false;

      function cleanup() {
        if (closed) return;
        closed = true;
        document.removeEventListener('keydown', onKey);
        overlay.removeEventListener('mousedown', onBackdrop);
        if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
        document.body.style.overflow = prevOverflow;
        if (previouslyFocused && typeof previouslyFocused.focus === 'function') {
          try { previouslyFocused.focus(); } catch (e) { /* noop */ }
        }
      }

      function closeWith(value) {
        cleanup();
        resolve(value);
      }

      function doCancel() {
        closeWith(isConfirm ? false : (isPrompt ? null : undefined));
      }

      function doPrimary() {
        if (isPrompt) {
          closeWith(input.value);
        } else if (isConfirm) {
          closeWith(true);
        } else {
          closeWith(undefined);
        }
      }

      function onKey(e) {
        if (e.key === 'Escape') {
          e.preventDefault();
          doCancel();
          return;
        }
        // Enter inside the prompt input confirms (buttons handle their own Enter natively).
        if (e.key === 'Enter' && isPrompt && document.activeElement === input) {
          e.preventDefault();
          doPrimary();
          return;
        }
        if (e.key === 'Tab') {
          var items = focusable(dialog);
          if (!items.length) return;
          var first = items[0];
          var last = items[items.length - 1];
          if (e.shiftKey && document.activeElement === first) {
            e.preventDefault();
            last.focus();
          } else if (!e.shiftKey && document.activeElement === last) {
            e.preventDefault();
            first.focus();
          }
        }
      }

      function onBackdrop(e) {
        if (e.target !== overlay) return;
        if (opts.danger) return; // danger dialogs require an explicit choice
        doCancel();
      }

      primary.addEventListener('click', doPrimary);
      if (cancel) cancel.addEventListener('click', doCancel);
      overlay.addEventListener('mousedown', onBackdrop);
      document.addEventListener('keydown', onKey);

      // Focus into the dialog.
      if (isPrompt && input) {
        input.focus();
        if (opts.defaultValue != null) input.select();
      } else {
        primary.focus();
      }
    });
  }

  global.AppDialog = {
    alert: function (opts) {
      return enqueue(function () { return show('alert', opts); });
    },
    confirm: function (opts) {
      return enqueue(function () { return show('confirm', opts); });
    },
    prompt: function (opts) {
      return enqueue(function () { return show('prompt', opts); });
    }
  };
})(window);
