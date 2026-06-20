# ChronoGrid

**A native Vaadin Flow calendar component for Java applications.**

ChronoGrid is a drop-in Vaadin Flow Composite that renders a CalDAV-
backed calendar with multi-server / multi-subscription support,
per-subscription colour picker, rolling N-days view, in-app discovery,
and the standard PUT / GET / DELETE / REPORT / PROPFIND wire stack
under the hood. The headless CalDAV layer is published as a separate
artefact so headless consumers (CLI tools, sync jobs, Spring Boot
services) can pull it without dragging the Vaadin runtime along.

The repo ships as a Maven reactor with three modules:

| Module | Coordinates |
|---|---|
| **`chronogrid-core`** | `com.svenruppert.chronogrid:chronogrid-core` — headless CalDAV wire + iCalendar mapping + service façade |
| **`chronogrid`** | `com.svenruppert.chronogrid:chronogrid` — Vaadin Flow add-on (the `ChronoGrid` composite + sub-components + bundled CSS) |
| **`chronogrid-demo`** | `com.svenruppert:chronogrid-demo` — the consuming application; doubles as the end-to-end integration harness |

The demo is wired against [`com.svenruppert:caldav-testbench`](https://8g8.eu/caldav)
for local development — an in-memory CalDAV server you can boot in
one terminal and point the Vaadin app at in another. Same wire
protocol as Nextcloud / Radicale / Baïkal; no auth, no persistence,
ideal as a deterministic prüfstand.

The "real provider" the blog post connects to is **Apple iCloud** —
it speaks plain CalDAV with HTTP Basic auth and an app-specific
password, so the same code that drives the testbench reaches iCloud
without modification. See *Running with a CalDAV backend — Option B*
below for the walkthrough.

## What the demo shows

| Slice | What it does | Where it lives |
|---|---|---|
| **CalDAV client** | JDK `HttpClient` for `PUT/GET/DELETE/REPORT`, XXE-hardened JAXP for `<multistatus>` parsing, 412 → `ConcurrentModificationException` | `chronogrid-core: client/CalDavClient.java` |
| **iCalendar mapping** | Biweekly-backed `VEVENT ↔ FullCalendar Entry` roundtrip, ETag and href stashed as custom properties | `chronogrid-core: mapping/EntryMapper.java` |
| **Service façade** | Split read/write methods (the jSentinel permission seam), conflict detection on `save` | `chronogrid-core: service/CalendarService.java` |
| **FullCalendar view** | Month view, `EntryProvider.fromCallbacks` for range queries, edit-dialog + drag/drop/resize listeners, conflict notification, toolbar with Settings / Refresh / New event buttons | `chronogrid: ui/ChronoGrid.java` |
| **Runtime CalDAV settings** | In-app Settings dialog: switch the collection URI + HTTP Basic credentials at runtime, persisted on `VaadinSession`. Test-connection button probes the server before saving. | `chronogrid: ui/ChronoGrid.java`, `chronogrid-core: service/CalDavConnectionConfig.java` |
| **Calendar discovery** | Three-step `PROPFIND` chain (current-user-principal → calendar-home-set → list) behind the *Discover calendars* button in Settings — pick from the dropdown, the URI field is filled in for you. | `chronogrid-core: client/CalDavDiscovery.java`, `chronogrid-core: client/DiscoveredCalendar.java` |
| **Provider quick-connect** | One-click presets in the Settings dialog. Currently ships Apple iCloud (entry URL + 2FA / app-password warning); the catalogue is a single-list extension point for adding Nextcloud, Radicale, Baïkal, … later. | `chronogrid-core: service/CalDavProviderPreset.java` |
| **Subscription management** | Toolbar Subscriptions button → Grid with per-row Visible toggle (hide without disconnect) and ✕ Disconnect (drop entirely). Subscriptions live on `VaadinSession` and are colour-stamped from the server's `<C:calendar-color>` (with a palette fallback). | `chronogrid-core: service/CalendarSubscription.java`, `chronogrid: ui/SubscriptionsDialog.java` |
| **Multi-server connections** | Each subscription remembers its owning server (id + creds). Settings → Save adds a server; a second Save with a different URI adds a second server. Service routes each subscription's wire calls through the matching server's credentials. | `chronogrid-core: service/CalDavServerConnection.java`, `CalendarService.fromConnections(...)` |
| **Per-event target calendar** | The New-event dialog carries a *Calendar* dropdown populated from the visible subscriptions; the new event's `PUT` lands in the chosen URI with that server's creds. | `chronogrid: ui/EventEditorDialog.java`, `CalendarService.save(Entry, URI)` |
| **All-connections status board** | Toolbar Connections button → Grid with one row per configured server (name, base URI, auth, live status pill). Status is probed on open via `REPORT` against that server's first subscription. | `chronogrid: ui/ConnectionsDialog.java` |
| **Editor server filter + rich calendar items** | The New-event dialog grows a *Server* filter above *Calendar* when more than one server is connected. Each calendar option renders as colour swatch + name + server name. | `chronogrid: ui/EventEditorDialog.java` |
| **Polished navigation strip** | Custom Lumo-themed bar above the grid: prev/today/next icon group + DatePicker + large interval label + segmented Day/Week/N-days/Month switcher + N-days spinner (1–21, persisted across view switches). Replaces FullCalendar's built-in toolbar. | `chronogrid: ui/CalendarNavigationBar.java` |
| **Framed ChronoGrid** | The whole view sits inside a card-style Lumo frame (rounded border + soft shadow + space against the surrounding AppLayout). FullCalendar's built-in toolbar is fully disabled via `headerToolbar: false` so the only navigation is the one we built. | `chronogrid: ui/ChronoGrid.java` |
| **Per-subscription colour picker** | The Subscriptions dialog's first column is an interactive HTML5 `<input type="color">` per row. Choosing a colour persists on the subscription record and is applied to every entry from that source on the next render — overrides the deterministic palette `CalendarService` ships. | `chronogrid: ui/SubscriptionsDialog.java`, `CalendarSubscription.withColor` |
| **Per-server status pills** | Toolbar shows one compact pill per configured server: coloured dot (green/red/grey) + logical name + URL tooltip. No raw URLs cluttering the bar; probe runs on attach and every 15 s alongside the connection badge. | `chronogrid: ui/ServerStatusList.java` |
| **Zebra calendar grid** | Subtle alternating tint on day columns + hour lanes in Day/Week/N-days; alternating cells in Month. CSS-only via `@CssImport`, scoped to FullCalendar's class names. | `chronogrid: META-INF/resources/frontend/styles/chronogrid.css` |
| **Live connection badge** | Coloured Connected / Disconnected / Unknown pill in the toolbar, updated passively after every CalDAV call and actively via a 15 s background poll. On reconnect: notification + automatic refresh. | `chronogrid: ui/ChronoGrid.java` (`ConnectionState`) |
| **Dev launcher (CalDAV side)** | Boots `caldav-testbench` on port `5232`, prints discovery URL, parks on Ctrl+C | `start-caldav-dev-server.sh` → `chronogrid-core: CalDavDevServer.java (test)` |
| **Dev launcher (Vaadin side)** | Embedded nano-vaadin-jetty `main` that pre-wires `app.caldav.baseUri` to the testbench so the app is connected on first start | `start-vaadin-demo.sh` → `chronogrid-demo: CalDavDemo.java` |
| **Tests** | `EntryMapperTest` + `CalDavClientIT` + `CalendarServiceIT` + `ChronoGridBrowserlessTest` — all against the live testbench | `chronogrid-core/src/test/.../calendar/` |

## Quick start

The project is a Maven reactor; the runnable application lives in
the `chronogrid-demo/` sub-module.

```bash
./mvnw -pl chronogrid-demo                     # default goal = jetty:run on :8080
# or equivalently:
( cd chronogrid-demo && ../mvnw )
```

The first start prints a bootstrap token to stdout and writes it to
`./data/jsentinel/bootstrap.token`. Open `http://localhost:8080/login`
— you'll be redirected to `/setup` until you've used the token to
create the first admin. The `Calendar` drawer entry appears once the
user has the `calendar:read` permission (default: both `USER` and
`ADMIN`).

The view loads on its own, but every range query needs a CalDAV
collection. Give it one with `-Dapp.caldav.baseUri=…` — the next
section covers the three ways to point it at something useful.

## Using the calendar component in your own Vaadin app

`chronogrid` is published as a drop-in Vaadin Flow add-on.
The demo in `chronogrid-demo/` is one consumer; this section is for the *other*
consumers — any Vaadin app that wants to mount a CalDAV-backed
calendar with the host's own routing, security and i18n stack.

### 1. Pull the dependency

```xml
<dependency>
    <groupId>com.svenruppert.chronogrid</groupId>
    <artifactId>chronogrid</artifactId>
    <version>00.10.00</version>
</dependency>
```

The component module transitively brings `chronogrid-core` (headless
wire + service), `org.vaadin.stefan:fullcalendar2`, `net.sf.biweekly`,
and `com.svenruppert:functional-reactive`. The bundled CSS lives in
`META-INF/resources/frontend/styles/chronogrid.css` — Vaadin's
frontend bundler picks it up automatically.

### 2. Write a route wrapper in the host app

`ChronoGrid` carries no `@Route`, no `@VisibleFor`, no layout
binding — those are the host's concerns. The wrapper is a few lines:

```java
@Route(value = "calendar", layout = MainLayout.class)
@RolesAllowed("USER")                          // or your own auth annotation
public final class CalendarRouteView
        extends Composite<Component> implements I18nSupport {

    public CalendarRouteView() {
        CalendarStateStore store    = new VaadinSessionCalendarStateStore();
        CalendarMessages   messages = this::tr;     // your I18nSupport.tr(...)
        ChronoGrid       calendar = new ChronoGrid(store, messages);
        getContent().getElement().appendChild(calendar.getElement());
    }
}
```

That's all. `ChronoGrid` reads/writes the user's configured CalDAV
connection through `store`, looks every user-facing string up through
`messages`, and bootstraps a default `CalendarService` from the first
quick-connect preset (Apple iCloud) when the store has nothing
stored yet.

### 3. The three injection seams

| Seam | Default | When to plug your own |
|---|---|---|
| **`CalendarStateStore`** | `VaadinSessionCalendarStateStore` — Connection / Servers / Subscriptions / N-days slider live on the current `VaadinSession`; everything resets on logout. | Switch to a DB- or file-backed store when users should keep their CalDAV configuration across logins, or when running multi-tab and the session-isolation gets in the way. |
| **`CalendarMessages`** | `CalendarMessages.fallbackOnly()` — uses the English fallbacks baked into the call sites. | Wire up your host's i18n. For the demo's `I18nSupport`-style API: `(k, fb, args) -> tr(k, fb, args)`. For Spring's `MessageSource`, ResourceBundle, or anything else: same shape, different lookup. |
| **`CalendarService`** *(optional)* | Resolves from the store's connection config, falls back to `CalDavProviderPreset.DEFAULTS.get(0)` (Apple iCloud quick-connect). | Use the four-arg constructor `new ChronoGrid(store, messages, service)` when you want to bootstrap with a pre-built service — e.g. a multi-server fan-out, or a testbench-backed service in tests. |

### 4. Customising the quick-connect catalogue

The Settings dialog renders one button per `CalDavProviderPreset` in
the bundled `DEFAULTS` list (currently just Apple iCloud). Nextcloud,
Radicale, Baïkal, mailbox.org and similar entries would be drop-in
additions to that list when the catalogue extension API ships — for
now, the host can wrap `ChronoGrid` and pre-seed the store's
connection config to skip the catalogue entirely.

### 5. Styling

The bundled CSS uses BEM-ish class names (`.chronogrid__frame`,
`.chronogrid-nav__group`, `.server-status-pill__dot--connected`, …)
and Lumo custom properties throughout (`--lumo-primary-color`,
`--lumo-contrast-5pct`, …). Override either in your own theme's CSS;
no Lumo-fork required.

Per-row data (subscription colour, server status) is bridged via CSS
custom properties (`--swatch-color`, dot-state modifier classes), so
dynamic values stay out of inline `style="…"` attributes.

### Reference

- [`chronogrid-core/README.md`](chronogrid-core/README.md) — headless
  layer: minimal CLI / service example, package map, presets API.
- [`chronogrid/README.md`](chronogrid/README.md) —
  Vaadin layer: mount wrapper, customisation cookbook.
- [`docs/CHRONOGRID_EXTRACTION.md`](docs/CHRONOGRID_EXTRACTION.md) —
  why the module split looks the way it does + journey notes.

## Running with a CalDAV backend

### Option A — bundled `caldav-testbench` (no Docker, no external server)

The project ships [`com.svenruppert:caldav-testbench`](https://8g8.eu/caldav)
as a test-scope dependency of `chronogrid-core`. A dev launcher under
`chronogrid-core/src/test/java/junit/com/svenruppert/vaadin/calendar/CalDavDevServer.java`
starts the in-memory server on port `5232`, seeded with a single
calendar collection named `personal`, and parks until you hit Ctrl+C.
The project root carries
[`start-caldav-dev-server.sh`](start-caldav-dev-server.sh) as a thin
wrapper around the right `./mvnw exec:java` invocation.

Two terminals, two wrapper scripts:

```bash
# Terminal 1 — start the CalDAV testbench on :5232
./start-caldav-dev-server.sh

# Terminal 2 — start the Vaadin demo (pre-wired to the testbench above)
./start-vaadin-demo.sh
```

That's it. Open `http://127.0.0.1:8080/`, log in, navigate to
`Calendar`, and read/write events round-trip to the testbench.

Both wrappers forward extra arguments verbatim, so any
`-D…` override lands on the JVM:

```bash
./start-caldav-dev-server.sh -Dcaldav.dev.port=8088
./start-caldav-dev-server.sh -Dcaldav.dev.calendar=team

./start-vaadin-demo.sh -Dapp.port=9090
./start-vaadin-demo.sh -Dapp.caldav.baseUri=https://nextcloud/.../personal/
```

Under the hood both wrappers use `./mvnw exec:java -Dexec.classpathScope=test`
— the `test` scope is necessary on the Vaadin side because
`jakarta.servlet-api` is declared `provided` so it doesn't leak into
the WAR; the default `runtime` classpath would skip it and the
embedded Jetty would fail with
`NoClassDefFoundError: jakarta/servlet/Servlet`.

The raw equivalents (in case you prefer typing it out):

```bash
./mvnw -pl chronogrid-core exec:java \
       -Dexec.classpathScope=test \
       -Dexec.mainClass=junit.com.svenruppert.chronogrid.CalDavDevServer

./mvnw -pl chronogrid-demo exec:java \
       -Dexec.classpathScope=test \
       -Dexec.mainClass=com.svenruppert.flow.CalDavDemo
```

#### IDE one-click: `CalDavDemo.main`

Open `chronogrid-chronogrid-demo/src/main/java/com/svenruppert/flow/CalDavDemo.java` in your
IDE and hit Run. The class pre-sets `app.caldav.baseUri` to the
testbench's default collection URL
(`http://127.0.0.1:5232/calendars/personal/`) before handing off to
the regular nano-vaadin-jetty `Application` launcher. IntelliJ
includes `provided`-scope deps in run classpaths by default; Eclipse
and VS Code may need that option enabled explicitly. Either way, the
shell wrapper above is the workflow that "just works" without
fiddling with IDE config.

Explicit `-Dapp.caldav.baseUri=…` still wins — the launcher only
fills in the testbench default when the property is unset.

State is in-memory — restart the launcher and the calendar is empty
again. That's the point: deterministic prüfstand, not a persistent
backend.

### Option B — Apple iCloud (the showcase)

iCloud is the "real provider" the companion blog post targets — it
speaks plain CalDAV with HTTP Basic auth, no OAuth, no Google Cloud
console, no app verification. The `CalDavClient` we built talks to
it without a single code change.

**The URLs you need to know**

| Purpose | URL |
|---|---|
| Generate an app-specific password | <https://appleid.apple.com> → *Sign-In and Security* → *App-Specific Passwords* |
| Discovery entry point (type this into Settings → *Collection URI*) | `https://caldav.icloud.com/` |
| Final collection URI (resolved by Discover for you) | `https://p<shard>-caldav.icloud.com/<numeric-id>/calendars/<calendar-id>/` |

The `p<shard>` prefix (`p01-`, `p27-`, `p52-`, …) is Apple's
load-balancing shard for your account and is **not predictable** —
the discovery helper learns it from the `<calendar-home-set>`
response. Type the discovery entry point, click *Apple iCloud* in
the *Quick connect* preset row (or paste the URL by hand), let
Discover do the rest.

**Why an app-specific password is required, not the regular one**

Apple enforces two-factor authentication on every Apple ID. Once 2FA
is on, the regular Apple ID password is **blocked** for every
non-Apple client — CalDAV included. Apple answers with HTTP 401.
App-specific passwords exist exactly to fill that gap: one per app,
revocable individually, scoped to the chosen integration. **Always
use an app-specific password here, even on the rare 2FA-off account
where the regular one would still work — putting your full Apple ID
password into a third-party demo app is a bad idea regardless of
what Apple's gate accepts.**

**Walkthrough**

1. Sign in to <https://appleid.apple.com> → *Sign-In and Security*
   → *App-Specific Passwords* → generate a fresh one (label it
   *flow-template* so you can revoke it later). Apple shows it
   exactly once — copy it.
2. Start the demo:
   ```bash
   ./start-vaadin-demo.sh
   ```
3. Open the Calendar view → **Settings**. In the *Quick connect*
   row click the **Apple iCloud** preset — the *Collection URI*
   field gets filled with `https://caldav.icloud.com/` and the
   hint text switches to the iCloud-specific copy that points at
   the app-password requirement.
4. Type your Apple ID e-mail into *Username* and the app-specific
   password from step 1 into *Password*.
5. Hit **Discover calendars**. The dropdown that appears lists
   your real calendars; pick one — the *Collection URI* field is
   replaced with the chosen calendar's full URL (the
   `p<shard>-caldav.icloud.com/<id>/calendars/<slug>/` form).
6. **Test connection** — should go green. **Save** — the calendar
   reloads and shows your real events.

The app-specific password sits on `VaadinSession` for the life of
the login. When you sign out it's gone; on the next login the demo
falls back to the testbench default. Revoke the password at
appleid.apple.com any time to invalidate it on Apple's side.

### Option C — Radicale via Docker

Real (still local) CalDAV server with persistence:

```bash
docker run --rm -p 5232:5232 -v "$PWD/.radicale:/data" tomsquest/docker-radicale
./mvnw -Dapp.caldav.baseUri=http://127.0.0.1:5232/admin/personal/
```

Radicale requires Basic auth for writes — the *Settings* dialog
ships them as `Authorization: Basic …` on every request.

### Option D — point at any other CalDAV server

```bash
./mvnw -Dapp.caldav.baseUri=https://your.host/calendars/<user>/<collection>/
```

If the server requires HTTP Basic auth, fill in username / password
in the *Settings* dialog — values are kept on `VaadinSession` and
sent on every request. Anything that uses Bearer / OAuth (Google
Calendar's legacy CalDAV endpoint included) is **not** supported yet
and is on the roadmap as a separate follow-up.

### Just running the tests

The integration tests already spin up the testbench themselves — no
separate process needed. The full end-to-end calendar suite is:

```bash
./mvnw test -Dtest='EntryMapperTest,CalDavClientIT,CalendarServiceIT,ChronoGridBrowserlessTest'
```

## Using the calendar

A short tour through the running app, after both wrappers in
*Option A* are up.

### First-time login

1. Open <http://127.0.0.1:8080/>. The first request redirects to
   `/setup`.
2. Copy the bootstrap token printed on stdout (also written to
   `./data/jsentinel/bootstrap.token`) into the *Token* field, pick a
   username + password (≥ 12 chars), submit.
3. The login form replaces the setup form. Sign in with the
   credentials you just created.
4. After login the drawer shows three sections — *Public*,
   *Application*, *Administration*. The **Calendar** entry sits
   under *Application* (it requires `calendar:read`, which both
   `ADMIN` and `USER` hold by default).

### Navigating dates and switching views

The calendar carries a Lumo-styled navigation strip above the grid
that replaces FullCalendar's built-in toolbar:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  [⏮ ◀ Today ▶ ⏭]  [📅 14 Jun 2026]    June 2026    │Day│Week│N days│Month│   │
└──────────────────────────────────────────────────────────────────────────────┘
```

Five distinct navigation behaviors in **one** unified nav group on
the left:

- **⏮ Page back** — jumps by a full interval (a week in Week view,
  a month in Month view, N days in the N-days view, a day in Day).
- **◀ Slide back** — shifts the visible window by **exactly one day**,
  always, regardless of view. The "smooth & rolling" motion.
- **Today** — recentres on the current date.
- **▶ Slide forward** — one-day shift, mirror of slide back.
- **⏭ Page forward** — full-interval jump, mirror of page back.

The DatePicker beside the nav group jumps to any arbitrary date.
Its built-in popover *Today* button is suppressed via
`DatePickerI18n.setToday("")` so there is exactly **one** Today
affordance in the bar — no duplication.

- **Center label** — a large bold date string that updates from
  FullCalendar's `addDatesRenderedListener`. Shows "June 2026" for
  single-month intervals and "1 Jun – 14 Jun 2026" for spanning
  ranges.
- **Segmented view switcher** — Day · Week · N days · Month, as
  Vaadin Tabs in a rounded container. Tabs are stable test
  selectors via IDs `calendar-nav-tab-{day|week|ndays|month}`.
- **N input — slider only** — visible only in *N days* mode, range
  **1–21**, defaults to 7. A single native HTML range slider with
  a live `N = 7 days` label next to it. Drag the thumb to sweep
  across the whole span in one motion; the value persists on
  `VaadinSession` (`SESSION_KEY_NDAYS`) so flipping to *Month* and
  back to *N days* keeps the previously chosen span.

  The N-days view is implemented as a `CustomCalendarView` with
  `{ type: 'timeGrid', duration: { days: N },
     dayHeaderFormat: { weekday: 'short', day: '2-digit', month: '2-digit' } }`
  — the column header on each day reads e.g. *"Mo 14.06"* so the
  rolling window stays orientation-friendly. 21 such views are
  pre-registered at FullCalendar construction time, the slider
  picks the right one on every change.

### The calendar view at a glance

```
┌───────────────────────────────────────────────────────────────┐
│  Calendar                                                     │
│  Events live on the configured CalDAV collection. …           │
├───────────────────────────────────────────────────────────────┤
│  Backend: http://127.0.0.1:5232/…  [● Connected]              │
│                            [⚙ Settings] [⟳ Refresh] [+ New event]│
├───────────────────────────────────────────────────────────────┤
│  ◀  ▶   today           June 2026          month  week  day   │
│ ┌──────────────────────────────────────────────────────────┐  │
│ │                                                          │  │
│ │   ░░░░░░░  Demo summary 10:00                            │  │
│ │   ░░░░░░░                                                │  │
│ │                                                          │  │
│ └──────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

- **Backend badge** — shows the collection URI you're currently
  talking to. Updates immediately when you switch backends via
  *Settings*.
- **Connection badge** — a colored pill next to the URI:
  - *Unknown* (grey) — initial state, no request has resolved yet.
  - *Connected* (green) — the most recent CalDAV call succeeded.
  - *Disconnected* (red) — the most recent call failed (server
    unreachable, DNS error, 5xx, wrong host). Hover the badge to
    see the underlying error message.

  The badge updates passively after every fetch / save / delete and
  is refreshed actively every 15 seconds via a background poll, so
  a server outage shows up within ~15 s even when you are idle. As
  soon as the server comes back, the badge flips to green, a *"back
  online — reloading"* notification appears and the visible range
  is reloaded automatically.
- **FullCalendar's own header strip** (prev / next / *today* /
  view switcher) is the standard add-on UI; use it to walk the
  timeline and toggle between month, week and day views.

### Creating an event

Three equivalent ways — pick whichever feels natural:

1. **Drag** across one or more timeslots in week / day view; the
   editor dialog opens with start + end pre-filled.
2. **Click an empty cell** in month view; same dialog, same
   pre-fill.
3. Click the **+ New event** toolbar button; the dialog opens with
   the next full hour (today, 1 h duration).

Fill in *Title*, optionally *Description*, adjust *Start* / *End*,
hit **Save**. The view fires a `PUT … If-None-Match: *` against the
configured collection; success is silent (the new event simply
appears in the grid).

### Editing an event

- **Click** an event to open the editor pre-filled with its current
  values. Change anything, hit **Save** — a `PUT … If-Match: <etag>`
  goes out. **Delete** in the same dialog removes the event.
- **Drag** an event to another slot to move it. The change is
  written through the same If-Match path.
- **Resize** by dragging the bottom edge of the event to change
  duration. Same write path.

### Conflict handling

If the resource has changed on the server since you opened it (the
ETag drifted), the save / delete returns 412. The view shows a
yellow notification — *"This event was changed by someone else.
Reloading."* — and reloads the visible range automatically.

### Switching the CalDAV backend at runtime

Use *Settings* in the toolbar when you want to point the calendar at
a different server without restarting the JVM:

1. **Collection URI** — full URI including trailing slash, e.g.
   `https://caldav.icloud.com/<id>/calendars/home/` or
   `https://nextcloud.example/remote.php/dav/calendars/alice/personal/`.
2. **Username** / **Password** — leave blank for the local
   testbench. For Radicale / Nextcloud / Baïkal fill in the
   account; both fields are sent as HTTP Basic auth on every
   request.
3. **Test connection** issues a probe `REPORT` for the current
   ±1 h window and shows the result as a notification. Use it
   before *Save* to catch typos or wrong credentials.
4. **Save** stores the config on your `VaadinSession`, swaps the
   live `CalendarService` and reloads. The setting survives
   navigation but resets on logout.

#### Connecting to Apple iCloud

The companion blog post uses iCloud as the demo provider — it works
with the unchanged demo code because Apple speaks plain CalDAV with
HTTP Basic auth.

1. Go to <https://appleid.apple.com> → *Sign-In and Security* →
   *App-Specific Passwords* → generate one (e.g. labelled
   *flow-template*). Copy it — Apple shows it exactly once.
   **The regular Apple ID password will not work** — Apple's
   enforced 2FA blocks it for every non-Apple client.
2. In the Calendar view → *Settings*, click the **Apple iCloud**
   button in the *Quick connect* row. The *Collection URI* field
   is filled with `https://caldav.icloud.com/` and the hint area
   switches to the iCloud-specific copy.
3. Type your Apple ID e-mail into *Username* and paste the app-
   specific password into *Password*.
4. Hit **Discover calendars**. The demo runs three PROPFIND calls
   (current-user-principal → calendar-home-set → list) and surfaces
   your calendars in a dropdown. Pick one — the URI field is
   replaced with the chosen calendar's full URL
   (`https://p<shard>-caldav.icloud.com/<numeric-id>/calendars/<slug>/`
   style).
5. **Test connection** — green badge means the credentials work and
   the server answered an empty `REPORT` for the ±1 h window. Red
   means the URI is wrong, the password was revoked, or the network
   is blocked.
6. **Save**. The toolbar's *Connection* badge flips to *Connected*
   and the visible range fills with your real events. The app-
   specific password lives on `VaadinSession` until you sign out;
   revoke it on Apple's side any time to drop access without
   touching the demo.

> Google Calendar's legacy CalDAV endpoint requires OAuth 2.0
> Bearer tokens — the current `CalDavClient` only supports HTTP
> Basic, so that path is on the roadmap as a separate follow-up
> post, not part of this iteration.

### Connecting to multiple servers

A single Settings → Save adds one server. Run the flow again with a
different URI / credentials and the demo appends a second server
(not replaces) — typical setup: iCloud for personal events, an
on-prem Nextcloud for work, the testbench for experiments. Each
server has its own credentials; subscriptions belong to exactly one
server.

The flow is:

1. Open *Settings*, type the new server's discovery URI (use *Apple
   iCloud* preset for the obvious one).
