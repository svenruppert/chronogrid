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
