# Feature Backlog

**This file is the shipped-features ledger.** Every entry below
describes a feature that is already in production. The file feeds
the public project website, which is why every entry is written in
British English and follows the same schema.

## Lifecycle

Features start their life in [`Feature-Planning.md`](Feature-Planning.md)
(German, internal). Out of Planning a feature has exactly two
exits:

```
                          ┌─►  FEATURE_BACKLOG.md   (this file, shipped)
Feature-Planning.md  ─────┤
                          └─►  Features-Skipped.md  (rejected, German)
```

When a feature is **shipped**, its entry is rewritten into the
shipped-schema below, translated into British English, and moved
here. When a feature is **rejected without shipping**, the entry
moves to [`Features-Skipped.md`](Features-Skipped.md) with the
rejection date and rationale instead. Either way the Planning
entry is deleted at the move.

The three files — Planning + Backlog + Skipped — together are
exhaustive: any feature ever considered is in exactly one of them.
The companion file [`FEATURES.md`](FEATURES.md) is a higher-level
overview of the shipped functionality grouped by capability area.

## Entry schema

- **Status** — `✅ shipped YYYY-MM-DD in <commit>` (the commit body
  carries the full implementation map; the Backlog summarises)
- **Filed** — date the entry was opened in Planning
- **Idea** — what the feature does, end-user wording
- **Motivation** — why it is worth doing
- **Sketch** — rough technical plan as it actually shipped
- **Acceptance signals** — how we know it is finished
- **Risks / open questions** — known follow-ups, tuning points,
  deferred v2 scope

## Historical note

This file was originally created as a post-freeze backlog of
queued ideas (see memory `project-feature-freeze`, in force from
2026-06-16 to 2026-06-20 for the companion blog post). With the
freeze lifted on 2026-06-20 the file's role narrowed to its
current shipped-only ledger function; the planning side moved into
`Feature-Planning.md`.

---

## #1 — Per-event colour with a calendar-coloured edge stripe

**Status:** ✅ shipped 2026-06-20 in `456c886` (see the commit body for
the full implementation map)
**Filed:** 2026-06-17

### Idea

Every individual calendar entry should be able to carry **its own
colour** (independent of the default colour of its owning calendar).
So that, when several calendars are displayed together, it stays
obvious at a glance **which calendar an event belongs to**, every
entry also wears a narrow **colour stripe at its edge** (e.g. a
left-hand border) in **the colour of its owning calendar**.

Visually, roughly:

```
┌─┬────────────────────────────────┐
│█│ 10:00  Team standup            │   ← event fill = individual colour
│█│ Room A                          │   ← left stripe = calendar's colour
└─┴────────────────────────────────┘
```

### Motivation