2. Enter that server's Apple-ID / Nextcloud / Radicale credentials.
3. *Discover calendars*, tick the ones you want, *Save*.
4. Repeat for the next server.

The **Subscriptions** dialog (toolbar) lists everything across all
servers, with a *Server* column so you can tell at a glance which
calendar belongs where.

### Seeing all server connections (with status)

The **Connections** toolbar button opens a grid that summarises
every configured server in one place:

| Name | Base URI | Auth | Status |
|---|---|---|---|
| iCloud | https://caldav.icloud.com/ | Basic (alice@…) | ● Connected |
| Nextcloud | https://nextcloud.example/dav/ | Basic (alice) | ● Connected |
| Radicale | http://127.0.0.1:5232/ | anonymous | ● Disconnected |

When the dialog opens it pings each server in turn — picks that
server's first subscription URI, builds a fresh
`CalDavClient` with the matching credentials, fires a ±1 min
`REPORT calendar-query`, and pills the row:

- **Connected** (green) — the server answered 207 Multi-Status.
- **Disconnected** (red) — the server didn't respond, threw an HTTP
  error or refused the credentials. Hover the pill for the error
  category (`NETWORK`, `UNAUTHORIZED`, `TIMEOUT`, …).
- **Unknown** (grey) — no subscription to probe through (rare;
  happens only mid-cleanup).

