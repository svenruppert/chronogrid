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
