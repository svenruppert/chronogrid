# ChronoGrid — extraction design rationale

This document captures the *why* and *how* of moving `ChronoGrid`
from a single-module demo view to a publishable Vaadin Flow add-on
spanning two artefacts. Written post-hoc with hindsight from running
all three phases end-to-end.

For status / scope of future evolutions, see
[`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md).

## 1. Goal

Originally `ChronoGrid` was hardwired into this specific project:
host-specific routing annotations, host-specific i18n interface,
direct `VaadinSession.getAttribute(...)` for persistence, dependency
on a static `CalendarServiceProvider` holder, custom CSS in the
host's `frontend/` tree. Extracting it as a drop-in Vaadin composite
the rest of the world can `dependency`-pull was the goal.

Target shape:

```xml
<dependency>
    <groupId>com.svenruppert.chronogrid</groupId>
    <artifactId>chronogrid</artifactId>
    <version>00.10.00</version>
</dependency>
```

Mount it in any Vaadin app with a thin host-side wrapper carrying
that app's own `@Route` + permission annotations.

## 2. Architecture decisions

Four load-bearing calls Sven took before Phase 1 started (preserved
in [`memory/project_calendar_component_extraction`](../.claude/projects/.../memory/project_calendar_component_extraction.md)):

| # | Decision | Rationale |
|---|---|---|
| **Modules** | Two Maven artefacts: `chronogrid-core` (headless) and `chronogrid` (Vaadin) | Headless CLI / sync-job consumers can pull only the wire layer; the UI tree stays separable. |
| **Error API** | `Result<T, CalDavError>` stays the public service-boundary type | Already runs through the codebase; `try/catch` is uglier and loses the typed error domain. |
| **Coordinates** | `com.svenruppert.chronogrid` namespace | Consistent with `com.svenruppert:functional-reactive`, `com.svenruppert:caldav-testbench`. |
| **Presets** | `CalDavProviderPreset.DEFAULTS` (incl. Apple iCloud) bundled in the artefact | Quick-connect out-of-the-box; consumers can still inject additional presets via the config. |

## 3. Final module shape

```
chronogrid-demo-reactor                  (pom — reactor root)
├── chronogrid-core                  (jar)
│   └── com.svenruppert.chronogrid
│       ├── client/                  CalDavClient · CalDavDiscovery · CalDavError(s) · RemoteEvent
│       ├── mapping/                 EntryMapper                      (VEVENT/VTODO ↔ Entry)
│       ├── service/                 CalendarService · CalDavConnectionConfig
│       │                            CalDavServerConnection · CalendarSubscription
│       │                            CalDavProviderPreset
│       ├── state/                   CalendarStateStore                (interface)
│       └── i18n/                    CalendarMessages                  (interface)
│
├── chronogrid               (jar — Vaadin Flow add-on)
│   └── com.svenruppert.chronogrid
│       ├── ui/                      ChronoGrid · CalendarNavigationBar
│       │                            ConnectionStatusBadge · ConnectionsDialog
│       │                            EventEditorDialog · ServerStatusList
│       │                            SubscriptionsDialog
│       └── state/                   VaadinSessionCalendarStateStore   (default impl)
│   └── META-INF/resources/frontend/styles/chronogrid.css
│
└── demo                             (war — the consuming application)
    └── com.svenruppert.flow
        ├── …                        (jSentinel, MainLayout, AppShell, all non-calendar views)
        └── views/CalendarRouteView  @Route("calendar"), @VisibleFor(USER),
                                     layout = MainLayout.class
                                     ↓ embeds
                                     new ChronoGrid(store, messages)
```

The host (`demo`) carries every project-specific concern; both
add-on artefacts are host-neutral.

## 4. Three integration seams

The published `chronogrid` exposes exactly three
constructor-injected concerns:

### 4.1 `CalendarStateStore`

Persists Connection, Servers, Subscriptions, and the N-days slider
between navigations. The default
`VaadinSessionCalendarStateStore` keeps everything on the current
session (the legacy behaviour). Plug a database-backed impl for
cross-session continuity, or an in-memory stub for tests.

```java
public interface CalendarStateStore {
  Optional<CalDavConnectionConfig> readConnection();
  void writeConnection(CalDavConnectionConfig cfg);

  List<CalDavServerConnection> readServers();
  void writeServers(List<CalDavServerConnection> servers);

  List<CalendarSubscription> readSubscriptions();
  void writeSubscriptions(List<CalendarSubscription> subs);

  int readNDays(int fallback);
  void writeNDays(int n);
}
```

### 4.2 `CalendarMessages`

The i18n seam. A single functional method
`tr(key, fallback, args...)`. Pass `this::tr` from your host's own
i18n interface, or `CalendarMessages.fallbackOnly()` for an
English-only build.

### 4.3 `CalendarService`  *(optional)*

A pre-built service instance. Omit and the view bootstraps from the
store's connection config, falling back to
`CalDavProviderPreset.DEFAULTS.get(0)` (Apple iCloud quick-connect).

## 5. The phases, retold

### Phase 1 — In-place decouple

Stayed inside the single module. Introduced the
`CalendarStateStore` and `CalendarMessages` interfaces, threaded
them through `ChronoGrid` + every sub-component, moved
`@Route` / `@VisibleFor` to a new `CalendarRouteView` wrapper.
Replaced the host's `PageHeader` brick with a local
`.chronogrid__page-header` div + CSS. Kept
`CalendarServiceProvider` for the test-injection shim,
encapsulated in `hostShimmedDefaultService()`.

Verification: 267 → 284 tests (the new abstractions ship with their
own coverage), all green. SpotBugs 0 findings.

### Phase 2 — Multi-module reactor

Root pom becomes `packaging=pom` reactor. Existing source moves
into `demo/`. Two new modules `chronogrid-core/` and
`chronogrid/` get carved out by file-rename + dependency
plumbing. `CalendarServiceProvider` deleted; the
`ChronoGridBrowserlessTest` injection mechanism switches from
`Provider.setService(testService)` to seeding the session
connection config in `@BeforeEach`.

The split-package gotcha (`com.svenruppert.flow.views.*` lived in
both demo and chronogrid) was resolved by giving the
`chronogrid` its own UI package layer
(`com.svenruppert.chronogrid.ui.*`).

Verification: 66 + 5 + 213 = 284 tests across the reactor, all
green. SpotBugs 0 each module.

### Phase 3 — Cleanup + namespace + publish prep

Trailing whitespace stripped from the EUPL headers that earlier
license-plugin runs had baked in; `maven-checkstyle-plugin`
re-enabled with 0 violations on both add-on modules.
`license-maven-plugin` stays disabled in the add-on modules — it
would otherwise re-inject the long header on every build.

The full namespace move from `com.svenruppert.flow.*` to
`com.svenruppert.chronogrid.*` ran via three `perl -i -pe`
passes — bulk-rewriting package declarations and imports across
the two new modules and threading through the demo. The
`ChronoGridBrowserlessTest` package and physical location
followed.

Sources + Javadoc jars wired up via explicit
`maven-source-plugin` + `maven-javadoc-plugin` executions in each
add-on pom — the publishable triplet now drops into
`target/` on every `mvn install`. `distributionManagement`
(s01.oss.sonatype.org) and `maven-gpg-plugin` are inherited from
the parent `com.svenruppert:dependencies`.

`README.md` for each add-on module covers coordinates, package
map, minimal mount snippet, and customisation seams.

## 6. What stayed deliberately out of scope

- **Maven Central deploy ceremony** — interactive: Sonatype creds
  + GPG passphrase. The pom is wired; the push is a Sven action.
- **Per-entry colour + calendar stripe** — parked in
  [`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md) #1.
- **Decoupling `EntryMapper` from `org.vaadin.stefan:fullcalendar2`**
  — would make `chronogrid-core` truly transitive-free of Vaadin
  at the cost of an extra DTO layer. Accept the transitive for v1.
- **Provider-agnostic preset catalogue** — for now `iCloud` ships
  hard-coded in `CalDavProviderPreset.DEFAULTS`; Nextcloud /
  Radicale / Baïkal entries would be drop-in additions to that
  list when the demand surfaces.
- **CSS-as-code variants** — the bundled CSS is fully themable via
  Lumo custom properties (`--lumo-*`) and the BEM-ish class names
  documented in `chronogrid/README.md`. No Lumo-fork.

## 7. Verification commands

```bash
./mvnw test                              # 284 tests across the reactor
./mvnw -pl <m> spotbugs:check            # 0 findings each module
./mvnw -pl <m> validate                  # 0 checkstyle violations (add-ons)
./mvnw -DskipTests install               # main + sources + javadoc jars
                                         # in calendar-{caldav,component}/target/
```

End-to-end smoke:

```bash
./start-caldav-dev-server.sh             # Terminal 1
./start-vaadin-demo.sh                   # Terminal 2
# http://localhost:8080/, login → /calendar
```
