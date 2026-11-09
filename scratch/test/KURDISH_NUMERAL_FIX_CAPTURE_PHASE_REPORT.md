# Kurdish Numeral Conversion - Capture Phase Fix

## Root Cause Identified

The previous fixes failed because of **conflicting conversion logic**:

1. `global-utils.js` had a global `input` listener on `document` (bubbling phase)
2. `dynamic-services.js`, `settings.js` had element-level `input` listeners (bubbling phase)
3. Both tried to convert Kurdish numerals, creating **race conditions and conflicts**
4. During event bubbling, element listeners fire BEFORE document listeners
5. Element listeners would convert, then document listener would convert again (or vice versa)
6. This caused inconsistent behavior and sometimes values being overwritten

## Solution Implemented

**Single source of truth:** Only the global listener in `global-utils.js` performs conversion.

**Capture phase ordering:** Changed the global listener to use the **capture phase** (`{ capture: true }`), which fires **BEFORE** element-level listeners.

### Event Phase Order

```
1. CAPTURE PHASE (down the DOM tree)
   document → html → body → ... → target element

2. TARGET PHASE
   target element

3. BUBBLING PHASE (up the DOM tree)
   target element → ... → body → html → document
```

**Before (Bubbling Phase):**
```
1. Element listener fires (reads unconverted value)
2. Document listener fires (converts value)
3. Element listener already processed unconverted value ❌
```

**After (Capture Phase):**
```
1. Document listener fires in capture phase (converts value)
2. Element listener fires (reads already-converted value) ✅
```

## Files Modified

### 1. `js/global-utils.js`

**Line 44-72:** Changed global `input` listener to use capture phase
```javascript
document.addEventListener('input', function(e) {
  // ... conversion logic ...
}, { capture: true }); // NEW: Use capture phase
```

**Line 73-96:** Changed global `paste` listener to use capture phase
```javascript
document.addEventListener('paste', function(e) {
  // ... conversion logic ...
}, { capture: true }); // NEW: Use capture phase
```

### 2. `js/dynamic-services.js`

**Lines 336-367:** Removed element-level Kurdish numeral conversion
- Now relies on global listener for conversion
- Only validates and updates cart quantity with already-converted values

### 3. `js/settings.js`

**Lines 358-369:** Removed element-level conversion from sofa total inputs
**Lines 371-382:** Removed element-level conversion from discount price inputs
**Lines 389-420:** Removed element-level conversion from discount percentage inputs

All three now rely on global listener for conversion.

## Why This Should Work

1. **Single conversion point:** Only `global-utils.js` converts Kurdish numerals
2. **Capture phase ensures ordering:** Conversion happens before any element listeners
3. **No conflicts:** Element listeners only read already-converted values
4. **Consistent behavior:** All inputs use the same conversion logic
5. **Simpler code:** No duplicated conversion logic across files

## Input Type Changes (Still in Place)

All numeric inputs still use `type="text"` with `inputmode="decimal"`:
- `pos.html`: 3 inputs
- `settings.html`: 2 inputs
- `report.html`: 2 inputs
- `dynamic-services.js`: 1 dynamically created input
- `settings.js`: 4 dynamically created inputs

This allows Kurdish numerals to be entered (bypassing browser validation).

## Validation (Still in Place)

All numeric inputs still use `isValidNumericString()` for strict validation:
- Rejects partially numeric strings like `123abc`
- Accepts pure numeric strings like `123`, `123.45`
- Works with Kurdish numerals after conversion

## Required Testing

**I cannot perform real-browser testing.** You must test the following:

### Test 1: Homepage Quantity Inputs (index.html)

1. Open `http://localhost:8080/index.html`
2. **Force refresh browser** (`Ctrl + Shift + R`) to load updated JavaScript
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

### Test 6: POS Modal Cancel Button (Regression Test)

1. Open `http://localhost:8080/pos.html`
2. Click a service card to open modal
3. Verify the "پاشگەزبوونەوە" button is **red**
4. Verify it darkens on hover
5. Click cancel and verify modal closes

### Test 7: Phone Numbers (Regression Test)

1. On any page with phone input, type Kurdish numerals:
   - Type `٠٧٥٠١٢٣٤٥٦٧` → Should convert to `07501234567`
2. Verify phone validation still works

## Important: Browser Cache

**Force refresh your browser** to load the updated JavaScript files:
- **Windows/Linux:** `Ctrl + Shift + R` or `Ctrl + F5`
- **Mac:** `Cmd + Shift + R`

Without a force refresh, the browser may use cached versions of the files, causing the old logic to run.

## What I Cannot Verify

Due to tool limitations, I **cannot verify**:
- ✗ Actual Kurdish numeral conversion in a real browser
- ✗ Cursor position preservation during conversion
- ✗ Cart total calculation accuracy with converted values
- ✗ Settings page calculations with Kurdish numerals
- ✗ Form submission with converted values
- ✗ Whether the capture phase fix resolves the race condition

**You must perform these tests manually.**

## Code-Level Verification

✅ **Verified:**
- Only `global-utils.js` performs Kurdish numeral conversion
- Global listener uses capture phase (`{ capture: true }`)
- All element-level conversions removed from `dynamic-services.js`
- All element-level conversions removed from `settings.js`
- All `type="number"` inputs changed to `type="text"` with `inputmode="decimal"`
- Validation logic still in place with `isValidNumericString()`
- POS modal cancel button styling still in place

## If This Still Doesn't Work

If the issue persists after force-refreshing, check:

1. **Browser console for errors:**
   - Open DevTools (F12)
   - Check Console tab for JavaScript errors
   - Look for errors related to `global-utils.js`

2. **Verify `global-utils.js` is loaded:**
   - In DevTools, go to Sources tab
   - Check if `global-utils.js` is in the file list
   - Verify the file contains the capture phase change

3. **Verify event listener is registered:**
   - In DevTools Console, type: `getEventListeners(document)`
   - Check if an `input` listener exists on document
   - The capture listener should be listed

4. **Test conversion directly:**
   - In DevTools Console, type:
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
     ```
   - Should return `'123'`

5. **Test if the event fires:**
   - Add a console.log to the global listener:
     ```javascript
     console.log('Input event fired on:', e.target.id, 'value:', e.target.value);
     ```
   - Type Kurdish numerals and check if the log appears

## Summary

**Fix:** Single conversion point using capture phase
**Files changed:** `global-utils.js`, `dynamic-services.js`, `settings.js`
**Lines changed:** ~50 lines total
**Testing required:** Manual browser testing
**Critical:** Force refresh browser to load updated files
