# Location Feature — Developer Reference

## What it does
An **optional** GPS / interactive-map location picker on the public order form (`index.html`).
After the customer places a marker (GPS or manual drag/click), the coordinates are sent with
the order. On the admin dashboard, orders that carry coordinates show a mini map, address
suggestion, GPS/manual label, accuracy, Google Maps link, and a coordinates copy button.

---

## Files changed / created

| Path | Purpose |
|------|---------|
| `db/migration/V14__add_location_fields.sql` | Nullable location columns on `orders` |
| `model/Order.java` | 6 new nullable fields (latitude, longitude, etc.) |
| `dto/OrderRequestDTO.java` | Corresponding optional request fields + `@ValidLocationFields` |
| `validation/LocationFieldsValidator.java` + `ValidLocationFields.java` | Both-or-neither lat/lng validation |
| `controller/GeocodeController.java` | `GET /api/v1/geocode/reverse` proxy to Nominatim |
| `config/GeocodeConfig.java` | RestTemplate, Caffeine cache (500 entries / 10 min TTL), semaphore |
| `config/CspReportOnlyFilter.java` | **New** — CSP-Report-Only header, off by default |
| `exception/GlobalExceptionHandler.java` | Validation error shaping for 400 responses |
| `service/OrderManagementService.java` | Persists location fields on web orders |
| `controller/AdminDashboardController.java` | Exposes location fields in requests response |
| `static/js/location-feature.js` | GPS, map picker, reverse-geocode autofill, chip suggestion |
| `static/css/location-feature.css` | Scoped `ol-` styles, RTL-safe, reduced-motion |
| `static/index.html` | Address input wrapper, chip elements, map panel |
| `static/dashboard.html` | Leaflet CSS link |
| `static/js/dashboard.js` | Expandable location row, lazy Leaflet, XSS-safe rendering |
| `static/js/script.js` | Merges `OrderLocation.getPayload()` into submit payload |
| `static/assets/vendor/leaflet-1.9.4/` | Self-hosted Leaflet (js, css, images) |

---

## API

### Order submission (POST /api/v1/orders)
All location fields are **optional**. Either both lat+lng are present or neither (validated).

| Field | Type | Constraints |
|-------|------|-------------|
| `latitude` | BigDecimal | -90..90 |
| `longitude` | BigDecimal | -180..180 |
| `accuracyMeters` | Integer | 0..10000, only with GPS |
| `locationSource` | String | `"gps"` or `"manual"` |
| `locationCapturedAt` | ISO-8601 offset datetime | not >5 min future, not >24 h old |
| `formattedAddress` | String | max 500 chars (informational, from geocode) |

On 400, the response is: `{ "status": 400, "error": "...", "errors": { "field": "message" } }`

### Reverse geocode (GET /api/v1/geocode/reverse)
| Param | Required | Description |
|-------|----------|-------------|
| `lat` | yes | -90..90 |
| `lng` | yes | -180..180 |

Responses: `200 { "address": "string or null" }` · `400` out-of-range · `429 Retry-After` rate limited  
Requires authentication (admin session).

---

## Configuration properties

| Property | Default | Description |
|----------|---------|-------------|
| `app.geocode.user-agent` | `""` | **Set in production** to `"AppName/1.0 (contact@email)"` per Nominatim policy |
| `app.geocode.referer` | `""` | HTTP Referer sent to Nominatim |
| `app.csp.report-only.enabled` | `false` | Adds `Content-Security-Policy-Report-Only` header. Monitoring only; does not block. |

**Reverse proxy note:** `server.forward-headers-strategy=framework` is commented out in
`application.properties`. If deployed behind a reverse proxy, enable this so Spring resolves
the correct client IP. Until then, the `OrderRateLimitInterceptor` reads `X-Forwarded-For`
directly — see the **open follow-up** below.

---

## Privacy

- **OSM tiles** are fetched directly by the browser. OpenStreetMap sees the user's IP and
  the map area being viewed. See [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/).
- **Nominatim reverse geocode** calls are proxied through the backend — the user's IP is
  hidden from Nominatim. The geocode proxy applies a 1 req/s rate limit and a 500-entry cache.
- **Coordinates are stored only on order submit**, never in localStorage/sessionStorage.

---

## OSM / Nominatim usage-policy notes

- **Tiles** (`*.tile.openstreetmap.org`): free for light usage; add an HTTP `User-Agent`
  identifying your app. Switch to a paid tile provider (MapTiler, Stadia, etc.) if sustained
  traffic exceeds ~1 000 tile requests/day per user or you go commercial.
- **Nominatim** geocode: max 1 request/second; must set a valid `User-Agent` with contact
  info; do **not** use for bulk geocoding. Move to a paid provider if order volume grows
  beyond ~500 geocodes/day.

---

## Leaflet upgrade procedure

`/assets/vendor/leaflet-1.9.4/` is served under `/assets/**` which is cached by the browser
for **365 days**. To upgrade:

1. Download the new release: `npm pack leaflet@X.Y.Z`
2. Extract to `static/assets/vendor/leaflet-X.Y.Z/` (**new folder — do not overwrite**)
3. Update the paths in `location-feature.js` (`loadLeaflet` src, `L.Icon.Default.imagePath`)
   and in both `index.html` and `dashboard.html` `<link>` tags
4. Remove the old versioned folder after verifying

---

## Known limitations

- **In-app browsers** (Instagram, Telegram, WhatsApp): UA sniffing is best-effort; some
  in-app browsers block `getCurrentPosition` silently. Users see a warning and can use
  manual map selection.
- **Keyboard dragging**: Leaflet supports keyboard navigation of the map but dragging the
  marker via keyboard is limited to what Leaflet provides out-of-the-box.
- **Scroll handling on touch**: Map scroll-wheel zoom and touch dragging are disabled until
  the user explicitly interacts with the map container to prevent page-scroll trapping.
- **Reverse geocode only fires from admin session** — the geocode endpoint requires auth;
  the location picker itself works offline (no geocode = user types address manually).

---

## Rollback

1. Revert the frontend: `git revert <B1-commit> <B2-commit> <C-commit>` (or restore from backup)
2. The `V14` migration adds **nullable** columns — old orders continue to work with no
   coordinates. You do **not** need to roll back the DB migration for normal operation.
3. If you must remove the columns: write a `V15__remove_location_fields.sql` (do not edit V14).

---

## Deployment checklist

- [ ] **HTTPS required** — GPS (`getCurrentPosition`) only works on secure contexts
- [ ] Set `app.geocode.user-agent=<AppName>/<version> (<contact-email>)` in production env
- [ ] Set `app.geocode.referer=<your-domain>` in production env
- [ ] Take a **DB backup** before Flyway runs V14 for the first time
- [ ] Decide on `server.forward-headers-strategy=framework` (see open follow-up below)
- [ ] Review OSM tile and Nominatim usage policy compliance for expected traffic
- [ ] Optional: enable `app.csp.report-only.enabled=true` and monitor browser console
      for CSP violations before any future enforcement

---

## Open follow-up: OrderRateLimitInterceptor and X-Forwarded-For

`OrderRateLimitInterceptor.java` (lines 90–103) reads `X-Forwarded-For` and `X-Real-IP`
directly from the request. This is correct **only** if the app runs behind a trusted reverse
proxy that sets these headers. Without `server.forward-headers-strategy=framework`, a
malicious client can spoof the header and bypass IP-based rate limiting.

**Action required before production:** Enable `server.forward-headers-strategy=framework`
in `application.properties` (uncomment the line already there) AND verify your reverse proxy
is configured to strip/override `X-Forwarded-For` from untrusted clients.
