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

---

## #7 — Focal day preserved across view switches

**Status:** ✅ shipped 2026-06-22 in `1d86b20`
**Filed:** 2026-06-22

### Idea

When the user jumps to a date (via the date picker, sliding one day
back/forth, paging by the current interval, or just navigating from
"Today") and then switches between Day / Week / N-days / Month
views, the calendar **stays anchored on that date**. A user looking
at the week of 2026-09-15 in Week-view and switching to Month-view
lands in September 2026, not on today's month.

### Motivation

Pre-feature, the view-switcher was a blind jump: FullCalendar
internally kept a *visible range start*, but the **focused day**
(the one the user just navigated to) was lost. The user typed
"15 Sep" in the picker, looked, switched to Month — and the grid
snapped back to today's month. That broke every multi-step
exploration of a future or past week and made N-days view almost
useless after any directed navigation.

A persistent focal day restores the temporal anchor: pick a day,
look at it through whichever view best fits the question, then
move on.

### Sketch

**State**

- `ChronoGrid` carries a private `LocalDate focalDay` field
  (default `LocalDate.now()` at mount).
- The `CalendarStateStore` interface gained optional
  `readFocalDay(LocalDate fallback) / writeFocalDay(LocalDate)`
  defaults; the `VaadinSessionCalendarStateStore` implementation
  persists the value under
  `SESSION_KEY_FOCAL_DAY = "calendar.nav.focalDay"`. Session-bound,
  so a page reload restores it but a logout drops it (deliberate).

**Update points (four user gestures + one async latch)**

| Trigger | What runs |
|---|---|
| Date picker `valueChange` | `setFocalDay(pickedDate)` |
| Slide ± buttons | Java-side `d ± 1` → `setFocalDay(next)` before `gotoDate(next)` |
| Today button | `setFocalDay(LocalDate.now())` |
| Page ± buttons | arm an `armFocalCapture` latch; the next `datesRendered` reads `getCurrentIntervalStart()` and sets focal |

The latch keeps the unreliable, race-prone read-after-`next()` out
of the user-gesture handlers — Java side gets a stale value until
the JS round-trip completes, so a Listener-based capture is the
only sound path. All other gestures compute the new date in pure
Java and call `setFocalDay` synchronously.

**View switch**

`applyViewMode` always finishes with:

```java
calendar.changeView(...);
if (focalDay != null) calendar.gotoDate(focalDay);
```

The follow-up `gotoDate` re-anchors the new view's visible window
on the user's focal day; the resulting `datesRendered` does **not**
overwrite `focalDay` because the latch is only set by Page ±.

**Initial mount**

If the persisted focal day differs from today, the constructor
issues one `calendar.gotoDate(focalDay)` after the calendar mount
so a returning user lands where they left off.

### Acceptance signals

- ✅ Picking 2026-09-15 in Week view and switching to Month →
  September 2026 shows, not today's month.
- ✅ Sliding back one day, then to N-days view → the slid-to day
  stays in the visible window.
- ✅ Clicking Today → focal flips to today and survives subsequent
  view switches.
- ✅ Page-Forward in Week view → the next week's Monday becomes the
  focal day, captured via the `datesRendered` latch.
- ✅ Page reload (F5) → focal day restored from the session,
  calendar opens on it.
- ✅ Switching views back and forth never causes `focalDay` to
  drift onto the current interval-start (idempotent `gotoDate`).

### Risks / open questions

- **Month-view day clicks.** Clicking a day in Month-view does not
  currently update `focalDay` — Month-view has no day-click
  listener wired. A v2 could add one; left out of v1 because the
  original ask was about *view-switch retention*, not click-as-
  navigation.
- **Page-back latch + custom views.** The arming latch relies on
  `getCurrentIntervalStart()` being set after FullCalendar's
  paging completes; verified for Day/Week/Month/N-days, but a
  future custom view would need to surface a sensible interval-
  start for the same latch to work.
- **N-days centring.** With N=7 and focal=Thursday, FullCalendar
  places Thursday in column 1 (not centred). v1 keeps the default
  for predictability; centring (`focalDay.minusDays(n/2)`) is a
  v2 candidate if users ask for it.

---

## #8 — Connection Manager UX refactor (Apple-Calendar-style)

**Status:** ✅ shipped 2026-06-22 / 2026-06-23 across five layers in
`1227e11`, `cab3cb1`, `d7ab55f`, `adab869`, `0a17133`, `c9b85b5`
**Filed:** 2026-06-22

