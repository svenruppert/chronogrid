# ChronoGrid — Feature Overview

Snapshot of what's actually in the codebase right now (post-rebrand,
post-Backlog-#1). For deferred / planned items see
[`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md); for the architecture
journey see [`CHRONOGRID_EXTRACTION.md`](CHRONOGRID_EXTRACTION.md).

Reactor at a glance:

```
chronogrid-parent                  (pom — reactor root)
├── chronogrid-core                (jar — headless CalDAV stack)
├── chronogrid                     (jar — Vaadin Flow component)
└── chronogrid-demo                (war — consumer + IT harness)
```

---

## 1. CalDAV wire (chronogrid-core)

### Verbs

| Verb | Method | Behaviour |
|---|---|---|
| **REPORT** `calendar-query` | `findInRange(from, to)` / `findTodosInRange(from, to)` | Time-range filtered VEVENT / VTODO fetch; returns 207 multistatus parsed via XXE-hardened JAXP |
| **PUT** new | `putNew(uri, iCalBody)` | `If-None-Match: *` guard against collision; returns the server-assigned ETag |
| **PUT** update | `putUpdate(uri, iCalBody, expectedEtag)` | `If-Match: <etag>` for optimistic concurrency; 412 → `ConcurrentModificationException` |
| **DELETE** | `delete(uri, expectedEtag)` | Optional `If-Match`; 404 silently succeeds (idempotent); 412 → `ConcurrentModificationException` |
| **GET** | `get(uid)` | Resolves UID → URI under the collection; returns `(uri, etag, iCalBody)` |
| **PROPFIND** | `CalDavDiscovery.discover(startUri, user, pass)` | Three-step chain: `<DAV:current-user-principal>` → `<C:calendar-home-set>` → enumerate calendars |

### Auth + safety

- **HTTP Basic** wired via `CalDavClient` constructor (`(URI, username, password)`); Base64-encoded `Authorization` header on every request
- **No-auth path** preserved for the local testbench (`CalDavClient(URI)` constructor)
- **User-Agent**: `flow-template/00.10.00 (+https://8g8.eu/caldav)` on every request
- **XXE-hardened JAXP** for parsing `<multistatus>` responses (no external entities, no DTDs, secure processing on)
- **Per-call structured logging** via `HasLogger`: REPORT/PUT/DELETE/GET outcomes + ETag transitions

### Error classification

`CalDavError` is a sealed-style record with a `Kind` enum:

| Kind | Trigger | Example UI mapping |
|---|---|---|
| `UNAUTHORIZED` | HTTP 401 / 403 | „App-specific password required" (iCloud) |
| `NOT_FOUND` | HTTP 404, `NoSuchElementException` | „The calendar at this URL is empty or gone" |
| `CONFLICT` | HTTP 412, `ConcurrentModificationException` | „Reloading — someone else changed this event" |
| `TIMEOUT` | `HttpTimeoutException` (nested) | „Server didn't respond in time" |
| `NETWORK` | `ConnectException`, `UnknownHostException`, generic I/O | „Can't reach the server" |
| `SERVER` | HTTP 5xx | „Server hiccup" |
| `MALFORMED` | XML parse failure | „Unexpected response shape" |
| `GENERIC` | Anything else | Catch-all fallback |

---

## 2. iCalendar mapping (chronogrid-core)

### Supported VEVENT properties

| iCal property | Entry slot | Notes |
|---|---|---|
| `UID` | `entry.id` | RFC 5545 unique identifier; UUID fallback when missing |
| `SUMMARY` | `entry.title` | |
| `DESCRIPTION` | `entry.description` | |
| `DTSTART` / `DTEND` | `entry.start` / `entry.end` | Date-only → `allDay = true`; date-time → uses TZID + display zone |
| `DTSTART;TZID=…` / `VTIMEZONE` | `entry.customProperty(CUSTOM_TZID)` | Source zone preserved round-trip; `VTIMEZONE` re-emitted on write |
| `LOCATION` | `entry.customProperty(CUSTOM_LOCATION)` | |
| `URL` | `entry.customProperty(CUSTOM_URL)` | |
| `COLOR` (RFC 7986) | `entry.customProperty(CUSTOM_ENTRY_COLOR)` | Drives the per-entry fill colour in the renderer |

### Supported VTODO properties

| iCal property | Entry slot | Notes |
|---|---|---|
| `UID` | `entry.id` | |
| `SUMMARY` | `entry.title` | |
| `DESCRIPTION` | `entry.description` | |
| `DTSTART` / `DUE` | `entry.start` / `entry.end` | `DUE` maps to `entry.end` so the same grid can render todos |
| `STATUS` | `entry.customProperty(CUSTOM_TODO_STATUS)` | Available for UI status badges |
| Component kind tag | `entry.customProperty(CUSTOM_KIND)` = `vtodo` | Lets the renderer distinguish from VEVENT |

### Per-event provenance colour pair

`CalendarService.applyColours(entry, calendarColor)` pairs the entry's
own colour with its source calendar's colour:

| Has `CUSTOM_ENTRY_COLOR`? | Fill (`backgroundColor`) | Border (`borderColor`) |
|---|---|---|
| No | calendar colour | calendar colour (uniform) |
| Yes | the user's chosen colour | calendar colour (provenance) |

`CUSTOM_CALENDAR_COLOR` is always stamped on every entry after fan-out
so downstream CSS hooks can read it independently of FullCalendar's
slots.

---

## 3. Service layer (chronogrid-core)

### `CalendarService` façade

- Split **read / write methods** as a permission seam: `findInRange` /
  `findById` (read) vs. `save` / `delete` (write). A future
  `@RequiresPermission("calendar:write")` slots in without touching
  the read path.
- **`Result<T, CalDavError>`-bearing variants** on every method
  (`findInRangeAsResult`, `saveAsResult`, etc.) for callers that
  prefer chained `peek` / `peekFailure` over `try/catch`.
- **Multi-server fan-out**: `fromConnections(servers, subscriptions,
  displayZone)` pairs each subscription with its owning server's
  credentials; `findInRange` aggregates across all configured CalDAV
  clients.
- **ETag-aware writes**: `putUpdate` carries the expected ETag;
  conflicts surface as `ConcurrentModificationException` → `CONFLICT`
  in the Result API.
- **Cross-calendar drag scope**: a `save(Entry)` writes back to the
  entry's existing `caldavHref` (so dragging a Work event stays in
  Work). A `save(Entry, targetCollection)` overload routes new
  entries to a user-chosen collection.

### Records exposed as data carriers

| Record | Fields |
|---|---|
| `CalDavConnectionConfig` | `URI collectionUri, String username, String password, List<URI> additionalCollections` |
| `CalDavServerConnection` | `String id, String displayName, URI baseUri, String username, String password` |
| `CalendarSubscription` | `URI uri, String displayName, String color, boolean visible, String serverId` |
| `CalDavProviderPreset` | `String id, String label, String entryUri, String hint, VaadinIcon icon` (catalogue entry) |
| `DiscoveredCalendar` | `URI href, String displayName, String color` (PROPFIND result) |
| `RemoteEvent` | `URI href, String etag, String iCalBody` (raw wire DTO) |

### Provider quick-connect catalogue

`CalDavProviderPreset.DEFAULTS` ships **Apple iCloud** out of the box.
The list is the single extension point for future providers
(Nextcloud, Radicale, Baïkal, mailbox.org…); a consuming host can
inject its own list via the component's config.

### Per-user state seam

`CalendarStateStore` interface (UI-agnostic):

```java
Optional<CalDavConnectionConfig> readConnection();
List<CalDavServerConnection>     readServers();
List<CalendarSubscription>       readSubscriptions();
int                              readNDays(int fallback);
// + matching write* methods
```

Default impl `VaadinSessionCalendarStateStore` (shipped in
`chronogrid` — Vaadin module) is session-scoped. A DB- or
file-backed impl is a trivial follow-up for consumers who want
cross-session persistence.

### i18n seam

`CalendarMessages` — single-method functional interface:

```java
String tr(String key, String fallback, Object... args);
```

`CalendarMessages.fallbackOnly()` ships as the no-i18n default;
consumers can wire `this::tr` from their host's i18n stack.

---

## 4. Vaadin component (chronogrid)

### `ChronoGrid` composite

The single user-facing entry point. Wraps a FullCalendar2 grid, four
sub-component slots, and four dialogs.

**Constructors** (drop-in usage):

```java
new ChronoGrid()                                   // session store + fallback i18n
new ChronoGrid(CalendarStateStore)                 // own store, fallback i18n
new ChronoGrid(CalendarStateStore, CalendarMessages)
new ChronoGrid(CalendarStateStore, CalendarMessages, CalendarService) // full control
```

### Sub-components

| Component | What it does |
|---|---|
| `CalendarNavigationBar` | 5-button nav group (page back / slide back / today / slide forward / page forward) + DatePicker + segmented Day/Week/N-days/Month switcher + N-days slider (1–21, slider-only, persisted across view switches) |
| `ConnectionStatusBadge` | Coloured Connected / Disconnected / Unknown pill; 15-second background poll; reconnect callback fires when state flips back to Connected |
| `ServerStatusList` | Per-server pill row: dot (green/red/grey) + logical name + URL tooltip; live updates after every probe |

### Dialogs

| Dialog | What it does |
|---|---|
| **Settings** | URL + Basic-Auth credentials, Apple iCloud quick-connect preset, *Test connection* + *Discover calendars* (PROPFIND-driven picker), multi-select subscription |
| **Subscriptions** | Per-row visibility toggle, HTML5 colour picker (overrides palette), Disconnect button |
| **Connections** | Server-level overview: name, base URI, auth scheme, live status pill (probed on open) |
| **Event editor** | Title / Description / Location / URL / Start / End / **per-entry colour picker** with checkbox + hint / Calendar select (with Server filter when ≥ 2 servers) / Save / Delete (with confirm) / Cancel |

### Per-event colour picker (Backlog #1, shipped 2026-06-20)

- Checkbox „Use custom colour" + HTML5 `<input type="color">` in the
  editor
- Entry-level fill = chosen colour; border = source-calendar colour
  (Google-Calendar-style)
- Persisted as the RFC 7986 VEVENT `COLOR` property; survives
  round-trips through iCloud / Nextcloud / Radicale where supported
- Reset = clear the checkbox → entry falls back to uniform calendar
  colour

### Polished navigation strip

- Replaces FullCalendar's built-in toolbar entirely (`headerToolbar:
  false`)
- One single „Today" affordance (DatePicker's built-in *Today* is
  suppressed via `DatePickerI18n.setToday("")`)
- N-days view 1–21 days with **rolling motion** (Slide ± shifts by one
  day in any view), persisted across navigations

### Live connection feedback

- Coloured Connected / Disconnected / Unknown pill in the toolbar
- 15-second background poll via Vaadin Flow's `addPollListener`
- Per-server pills update simultaneously with the toolbar badge
- On reconnect: a Vaadin Notification fires and the EntryProvider is
  refreshed automatically

### Theming

- Bundled CSS at `META-INF/resources/frontend/styles/chronogrid.css`
  per the Vaadin add-on convention
- BEM-ish class names: `.chronogrid__frame`, `.chronogrid-nav__group`,
  `.server-status-pill__dot--connected`, …
- Uses Lumo custom properties throughout (`--lumo-primary-color`,
  `--lumo-contrast-5pct`, …) — fully themable without a Lumo fork
- Dynamic per-row data bridges via CSS custom properties
  (`--swatch-color`) or state-modifier classes; no inline `style="…"`
  attributes

---

## 5. Demo (chronogrid-demo)

The consuming application; a Vaadin Flow war with jSentinel-secured
end-to-end.

### Host wiring

- `CalendarRouteView` is the route-bearer — carries `@Route`,
  `@VisibleFor(USER)`, `MainLayout` reference; embeds a `ChronoGrid`
  via the three injection seams (`Store`, `Messages`, optional
  `Service`)
- EN + DE translation bundles in `vaadin-i18n/translations*.properties`
- jSentinel-backed login + role-gated drawer (`calendar:read`
  permission gates the Calendar drawer entry)

### Dev launchers

| Script | What it does |
|---|---|
| `start-caldav-dev-server.sh` | Boots `caldav-testbench` on port 5232 with a seeded `personal` collection; parks until Ctrl+C |
| `start-vaadin-demo.sh` | Boots the Vaadin demo on port 8080 with `app.caldav.baseUri` pre-wired to the testbench |

### Provider walkthrough (`README.md`)

Four configurable backend paths documented:

1. **Option A** — bundled `caldav-testbench` (no Docker, no auth)
2. **Option B** — Apple iCloud (app-specific password, real provider)
3. **Option C** — Radicale via Docker
4. **Option D** — any other CalDAV server via `-Dapp.caldav.baseUri=…`

---

## 6. Infrastructure features

### Quality gates

- **SpotBugs**: standing rule — runs after every non-trivial change,
  0 findings on every module
- **Checkstyle**: 0 violations on the two add-on modules
  (chronogrid-core, chronogrid); inherited config
- **PIT mutation gate**: per-package coverage floors enforced in
  `tools/pit-baselines.txt`
- **289 tests** across the reactor (71 core / 5 component / 213 demo)

### Build + publish setup

- **Reactor build**: `./mvnw clean install` from the root produces
  the publishable triplet (main + sources + javadoc jars) in each
  add-on module's `target/`
- **Maven Central readiness**: full `<scm>` / `<developers>` /
  `<licenses>` / `<url>` metadata on the two add-on modules; GPG
  signing opt-in via the parent's `_release_sign-artifacts` profile;
  `distributionManagement` inherited from `com.svenruppert:dependencies`
- **Release ceremony**: first tagged release `v00.10.00` (pre-rebrand);
  current `01.00.00-SNAPSHOT` on `main` — next tag will be the first
  ChronoGrid-branded release

---

## 7. What's NOT yet in the box

See [`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md) for the designed-but-
deferred items. Architecturally identified but not yet planned:

