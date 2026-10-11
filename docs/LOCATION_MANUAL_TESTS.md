# Location Feature — Manual Test Checklist

All items below are **UNCHECKED**. Run through these before each release that touches the location feature.

Legend: `[ ]` = unchecked, `[x]` = passed, `[!]` = failed/blocked

---

## 1. GPS Flow

- [ ] **Android Chrome (HTTPS)** — tap "Use my location", browser prompts for permission, GPS coordinates appear, map centering, accuracy badge shows
- [ ] **iOS Safari (HTTPS)** — same as above; first-time permission dialog; re-open page and confirm permission is remembered
- [ ] **Desktop Chrome/Firefox/Edge (HTTPS)** — GPS permission prompt works; coordinates populated
- [ ] **Permission denied** — after denying, feature degrades gracefully: map still shown, can place marker manually, no JS errors in console
- [ ] **No GPS (desktop or airplane mode)** — `getCurrentPosition` timeout fires, fallback to manual; error message shown in Kurdish

---

## 2. Manual Map Picker

- [ ] **Click on map** — marker moves to clicked point, coordinates update, reverse geocode fires (with 600 ms debounce)
- [ ] **Drag marker** — drag end updates coordinates; reverse geocode fires
- [ ] **RTL layout** — map controls (zoom +/−) do not overlap Kurdish UI text
- [ ] **Zoom in/out** — works via buttons and scroll wheel after user interacts with map

---

## 3. Network Conditions

- [ ] **Offline / no network** — form still submittable without location; geocode suggestion shows "خزمەتگوزاری بەردەست نییە" or similar graceful error; no uncaught promise rejections
- [ ] **Slow 3G** — debounce fires, spinner shown, response arrives late; no duplicate requests
- [ ] **429 from geocode endpoint** — `Retry-After` header respected; retry message shown; subsequent request fires after delay

---

## 4. HTTP vs HTTPS

- [ ] **HTTP (non-secure context)** — GPS button is hidden or disabled; form still functional without location
- [ ] **HTTPS** — full feature available

---

## 5. In-App Browsers

- [ ] **Instagram in-app browser (Android)** — UA detection fires; warning shown; manual map available
- [ ] **Telegram in-app browser (Android/iOS)** — same as above
- [ ] **WhatsApp WebView** — same as above
- [ ] **Known-safe browser opened from in-app** (Open in Chrome / Safari) — full GPS flow works

---

## 6. Address Autofill (Geocode Suggestion Chip)

- [ ] **Chip appears** after reverse geocode returns a non-null address
- [ ] **Accept chip** — address field filled with suggestion, chip dismissed
- [ ] **Dismiss chip** — chip removed, address field unchanged
- [ ] **No suggestion (null response)** — chip does not appear; no layout shift
- [ ] **Chip with long address** — wraps correctly in RTL layout, no overflow

---

## 7. Order Submission

- [ ] **Submit with location** — POST body contains `latitude`, `longitude`, `accuracyMeters`, `locationSource`, `locationCapturedAt`, `formattedAddress` (check Network tab)
- [ ] **Submit without location** — POST body contains none of the location fields; 200 response
- [ ] **locationCapturedAt > 5 min future** — 400 validation error returned; form shows error
- [ ] **locationCapturedAt > 24 h old** — 400 validation error returned
- [ ] **lat without lng** — 400 returned (both-or-neither validation)

---

## 8. Admin Dashboard

- [ ] **Order with coordinates** — location pin icon (📍) visible in order row
- [ ] **Expand location row** — mini Leaflet map loads (lazy), marker placed, address shown, GPS/manual label, accuracy, Google Maps link
- [ ] **Collapse and re-expand** — map re-initialises; no stale instance / console error
- [ ] **Multiple rows open simultaneously** — each has its own independent map; no crossover
- [ ] **"Copy coordinates" button** — copies `lat, lng` to clipboard; button text briefly changes to confirm (Kurdish)
- [ ] **Google Maps link** — opens correct location in a new tab
- [ ] **Old order without coordinates** — no pin icon, no expandable row; no JS error
- [ ] **Pagination change while row is open** — open map is removed cleanly; no memory leak
- [ ] **formattedAddress = null** — address section hidden or shows placeholder; no "null" text rendered

---

## 9. XSS Payloads (Security)

Run these on a **test environment** only. Inject each as the relevant field value via the API directly (bypass form validation).

- [ ] `customerName = '<img src=x onerror=alert(1)>'` — displayed safely in dashboard (textContent, not rendered as HTML)
- [ ] `address = '"><script>alert(2)</script>'` — same
- [ ] `message/notes = '{{7*7}}'` — rendered literally
- [ ] `formattedAddress = '<svg onload=alert(3)>'` — rendered as text in location row
- [ ] Coordinate values with extreme precision — no layout break in dashboard

---

## 10. Accessibility

- [ ] **Map panel** — operable with keyboard (Tab to map, arrow keys to pan)
- [ ] **Location toggle button** — has visible focus ring; activatable with Enter/Space
- [ ] **Chip buttons** (accept / dismiss) — keyboard accessible
- [ ] **Reduced-motion** — no animations when `prefers-reduced-motion: reduce`

---

## Notes
- Real-device GPS tests require HTTPS and a physical device; cannot be automated.
- In-app browser detection depends on UA strings that change — re-test after major app updates.
- Kurdish string proofreading must be done by a native speaker (see strings dump in final report).
