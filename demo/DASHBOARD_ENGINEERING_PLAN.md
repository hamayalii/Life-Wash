# Engineering Plan — Rebuild the "Dashboard" Page (Ghasl Service)
### v2 — updated after Step 0 clarifications with the project owner

> This is a step-by-step plan for a coding agent (Antigravity IDE / Devin / Claude Code / etc.).
> **Hard rule:** Do not start a step until the previous step is fully completed and confirmed by the user.
> **If anything in any step is ambiguous, underspecified, or contradicts what you find in the actual codebase, STOP and ask the user before writing a single line of code.** No silent assumptions are allowed anywhere in this plan.

---

## Step 0 — Prerequisites — RESOLVED

Verified: `frontendd/` exists at `demo/frontendd/` with `index.html`, `css/style.css`, `js/app.js`. `index.html` is confirmed to be the dashboard page itself (the other two "pages" mentioned earlier do not have designs yet — out of scope for this task).

### Decisions locked in from Q1–Q6

| # | Decision |
|---|----------|
| Q1 | `frontendd/index.html` is the dashboard design. Proceed with this single page. |
| Q2 | Profit = `confirmedRevenue - monthlyExpenses`. `monthlyExpenses` returns `BigDecimal.ZERO` for now (placeholder, marked `TODO`) since there is no expense-tracking feature yet, but the calculation must be written as a subtraction from day one — **not** as `profit = revenue` — so that adding a real expenses source later is a one-line change, not a rewrite. |
| Q3 | "Customers" = count of `Order` rows with `workStatus = ACCEPTED` (this is currently the only real customer-acquisition channel; a POS-based channel does not exist yet and is out of scope). See **Q7 below — still open.** |
| Q4 | Growth = current week vs. previous week by default, driven by the `period` query param (see API contract) so it can be changed later without code changes. |
| Q5 | **Split "Rug" and "Carpet" into two distinct types.** Correction to the original assessment: `rugType` is a plain `String` column, not an enum — no DB schema migration is required. See Step 5.5 below for the exact split plan. |
| Q6 | Use `Order` (not `RugWashLead`) as the source for the revenue chart, the donut chart, and the requests list. |
| **Donut chart** | Show only the top 4 service types by count. Combine everything else into a 5th slice/legend entry labeled "ئەوانی تر" (Others), colored gray. This keeps the legend at 4 rows (same as the current design) instead of growing to 7-8 rows, so it still fits the no-scroll layout without needing a denser legend redesign. |
| **Table columns** | "Product Name" → "Item Type", "Amount" → "Customer Name" (plain text, not currency-formatted). Other columns remain as-is. |

### Still-open questions — MUST be answered before Step 7 (backend implementation)

| # | Question | Default if no answer given |
|---|----------|------------------------------|
| Q7 | If the same customer (same phone number) submits and gets ACCEPTED more than once, should the "customers" card count them once or every time? | Default: count every ACCEPTED order as one customer (no dedup), for simplicity and consistency with existing `ReportService` counting logic. Ask before finalizing Step 7's customer-count query. |
| Q8 | Historical `Order`/`RugWashLead` rows already have `rugType = "persian"` and cannot be retroactively classified as Rug vs. Carpet (that distinction was never captured at submission time). Should these legacy rows be shown as a third, separate bucket ("Rug/Carpet — legacy"), or handled some other way? | Default: keep `"persian"` as a distinct legacy label in all breakdowns (dashboard donut chart, `ReportService` label mapping, Telegram reports) alongside the new `"rug"` / `"carpet"` values going forward. |

---

## Codebase Context (for the agent's awareness — already verified in the current repo)

- Backend: Spring Boot, under `demo/demo/src/main/java/com/ghasl_service/demo/`
- Entities: `Order` (id, customerName, phoneNumber, **rugType — plain String, not an enum**, price, createdAt, address, message, quantity, workStatus[PENDING/ACCEPTED/REJECTED]), `RugWashLead`
- Services: `OrderService`, `PricingService`, `LeadOrderCoordinationService`, `ReportService` (contains ALL revenue/comparison/label logic already — **this exact logic must be reused for the dashboard, not reimplemented**, so that web dashboard numbers, the dashboard donut chart labels, and Telegram bot report numbers always match)
- Security: `SecurityConfig` restricts `/api/v1/admin/**`, `/reports`, `/pos` to `ROLE_ADMIN`. **Note (unrelated pre-existing issue, FYI only, not in scope):** `/pos` and `/reports` currently have no backing `@Controller` or static file — they are dead links in the existing admin dropdown menu today.
- **The admin hamburger menu already exists** in `static/index.html`: `<div id="adminMenu">` → `<div class="admin-dropdown">` containing `<a href="/pos">` and `<a href="/reports">`. **Do not rebuild this menu — only add one new link inside it.**
- Current customer-facing frontend: `static/index.html, script.js, styles.css`. **This file must not be restructured or have its existing behavior changed** — the only edit permitted to it in this whole plan is: (a) one new `<a>` link in the existing admin dropdown, and (b) splitting the single `rugType="persian"` `<option>` into two options per Q5/Step 5.5.
- **There is currently no dashboard REST endpoint at all** — it must be built from scratch in Step 7.
- Pricing table for reference (from `PricingService`): persian/rug/carpet = 1250/m, shag (curtain) = 1500/m, silk (blanket) = 5000/piece, synthetic (rooftop tank) = 25000/piece, wool (sofa) = price confirmed by admin by phone, antique (home/shop/garden cleaning) = no fixed price, inquiry only.

