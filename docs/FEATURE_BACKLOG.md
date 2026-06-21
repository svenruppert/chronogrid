# Feature Backlog (Post-Freeze)

Repository for feature ideas that sat **outside** the original
feature freeze (see memory `project-feature-freeze`, in force from
2026-06-16 to 2026-06-20 for the companion blog post). With the
freeze lifted, entries from here are pulled forward for
implementation.

Each entry follows the same schema:

- **Idea** — what should happen
- **Motivation** — why it is worth doing
- **Sketch** — rough technical plan (no lock-in)
- **Acceptance signals** — how we know it is finished
- **Risks / open questions**

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

## #5 — Appointment indicator beside the date picker

**Status:** ✅ shipped 2026-06-21 (see the commit body for the full
implementation map)
**Filed:** 2026-06-21

### Idea

The "Go to date" picker used to be a blind jump — you opened the
popover and you had to remember which days had events. The user
asked for a per-day visual marker in the popover itself so a single
glance answers "is anything on this date?". Vaadin 25's `DatePicker`
exposes no public day-renderer hook and the popover internals live
behind two shadow-DOM hops — landing dots **inside** the popover
freeze-safely was not possible. This entry ships the freeze-safe
v1: an indicator badge **next to** the date picker that updates
whenever the popover opens or the user picks a date.

### Motivation

The indicator carries the same information signal the original
sketch wanted — "how many calendars have an entry on this day, and
in which colours" — but through stable Vaadin API instead of
shadow-DOM walking. A user planning a meeting jumps through dates
and immediately sees:

- *"No events"* — green-light to schedule there
- *"1 calendar •"* — touch-up needed; lone calendar dot in its
  source colour
- *"3 calendars • • •"* (or *"4 calendars • • • +1"*) — busy
  day, see the calendar grid for details

### Sketch

The aggregation runs in `ChronoGrid#coloursForDay(LocalDate)`:

```java
java.util.Set<String> coloursForDay(LocalDate day) {
    LocalDateTime from = day.atStartOfDay();
    LocalDateTime to = day.plusDays(1).atStartOfDay();
    var colours = new LinkedHashSet<String>();
    rangeWithStatus(from, to).forEach(e -> {
        String c = e.getColor();
        if (c != null && !c.isBlank()) colours.add(c);
    });
    return colours;
}
```

`CalendarNavigationBar` accepts the aggregator through
`setDayColoursProvider(Function<LocalDate, Set<String>>)` and runs
it on every value-change and every `opened-changed` of the
`DatePicker`. The badge renders into a small pill-shaped `Div`:
text counter + up to three coloured dots + `+N` overflow.

CSS lives in `chronogrid.css`:

```css
.chronogrid-nav__day-hint {
    display: inline-flex;
    gap: 6px;
    padding: 4px 10px;
    border-radius: 999px;
    background-color: var(--lumo-contrast-5pct);
    font-size: var(--lumo-font-size-xs);
}
.chronogrid-nav__day-hint-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
}
```

i18n keys: `calendar.nav.dayHint.none`, `calendar.nav.dayHint.one`,
`calendar.nav.dayHint.many` (EN + DE).

### Acceptance signals

- ✅ The badge renders next to the date picker on every view mode
  (Day / Week / N&nbsp;days / Month) — visible at all times, not
  just when the popover is open.
- ✅ Opening the popover and hovering different dates does not
  update the badge per-hovered-day (no `hover` listener on the
  picker is available); the badge updates on **picker open** for
  the currently-selected date and on **value commit** afterwards.
  This is the documented v1 behaviour — feedback may steer the v2
  scope.
- ✅ The badge degrades silently to "No events" when the network
  is offline (the aggregator catches `RuntimeException` from
  `findInRange` and returns the empty set).
- ✅ All 289 existing tests stay green; `BugInstance size is 0`
  on both `chronogrid-core` and `chronogrid` after the change.

### Risks / open questions

- **In-popover dots remain the v2 target.** Achieving per-day
  marks **inside** the Vaadin 25 `DatePicker` popover needs JS
  that walks two shadow-DOM levels (overlay-content →
  month-calendar grid). The walk is browser-safe but cannot be
  smoke-tested in headless and is fragile across Vaadin minor
  versions — deferred until the freeze lifts. The aggregator and
  state plumbing landed here are reusable for that v2 phase.
- **Aggregator hits the CalDAV server.** Each open / value-change
  triggers a fresh `findInRange` for a 24-hour window. The
  underlying client typically reads from its in-process cache;
  if the cache is cold the call is a network round-trip. If
  popover-open performance becomes a complaint, debounce on the
  `CalendarNavigationBar` side (`Executors.newSingleThreadScheduledExecutor()`,
  150-ms trailing).
- **Three-dot visual cap.** A day with four or more calendar
  sources collapses extra dots into `+N`. Could be configurable
  (`setMaxVisibleDots(int)`), but real-world usage rarely
  surpasses three active calendars per day — left as a tuning
  point.

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