### Idea

A single Apple-Calendar-style Connection Manager replaces the three
legacy dialogs (Settings, Connections, Subscriptions) with one
Master-Detail dialog: server tabs on the left, the selected server's
subscription list on the right. Adding a new server happens through
a three-step wizard; toggling calendar visibility happens via a
toolbar dropdown that never requires opening a dialog.

### Motivation

The bug-tracker had three separate entries — connection-management
UX is unintuitive (closed BUG #6), subscribe/unsubscribe is too
cumbersome (closed BUG #8), notifications do not fit the multi-
server world (closed BUG #9) — that on closer reading were all the
same user story: "make connecting, subscribing and seeing what is
going on across multiple servers feel like one coherent flow." The
three dialogs each owned one fragment of the story; new users hit
the wrong one first, and the legacy single-connection editor
silently fought the multi-server state model (the source of the
previously-fixed closed BUG #4).

A unified Connection Manager + Wizard + Quick-Toggle dropdown
collapses the click-paths and removes the overlapping mental
models in one go.

### Sketch

**Layer 1 — Quick-Toggle dropdown.** A toolbar button labelled
`Visible (N/M)` opens a `Popover` containing a one-row-per-
subscription toggle list plus bulk Show-all / Hide-all buttons.
Same persistence path as the legacy Subscriptions dialog
(`stateStore.writeSubscriptions` + `refreshAll`) so toggling from
the dropdown stays in sync with everything else.

**Layer 2 — three-step Connection Wizard.** A new
`ConnectionWizardDialog` covers:

1. *Provider + URL* — a `Quick connect` row of preset buttons
   (Apple iCloud + future providers) pre-fills the collection URI
   and swaps in a provider-specific hint for step 2.
2. *Credentials* — username + password fields, a Test-Connection
   button, and the provider hint (e.g. iCloud's app-specific-
   password warning).
3. *Calendars* — runs CalDAV discovery on step entry, displays the
   results as a checkbox group with a **hybrid default-visibility
   rule**: ≤ 5 calendars are pre-selected; > 5 are pre-deselected
   and the bulk Select-all / Select-none buttons appear so a
   30-calendar Nextcloud workspace does not flood the grid.

The dialog itself is test-friendly: it accepts injected
`discoveryFn`, `probeFn`, `onComplete` and `notify` callbacks so
its UI logic stays independent of any specific `CalendarService`
wiring.

**Layer 3a — Apple-style Connection Manager.** The new
`ConnectionManagerDialog` is a Master-Detail layout: vertical
server `Tabs` on the left, the selected server's
subscription list on the right. Each subscription row carries a
colour dot, name, visibility checkbox, inline HTML5 colour picker
and a remove-this-calendar button. The footer hosts
"+ Add server" (opens the wizard), Close, plus per-server
Re-discover (merges any newly-found calendars as
`visible=false`) and Remove server (atomic — drops the server,
all of its subscriptions and all of their per-URI colour
fall-back entries from the BUG-#2 store, in one write).

**Layer 3b — legacy cleanup.** With the new dialog covering every
flow, the three legacy entry points are removed:
`openSettingsDialog` (140 lines of inline dialog),
`openConnectionsDialog`, `openSubscriptionsDialog`, plus the
`ConnectionsDialog` and `SubscriptionsDialog` classes themselves.
The toolbar shrinks from six buttons to three (Connection Manager
/ Refresh / New event) plus the Visibility dropdown and tag filter.

**Layer 4 — status-aware multi-server notifications.** Two
helpers — `multiServerSummary()` produces `3 servers, 7 calendars,
5 visible`, `serverScopeHint(serverId)` produces a bracketed
`[iCloud]` subject suffix — feed every previously-misleading
notification template:

- Refresh-button toast: `Refreshed — 3 servers, 7 calendars, 5 visible`
- Connect-success toast: `Connected to <serverName> — 3 servers, 7 calendars, 5 visible`
- Remove-server toast: `Removed server "<serverName>" — 2 servers, 4 calendars, 3 visible`
- Subscription-remove toast: `Disconnected from "<calendarName>" [iCloud]`
- Re-discover toast / error: same scope suffix appended

The pre-refactor templates referred only to the primary collection
URI — actively misleading once three different backends were
involved in the same refresh.

**Layer 5 — one-shot legacy auto-migration.** A pre-refactor
session that held only the legacy
`calendar.connection.config` single-server key now silently
migrates on mount: the constructor converts that one
`CalDavConnectionConfig` into one `CalDavServerConnection` plus
one matching `CalendarSubscription`, then rebuilds the service.
The legacy key is deliberately **not deleted** — a rolled-back
deploy can still read it; a future major release removes it once
telemetry confirms zero readers.

### Acceptance signals

- ✅ Toolbar carries only `Connection Manager`, `Refresh`,
  `New event` plus the Visibility dropdown + tag filter. No
  Settings / Connections / Subscriptions buttons remain.
- ✅ Connection Manager shows one tab per configured server;
  selecting a tab lists that server's calendars on the right with
  inline colour picker and visibility checkbox.
- ✅ "+ Add server" opens the three-step wizard; the iCloud preset
  pre-fills `https://caldav.icloud.com/` and switches the credentials-
  step hint to the app-specific-password warning.
- ✅ Hybrid default visibility: a server returning ≤ 5 calendars
  pre-selects all; > 5 pre-selects none and shows the bulk
  buttons.
- ✅ Remove server is atomic — the server entry, its subscriptions
  and their colour fall-back entries vanish in one write; the
  refresh fires once.
- ✅ Refresh toast carries the multi-server summary line; the
  per-subscription disconnect toast carries the bracketed server
  scope suffix.
- ✅ A pre-refactor session with only the legacy connection key
  set lands directly inside the Connection Manager with the
  migrated server + subscription pre-populated. The legacy key
  stays readable for rollback safety.

### Risks / open questions

- **Re-discover trigger is manual.** A periodic auto-pull is
  deferred to a follow-up — manual avoids the trickier "when is
  it safe to retry a server that just timed out?" question and
  matches the rest of the toolbar's explicit-refresh model.
- **Per-server streaming progress** (entries arriving as each
  server finishes, not all at once) is deferred. The smart-delay
  ProgressBar (Feature #9) covers the wall-clock UX; per-server
  arrival ordering would need a custom EntryProvider on top of
  the FullCalendar add-on.
- **Notification spam at 5+ servers.** The summary line scales
  fine, but every individual `connect/disconnect/refresh` still
  fires its own toast; if telemetry shows a busy user feeling
  flooded, a smart-delay/collapse pattern (similar to the
  ProgressBar's 500 ms window) is the next step.
- **i18n volume.** The wizard + manager + status-aware
  notifications added ~30 EN+DE keys. Future provider presets
  (Google / mailbox.org) inflate this further; a per-provider
  bundle file under `vaadin-i18n/providers/` may eventually beat
  one flat translations file.

---

## #9 — Parallel multi-server fan-out with smart-delay progress bar

**Status:** ✅ shipped 2026-06-23 across two stages in `b4d4cd6`
(parallel fan-out) and `cecb967` (progress bar)
**Filed:** 2026-06-22

### Idea

`CalendarService.findInRange` queries every configured CalDAV
client in **parallel** instead of one after the other. A small
indeterminate progress bar in the toolbar surfaces only when a
refresh actually takes longer than a 500 ms smart-delay window;
fast refreshes never flash it.

### Motivation

Closed BUG #10 reported the symptom: five servers each taking
~800 ms REPORT meant ~4 s until the first entry appeared, and one
hanging server blocked every other. The sequential `for` loop
inside `fanOut` was the bottleneck — the wall-clock added up
rather than collapsing to the slowest single client. Once two or
more CalDAV backends were attached (the explicit goal of the
Connection Manager refactor), the latency became actively painful
for the user.

A parallel fan-out drops wall-clock from `sum(client_i)` to
`max(client_i)` and isolates per-client failures from the
overall round-trip. A small indeterminate progress bar gives a
visual hint that work is in flight while still being unobtrusive
on the fast path.

### Sketch

**Stage A — parallel fan-out.**
`CalendarService.fanOut` now spawns one
`CompletableFuture.supplyAsync(...)` per `CalDavClient` against
`ForkJoinPool.commonPool()`. Each per-client task builds a private
`ArrayList<Entry>` and applies the calendar's palette colour;
the colour lookup is pre-snapshotted into a `HashMap` before any
worker starts so the workers never touch shared mutable state.
After `allOf(futures).join()` returns, a single-threaded loop
flattens the per-client lists into the `Stream.Builder` the
existing API contract expects.

Failure semantics match the pre-refactor contract: if any client
throws, the wrapping `CompletionException` is unwrapped and the
underlying `RuntimeException` propagates, exactly as before.
Graceful per-client partial-failure surfacing is left to a
follow-up (it would change observable error behaviour and break
the test contracts).

**Stage B — smart-delay progress bar.**
`ChronoGrid` carries a hidden indeterminate `ProgressBar` in the
status row plus a per-UI `ScheduledExecutorService` (single
daemon thread, lazy-init on `attach`, `shutdownNow` on `detach`).

```
PROGRESS_SMART_DELAY_MS = 500
```

On every user-triggered refresh:

1. `scheduleProgressShow()` cancels any pending earlier
   schedule, then schedules a `UI.access` call to set the bar
   visible after the smart-delay window.
2. `refreshAll()` triggers the fetch.
3. The existing `addDatesRenderedListener` calls
   `hideProgressBar()` after rendering completes; that cancels
   the pending show and sets the bar invisible — idempotent, so
   it is safe to call on any rendering event regardless of
   whether a refresh was in flight.

A refresh that completes inside 500 ms never lets the scheduled
show fire — the cancellation arrives first — so the bar visually
never appears. A refresh that takes longer surfaces the bar; the
hide event drops it as soon as data lands. The Vaadin
`UIDetachedException` thrown when the UI is gone before the
schedule fires is silently swallowed.

### Acceptance signals

- ✅ Wall-clock for a multi-server `findInRange` is approximately
  the slowest single client, not the sum (verified via the
  existing fixture-backed multi-client integration tests still
  passing under the parallel implementation).
- ✅ A hanging server no longer freezes the entire fetch — its
  future blocks the join, but the others can complete; failure
  semantics still propagate the offending exception (see
  follow-ups below).
- ✅ Toolbar carries the indeterminate `ProgressBar` with
  id `calendar-toolbar-progress`, initial visibility `false`,
  `PROGRESS_SMART_DELAY_MS = 500` pinned.
- ✅ Fast refresh (local testbench, < 500 ms) never flashes the
  bar — the cancellation outruns the show.
- ✅ Slow refresh (multi-server cold cache) shows the bar; the
  `datesRendered` event hides it cleanly.
- ✅ Logout / route-switch shuts down the per-UI scheduler;
  no thread leak across sessions.

### Risks / open questions

- ✅ **Partial-failure isolation.** Shipped as BACKLOG #10
  (2026-06-23). The new `findInRangeWithStatus` collects per-client
  `CalDavError`s without aborting the refresh; the legacy
  `findInRange` keeps the throw-on-any-failure contract for
  callers (and tests) that depend on it.
- **No per-server streaming arrival.** Entries from all clients
  surface together once `allOf` completes; a future EntryProvider
  rewrite could push per-client results into the grid as each
  future resolves. The Vaadin Stefan FullCalendar add-on's
  EntryProvider API would need verification first.
- **Executor choice.** `ForkJoinPool.commonPool()` is the JVM
  default and right for short bursts of HTTP I/O. A dedicated
  `Executors.newFixedThreadPool(8)` field plus
  session-destroy-listener shutdown was considered and deferred —
  if telemetry shows the common pool contended by long-running
  fetches, switching to a dedicated pool is a one-field-and-
  shutdown-hook change.
- **Smart-delay tuning.** 500 ms is the convention from BUG #10's
  analysis. If user feedback says fast servers still flash the
  bar (race against scheduled task), increase to 750 ms; if slow
  servers feel like the bar appears too late, decrease to 300 ms.
- **Progress granularity.** v1 is indeterminate. A
  determinate-counter ("server 2 of 5 loaded") would need the
  parallel fan-out to notify per-completion — straightforward but
  re-uses the per-server streaming arrival hook above. Deferred
  until that lands.

---

## #10 — Partial-failure isolation for multi-server fan-out

**Status:** ✅ shipped 2026-06-23 (follow-up to BACKLOG #9)
**Filed:** 2026-06-23

### Idea

A single timing-out or 5xx-replying CalDAV server no longer blocks
the rest of the refresh. The grid shows every surviving server's
entries; each failing server produces one toast naming the
affected account, so the user knows exactly which connection
needs attention. The connection badge only flips to
"disconnected" when **every** configured client failed.

### Motivation

BACKLOG #9 shipped parallel fan-out but kept the legacy
all-or-nothing failure contract: any client throwing aborted the
entire refresh. Once Connection Manager (BACKLOG #8) made
multi-server setups routine, that contract started actively
hurting — a single iCloud blip in a 4-account setup wiped four
calendars off the grid at once.

The fix is graceful degradation. The user almost always prefers
"three calendars work, one is dimmed out with an error message"
over "everything is gone, try again." This iteration also
finalises the BACKLOG #9 risks block by implementing the
"partial-failure isolation" follow-up listed there.

### Sketch

**New value type.** `FanOutOutcome` (record, in
`com.svenruppert.chronogrid.service`) carries:

- `List<Entry> entries` — defensively copied, every successful
  client's already-colour-stamped entries flattened in the same
  order the legacy `findInRange` produced;
- `Map<URI, CalDavError> failures` — keyed by failed client's
  `collectionUri()`, with the existing
  `CalDavError(kind, detail, cause)` record as the value.

**Service-side change.** A new
`fanOutWithStatus(op)` reuses the parallel
`CompletableFuture.supplyAsync` architecture but wraps each
per-client future with `.handle((ok, ex) → ...)`. An exception
becomes a `CalDavError` entry on the outcome's failures map
instead of a thrown `CompletionException`. `allOf().join()` then
returns cleanly even when some clients failed.

The public API gains
`findInRangeWithStatus(from, to): FanOutOutcome` and
`findTodosInRangeWithStatus(...)`. The legacy
`findInRange / findTodosInRange` keep their
throw-on-any-failure contract by delegating to
`fanOutWithStatus` and re-throwing the first failure's cause —
preserves every existing test and every existing caller.

**UI-side wiring.** `ChronoGrid.rangeWithStatus` switches to
`findInRangeWithStatus`. On a non-empty failures map:

1. `surfacePartialFailures(outcome)` walks every entry and calls
   `notifyError("Could not reach calendar: {0}" + serverScopeHint)`
   for each — the existing serverScopeHint from BACKLOG #8 Schicht
   4 reuses the `[iCloud]` bracket-suffix convention.
2. Badge update logic compares
   `outcome.failures().size() ≥ totalClients`: only then does
   the badge flip to DISCONNECTED. Otherwise the badge stays
   CONNECTED — the per-failure toasts now own the "this server
   is broken" signal.

### Acceptance signals

- ✅ Two-server setup with one server pointing at a closed port:
  `findInRangeWithStatus` returns 1 failure + the live server's
  entries; `findInRange` (legacy) still throws on the same setup.
- ✅ Connection badge stays CONNECTED while at least one server
  is reachable; only flips to DISCONNECTED when every client
  failed.
- ✅ One `notifyError` toast per failed server, with the bracketed
  server-scope suffix — covered by the new
  `calendar.notify.partialFail` i18n entry in EN+DE.
- ✅ `CalendarServiceResultTest`: mixed-live-and-dead-client test
  surfaces the dead client as a NETWORK kind failure while the
  live client's seeded event still surfaces in the entries list.
- ✅ Backwards compatibility: legacy `findInRange` still throws,
  guarded by an explicit `legacyFindInRangeStillThrowsOnPartialFailure`
  test.

### Risks / open questions

- **Toast spam.** The Vaadin FullCalendar EntryProvider typically
  fires one query per refresh, but month-view prefetching can
  split that into multiple sub-queries. v1 accepts the rare
  duplicate toast rather than introducing per-UI suppression
  state. A short-window de-dup set is a low-cost follow-up if
  telemetry shows it as noisy.
- **Authoritative client count.** The badge's "all clients
  failed" check uses `stateStore.readSubscriptions().size()` as
  the total. That equals the live client count after the BACKLOG
  #8 Schicht 5 auto-migration, but a stale state-store reading
  during a Connection-Manager edit could disagree with what
  `CalendarService.fanOutWithStatus` actually exercised. A
  future iteration could surface the client count on the
  `FanOutOutcome` itself.
- **Per-failure i18n verbosity.** Each toast carries the kind-
  classified detail string from `CalDavErrors.detail(ex)`. For
  very technical exceptions (e.g. SSL handshake failures) the
  text leans engineer-speak. A friendly mapping per
  `CalDavErrors.Kind` is a UX follow-up.
- **Failure-state memory.** A server that briefly fails then
  recovers still produces a one-shot toast on the failing
  refresh; the next successful refresh emits no "back online"
  signal. If users ask for it, a session-scoped
  last-fail-per-server map could drive a "Server X is back
  online" success toast on recovery.