Today every entry inherits its calendar's colour automatically
(`CalendarService.fanOut` → `entry.setColor(color)` as the fallback).
Someone showing several calendars at once therefore gets a clear
calendar-provenance signal — but loses any way of visually
emphasising individual events („important" / „private" / „rescheduled").
With per-event colour + calendar stripe, the user gets **both**:

- a semantic per-event colour (their own choice)
- calendar provenance (orientation aid in the multi-calendar view)

### Sketch

**Data model**

- iCalendar: VEVENT property `COLOR` (RFC 7986). When present, it
  counts as the individual event colour; otherwise the entry falls
  back to the calendar's default colour.
- `EntryMapper`:
  - **Read**: take `vevent.getColor()` → `entry.setColor(...)`
    directly from the iCal source (previously only the
    subscription colour was applied).
  - **Write**: when saving from the editor, if the user has picked
    a colour, emit `vevent.setColor(value)`.

**Service**

- `CalendarService.fanOut(...)` must distinguish between „entry has
  its own colour" and „entry inherits the calendar colour":
  - Custom property `caldavEntryColor` marks the source „event has
    its own colour set"; only this overrides the fallback colour.
  - The calendar colour additionally lands on a second custom
    property `caldavCalendarColor`, so the view can render the
    stripe — independently of whether `entry.getColor()` was
    overridden by an individual colour.

**UI / rendering**

- `EventEditorDialog` gains an HTML5 `<input type="color">`
  (analogous to `SubscriptionsDialog`) plus a „reset to calendar
  default" button.
- Render the stripe: either a FullCalendar `entryDidMount` callback
  **or** a CSS-only approach via a custom property:
  - Inline hook on the rendered event element:
    `--entry-stripe-color` read from `caldavCalendarColor`.
  - Static CSS rule in `styles/chronogrid.css`:
    ```css
    .fc-event {
      border-left: 4px solid var(--entry-stripe-color, transparent);
    }
    ```
  - The event colour (fill) remains FullCalendar-managed via
    `entry.setColor(...)`.

**Tests**

- `EntryMapperTest`: round-trip a VEVENT with `COLOR:#FFAA00`.
- `CalendarServiceResultTest`: entry without `COLOR` inherits the
  calendar colour; entry with `COLOR` keeps its individual colour.
- Browserless: the entry element carries `--entry-stripe-color`
  whose value equals the calendar colour.

### Acceptance signals

- ✅ In the multi-calendar view: two entries with the **same**
  individual colour, but from **different** calendars, remain
  distinguishable via their differently coloured stripes.
- ✅ In the editor: the user can pick their own colour **and** reset
  to the calendar default.
- ✅ Saving persists `COLOR` into iCalendar; re-read yields the same
  colour.
- ✅ Existing events without `COLOR` still show the calendar's
  default colour and a stripe of the same colour (no visual
  regression).

### Risks / open questions

- **CalDAV server support for `COLOR`**: iCloud, Radicale and
  Nextcloud handle it differently. For servers that reject it a
  fallback is needed (a custom X-property instead of RFC 7986?).
- **Contrast against dark / light event fills**: the text colour
  must remain legible (`color: white` vs. `color: black` depending
  on luminance). Either reuse FullCalendar's existing logic or
  write our own luminance helper in CSS via `color-mix` / `lab()`.
- **Stripe width has to stay consistent** across `dayGridMonth`
  (compact events) and `timeGridDay/Week/nDays` (large blocks).
  A per-view variant may be needed.
- **Default colour palette**: when the user picks a colour in the
  editor, do we offer a curated set of swatches (Material?
  Tailwind? Lumo tones?) — or just a freeform picker?

---

## #2 — Visible today anchor in the main calendar grid

**Status:** ✅ shipped 2026-06-21 (see the commit body for the full
implementation map)
**Filed:** 2026-06-21

### Idea

Make today's day visually unmistakable in the main calendar grid —
the FullCalendar widget itself, not the date-picker popover. In
TimeGrid views (Day, Week, N-days) the column for today gets a subtle
tint and a stronger top border on its column header; in DayGrid (Month)
view today's cell + day-number badge pop with the same accent.

### Motivation

FullCalendar's default `.fc-day-today` styling is a faint background
tint that competes with — and is swallowed by — ChronoGrid's existing
zebra rows / columns. In Week and N-days views the user most often
asks „which column am I on right now?", and the lack of an anchor
forces a slow scan of the column headers. With a visible today
highlight, the temporal anchor is restored at zero cognitive cost.

### Sketch

CSS-only solution in `chronogrid.css`. Four rules placed **after** the
existing zebra-tint block so source-order beats equal specificity:

```css
.fc .fc-timegrid-col.fc-day-today {
    background-color: var(--lumo-primary-color-10pct);
}
.fc .fc-col-header-cell.fc-day-today {
    border-top: 3px solid var(--lumo-primary-color);
    font-weight: 600;
}
.fc .fc-daygrid-day.fc-day-today {
    background-color: var(--lumo-primary-color-10pct);
}
.fc .fc-daygrid-day.fc-day-today .fc-daygrid-day-number {
    color: var(--lumo-primary-color);
    font-weight: 700;
}
```

`--lumo-primary-color-10pct` adapts to both light and dark Lumo
themes automatically, so consumer themes inherit a sensible default
without configuration.

### Acceptance signals

- ✅ Today's column in TimeGrid views (Day / Week / N-days) shows a
  visible tint, distinct from the zebra background.
- ✅ Today's column header carries a stronger Lumo-primary top
  border + bold weight.
- ✅ Today's cell in Month view shows the same tint; the day-number
  badge picks up the Lumo-primary accent.
- ✅ Works in both light and dark Lumo themes without further
  tuning.
- ✅ When today is not in the visible range (rolling N-days view
  scrolled away), no spurious highlight appears — FullCalendar
  drops the `.fc-day-today` class and the rules disengage.

### Risks / open questions

- **Zebra collision:** if today happens to land on an „even" column,
  both rules evaluate. Equal specificity → CSS source order wins;
  the today block is placed after the zebra block, so today's tint
  prevails.
- **Dark theme tint strength:** `--lumo-primary-color-10pct` may
  feel slightly faint on the darkest dark backgrounds. If consumer
  feedback complains, bump to `-20pct`.
- **Locale edge case:** „today" is computed client-side relative to
  the user's time zone by FullCalendar — no server-timing risk.

---

## #3 — Today highlight in the date-picker popover

**Status:** ✅ shipped 2026-06-21 (see the commit body for the full
implementation map)
**Filed:** 2026-06-21

### Idea

In the **„Zum Datum springen"** selector (the `DatePicker` to the
right of the navigation group in `CalendarNavigationBar`), today's
day should be **visually unmistakable** when the popover is open —
an outline + bold weight using the same Lumo-primary vocabulary as
the main calendar grid (see BACKLOG #2 for the grid counterpart).

### Motivation

When the navigation bar was designed, the Today button inside the
popover was suppressed via `DatePickerI18n.setToday("")` so the
single Today affordance lives in the navigation group. Side effect:
Vaadin's date picker marks today through the same styling class the
Today button uses — with the button suppressed, the popover lost its
visual „today" anchor. Anyone working out „2026-09-15 — how many
weeks from today?" mentally had no reference point.

Restoring the visual anchor without bringing back the duplicate
button is the right balance.

### Sketch

CSS-only solution in `chronogrid.css` via Vaadin's date-picker shadow
parts:

```css
vaadin-date-picker-overlay-content::part(today),
vaadin-date-picker::part(today) {
    outline: 2px solid var(--lumo-primary-color);
    outline-offset: -2px;
    border-radius: 50%;
    font-weight: 700;
}
```

Both selectors are listed so the rule works whether the consumer's
Vaadin version exposes the `today` part on the overlay-content or
on the host date-picker element directly.

### Acceptance signals

- ✅ Opening the date-picker popover („Zum Datum springen") shows
  today's day with a Lumo-primary outline and bold weight, clearly
  distinguishable from other days.
- ✅ Works in both light and dark Lumo themes (outline colour
  inherits the consumer's `--lumo-primary-color`).
- ✅ The „single Today affordance" rule still holds — exactly ONE
  Today button stays visible (in the navigation bar's `navGroup`);
  the popover gains visual anchoring without restoring its own
  button.

### Risks / open questions

- **Vaadin part-name verification:** the rule covers both
  `vaadin-date-picker-overlay-content::part(today)` and
  `vaadin-date-picker::part(today)`. If a future Vaadin version
  changes the part name, the rule degrades to a no-op (no visual
  regression elsewhere) and the issue surfaces in manual smoke
  testing.
- **Locale edge case:** „today" is client-side, time-zone-relative.
  Vaadin's date picker computes the today flag on the browser side,
  so a daylight-saving change at midnight cannot land on the wrong
  day server-side.
- **Future variant — Path B:** if the part-name verification ever
  shows that Vaadin has stopped exposing a today part, the fallback
  is to bring the popover's default Today button back and remove
  the navigation-bar one. That breaks the established
  „one-Today-affordance" UX decision, so the path is documented but
  deliberately not taken first.

---

## #4 — Stronger event borders for visible provenance

**Status:** ✅ shipped 2026-06-21 (see the commit body for the full
implementation map)
**Filed:** 2026-06-21

### Idea

Make the calendar-coloured border that BACKLOG #1 introduced for
custom-coloured events **clearly visible**. FullCalendar's default
1-px border vanishes against a saturated event fill — the calendar
provenance signal that BACKLOG #1 was meant to carry was getting lost
at exactly the moment it mattered most (i.e. when the user did pick
their own event colour).

### Motivation

BACKLOG #1 paints the entry fill in the user-picked colour and the
entry border in the calendar's colour. Visually, the border is the
provenance signal — „this red event is from my Family calendar even
though I painted it green." A 1-px line that the browser anti-aliases
away on retina displays is not a signal; it is a hope.

Bumping the border width to a robust 3 px (4 px on TimeGrid event
blocks where stacking is denser) makes the provenance signal land
without changing any of the colour-allocation logic.

### Sketch

CSS-only, two rules in `chronogrid.css`:

```css
.fc .fc-event {
    border-width: 3px;
}
.fc .fc-timegrid-event {
    border-left-width: 4px;
}
```

For uniform-coloured events (no own colour set), `borderColor ==
backgroundColor`, so the thicker border reads as a single solid block
— no visual regression for events that didn't pick their own colour.

### Acceptance signals

- ✅ A custom-coloured event in a multi-calendar view shows its
  calendar provenance unambiguously without zooming in.
- ✅ Uniform-coloured events still read as a single solid block —
  no „decoration" effect from a visible border on top of the same
  fill.
- ✅ TimeGrid-stacked events on a busy day stay distinguishable —
  the 4-px left edge survives the visual squeeze.
- ✅ No JavaScript interop, no Vaadin shadow-DOM hooks; the rule
  works on every supported Vaadin version unchanged.

### Risks / open questions

- **Cheap path vs. left-stripe path:** the original Feature-Planning
  sketch (Planning #2) preferred a left-only stripe via a
  `data-cg-custom-coloured` attribute on the rendered event element,
  which would have avoided the thicker border on uniform events.
  That path needs a FullCalendar `eventDidMount` JS callback or
  `eventClassNames` returning logic — both fragile without browser
  smoke testing. Cheap path ships now; the left-stripe path can
  succeed it as a polish iteration.
- **Month-view tightness:** in `dayGridMonth` the event blocks are
  compact (often 16–20 px tall). A 3-px border eats ~30 % of the
  block height. If consumer feedback complains, reduce to 2 px in
  the Month view only via a more specific selector.

---

## #5 — Per-day appointment dots inside the date-picker popover

**Status:** ✅ shipped 2026-06-21 (see the commit body for the full
implementation map)
**Filed:** 2026-06-21

### Idea

When the user opens the "Go to date" picker, every day cell in the
popover calendar shows **inline visual dots** for the calendars
that already have entries on that day. A glance at the dropdown
answers *"which days are free, which days are busy, and from which
calendars?"* — without leaving the picker, without committing a
selection, without scrolling the main grid.

Concrete use case Sven described: picking a free day for a
publication. The dots reveal at a glance which weeks already have
publications stacked up, so the eye lands on a quiet stretch
without trial-and-error jumping.

### Motivation

The popover used to be a blind jump — opening it gave no signal
about which dates were already committed. Pre-#5 the user had to
either remember the schedule, open the main grid in parallel, or
commit a pick and read it from the post-selection rendering. None
of these are how a date picker is supposed to work; the popover IS
the planning surface for "find me a day", and it should carry the
information needed to make that decision.

The dot indicator is colour-coded by **calendar source** (same
palette the main grid uses), so a busy day still tells the user
*which* calendar is busy — useful when juggling personal /
work / family / publication calendars.

### Sketch

The aggregation runs in `ChronoGrid#coloursForRange(from, to)`:

```java
Map<LocalDate, Set<String>> coloursForRange(LocalDate from, LocalDate to) {
    LocalDateTime fromDt = from.atStartOfDay();
    LocalDateTime toDt = to.plusDays(1).atStartOfDay();
    Map<LocalDate, Set<String>> out = new HashMap<>();
    rangeWithStatus(fromDt, toDt).forEach(e -> {
        String colour = e.getColor();
        if (colour == null || colour.isBlank()) return;
        LocalDate dayStart = e.getStart().toLocalDate();
        LocalDate dayEnd   = e.getEnd() != null ? e.getEnd().toLocalDate() : dayStart;
        // multi-day events contribute their colour to every covered day
        for (LocalDate d = dayStart; !d.isAfter(dayEnd); d = d.plusDays(1)) {
            out.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(colour);
        }
    });
    return out;
}
```

ONE CalDAV `findInRange` call per popover-open covers the whole
visible month-±1 window — not one call per day. Multi-day entries
contribute to every day they span, with the FullCalendar
"midnight closes the previous day" convention honoured so a
14:00–15:00 entry doesn't spill into the next bucket.

`CalendarNavigationBar` accepts the aggregator through
`setDayDotsProvider(BiFunction<LocalDate, LocalDate, Map<LocalDate, Set<String>>>)`
and triggers it on every `opened-changed`/`value-change` of the
`DatePicker`. The result is serialised to JSON
(`{"2026-06-15":["#1f77b4","#ff7f0e"], …}`) and pushed to the
picker element together with a shadow-DOM walker via `executeJs`.

The walker — validated against Vaadin 25.1.1 — does five things:

1. **Find the month calendars** via a document-wide
   `querySelectorAll('vaadin-month-calendar')`. Vaadin teleports
   the popover to `document.body`, so the calendars are reachable
   document-globally once the overlay has mounted. A recursive
   shadow-tree walk from `document.body` catches any case where
   the overlay nests them under custom scroller wrappers.
2. **Race-resilient mount.** First open often runs before the
   overlay has finished mounting; `paintWithRetry` polls every
   60 ms (max 10×) until at least one cell is found.
3. **Inject a `<style data-cg="1">`** into each month-calendar's
   shadow root, exactly once. The stylesheet lives in the same
   shadow scope as the cells, so its selectors match without
   needing `::part()` (which only pierces one shadow boundary,
   so document-level rules can't reach the cells two hops deep).
4. **Per cell**: set `data-cg-events="N"` plus a custom property
   `--cg-day-bg` — either a single colour or a 2-/3-stop
   `linear-gradient`. The injected stylesheet renders the bar via
   an `::after` pseudo-element which Vaadin's own td-background
   rules cannot fight on specificity (it's a brand-new box).
5. **Recursive MutationObservers** on every reachable shadow root
   (one observer doesn't cross boundaries) so user navigation
   inside the popover — month scrolling, year picking — triggers
   automatic re-paints.

The injected stylesheet:

```css
[data-cg-events]            { position: relative; }
[data-cg-events]::after {
    content: "";
    position: absolute; left: 50%; bottom: 2px;
    transform: translateX(-50%);
    width: 60%; max-width: 24px; height: 4px;
    border-radius: 2px;
    background: var(--cg-day-bg, currentColor);
    pointer-events: none;
}
```

Days with 4+ contributing calendars cap at the 3-third gradient
(no overflow indicator — the bar is a "many" signal, not a
counter).

### Acceptance signals

- ✅ Opening the popover paints a ~24 px × 4 px rounded bar at the
  bottom of every day cell that has at least one entry within the
  visible month-±1 window. Verified visually against a populated
  iCloud calendar.
- ✅ Scrolling months inside the popover re-paints via the
  recursive MutationObserver chain — newly mounted month cells
  get their bars without a manual re-trigger.
- ✅ Days with 1 / 2 / 3 contributing calendars render a solid /
  half-split / third-split bar in the source colours via a
  `linear-gradient` on the `--cg-day-bg` custom property.
- ✅ Days with 4+ contributing calendars render the 3-third bar
  (capped, no extra overflow indicator).
- ✅ Empty days render no bar.
- ✅ When the network is offline, the aggregator catches the
  `RuntimeException` from `findInRange` and returns an empty map
  — the popover renders cleanly with no bars.
- ✅ All tests stay green; `BugInstance size is 0` on both
  `chronogrid-core` and `chronogrid`.

### Open history note

This entry went through three iterations on the day it shipped:

1. **Adjacent-badge misread.** First version landed an indicator
   badge *next to* the date picker showing count + dots for the
   selected date. Wrong scope — Sven's intent was an in-cell signal
   inside the popover so a busy week stands out at a glance during
   date-search. Rolled back same-day.
2. **Shadow-DOM walker, first attempt.** Walked
   `picker._overlayContent.shadowRoot →
   vaadin-month-calendar.shadowRoot → [part~="date"]`, set inline
   `background-image: linear-gradient(...) !important` on the cells.
   Two problems: (a) Vaadin 25.1.1 mounts the overlay outside the
   picker's shadow tree (teleports to `document.body`), so the
   `_overlayContent` traversal path returned zero month calendars;
   (b) even where cells were reachable, Vaadin's own td-background
   rules in the constructed-stylesheet won the specificity battle
   despite `!important`.
3. **Stylesheet-injection, final.** Document-wide `querySelectorAll`
   to find the calendars, polling retry for the overlay-mount race,
   and a `<style>` element injected into each month-calendar's
   shadow root so the `::after` bar is a brand-new box Vaadin's
   own rules can't fight. Validated visually against a populated
   iCloud calendar (4 days with entries, all painting correctly).

Recorded open so the next reader sees the failure modes — the
Vaadin date-picker shadow-DOM access pattern is brittle in a
specific way (one-hop ::part(), teleported overlay, virtual scroll)
and the working pattern documented above is the result of
iterating on each of those.

### Risks / open questions

- **Vaadin shadow-DOM contract.** The walker depends on three
  things being stable: (a) the overlay being reachable via
  document-wide `querySelectorAll('vaadin-month-calendar')`,
  (b) each month-calendar having an open shadow root we can
  append a `<style>` to, (c) day cells matching one of
  `[part~="date"]` / `#days-container td` / `#monthGrid tbody td`.
  A future Vaadin minor that renames these would silently drop
  the bars (no Java exception, no test failure, just no paint).
  Pin a smoke-test of this feature into the upgrade checklist
  whenever Vaadin's `vaadin.version` property is bumped — the
  diagnostic `data-cg-dots-days` attribute on the picker element
  reveals at a glance whether the Java side is providing data;
  combine with DevTools-Elements inspection of a month-calendar
  to verify cells are being stamped.
- **Aggregator hits the CalDAV server.** Each popover-open
  triggers one `findInRange` for ~60 days. The underlying client
  typically reads from its in-process cache; if cold, a network
  round-trip per open. Acceptable for v1 (one call per
  pop-open ≠ per-day-hover); the obvious tuning is to also
  push on month-navigation-inside-the-popover, but that needs a
  Java↔JS event channel that v1 deliberately skips.
- **Multi-day entries contribute to every covered day.** A 5-day
  vacation paints the bar in all 5 days, not just start/end —
  this is what you want for "is the day free?", but it shows up
  as 5 separate busy days. Could be tuned to only mark
  start+end if a use case argues for it; v1 ships the simpler
  rule.
- **3-bar cap is silent.** Days with 4+ contributing calendars
  cap at the 3-third gradient without an overflow indicator (no
  `+N`). A v2 could surface this via a small badge below the
  bar; for now "3 distinct colours" is a strong enough signal
  that the user understands the day is busy across multiple
  sources.

---

## #6 — Per-entry tags + cross-calendar tag filter

**Status:** ✅ shipped 2026-06-21 (see the commit body for the full
implementation map)
**Filed:** 2026-06-21

### Idea

Each calendar entry carries a free-form list of tags ("work",
"client-acme", "deep-focus", …) round-tripped through the
iCalendar `CATEGORIES` property (RFC 5545 §3.8.1.2). The toolbar
gains a multi-select tag combobox that filters the visible entries
**across every subscribed calendar at once** — the user can ask
"show me every client-acme commitment regardless of which
calendar holds it" and get a single, coherent answer.

### Motivation

Today the only cross-calendar lens the grid offers is "show all of
them at once" or "hide a calendar entirely". Tags give a *third*
dimension orthogonal to the calendar-source colour: one tag like
`client-acme` can live across a personal calendar, a work
calendar, and a family calendar — pre-existing rationale for the
multi-subscription model. Filtering by tag answers the everyday
"what's on my plate for X" question without the user manually
juggling subscription visibility toggles.

### Sketch

Five-layer implementation, each layer freeze-conformant and tested:

| Layer | Change |
|---|---|
| `EntryMapper` (core) | New `CUSTOM_CATEGORIES` constant; read+write CATEGORIES on VEVENT. Tags normalise to trim+lower-case so cross-server equality is canonical. Public helpers `readTags(Entry)` / `writeTags(Entry, Set<String>)`. |
| `CalendarStateStore` (core) | New `default` methods `readTagFilter()` / `writeTagFilter(Set<String>)` returning empty set / no-op. Existing impls compile unchanged. |
| `VaadinSessionCalendarStateStore` (component) | Overrides the two new methods, persisting to attribute `calendar.tagFilter` as a defensively-copied unmodifiable Set. |
| `EventEditorDialog` (component) | New comma-separated `TextField` "Tags" with helper text. Value round-trips via `EntryMapper.writeTags` on Save. |
| `ChronoGrid` (component) | New `MultiSelectComboBox` in the toolbar, populated from a running `tagUniverse: NavigableSet<String>` that harvests on every fetch. Selection writes through to the state store + triggers `refreshAll()`. Fetch-side filter (`matchesAnyTag`) applies inside `rangeWithStatus` so the filtered set is FullCalendar's source of truth, not a post-rendering DOM mask. |

i18n: 4 new keys (`calendar.toolbar.tagFilter`,
`calendar.toolbar.tagFilter.placeholder`, `calendar.field.tags`,
`calendar.field.tags.hint`) in EN + DE.

CSS: none — the components inherit Lumo defaults.

### Acceptance signals

- ✅ Saving an entry with `Work, client-acme` writes a single
  `CATEGORIES:work,client-acme` line in the iCalendar body
  (verified by `EntryMapperTagsTest#writesCategories`).
- ✅ Reading the same iCalendar back populates
  `EntryMapper.readTags(entry)` with `{work, client-acme}`
  (verified by `EntryMapperTagsTest#parsesCategories`).
- ✅ Clearing the tags input removes the `CATEGORIES` line entirely
  (verified by `EntryMapperTagsTest#clearingTagsRemovesCategories`).
- ✅ Selecting tags in the toolbar combobox filters the grid;
  deselecting restores. Persists across navigations.
- ✅ The tag universe grows monotonically as the user pans through
  date ranges; tags only seen after a `refreshAll()` join the
  combobox on the next fetch.
- ✅ 293 tests green (213 demo + 75 core including 4 new + 5
  component); `BugInstance size is 0` on both core and component.

### Risks / open questions

- **API surface widened on `CalendarStateStore`.** New default-no-op
  methods preserve binary compat for external implementers, but
  external consumers that want the feature must override both
  methods. Migration documented in the BACKLOG and (when the
  freeze lifts) in `CHANGELOG.md`.
- **Case sensitivity is a normalisation choice.** Tags are
  trim+lower-case on the wire and in storage. A user who writes
  `Work` and another who writes `work` get the same tag — by
  design, but it loses the original casing on round-trip. A future
  v2 could store the first-seen casing as display label while
  comparing case-insensitively. For v1 the simpler invariant wins.
- **`MultiSelectComboBox` items snapshot.** The universe is held in
  a synchronized `NavigableSet`; `setItems` runs through
  `UI#access` only when the universe genuinely grew. No silent
  truncation, no hidden cap — but with > 50 tags the combobox UX
  degrades. Documented as a v2 tuning point.
- **Tag editor is plain comma-separated text.** A future v2 could
  use a `TagsField`-style component with autosuggest from the
  current universe. v1 ships the simpler text input — the
  underlying normalisation makes the input forgiving (extra
  whitespace, case mixing, trailing commas all collapse cleanly).