### Managing subscriptions (show / hide / disconnect / recolour)

Once you've Discovered + Saved one or more calendars, the **Subscriptions**
button in the toolbar opens a grid:

| Color | Calendar | Server | Visible | |
|---|---|---|---|---|
| 🟦 | Home | iCloud | ☑ | ✕ |
| 🟧 | Work | Nextcloud | ☑ | ✕ |
| 🟩 | Family | Nextcloud | ☐ | ✕ |

- **Color picker** (HTML5 `<input type="color">`) mirrors the
  server's `<C:calendar-color>` (or a deterministic palette
  fallback). Click the swatch to override with any colour from the
  native OS picker — the chosen value writes through
  `CalendarSubscription.withColor(...)` to the session list and is
  applied to every entry from that subscription on the next render.
- **Server** shows the human-readable name (the URI host by
  default) — useful when several servers expose a calendar called
  *Personal*.
- **Visible** checkbox hides a calendar from the view without
  disconnecting — the next `REPORT` round still fetches it, but
  entries from that URI are filtered out before render. Toggle to
  show/hide on the fly.
- **✕** disconnects the subscription entirely. The next round of
  REPORTs skips that server URI; the entries vanish from the grid
  and the calendar is removed from the row. When the last
  subscription from a server is disconnected, the server's
  credentials are pruned from the session too.

