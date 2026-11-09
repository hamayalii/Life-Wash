# Kurdish Numeral Conversion - Element-Level Listener Fix

## Root Cause Identified

The previous fix using a global document-level listener with capture phase **did not work** because:

1. **Capture phase on document fires too early in the DOM tree**
   - Document capture fires before reaching the target element
   - By the time the event reaches the target, document listener has already fired
   - Element-level listeners on the target element fire during the target/bubbling phase
   - The timing mismatch still allowed element listeners to read unconverted values

2. **Global listener wasn't attached to dynamically created inputs**
   - The MutationObserver only converted initial values
   - It didn't attach persistent event listeners to handle ongoing typing
   - Once the initial value was set, subsequent typing wasn't converted

## Solution Implemented

**Direct element-level listeners with capture phase:**

Instead of a single global listener, we now attach conversion listeners **directly to each input element** using the capture phase on the element itself.

### How This Works

**Element-level capture phase:**
```javascript
element.addEventListener('input', conversionFunction, true);
// true = useCapture, fires during capture phase on this element
```

**Event flow with element-level capture:**
```
1. CAPTURE PHASE (down the DOM tree)
   document → html → body → ... → target element
   ✅ Element's capture listener fires HERE (conversion happens)

2. TARGET PHASE
   target element
   ✅ Element's bubbling listeners fire HERE (read converted value)

3. BUBBLING PHASE (up the DOM tree)
   target element → ... → body → html → document
```

**Key advantage:** The capture listener on the element itself fires **immediately when the event reaches the element**, before any bubbling listeners on that same element.

## Implementation Details

### 1. `attachConversionListener()` Function

**Lines 17-72:** New function that attaches conversion listeners to a single input element

```javascript
function attachConversionListener(element) {
  // Prevent duplicate listeners
  if (element.dataset.kurdishConversionAttached === 'true') {
    return;
  }
  element.dataset.kurdishConversionAttached = 'true';

  // Attach input listener with capture phase
  element.addEventListener('input', function(e) {
    const originalValue = element.value;
    const normalizedValue = convertKurdishNumerals(originalValue);

    if (originalValue !== normalizedValue) {
      // Save cursor position
      const start = element.selectionStart;
      const end = element.selectionEnd;

      // Update value
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
  }, true); // Use capture phase on this element

  // Also handle paste events
  element.addEventListener('paste', function(e) {
    // ... similar logic with setTimeout
  }, true);
}
```

### 2. DOMContentLoaded Listener

**Lines 197-206:** Attach conversion listeners to all existing inputs when page loads

```javascript
document.addEventListener('DOMContentLoaded', function() {
  const existingInputs = document.querySelectorAll('input, textarea');
  existingInputs.forEach(function(input) {
    attachConversionListener(input);
  });
});
```

This ensures all static inputs in the HTML get conversion listeners immediately.

### 3. MutationObserver

**Lines 117-195:** Attach conversion listeners to dynamically added inputs

```javascript
const observer = new MutationObserver(function(mutations) {
  mutations.forEach(function(mutation) {
    mutation.addedNodes.forEach(function(node) {
      if (node.nodeType === 1) {
        if (node.tagName === 'INPUT' || node.tagName === 'TEXTAREA') {
          // Convert initial value
          if (node.value) {
            const normalizedValue = convertKurdishNumerals(node.value);
            if (node.value !== normalizedValue) {
              node.value = normalizedValue;
            }
          }
          // Attach conversion listener
          attachConversionListener(node);
        }
        // Also check child nodes
        const inputs = node.querySelectorAll('input, textarea');
        inputs.forEach(function(input) {
          if (input.value) {
            const normalizedValue = convertKurdishNumerals(input.value);
            if (input.value !== normalizedValue) {
              input.value = normalizedValue;
            }
          }
          attachConversionListener(input);
        });
      }
    });
  });
});

observer.observe(document.body, {
  childList: true,
  subtree: true
});
```

This ensures dynamically created inputs (like homepage quantity fields) get conversion listeners.

## Why This Should Work

1. **Direct attachment:** Each input has its own conversion listener
2. **Element-level capture:** Listener fires immediately when event reaches the element
3. **No global conflicts:** No global document listener to cause timing issues
4. **Persistent listeners:** MutationObserver attaches listeners to all dynamically added inputs
5. **Duplicate prevention:** `dataset.kurdishConversionAttached` prevents attaching multiple listeners

## Comparison of Approaches

### Approach 1: Global Document Listener (Failed)
- ❌ Document capture fires too early in DOM tree
- ❌ Element listeners fire during target/bubbling phase
- ❌ Timing mismatch allowed reading unconverted values

### Approach 2: Global Document Listener + Element-Level Conversion (Failed)
- ❌ Both tried to convert, creating conflicts
- ❌ Race conditions between listeners
- ❌ Values overwritten

### Approach 3: Element-Level Capture Listeners (Current)
- ✅ Each input has its own listener attached directly
- ✅ Element capture fires immediately when event reaches element
- ✅ Fires before any bubbling listeners on same element
- ✅ No conflicts or race conditions
- ✅ MutationObserver ensures dynamic inputs get listeners

## Files Modified

**`js/global-utils.js`** (complete rewrite of listener logic):
- Lines 17-72: New `attachConversionListener()` function
- Lines 117-195: Modified MutationObserver to call `attachConversionListener()`
- Lines 197-206: Added DOMContentLoaded listener for existing inputs
- Removed: Global document-level input/paste listeners (no longer needed)

**Other files unchanged:**
- `dynamic-services.js` - No element-level conversion (relies on element-level listeners from global-utils)
- `settings.js` - No element-level conversion (relies on element-level listeners from global-utils)
- HTML files - Input types already changed to `type="text"` with `inputmode="decimal"`

