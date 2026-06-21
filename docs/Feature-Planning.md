# Feature-Planning

Kurz-Konzepte für die nächste Welle Features. Pro Eintrag eine Idee +
ein knappes technisches Konzept (~10 Zeilen), nicht die volle Skizze.
Die ausgefeilten Designs leben weiter in
[`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md); was schon **drin** ist
steht in [`FEATURES.md`](FEATURES.md).

Status-Legende: 🟡 geplant · 🔵 designed in BACKLOG · ⚫ blockiert / wartet

---

## #1 — Termin-Indikator pro Tag im „Zum Datum springen"-Popover

> **Original:** Wenn man in dem Selector „zum Datum springen" ist,
> soll man zu jedem Tag sehen, ob dort Termine sind.

**Status:** 🟡 geplant

### Konzept

Im aufgeklappten DatePicker-Popover bekommt jeder Tag, an dem mindestens
ein Termin liegt, einen dezenten visuellen Marker — ein kleiner Punkt
(•) unter der Tageszahl. Maximal drei Punkte nebeneinander stehen für
„Termine aus drei verschiedenen Kalendern", farblich in den
Kalender-Farben. Ein einzelner grauer Punkt heißt „Termine vorhanden,
aber irrelevant aus welchem Kalender" — Fallback wenn der Tag mehr als
drei aktive Sub-Quellen hat.

**Datenpfad:** Beim `opened-changed`-Event des DatePickers (oder via
`MonthsChanged`-Listener wenn vorhanden) wird die sichtbare Range
(typisch ±2 Monate um das gewählte Datum) aus den bereits-gefetchten
Entries des Hauptgrids gezählt. Eine zusätzliche REPORT-Query gegen den
Server vermeidet man — der Hauptgrid hat die Daten ohnehin im
`EntryProvider`-Cache. Pro Tag wird ein `Map<LocalDate, Set<String>>`
aufgebaut (Set der Kalender-Farben).

**Render-Mechanik:** Vaadin 25 DatePicker rendert Tage über das
`vaadin-date-picker-overlay-content`-Shadow-DOM. Über `::part(day)` oder
einen Day-Renderer-Slot CSS-Custom-Properties pro Tag setzen
(`--cg-day-colors: #1F77B4 #FF7F0E`). CSS rendert die Dots via
`::after` mit `linear-gradient` o.ä.

**Touchpoints:**
- `chronogrid: ui/CalendarNavigationBar.java` — Popover-Open-Listener,
  Daten-Aggregation
- `chronogrid: META-INF/resources/frontend/styles/chronogrid.css` — Dot-Renderer
- `chronogrid-core: service/CalendarService.java` — optionale
  `countByDayInRange(...)` falls Cache-Pfad nicht reicht

**Größe:** M. Hängt davon ab ob Vaadin 25's DatePicker einen Day-
Renderer-Hook hat. Falls nein, Shadow-DOM-Patching (nicht-trivial).

**Risiko:** Performance bei langen Ranges (mehrere Jahre rückwärts/
vorwärts gescrollt). Lösung: nur sichtbares Monatsfenster aggregieren,
nicht den ganzen Cache.

---

## #2 — Per-Termin-Farbumrandung visuell deutlicher

> **Original:** Die farbliche Markierung von einem Kalendereintrag mit
> eigener Farbe ist nicht deutlich genug. Die Umrandung ist so dünn,
> das man es kaum feststellen kann.

**Status:** 🟡 geplant — Enhancement zu [`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md)
**#1** (shipped 2026-06-20)

### Konzept

Mit Backlog #1 wurde der Provenance-Indikator via
`entry.setBorderColor(calendarColor)` realisiert — FullCalendar rendert
das als 1-px-Border um die Eintrags-Box. Bei satter Hintergrund-Füllung
(z. B. `#FFAA00` Eintragsfarbe + dünne `#2CA02C` Border) verschluckt das
Auge die 1 px schnell.

**Empfohlener Pfad — linker Streifen statt voller Border** (Google-
Calendar-Look):

Die FullCalendar-`borderColor`-Property wird in der CSS auf `transparent`
zurückgenommen für Einträge mit `CUSTOM_ENTRY_COLOR`; stattdessen wird
ein **6-px-breiter Streifen am linken Rand** gezeichnet, der die
Kalender-Farbe trägt. Statisch in `chronogrid.css`:

```css
.fc-event[data-cg-custom-coloured="true"] {
  border-color: transparent;
  border-left: 6px solid var(--cg-calendar-color, transparent);
}
```

`--cg-calendar-color` und das `data-cg-custom-coloured`-Attribut werden
beim Render gesetzt — entweder via FullCalendar-`entryDidMount`-Hook
(JS-Interop nötig) oder cleaner via Custom-Property auf dem Entry,
gepaart mit einer kleinen `eventClassNames`-Logik.

**Alternative (cheap):** Globale Border-Stärke auf 3 px hochziehen
(`chronogrid.css`: `.fc-event { border-width: 3px; }`). Funktioniert
für alle Einträge, ist aber visuell stumpf — Einträge ohne
CUSTOM_ENTRY_COLOR sehen dann nach „Border-Dekoration" aus statt
„uniform".

**Touchpoints:**
- `chronogrid: ui/ChronoGrid.java` — `entryDidMount`-Hook oder
  `eventClassNames`-Returning-Logik
- `chronogrid: META-INF/resources/frontend/styles/chronogrid.css` —
  neue Streifen-Regel + Width-Adjustments für die kleinen Month-View-
  Events
- `chronogrid-core: service/CalendarService.java` — `applyColours(...)`
  setzt zusätzlich `CUSTOM_CALENDAR_COLOR` (existiert bereits seit
  Backlog #1 — nur konsumieren)

**Größe:** S. Pure UI-Politur über bereits-vorhandene Data-Property.

**Risiko:** Streifenbreite muss in `dayGridMonth` (kompakte
Events) vs. `timeGridDay/Week/nDays` (große Blöcke) anders
proportioniert sein, sonst wirkt es im Month-View wuchtig.

---

## #3 — Tags pro Eintrag + Cross-Kalender-Tag-Filter

> **Original:** Ein Kalendereintrag soll eine Liste von Tags erhalten.
> So kann man nach Tags filtern in der Kalenderansicht. Das soll über
> verschiedene Kalender hinweg funktionieren, damit man in
> verschiedenen Kalendern alle Termine eines Tags herausfiltern kann
> für eine Übersicht.

**Status:** 🟡 geplant — größerer Eingriff (L)

### Konzept

Jeder Eintrag bekommt eine Liste freier String-Tags („work",
„kunde-acme", „deep-focus", …). Die Toolbar bekommt eine Multi-Select-
Combobox aller bisher gesehenen Tags; ein oder mehrere ausgewählt
filtert die sichtbaren Entries Grid-weit. Funktioniert über **alle
aktiven Subscriptions hinweg** — der Nutzer sieht z. B. alle „kunde-
acme"-Termine über Privat- + Arbeits- + Familienkalender hinweg.

**Datenformat:** Wir nutzen das iCalendar-Standardproperty `CATEGORIES`
(RFC 5545 §3.8.1.2) — kommaseparierte Liste von Strings, ist
round-trippable über alle gängigen CalDAV-Server (iCloud, Nextcloud,
Radicale, Baïkal). Mapping läuft analog zu LOCATION/URL/COLOR durch
`EntryMapper`: neue `CUSTOM_CATEGORIES`-Konstante, vevent.getCategories()
liest, vevent.setCategories(...) schreibt.

**Filter-Architektur:**

| Layer | Was passiert |
|---|---|
| `CalendarStateStore` | Neues Pärchen `readTagFilter()` / `writeTagFilter(Set<String>)` für Persistenz über Navigation |
| `CalendarService.fanOut` | Optionaler `Predicate<Entry>`-Filter — Entries die kein Tag aus dem Filter-Set haben fliegen raus |
| `ChronoGrid` UI | Tag-ComboBox in der Toolbar, persisted state → fanOut-Predicate |
| `EventEditorDialog` | Tag-Input mit Auto-Suggest aus dem bisherigen Tag-Universum + Free-Text-Add |

**Tag-Universum sammeln:** Beim ersten Fan-out-Pass wird ein
`Set<String>` aller gesehenen Tags aufgebaut und auf der ChronoGrid-
Instanz gehalten. Combobox sourct sich daraus.

**Touchpoints:**
- `chronogrid-core: mapping/EntryMapper.java` — `CUSTOM_CATEGORIES`,
  read+write
- `chronogrid-core: service/CalendarService.java` — Filter-Parameter
  in `findInRange*`
- `chronogrid-core: state/CalendarStateStore.java` — Tag-Filter-
  Persistence-Pärchen + Default-Impl-Update in
  `VaadinSessionCalendarStateStore`
- `chronogrid: ui/EventEditorDialog.java` — Tag-Input + Auto-Suggest
- `chronogrid: ui/ChronoGrid.java` — Toolbar-Filter-ComboBox + Wiring

**Größe:** L. Multi-Layer, neuer State-Store-Key (breaking — major
Bump?), neue Toolbar-Stelle, neue Edit-Dialog-Sektion.

**Risiko / offene Fragen:**
- **Kollidierende Tag-Schreibweisen** (`work` vs. `Work` vs. `WORK`).
  Vorschlag: case-insensitive Match, beim Speichern wird die zuerst-
  gesehene Schreibweise normalisiert.
- **Skalierung**: Bei >50 Tags wird die Combobox unübersichtlich —
  Vaadin's Search-im-Dropdown sollte reichen.
- **Persistenz-Bruch**: Das neue `tagFilter`-Pärchen am Store ist eine
  API-Erweiterung der `CalendarStateStore`-Schnittstelle → bricht
  externe Impls. Major-Bump auf v02.00.00 erwägen.

---

## #6 — Selektierten Tag beim View-Wechsel beibehalten

> **Original:** wenn man auf eine andere Ansichtsbasis schaltet z.B.
> Woche auf N Tage, dann soll der gerade selektierte Tag immer noch
> aktiv sein.

**Status:** 🟡 geplant

### Konzept

Aktuell ist der View-Wechsel via Tab-Switcher
(`CalendarNavigationBar.viewTabs`) ein „blinder" Sprung — FullCalendar
behält intern zwar einen *visible range start*, aber der Tag, den der
Nutzer **gerade fokussiert** (durch DatePicker-Jump, Page/Slide-
Navigation oder Klick auf einen Eintrag), geht beim View-Wechsel
verloren. Wer in der Woche vom 2026-09-15 ist, dann auf „N Tage"
umschaltet, landet typischerweise nicht in einem Fenster das den
15. September enthält — sondern springt zurück auf den heutigen
Wochen-/N-Tages-Start.

**Fokal-Tag-Konzept einführen:** ChronoGrid hält intern einen
`LocalDate focalDay` (Default: heute beim Mount, danach getrackt aus
DatePicker-Jumps und FullCalendar's `getCurrentIntervalStart()` nach
Slide/Page-Navigation). Der View-Switch-Listener läuft jetzt:

1. `calendar.changeView(newMode)` ausführen
2. `calendar.gotoDate(focalDay)` direkt danach

FullCalendar rendert die neue View so, dass der Fokal-Tag **im
sichtbaren Fenster liegt** (in Week-View die enthaltende Woche, in
N-days-View ein N-Fenster mit dem Tag drin, in Month-View der
enthaltende Monat).

**Fokal-Tag-Update-Punkte:**

| Trigger | Was passiert |
|---|---|
| DatePicker `valueChange` (Zeile 154–159) | `focalDay = e.getValue()` |
| Slide ± / Page ± Buttons | `focalDay = calendar.getCurrentIntervalStart()` (nach dem Move) |
| Today-Button | `focalDay = LocalDate.now()` |
| Initial mount | `focalDay = initialDate` (= `LocalDate.now()`) |

**Persistenz:** Optional — `focalDay` kann zusätzlich auf den
`CalendarStateStore` (analog zu `NDays`) wandern, damit die Session-
Wiederherstellung den letzten Fokus reproduziert. Klein-S-Erweiterung
zum gleichen Code-Touch — empfohlen als „Bonus".

**Touchpoints:**
- `chronogrid: ui/CalendarNavigationBar.java` — `focalDay`-Feld +
  Update-Calls in den vier Listener-Pfaden (DatePicker, Slide ±,
  Today, View-Tab-Switch)
- `chronogrid: ui/ChronoGrid.java` — falls Fokal-Tag persistiert wird:
  Read/Write gegen den Store
- `chronogrid-core: state/CalendarStateStore.java` (optional) — neues
  `readFocalDay(LocalDate fallback)` / `writeFocalDay(LocalDate)`
  Pärchen + Default-Impl-Update in `VaadinSessionCalendarStateStore`

**Größe:** S. Ohne Persistenz pure UI-Patch (1–2 Stunden); mit
Persistenz +30 Minuten und ein kleiner State-Store-API-Eingriff
(analog zu wie NDays heute persistiert wird, also gleicher Pattern).

**Komplementär mit #5:** Wenn der Fokal-Tag = heute ist und View-
Switch zu Month, sieht der Nutzer den hervorgehobenen Today-Cell aus
#5 sofort — beide Features verstärken den „heute"-Ankerpunkt im UI.

**Risiko / offene Fragen:**
- **Fokal-Tag vs. interval-start**: Bei Slide-Navigation (eine Spalte
  weiter) ist „was der Nutzer fokussiert" nicht zwingend
  `getCurrentIntervalStart()` — eher die *aktuell sichtbare Mitte*.
  Vereinfachung für v1: `focalDay = intervalStart` reicht; verfeinern
  falls Nutzer-Feedback es einfordert.
- **N-days mit ungeradem N**: Bei N=7, fokalDay am Donnerstag —
  FullCalendar's `gotoDate(donnerstag)` zentriert NICHT automatisch.
  Standard-Verhalten: Donnerstag wird Spalte 1 von 7, danach Fr–Mi
  folgen. Akzeptabel? Falls zentrieren gewünscht ist: vor
  `gotoDate` eine eigene `start = focalDay.minusDays(n/2)` Rechnung.
  Vorschlag für v1: zentrieren NICHT, FullCalendar-Default
  beibehalten (vorhersehbarer).
- **Month-View Wechsel zurück zu Day**: Klick auf einen anderen Tag in
  der Month-View löst aktuell keinen Fokal-Update aus (kein Event
  Handler). Falls erwünscht: separater Day-Klick-Listener in der
  Month-View, der `focalDay` setzt. Aus dem Original-Wortlaut nicht
  zwingend ableitbar — als Folgeticket markieren falls Bedarf.