Both state changes persist on `VaadinSession` until logout — they
are not written back to the CalDAV server. Re-running Discover +
Save adds new subscriptions (and preserves the visibility flag of
any existing ones).

### Assigning a new event to a specific calendar

The *New event* dialog adapts to how many calendars and servers you
subscribe to:

- **One calendar** — no extra controls. The event lands there.
- **Several calendars on one server** — a *Calendar* dropdown
  appears at the top. Each option is rendered with a coloured
  swatch (matching the server's `<C:calendar-color>`) plus the
  calendar name plus, in muted small text, the server name —
  enough to disambiguate two "Personal" calendars from two
  servers at a glance.
- **Several servers** — an additional *Server* filter appears
  above the *Calendar* dropdown. Picking a server narrows the
  calendar list to that server's calendars; *All servers*
  restores the full list. This is purely a filter for the
  picker — it does not constrain where the event goes; the
  *Calendar* dropdown is still the authoritative target.

The demo issues `PUT … If-None-Match: *` against the chosen
calendar's URI with the matching server's credentials. Edits and
drags of existing events route back to whichever calendar the
event already lives in (visible from its stored `caldavHref`),
so dragging a Work event into a free slot keeps it in Work —
moving events across calendars is out of scope for this iteration.

### Refresh

The **Refresh** toolbar button forces a `REPORT calendar-query` for
the visible range — useful after editing the same calendar from
another client (a phone, another browser tab) to pick up the
changes immediately.

### Language

The top-right *LocaleSwitcher* offers English and German. Calendar
labels, dialogs and toolbar buttons follow.

### Sign out

Top-right button. The next visit to a permission-gated route
re-prompts for credentials. The `VaadinSession`-stored Settings
config is cleared with the session.

## How the data flows

```mermaid
sequenceDiagram
    autonumber
    actor User as Browser
    participant View as ChronoGrid
    participant Svc as CalendarService
    participant Cli as CalDavClient
    participant Srv as iCloud / Radicale / testbench

    User->>View: open /calendar
    View->>Svc: findInRange(from, to)
    Svc->>Cli: REPORT calendar-query<br/>(VEVENT + VTODO time-range)
    Cli->>Srv: HTTP REPORT (XML body, ETag, User-Agent, Basic-Auth)
    Srv-->>Cli: 207 multistatus
    Cli-->>Svc: List&lt;RemoteEvent&gt; (raw iCal + ETag + href)
    Svc-->>View: Stream&lt;Entry&gt; (mapped, TZID-aware, colour-stamped)
    View-->>User: FullCalendar render

    User->>View: edit + Save
    View->>Svc: save(entry)
    Svc->>Cli: PUT If-Match: &lt;etag&gt;
    Cli->>Srv: HTTP PUT
    alt 412 Precondition Failed
        Srv-->>Cli: 412
        Cli-->>Svc: ConcurrentModificationException
        Svc-->>View: ConflictResult
        View-->>User: yellow notification + refresh
    else 201 / 204
        Srv-->>Cli: new ETag
        Cli-->>Svc: ok
        Svc-->>View: persisted Entry
        View-->>User: refreshItem
    end

    loop every 15 s
        View->>Svc: findInRange(now-1m, now+1m) — liveness probe
        Svc->>Cli: REPORT
        Cli->>Srv: HTTP REPORT
        Srv-->>Cli: 207 / failure
        Cli-->>Svc: result
        Svc-->>View: drives ConnectionStatusBadge state
    end
```

## Calendar architecture

The repository is a Maven reactor with three modules — two
publishable Vaadin add-ons plus the consuming demo. The split lets
a headless CLI or sync job pull only `chronogrid-core` without
dragging the Vaadin runtime along. See
[`docs/CHRONOGRID_EXTRACTION.md`](docs/CHRONOGRID_EXTRACTION.md)
for the design rationale and journey.

```
chronogrid-demo-reactor                  (pom — reactor root)
│
├── chronogrid-core                  (jar — headless wire + service)
│   └── com.svenruppert.chronogrid
│       ├── client/                  CalDavClient    PUT/GET/DELETE/REPORT, XXE-hardened
│       │                            CalDavDiscovery three-step PROPFIND
│       │                            CalDavError(s)  classified failure types
│       │                            RemoteEvent · DiscoveredCalendar
│       ├── mapping/                 EntryMapper     VEVENT/VTODO ↔ FullCalendar Entry
│       ├── service/                 CalendarService façade, Result<T, CalDavError>
│       │                            CalDavConnectionConfig · CalDavServerConnection
│       │                            CalendarSubscription   · CalDavProviderPreset
│       ├── state/                   CalendarStateStore  interface (UI-agnostic)
│       └── i18n/                    CalendarMessages    functional interface
│   → see chronogrid-core/README.md for consumer-side docs
│
├── chronogrid               (jar — Vaadin Flow add-on)
│   └── com.svenruppert.chronogrid
│       ├── ui/                      ChronoGrid    + 6 sub-components
│       │                            (CalendarNavigationBar, ConnectionStatusBadge,
│       │                             ConnectionsDialog, EventEditorDialog,
│       │                             ServerStatusList, SubscriptionsDialog)
│       └── state/                   VaadinSessionCalendarStateStore  (default impl)
│   resources/META-INF/resources/frontend/styles/chronogrid.css
│   → see chronogrid/README.md for consumer-side docs
│
└── demo                             (war — the consuming application)
    └── com.svenruppert.flow
        ├── CalDavDemo.java          IDE main: pre-wires testbench URI
        ├── Application + AppShell + AppServlet + MainLayout
        ├── security/                jSentinel bootstrap, roles, permissions
        ├── i18n/                    AppI18NProvider + I18nSupport host adapter
        └── views/
            ├── CalendarRouteView    @Route("calendar") + @VisibleFor(USER)
            │                        — embeds new ChronoGrid(store, messages)
            └── …                    AppLogin, Dashboard, About, …

chronogrid-core/src/test/java/junit/com/svenruppert/vaadin/calendar/
├── CalDavDevServer.java             long-running dev launcher (Main class)
├── EntryMapperTest.java             VEVENT roundtrip
├── CalDavClientIT.java              PUT/GET/DELETE/REPORT vs. testbench
├── CalDavClientAuthTest.java        Basic-Auth header injection
├── CalDavConnectionConfigTest.java  record + normalise
├── CalDavProviderPresetTest.java    catalogue + iCloud entry
├── CalDavDiscoveryTest.java         three-step PROPFIND chain (iCloud shape)
├── CalendarServiceIT.java           service-level read/write façade
├── state/CalendarStateStoreTest.java       contract round-trip
└── i18n/CalendarMessagesTest.java          host-i18n seam

chronogrid/src/test/java/junit/com/svenruppert/vaadin/calendar/state/
└── VaadinSessionCalendarStateStoreTest.java   no-session fallback

chronogrid-chronogrid-demo/src/test/java/junit/com/svenruppert/vaadin/calendar/
└── ChronoGridBrowserlessTest.java   route gating + EntryProvider + status badge

start-caldav-dev-server.sh           project-root wrapper for the CalDAV testbench launcher
start-vaadin-demo.sh                 project-root wrapper for CalDavDemo (Vaadin side)
```

The seam to watch: `CalendarService` keeps `findInRange` / `findById`
separate from `save` / `delete`. That's where a future
`@RequiresPermission("calendar:write")` slots in without touching the
read path.

## What's deliberately left out of the calendar feature

The CalDAV integration covers the read/write happy path. The
following stages from the concept doc are intentionally not yet
implemented:

- **Bearer / OAuth2 tokens.** Only HTTP Basic is wired (entered via
  the in-app Settings dialog). Bearer / OAuth2 token flows would
  need their own header injection in `CalDavClient.baseRequest(...)`
  and an OIDC-aware login.
- **MKCALENDAR.** Collections must exist server-side; the client
  does not create them.
- **RRULE expansion.** Recurring events are pushed to the server as
  a single VEVENT with RRULE; expansion into individual occurrences
  is delegated to the server side (the testbench handles it, iCloud
  expands on the wire when REPORTed). `EntryMapper` does not
  enumerate occurrences on the client.

> **Implemented since the v00.10.00 concept doc was written**:
> TZID / VTIMEZONE round-trip (`EntryMapper` preserves the source
> zone — see `EntryMapperTimezoneTest`); server discovery via the
> three-step PROPFIND chain (`CalDavDiscovery`, with `<C:calendar-
> home-set>` + `<DAV:current-user-principal>`); per-subscription
> colour picker; multi-server / multi-subscription fan-out.

---

## Built on top of the core-vaadin template

The demo runs inside the
[core-vaadin-project-template](https://github.com/svenruppert/core-vaadin-project-template)
— Vaadin Flow 25.1, Java 26, Jetty 12.1 EE11, jSentinel-secured
end-to-end. That platform is why the demo can stay small: auth,
roles, persistence, audit, theming and mutation gates are already
there. The sections below summarise what the template gives you for
free; if you want a non-calendar starter, fork that repo directly.

### Platform feature inventory

| Concern | What you get | Where it lives |
|---|---|---|
| Authentication | Username/password, role + permission catalog, drift detection | `security/{services,roles}` |
| First-admin bootstrap | One-time-token flow, persistent token file, `/setup` view | `security/bootstrap`, `views/SetupView` |
| Persistence | Eclipse-Store for users, sessions, audit events | `security/model/PersistentUserDirectory` |
| Audit log | Ring buffer + persistent sink, live `/audit` grid | `views/AuditView` |
| Session admin | Active-session inventory, revoke-on-click | `views/SessionsView` |
| Role admin | Add/remove users + roles, version-bump-on-mutation | `views/AdminRolesView` |
| Mutation tests | PIT + Browserless, per-package coverage floors enforced in CI | `tools/`, `chronogrid-chronogrid-demo/src/test/.../*BrowserlessTest.java` |
| Design system | `TemplateBrand` + theme tokens + reusable components | `views/ui/`, `frontend/themes/my-theme/styles.css` |
| Push demo | Vaadin `@Push` example view | `views/main/PushDemoView` |

### Security layering — three additive layers

The bootstrap pipeline is an SPI (`BootstrapExtension`) that picks up
every layer registered in `META-INF/services`. Each layer overrides
only the slice it cares about; the order is fixed by `order()`.

| Layer | Order | Provides |
|---|---|---|
| Default | 0 | Ring-buffer audit + logging, PBKDF2 hashing |
| Persistence | 10 | Eclipse-Store-backed audit + session store |
| Hardening | 20 | Argon2id hashing, drift-detection wiring |

Adding a fourth layer (MFA, multi-tenant, …) is one new
`BootstrapExtension` implementation + one line in
`META-INF/services`. Nothing else changes.

### Mutation-coverage gate

PIT mutation tests have per-package floors that CI enforces — see
[`tools/README.md`](tools/README.md). Current floors:

| Package | Floor |
|---|---|
| `security` | 90 % |
| `security.bootstrap` | 75 % |
| `security.model` | 80 % |
| `security.permissions` | 90 % |
| `security.roles` | 80 % |
| `security.services` | 80 % |
| `views` | 25 % |
| `views.main` | 20 % |
| **overall** | **42 %** |

A failing gate either means real regression (kill the surviving
mutants) or that the team decided to lower a floor — both demand a
written reason in the commit message.

The `calendar.*` packages do not have per-package floors yet — once
the feature stabilises, baseline them in `tools/pit-baselines.txt`.

### Rebranding a fork (30 minutes)

1. **`chronogrid-chronogrid-demo/src/main/java/com/svenruppert/flow/views/ui/TemplateBrand.java`**
   — change `NAME`, `TAGLINE`, `LANDING_INTRO`, `ICON`. The wordmark,
   navbar, hero copy and document title all pull from here.
2. **`chronogrid-chronogrid-demo/src/main/frontend/themes/my-theme/styles.css`** — change the
   six `--app-brand-*` hex values at the top. Lumo's
   `--lumo-primary-*` is mapped to it, so the entire app retheme
   follows.
3. **`PublicHomeView.buildFeatureGrid()`** — replace the three
   `FeatureCard`s with what your product actually ships.
4. **`MainLayout.buildDrawer()`** — add or remove drawer sections.
   Role-gated visibility is automatic.

Full design-system docs:
[`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md).

### Hardening you might want to add

Already wired via skills, but not pre-configured here:

- **HIBP password leak check** — flip a flag in the hardening skill,
  `PasswordPreflight` queries `api.pwnedpasswords.com` with
  k-anonymity range.
- **Persistent JSentinelVersionStore** — swap the in-memory drift
  store for the Eclipse-Store-backed one in `META-INF/services`.
- **Multi-tenant** — `TenantId` already threads through audit events
  and sessions; add per-tenant storage partitioning in
  `JSentinelStorageProvider`.

## Build commands

```bash
./mvnw                                              # dev server
./mvnw test                                         # unit + browserless tests
./mvnw -Pproduction package                         # production WAR
./mvnw -P_shadejar -DskipTests package              # standalone Jetty fat-jar
./mvnw -P_mutation-gate \
       org.pitest:pitest-maven:mutationCoverage \
       verify                                       # PIT + enforce coverage floors
./mvnw spotbugs:check                               # SpotBugs gate (run after any code change)
./mvnw versions:display-dependency-updates          # dependency audit
```

## Issue tracking

* [GitHub Issues](https://github.com/svenruppert/core-vaadin-project-template/issues)
* [GitHub Projects](https://github.com/svenruppert/core-vaadin-project-template/projects)

## Vulnerability hunting

Free scanners that integrate as GitHub PR checks — useful even for
personal projects, since they often complement each other:

* [Snyk](https://snyk.io/)
* [OX Security](https://app.ox.security/)
* [FaradaySec](https://faradaysec.com/)

## License

European Union Public Licence 1.2 — see `pom.xml` for the per-file
header that every source must carry.