| Item | Notes |
|---|---|
| **Bearer / OAuth2 token flow** | Only HTTP Basic wired; Google CalDAV / OIDC-fronted servers need a separate header-injection path |
| **MKCALENDAR** | Collections must exist server-side; the client doesn't create them |
| **RRULE client-side expansion** | Recurring events are pushed as a single VEVENT with RRULE; expansion is delegated to the server |
| **Cross-calendar drag** | Dragging an event from Work → Family is currently out of scope; events stay in their existing calendar |
| **Truly Vaadin-free chronogrid-core** | Today `EntryMapper` references `org.vaadin.stefan.fullcalendar.Entry`, pulling Vaadin transitively. A DTO-layer split is „Phase 4" material |
| **CalendarMessages.defaultBundle(Locale)** | An EN+DE i18n bundle as an opt-in factory on the add-on, sparing consumers from setting up i18n for the demo phrasings |
| **Maven Central deploy ceremony** | Pom is wired; Sonatype credentials + GPG passphrase remain Sven-interactive |

---

## 8. Cross-references

- [`README.md`](../README.md) — top-level project walkthrough,
  consumer-side integration snippet, provider options
- [`chronogrid-core/README.md`](../chronogrid-core/README.md) —
  headless-layer consumer docs
- [`chronogrid/README.md`](../chronogrid/README.md) — Vaadin-layer
  consumer docs (mount-wrapper recipe, customisation seams)
- [`CHRONOGRID_EXTRACTION.md`](CHRONOGRID_EXTRACTION.md) — design
  rationale + journey notes
- [`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md) — designed-but-deferred
  ideas (e.g. #1 = the per-entry colour shipped 2026-06-20)
- [`BLOGPOST_CONCEPT.md`](BLOGPOST_CONCEPT.md) — companion blog-post
  planning artefact for the extraction story