---

## Step 1 — Analyze the `frontendd` design files (read-only, no code yet)

1. Read `frontendd/index.html`, `frontendd/css/style.css`, `frontendd/js/app.js`.
2. Document precisely (without changing anything yet):
   - HTML structure of each of the 4 cards (class/id names, what data is currently hardcoded in them)
   - Structure of the line/area chart (which library? Chart.js? Hand-rolled SVG? Where exactly is the red color defined — CSS variable or inline hex?)
   - Structure of the donut chart (library, current data)
   - Structure of the list (current column names)
   - Container sizes and viewport units (px vs %)
3. **Stop and report a short summary** before moving to Step 2 — confirm the analysis is correct and nothing was missed.

---

## Step 2 — Build the new "Dashboard" page as a pixel-faithful copy (no data logic yet)

1. Create new files at the project's static root (same level as `index.html`, NOT nested): `static/dashboard.html`, `static/dashboard.css`, `static/dashboard.js`. **These are entirely separate from `index.html`/`script.js`/`styles.css` — the existing customer-facing page must never be touched by this step.**
2. Port the HTML/CSS/JS from `frontendd/` into these new files **with zero changes to structure, colors, fonts, or spacing.**
3. Leave the current data in the file as-is (whether hardcoded or mock) — data changes come in Steps 6–8.
4. Test in-browser: does it match the Figma design 100%?
5. **Stop.** Present screenshots/details for confirmation before moving to Step 3.

---

## Step 2.5 — Wire the dashboard into the existing admin menu (small, isolated change)

1. In `static/index.html`, inside the existing `<div class="admin-dropdown">` block (which currently contains the `/pos` and `/reports` links), add exactly one new line:
   ```html
   <a href="/dashboard.html">داشبۆرد</a>
   ```
   Do not reorder, restyle, or remove the existing two links.
2. In `SecurityConfig`, add `/dashboard.html` to the same `.requestMatchers(...).hasRole("ADMIN")` pattern currently used for `/pos` and `/reports`, so the page is protected the same way.
3. **Stop and confirm** this is the only change made to `index.html` and `SecurityConfig` before moving to Step 3.

---

## Step 3 — Enforce "no scroll" layout for desktop

**Engineering basis:** for all 4 pieces (4 cards + line chart + list + donut chart) to fit in a single viewport with no scrolling, the following is required:

1. Set the main container to `height: 100vh` (or `100dvh` for better accuracy) and `overflow: hidden`.
2. Convert the layout to **CSS Grid** with tight rows (e.g., row 1 = 4 cards at a fixed height, row 2 = chart + donut + list using `grid-template-rows: auto 1fr` so the larger sections automatically fill remaining space).
3. Each section should have its own internal `overflow-y: auto` (e.g., if the list grows longer than its allotted space, it scrolls internally — the page itself never scrolls).
4. Test on standard screen sizes (1920×1080 and 1366×768) — **desktop only**, as explicitly requested (no mobile responsiveness required for this task).
5. **Stop and confirm** before moving to Step 4.

---

## Step 4 — Chart color: red → blue

1. Locate exactly where the red color is defined in the revenue chart's code (CSS variable, inline hex, or a JS charting-library config option).
2. Change only that color definition to blue (suggestion: reuse the same blue tone already present elsewhere in the design for consistency — ask if no blue hex code exists in the design to match against).
3. **Do not change anything else about the chart** — not its size, not its chart type (line/bar/area), not the data it displays, in this step.
4. **Stop and confirm.**

---

## Step 5 — Currency: USD → IQD

1. Locate where `$` or `USD` appears in the revenue chart's code (label, tooltip, axis, or a formatting function).
2. Change it to an IQD format — suggestion: number + space + "IQD" or "دینار" (match the exact style already used in `ReportService`: `revenue.longValue() + " دینار"`).
3. **Important:** the current numbers are still in USD (likely scaled to look reasonable). Once real data is wired in Step 8, the numbers will correct themselves automatically — this step is **only about the display format**, not the underlying number logic.
4. **Stop and confirm.**

---

## Step 5.5 — Split "Rug" and "Carpet" into distinct service types (per Q5)

This touches four places, all of which must be updated together and consistently:

1. **`static/index.html`** — split the single option:
   ```html
   <option value="persian">فەرش / کومبار (مەتری بە ١٢٥٠ دینار)</option>
   ```
   into two:
   ```html
   <option value="rug">فەرش (مەتری بە ١٢٥٠ دینار)</option>
   <option value="carpet">کومبار (مەتری بە ١٢٥٠ دینار)</option>
   ```