## Required Testing

**I cannot perform real-browser testing.** You must test the following:

### Critical: Force Refresh Browser
**Force refresh** (`Ctrl + Shift + R`) to load updated JavaScript files.

### Test 1: Homepage Quantity Inputs (index.html)

1. Open `http://localhost:8080/index.html`
2. **Force refresh browser** (`Ctrl + Shift + R`)
3. Select a service (e.g., فەرش or پەردە)
4. In the quantity field, type Kurdish numerals:
   - Type `١٢٣` → Should immediately convert to `123`
   - Type `٥٠٠٠` → Should immediately convert to `5000`
   - Type `٣.٥` or `٣.٥` → Should immediately convert to `3.5`
   - Type `١٥٠٠٠` → Should immediately convert to `15000`
5. Verify the cart total calculates correctly
6. Test with multiple services (all quantity inputs should work)
7. Test pasting Kurdish numerals (Ctrl+V)
8. Test editing values in the middle of an existing number
9. Test clearing a field and re-entering

### Test 2: Settings Page - Base Price (settings.html)

1. Open `http://localhost:8080/settings.html`
2. **Force refresh browser** (`Ctrl + Shift + R`)
3. Create a new service or edit existing one
4. In the base price field, type Kurdish numerals:
   - Type `١٥٠٠٠` → Should immediately convert to `15000`
   - Type `٥٠٠٠` → Should immediately convert to `5000`
5. Click save and verify the value is saved correctly
6. Reload the page and verify the saved value displays correctly

### Test 3: Settings Page - Discount Price (settings.html)

1. On the settings page, enable discount for a service
2. In the discount price field, type Kurdish numerals:
   - Type `١٠٠٠٠` → Should immediately convert to `10000`
   - Type `٥٠٠` → Should immediately convert to `500`
3. Verify the discount percentage is calculated automatically
4. Click save and verify the value is saved correctly

### Test 4: Settings Page - Discount Percentage (settings.html)

1. On the settings page, enable discount for a service
2. In the discount percentage field, type Kurdish numerals:
   - Type `١٥` → Should immediately convert to `15`
   - Type `٢٠` → Should immediately convert to `20`
3. Verify the discount price is calculated automatically
4. Click save and verify the value is saved correctly

### Test 5: Settings Page - Sofa Total (settings.html)

1. On the settings page, edit a SOFA service
2. In the sofa total field, type Kurdish numerals:
   - Type `١٠٠٠٠` → Should immediately convert to `10000`
3. Verify the per-person rate is calculated automatically
4. Click save and verify the value is saved correctly

### Test 6: Phone Numbers (Regression Test)

1. On any page with phone input, type Kurdish numerals:
   - Type `٠٧٥٠١٢٣٤٥٦٧` → Should convert to `07501234567`
2. Verify phone validation still works

### Test 7: POS Modal Cancel Button (Regression Test)

1. Open `http://localhost:8080/pos.html`
2. Click a service card to open modal
3. Verify the "پاشگەزبوونەوە" button is **red**
4. Verify it darkens on hover
5. Click cancel and verify modal closes

## Debugging If It Still Doesn't Work

### Check if Listeners Are Attached

In browser console:
```javascript
// Check if dataset marker is set
document.querySelectorAll('input').forEach(input => {
  console.log(input.id, input.dataset.kurdishConversionAttached);
});

// Should show "true" for all inputs
```

### Check Event Listeners on an Input

In browser console:
```javascript
// Get a specific input (e.g., quantity field)
const input = document.querySelector('input[id^="quantity-"]');
console.log(getEventListeners(input));

// Should show an "input" listener with useCapture: true
```

### Test Conversion Function Directly

In browser console:
```javascript
const arabicToEnglishMap = {
  '٠': '0', '١': '1', '٢': '2', '٣': '3', '٤': '4',
  '٥': '5', '٦': '6', '٧': '7', '٨': '8', '٩': '9',
  '۰': '0', '۱': '1', '۲': '2', '۳': '3', '۴': '4',
  '۵': '5', '۶': '6', '۷': '7', '۸': '8', '۹': '9'
};
'١٢٣'.replace(/[٠-٩۰-۹]/g, function(match) {
  return arabicToEnglishMap[match];
});
// Should return '123'
```

### Check Browser Console for Errors

Open DevTools (F12) and check Console tab for JavaScript errors related to `global-utils.js`.

## What I Cannot Verify

Due to tool limitations, I **cannot verify**:
- ✗ Actual Kurdish numeral conversion in a real browser
- ✗ Whether element-level capture listeners work as expected
- ✗ Whether MutationObserver attaches listeners to dynamic inputs
- ✗ Cursor position preservation during conversion
- ✗ Cart total calculation accuracy with converted values
- ✗ Settings page calculations with Kurdish numerals
- ✗ Form submission with converted values

**You must perform these tests manually.**

## Code-Level Verification

✅ **Verified:**
- Global document-level listeners removed
- `attachConversionListener()` function creates element-level capture listeners
- DOMContentLoaded listener attaches to all existing inputs
- MutationObserver attaches to dynamically added inputs
- Duplicate prevention with `dataset.kurdishConversionAttached`
- Input types still `type="text"` with `inputmode="decimal"`
- Validation logic still in place with `isValidNumericString()`
- POS modal cancel button styling still in place

## Summary

**Fix:** Element-level capture listeners attached directly to each input
**Files changed:** `global-utils.js` only
**Lines changed:** ~100 lines (removed global listeners, added element-level logic)
**Testing required:** Manual browser testing
**Critical:** Force refresh browser to load updated files
