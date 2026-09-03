// ── GLOBAL INTERCEPTOR FOR EASTERN ARABIC/KURDISH NUMERALS ─────────────────────
// Normalizes Eastern Arabic/Persian numerals to standard ASCII numbers
// This ensures that users can input Kurdish/Arabic numerals (٠-٩, ۰-۹) and they will be
// automatically converted to standard ASCII numbers (0-9) for proper JavaScript processing
document.addEventListener('input', function(e) {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
    const arabicToEnglishMap = {
      '٠': '0', '١': '1', '٢': '2', '٣': '3', '٤': '4',
      '٥': '5', '٦': '6', '٧': '7', '٨': '8', '٩': '9',
      '۰': '0', '۱': '1', '۲': '2', '۳': '3', '۴': '4',
      '۵': '5', '۶': '6', '۷': '7', '۸': '8', '۹': '9'
    };

    let originalValue = e.target.value;
    let normalizedValue = originalValue.replace(/[٠-٩۰-۹]/g, function(match) {
      return arabicToEnglishMap[match];
    });

    if (originalValue !== normalizedValue) {
      // Update the value seamlessly without losing cursor position
      const start = e.target.selectionStart;
      const end = e.target.selectionEnd;
      e.target.value = normalizedValue;
      e.target.setSelectionRange(start, end);
    }
  }
});

// ── DISABLE MOUSE WHEEL SCROLLING ON NUMBER INPUTS ─────────────────────────────
// Prevents accidental value changes when scrolling past number inputs
document.addEventListener('wheel', function(e) {
  if (e.target.type === 'number') {
    e.preventDefault();
    e.target.blur();
  }
}, { passive: false });

// ── IDEMPOTENCY KEY FUNCTIONS (Network Drop Protection) ─────────────────────
// Generic utility functions for generating and managing idempotency keys
// Used across POS and web order forms to prevent duplicate submissions

// In-memory fallback for Incognito/Restricted modes where sessionStorage is unavailable
let inMemoryIdempotencyKey = null;

/**
 * Generate UUID v4 for idempotency
 * Ensures unique request identification for retry scenarios
 * @returns {string} UUID v4 string
 */
function generateIdempotencyKey() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

/**
 * Get or create idempotency key from sessionStorage
 * Persists across page reloads for retry scenarios
 * Includes in-memory fallback for Incognito/Restricted modes
 * @param {string} storageKey - The sessionStorage key to use (e.g., 'pos_idempotency_key', 'web_idempotency_key')
 * @returns {string} The idempotency key
 */
function getIdempotencyKey(storageKey) {
  try {
    let key = sessionStorage.getItem(storageKey);
    if (!key) {
      key = generateIdempotencyKey();
      sessionStorage.setItem(storageKey, key);
    }
    return key;
  } catch (e) {
    // Fallback for Incognito/Restricted modes
    console.warn('SessionStorage unavailable, using RAM fallback.');
    if (!inMemoryIdempotencyKey) {
      inMemoryIdempotencyKey = generateIdempotencyKey();
    }
    return inMemoryIdempotencyKey;
  }
}

/**
 * Clear idempotency key after successful order
 * Prevents stale keys from interfering with new orders
 * @param {string} storageKey - The sessionStorage key to clear
 */
function clearIdempotencyKey(storageKey) {
  sessionStorage.removeItem(storageKey);
}