2. **`PricingService`** — add both `"rug"` and `"carpet"` to `UNIT_PRICES` (1250 each) and to `DECIMAL_QTY_TYPES`. Leave `"persian"` handling in place for backward compatibility with historical rows (see Q8) — do not delete it.
3. **`ReportService.rugTypeLabel(...)`** — split the `"persian"` case into three: `"rug"` → "فەرش", `"carpet"` → "کومبار", and keep `"persian"` → "فەرش/کومبار (پێشتر)" for legacy rows (per the Q8 default, unless the user specifies otherwise).
4. **Telegram bot** — no direct code change needed; it already calls `ReportService`'s label function, so the split propagates automatically. Verify this with a real report generation after the change.
5. **Stop and confirm** all four points are updated consistently before moving to Step 6.

---

## Step 6 — Design the API contract (before writing any backend code)

Before writing any Controller/Service code, write out this contract and present it for confirmation:

```
GET /api/v1/admin/dashboard/summary?period={today|week|month}
→ {
    "customers": <long>,          // count of Order rows with workStatus=ACCEPTED — see Q7
    "orders": <long>,
    "growthPercent": <double>,     // comparison vs previous period, same period length
    "profit": <BigDecimal>,        // confirmedRevenue - monthlyExpenses(currently 0, TODO)
    "currency": "IQD"
  }

GET /api/v1/admin/dashboard/revenue-trend?period={...}
→ [ { "label": "<day/week/month>", "amountIQD": <BigDecimal> }, ... ]

GET /api/v1/admin/dashboard/top-services?period={...}
→ [ { "rugType": "rug", "label": "فەرش", "count": <long> },
    { "rugType": "carpet", "label": "کومبار", "count": <long> },
    { "rugType": "persian", "label": "فەرش/کومبار (پێشتر)", "count": <long> },  // legacy bucket, see Q8
    ... ]
  // sorted by count descending — backend returns all, frontend shows top 4 + "ئەوانی تر" (Others) combined

GET /api/v1/admin/dashboard/requests?limit=20
→ [ {
      "customerName": "...",
      "requestedAt": "yyyy-MM-dd",
      "serviceType": "فەرش" | "کومبار" | "بەتانی" | ... ,   // via ReportService.rugTypeLabel
      "quantity": <double>,
      "priceIQD": <BigDecimal | null>            // null = pending admin pricing (wool)
    }, ... ]
```

**Hard rule:** ALL revenue/growth-calculation/REJECTED-filtering/label logic must **reuse the exact logic already in `ReportService` and `OrderRepository`** — do not write a second, separate implementation of this logic. This is required so that dashboard numbers and Telegram bot report numbers are **always identical**.

**Stop and present the contract for confirmation**, and get final answers to Q7 and Q8, before Step 7.

---

## Step 7 — Implement the backend (Controller + repository queries + DTOs)

1. Create `AdminDashboardController`, following the same style as `LeadCaptureController`.
2. Add new queries to `OrderRepository` for:
   - Customer count: `COUNT(o) WHERE workStatus = ACCEPTED` (adjust per final Q7 answer)
   - Service-type breakdown grouped by `rugType`, for the donut chart
   - Latest requests list, ordered by `createdAt DESC`, limited to N rows
3. Add a placeholder `monthlyExpenses()` method (returns `BigDecimal.ZERO`, marked `TODO`) so `profit` is computed as a subtraction from day one.
4. Confirm `SecurityConfig` restricts `/api/v1/admin/dashboard/**` to `ROLE_ADMIN`.
5. Write unit tests for each new endpoint (following the style of existing tests in `src/test/java`), including a test asserting the rug/carpet/persian label split works correctly.
6. **Stop and present test results** before moving to Step 8.

---

## Step 8 — Wire the frontend to live data

1. In `dashboard.js`, remove all mock/hardcoded data.
2. Write `fetch()` calls for each of the 4 endpoints, including the JWT cookie (same auth mechanism already in use).
3. Add loading and error states for each section.
4. Add an auto-refresh option (e.g. every 60 seconds) — ask whether this is wanted or not.
5. **Stop.**

---

## Step 9 — Verify consistency between the web dashboard and the Telegram bot

1. On the same day, compare: the revenue number shown on the web dashboard must be **exactly identical** to the number in the Telegram bot's `/reports` output for the same period.
2. Compare rug/carpet breakdown counts between the dashboard donut chart and a manually triggered Telegram report — they must match.
3. If there's a discrepancy, the logic must be unified (e.g. one side isn't filtering REJECTED orders, or is using a different date range).
4. Final check: both sides (web + bot) must pull from the same service layer, not parallel/duplicated logic.

---

## Final Step — Overall Review

- Visual diff against the Figma design — nothing in the original design should have changed except the explicitly specified items (card data, chart color, currency, donut/list content).
- No-scroll check on 2 different screen sizes.
- Confirm `index.html`'s existing behavior is completely unchanged apart from the one new menu link and the rug/carpet option split.
- Data-consistency check between web and Telegram (revenue, counts, and rug/carpet/legacy labels).
