// ── GLOBAL INTERCEPTOR FOR EASTERN ARABIC/KURDISH NUMERALS ─────────────────────
// Normalizes Eastern Arabic/Persian numerals to standard ASCII numbers
// This ensures that users can input Kurdish/Arabic numerals (٠-٩, ۰-۹) and they will be
// automatically converted to standard ASCII numbers (0-9) for proper JavaScript processing
const arabicToEnglishMap = {
  '٠': '0', '١': '1', '٢': '2', '٣': '3', '٤': '4',
  '٥': '5', '٦': '6', '٧': '7', '٨': '8', '٩': '9',
  '۰': '0', '۱': '1', '۲': '2', '۳': '3', '۴': '4',
  '۵': '5', '۶': '6', '۷': '7', '۸': '8', '۹': '9'
};

function convertKurdishNumerals(value) {
  return value.replace(/[٠-٩۰-۹]/g, function(match) {
    return arabicToEnglishMap[match];
  });
}

/**
 * Validates that a string is a pure numeric value (integer or decimal)
 * Rejects partially numeric strings like "123abc" or "12.34.56"
 * Accepts: "123", "123.45", "0.5", "-123" (if allowNegative)
 * @param {string} value - The string to validate
 * @param {boolean} allowNegative - Whether to allow negative numbers (default: false)
 * @returns {boolean} True if the string is a valid pure number
 */
function isValidNumericString(value, allowNegative = false) {
  if (value === '' || value === null || value === undefined) {
    return false;
  }

  // After Kurdish numeral conversion, check if it's a valid number
  const converted = convertKurdishNumerals(value);

  // Regular expression for pure numeric validation
  // Allows: optional sign, digits, optional decimal point, more digits
  // Rejects: any non-numeric characters, multiple decimal points
  const regex = allowNegative
    ? /^-?\d*\.?\d+$/  // Allows negative numbers
    : /^\d*\.?\d+$/;   // Only positive numbers

  return regex.test(converted);
}

// MutationObserver to handle dynamically added inputs (for elements added after page load)
if (typeof MutationObserver !== 'undefined') {
  const observer = new MutationObserver(function(mutations) {
    mutations.forEach(function(mutation) {
      mutation.addedNodes.forEach(function(node) {
        // Check if the added node is an input or textarea
        if (node.nodeType === 1) { // Element node
          if (node.tagName === 'INPUT' || node.tagName === 'TEXTAREA') {
            // Convert any existing value
            if (node.value) {
              const normalizedValue = convertKurdishNumerals(node.value);
              if (node.value !== normalizedValue) {
                node.value = normalizedValue;
              }
            }
            // Attach conversion listener directly to this input
            // This ensures it runs before any other element-level listeners
            attachConversionListener(node);
          }
          // Also check child nodes for inputs/textareas
          const inputs = node.querySelectorAll ? node.querySelectorAll('input, textarea') : [];
          inputs.forEach(function(input) {
            if (input.value) {
              const normalizedValue = convertKurdishNumerals(input.value);
              if (input.value !== normalizedValue) {
                input.value = normalizedValue;
              }
            }
            // Attach conversion listener directly to this input
            attachConversionListener(input);
          });
        }
      });
    });
  });

  // Start observing the document with the configured parameters
  observer.observe(document.body, {
    childList: true,
    subtree: true
  });
}

// Attach conversion listeners to all existing inputs when the page loads
document.addEventListener('DOMContentLoaded', function() {
  const existingInputs = document.querySelectorAll('input, textarea');
  existingInputs.forEach(function(input) {
    attachConversionListener(input);
  });
});

// Function to attach conversion listener to an input element
function attachConversionListener(element) {
  // Check if we've already attached a listener to this element
  if (element.dataset.kurdishConversionAttached === 'true') {
    return;
  }

  // Mark as attached to prevent duplicate listeners
  element.dataset.kurdishConversionAttached = 'true';

  // Attach input listener with capture phase and useCapture flag
  element.addEventListener('input', function(e) {
    const originalValue = element.value;
    const normalizedValue = convertKurdishNumerals(originalValue);

    if (originalValue !== normalizedValue) {
      // Save cursor position before updating
      const start = element.selectionStart;
      const end = element.selectionEnd;

      // Update the value
      element.value = normalizedValue;

      // Restore cursor position
      requestAnimationFrame(function() {
        try {
          element.setSelectionRange(start, end);
        } catch (err) {
          element.setSelectionRange(normalizedValue.length, normalizedValue.length);
        }
      });
    }
  }, true); // Use capture phase to fire before other listeners on this element

  // Also handle paste events
  element.addEventListener('paste', function(e) {
    setTimeout(function() {
      const originalValue = element.value;
      const normalizedValue = convertKurdishNumerals(originalValue);

      if (originalValue !== normalizedValue) {
        const start = element.selectionStart;
        const end = element.selectionEnd;
        element.value = normalizedValue;
        requestAnimationFrame(function() {
          try {
            element.setSelectionRange(start, end);
          } catch (err) {
            element.setSelectionRange(normalizedValue.length, normalizedValue.length);
          }
        });
      }
    }, 0);
  }, true); // Use capture phase
}

// ── DISABLE MOUSE WHEEL SCROLLING ON NUMBER INPUTS ─────────────────────────────
// Prevents accidental value changes when scrolling past number inputs
// Also applies to text inputs with inputmode="decimal" (used for Kurdish numeral support)
document.addEventListener('wheel', function(e) {
  if (e.target.type === 'number' || e.target.inputMode === 'decimal') {
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
