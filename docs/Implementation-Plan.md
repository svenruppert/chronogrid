# Implementierungsplan — Pipeline aus `Feature-Planning.md`

Reihenfolge + Bündelung der sechs offenen Features aus
[`Feature-Planning.md`](Feature-Planning.md). Ziel: jede Iteration
liefert eine kohärente Verbesserung, lässt 289+ Tests grün, SpotBugs
sauber, und produziert genau einen Commit der ein Feature komplett
abschließt (inkl. Tests + Doku-Move nach
[`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md)).

Pro Feature gilt nach Abschluss (Memory `feature-lifecycle-planning-
to-backlog`):

1. Eintrag aus `Feature-Planning.md` löschen
2. In `FEATURE_BACKLOG.md` neu anlegen — British English,
   Shipped-Schema, Status-Zeile mit Commit-Hash
3. `FEATURES.md` ggf. ergänzen (User-visible Features)

---

## Übersicht

| # | Feature | Größe | Risiko | Abhängigkeiten | Iteration |
|---|---|:---:|:---:|---|:---:|
| #5 | Heute-Tag im Kalender-Grid | S | niedrig | — | **1** |
| #4 | Heute-Tag im DatePicker-Popover | S | mittel (Vaadin 25 Shadow-DOM-API) | — | **1** |
| #2 | Per-Termin-Farbumrandung deutlicher | S | niedrig | Backlog #1 (shipped) | **2** |
| #6 | Selektierten Tag beim View-Wechsel beibehalten | S | niedrig | — | **2** |
| #1 | Termin-Indikator pro Tag im Popover | M | mittel (Shadow-DOM + Aggregations-Pipeline) | #4 (gleicher Touchpoint) | **3** |
| #3 | Tags pro Eintrag + Cross-Kalender-Filter | L | hoch (State-Store-API-Bruch, evtl. Major-Bump) | — | **4** |

Gesamt: 4 Iterationen, ~6–10 Implementations-Stunden + Verifikation +
Commits.

---

## Iteration 1 — „Heute"-Anker etablieren (#5 + #4)

**Warum gebündelt:** Beide Features touchen denselben CSS-File
(`chronogrid.css`), beide nutzen dieselbe Lumo-Primary-Vokabular
(`--lumo-primary-color`, `--lumo-primary-color-10pct`), beide
verstärken denselben Mental-Model-Anker („wo bin ich heute?"). In
einer Iteration kommt das visuelle System sauber raus statt in zwei
inkonsistenten Halbschritten.

### Reihenfolge in der Iteration

**Schritt 1.1 — #5 (Kalender-Grid Today-Cell):**

- Datei: `chronogrid/src/main/resources/META-INF/resources/frontend/styles/chronogrid.css`
- Vier neue CSS-Regeln am Ende des Files (NACH den Zebra-Regeln, damit
  Selektor-Spezifität korrekt auflöst):
  - `.fc .fc-timegrid-col.fc-day-today` — TimeGrid-Spalte
  - `.fc .fc-col-header-cell.fc-day-today` — Header-Akzent
  - `.fc .fc-daygrid-day.fc-day-today` — Month-Zelle
  - `.fc .fc-daygrid-day.fc-day-today .fc-daygrid-day-number` —
    Zahl-Akzent in Month
- Code aus Planning #5 § „Empfohlener Pfad" 1:1 übernehmen

**Schritt 1.2 — #4 (DatePicker-Popover Today-Highlight):**

- Datei: `chronogrid/src/main/resources/META-INF/resources/frontend/styles/chronogrid.css`
- Eine neue CSS-Regel: `::part(today)` mit Outline (Pfad A aus
  Planning #4)
- **Verifikation experimentell**: Browser-DevTools an dem Popover
  öffnen, im Shadow-DOM den exakten Part-Namen prüfen. Wenn
  `part="today"` nicht greift, Fallback auf Pfad B (Today-Button im
  Popover wieder aktivieren, NavigationBar-Today entfernen)
- Falls Pfad A greift: ein Browserless-Test der das Today-Element im
  DOM findet und behauptet dass eine sichtbare Klasse vorhanden ist

**Verifikation pro Schritt:**

```bash
./mvnw -DskipTests install                # builds the CSS into the bundle
./start-caldav-dev-server.sh              # Terminal 1
./start-vaadin-demo.sh                    # Terminal 2
# Browser → http://localhost:8080 → login → /calendar
# Visual checks:
#   - heutige Spalte/Zelle ist sichtbar getönt im Week + Month
#   - DatePicker-Popover öffnen, heutiger Tag hat Outline
#   - Dark-Theme prüfen (theme switcher)
```

**Tests:**

- Bestehende 289 Tests bleiben grün (CSS-Änderungen brechen sie
  nicht)
- Browserless: `ChronoGridBrowserlessTest` Assertion dass DOM-Element
  mit Class `fc-day-today` existiert nach Mount

**Commit-Botschaft:** „feat(ui): visible today anchor in calendar
grid and date-picker popover (#5 + #4)"

**Pro Iteration nach Implementation:**

- Beide Einträge aus `Feature-Planning.md` löschen
- Neue BACKLOG-Einträge in British English:
  - BACKLOG #2 — „Visible today anchor in the main calendar grid"
  - BACKLOG #3 — „Today highlight in the date-picker popover"
- `FEATURES.md` § 4 (Vaadin component) ergänzen: „Today highlight
  across grid and popover"

**Geschätzter Aufwand:** 1–2 h (~30 Min #5 + ~45 Min #4 + Verifikation
in beiden Themes + Tests + Commit).

---

## Iteration 2 — Visuelle Politur (#2 + #6)

**Warum gebündelt:** #2 ist UI-Politur, #6 ist UI-State-Politur.
Beide haben S-Größe, beide treffen das `ChronoGrid` UI-Modul,
keiner braucht Cross-Layer-Eingriffe. Eine Iteration, zwei
Verbesserungen.

### Reihenfolge in der Iteration

**Schritt 2.1 — #2 (Streifen-Look für CUSTOM_ENTRY_COLOR):**

- Dateien:
  - `chronogrid/src/main/java/com/svenruppert/chronogrid/ui/ChronoGrid.java`
    — neue `eventClassNames`-Logik oder `entryDidMount`-Hook setzt
    `data-cg-custom-coloured="true"` und CSS-Custom-Property
    `--cg-calendar-color` auf den gerenderten Eintrag, wenn
    `CUSTOM_ENTRY_COLOR` gesetzt ist
  - `chronogrid/src/main/resources/META-INF/resources/frontend/styles/chronogrid.css`
    — neue Regel `.fc-event[data-cg-custom-coloured="true"]` mit
    `border-left: 6px solid var(--cg-calendar-color, transparent)`
    und `border-color: transparent`
- CSS-Snippet aus Planning #2 1:1 übernehmen
- Falls `entryDidMount`-Callback in FullCalendar2 Java-API exponiert
  ist: bevorzugt. Sonst über `eventClassNames` + `data-`-Attribut
  via DOM-API gehen.

**Schritt 2.2 — #6 (Fokal-Tag bei View-Wechsel):**

- Datei:
  `chronogrid/src/main/java/com/svenruppert/chronogrid/ui/CalendarNavigationBar.java`
- Neues Feld `private LocalDate focalDay = LocalDate.now();`
- Update-Calls in den vier Listener-Pfaden:
  1. DatePicker-`valueChangeListener` (heute Zeile 154–159):
     `if (e.getValue() != null) { focalDay = e.getValue(); ... }`
  2. Slide ± / Page ± Listener: nach Move
     `focalDay = calendar.getCurrentIntervalStart().toLocalDate()`
  3. Today-Button-Listener: `focalDay = LocalDate.now()`
  4. View-Tab-Selected-Change-Listener: nach `changeView(newMode)`
     **direkt** `calendar.gotoDate(focalDay)` aufrufen
- Persistenz (optional, +30 Min): neues
  `CalendarStateStore.readFocalDay(LocalDate fallback)` /
  `writeFocalDay(LocalDate)` Pärchen + Default-Impl. Halbe Stunde,
  hohe Symmetrie zum vorhandenen NDays-Persistenz-Pattern.

**Verifikation:**

```bash
./mvnw test                                # 289+ Tests grün
./mvnw -DskipTests install
./start-caldav-dev-server.sh
./start-vaadin-demo.sh
# Browser → manuell:
#   - Termin mit eigener Farbe anlegen → Streifen am linken Rand sichtbar?
#   - DatePicker auf 2026-09-15 springen, in Day-View
#   - Auf Week-View wechseln → das Datum 2026-09-15 muss in der
#     sichtbaren Woche enthalten sein (nicht zur aktuellen Woche springen)
#   - Analog Week → N-Tage → Month
```

**Tests:**

- Browserless: `CalendarNavigationBar`-Test der `focalDay` über
  View-Wechsel hinweg prüft (mock DatePicker-Wert setzen, View
  switchen, sichtbares Intervall enthält den Wert)
- Bestehende Tests grün

**Commit-Botschaft:** „feat(ui): event-colour stripe + focal-day
preservation across view switches (#2 + #6)"

**Pro Iteration nach Implementation:**

- Beide Einträge aus `Feature-Planning.md` löschen
- Neue BACKLOG-Einträge:
  - BACKLOG #4 — „Calendar-coloured edge stripe for custom-coloured
    events" (Enhancement zu BACKLOG #1)
  - BACKLOG #5 — „Preserve focal day across view-mode switches"
- `FEATURES.md` § 4 ergänzen

**Geschätzter Aufwand:** 2–3 h (~1 h #2 + ~1 h #6 + Persistenz + Tests
+ Commit).

---

## Iteration 3 — DatePicker-Popover Termin-Indikatoren (#1)

**Standalone-Iteration:** M-Größe, Shadow-DOM-Verifikation nötig,
Aggregations-Pipeline gegen den EntryProvider-Cache. Sollte nicht
mit anderen Features gebündelt werden, weil der Vaadin-25-Shadow-DOM-
Pfad das Hauptrisiko ist und volle Aufmerksamkeit braucht.

### Vorbereitung

**Spike vorab:**

1. Browser-DevTools im DatePicker-Popover öffnen, das Shadow-DOM
   inspecten: Welche `::part(...)` Namen gibt es auf den einzelnen
   Day-Cells? Gibt es einen Day-Renderer-Slot (in Vaadin 25.1.1
   prüfen: API-Doc + `vaadin-date-picker-overlay-content`-Web-
   Component-Source)?
2. Falls Day-Renderer-Slot vorhanden: Java-API-Path. Falls nicht:
   CSS-Custom-Property-Injection via Shadow-DOM-Walk (Element-API).

### Schritte

**Schritt 3.1 — Aggregation-Pipeline:**

- Datei:
  `chronogrid/src/main/java/com/svenruppert/chronogrid/ui/ChronoGrid.java`
- Neue Methode `Map<LocalDate, Set<String>> dayColourMap()` —
  durchläuft den aktuellen EntryProvider-Cache und sammelt pro Tag
  ein Set der Kalender-Farben (`CUSTOM_CALENDAR_COLOR` aus Backlog #1)
- Wird beim DatePicker-`opened-changed`-Event aufgerufen, nur sichtbarer
  Monat ±2 Monate

**Schritt 3.2 — Renderer:**

Pfad-abhängig nach Spike:
- **Pfad A** (Day-Renderer-Slot): pro Day-Cell die Farben als Custom-
  Property `--cg-day-colors` setzen, CSS rendert Dots via `::after`
- **Pfad B** (Shadow-DOM-Injection): Vaadin Element-API auf den
  DatePicker, Shadow-DOM-Walk zum Overlay, pro Day-Cell Custom-
  Property setzen. Fragiler bei Vaadin-Updates.

**Schritt 3.3 — CSS:**

- Neue Regel in `chronogrid.css`:
  ```css
  vaadin-date-picker-overlay-content::part(day)::after {
    content: '';
    display: block;
    height: 4px;
    background: var(--cg-day-colors, transparent);
    border-radius: 2px;
    margin: 1px auto 0;
  }
  ```
  (Exakte Selektor-Form hängt vom Pfad ab)

**Verifikation:**

- Manuell: DatePicker öffnen → Tage mit Terminen tragen Punkte;
  Tage ohne Termine sind clean
- Multi-Kalender: zwei aktive Subscriptions, ein Tag in beiden →
  zwei Dots (oder ein zweifarbiges Element)
- Performance: bei langer Range scrollen → keine spürbare Verzögerung

**Tests:**

- Unit-Test der `dayColourMap`-Logik (gegen synthetische Entry-
  Sammlung)
- Browserless (falls Shadow-DOM erreichbar): Assertion dass der
  passende `--cg-day-colors`-Wert auf der richtigen Day-Cell sitzt

**Commit-Botschaft:** „feat(ui): per-day event indicators in
date-picker popover (#1)"

**Pro Iteration nach Implementation:**

- Eintrag aus `Feature-Planning.md` löschen
- BACKLOG #6 — „Per-day event indicators in the date-picker popover"
- `FEATURES.md` ergänzen

**Geschätzter Aufwand:** 3–5 h (Spike + Implementation + Tests +
Verifikation in beiden Themes).

**Risiko-Mitigation:** Wenn der Spike zeigt dass Vaadin 25 keinen
sauberen Renderer-Hook bietet, **defer** auf Vaadin 25.2 / 26 statt
Shadow-DOM-Walk in Production zu shippen. Dann Planning #1 mit Status
⚫ blockiert markieren.

---

## Iteration 4 — Tags + Cross-Kalender-Filter (#3)

**Standalone-Iteration:** L-Größe, Multi-Layer, breaking-Risk auf
`CalendarStateStore`-Interface. Eigene Release-Linie — vermutlich
v02.00.00 Major-Bump.

### Vorbereitung

**Open Decisions vorab:**

1. **State-Store-Änderung breaking oder additiv?** Wenn als
   `default`-Methode auf dem Interface implementiert: additiv, kein
   Major-Bump nötig. Wenn nicht: Major-Bump.
2. **Tag-Normalisierung**: case-insensitive (Vorschlag aus Planning
   #3) — entscheiden ob das Default ist oder konfigurierbar.
3. **Tag-Persistenz im Filter**: nur Session (analog NDays) oder auch
   beim Logout erhalten (eigenes Persistenz-Layer)?

### Schritte

**Schritt 4.1 — Headless Layer (chronogrid-core):**

- `mapping/EntryMapper.java`:
  - Neue Konstante `CUSTOM_CATEGORIES = "caldavCategories"`
  - Read: `vevent.getCategories()` → komma-joinen → Custom-Property
  - Write: Custom-Property → Komma-splitten →
    `vevent.setCategories(...)`
- `service/CalendarService.java`:
  - `findInRange(...)` / `findInRangeAsResult(...)` optionaler
    Predicate-Parameter
- `state/CalendarStateStore.java`:
  - Neue Default-Methoden `Set<String> readTagFilter()` und
    `void writeTagFilter(Set<String>)` mit no-op Default
- `state/VaadinSessionCalendarStateStore.java`:
  - Konkrete Impl der Tag-Filter-Methoden gegen SESSION_KEY

**Schritt 4.2 — Tests headless:**

- `EntryMapperTest` — CATEGORIES round-trip
- `CalendarServiceColoursTest` — Predicate-Filter
- `CalendarStateStoreTest` — Tag-Filter-Pärchen Roundtrip
- `VaadinSessionCalendarStateStoreTest` — neuer Session-Key

**Schritt 4.3 — UI Layer (chronogrid):**

- `ui/EventEditorDialog.java`: neue Tag-Input-Sektion mit Auto-
  Suggest (ComboBox<String>-mit-AllowCustomValue) — Tag-Universum
  als Konstruktor-Parameter (`Set<String> knownTags`)
- `ui/ChronoGrid.java`:
  - Neues Tag-Universum-Set, aufgefüllt beim ersten Fan-out
  - Toolbar-Filter-ComboBox zwischen den existierenden Buttons
  - Filter-Predicate an `CalendarService.findInRange(..., predicate)`
    durchreichen
  - Tag-Combobox aus dem Universum sourcen + auf StateStore
    persistieren

**Schritt 4.4 — i18n:**

- `calendar.field.tags`, `calendar.toolbar.tagFilter`,
  `calendar.tagFilter.placeholder` + EN/DE

**Verifikation:**

- Manuell: Termin mit Tags „work, kunde-acme" anlegen, in iCloud
  speichern, am iCloud-Web prüfen ob die Categories da sind, in
  ChronoGrid neu laden → Tags persistiert
- Multi-Kalender + Tag-Filter: zwei Subscriptions, in beiden je ein
  „kunde-acme"-Tag, Toolbar-Filter setzen → nur „kunde-acme"-Termine
  bleiben sichtbar, aus beiden Kalendern

**Tests:**

- Voller Reactor-Lauf
- Optional Browserless: Tag-Filter setzen + assertEquals(Anzahl
  sichtbarer Entries)

**Commit-Botschaft:** „feat(tags): per-event tags + cross-calendar
tag filter (#3)"

**Pro Iteration nach Implementation:**

- Eintrag aus `Feature-Planning.md` löschen
- BACKLOG #7 — „Per-event tags + cross-calendar tag filter"
- `FEATURES.md` § 2 (iCalendar mapping) ergänzen + § 4 (Vaadin
  component) ergänzen

**Major-Bump prüfen:** Wenn die State-Store-Änderung als Default-
Methode realisierbar ist (keine breaking Interface-Change), bleibt
v01.x.y. Sonst v02.00.00 ziehen + Tag tag-release-style ankündigen.

**Geschätzter Aufwand:** 6–10 h (verteilbar über 2–3 Sessions).

---

## Cross-cutting Konzerne

### Test-Strategie

- **Pro Iteration:** voller Reactor-Test (`./mvnw test`) + SpotBugs
  auf allen drei Modulen + manuelles Smoke-Testing der visuellen
  Änderungen in beiden Lumo-Themes
- **Baseline:** 289 Tests grün heute; jede Iteration darf nach unten
  nur reduzieren wenn Tests bewusst gelöscht werden (z. B. weil ein
  veraltetes Verhalten ersetzt wird)
- **Browserless-Tests** bevorzugen wo praktisch — `getComputedStyle`
  geht nicht headless, aber Klassen-Existenz + DOM-Elementhierarchie
  schon

### Commit-Hygiene

- Pro Iteration **ein** Feature-Commit (Code + Tests + i18n in einem
  Commit)
- Anschließend **ein** Doku-Commit (Move von Planning → BACKLOG +
  FEATURES.md-Update)
- Standing Rule: keine Claude-Attribution, kein `.idea/` committen

### Sequenz-Regel

Iteration 1 vor 2 vor 3 vor 4 — und zwar weil:

- **Iter 1 baut die „heute"-Anker-Vokabular** das #5 und #4
  konsistent etabliert. Wenn später #1 (popover-day-indicators)
  hinzukommt, sitzt der Today-Highlight schon → Indikatoren überlagern
  sich sauber.
- **Iter 2 entlastet die offene UI-Schuld** aus Backlog #1
  (Border-Stärke) bevor #1 weitere Optik im selben Popover hinzufügt.
- **Iter 3** profitiert von beiden vorigen Iterationen — Today ist
  markiert, Streifen sind klar.
- **Iter 4** ist orthogonal zu UI-Anker-Polish und kann später
  rauchen ohne dass die State-Store-API-Frage frühere Iterationen
  blockiert.

### Versionierungs-Strategie

- Iter 1 + 2 + 3: kein API-Bruch → `01.00.01-SNAPSHOT` → kann als
  `v01.01.00` releaset werden wenn alle drei drin sind
- Iter 4: potenzieller API-Bruch → eigener Release-Zyklus, ggf.
  `v02.00.00`

### Was beim Start jeder Iteration nochmal geprüft wird

- Memory-Index aktuell? (FEATURES.md, FEATURE_BACKLOG.md, Feature-
  Planning.md)
- Aktuelle Tests grün? (`./mvnw test` baseline)
- Originstand vor Push? (`git log origin/main..HEAD`)

---

## Offene Entscheidungen vor Iter 1

- **#4 Pfad-A vs. Pfad-B:** Erst Browser-DevTools-Spike laufen lassen
  ob `::part(today)` an Vaadin 25.1.1 DatePicker greift. Falls nein,
  Pfad B (Today-Button-Restore) ziehen — bricht die etablierte
  „one-Today-Affordance"-UX-Entscheidung, aber liefert sicheres
  Highlighting.
- **#6 Persistenz ja/nein:** Trivial-Erweiterung +30 Min, würde aber
  einen weiteren State-Store-Key einführen — kann auch in Iter 4
  zusammen mit dem Tag-Filter-Key kommen. Empfehlung: **ja**, jetzt
  mit-machen (low cost, hohe Konsistenz mit NDays).

---

## Was nach Iter 4 als nächstes auftaucht

Aus `FEATURES.md` § 7 „What's NOT yet in the box":

- Bearer / OAuth2 Token-Flow (Google CalDAV)
- MKCALENDAR-Support
- RRULE Client-side Expansion
- Cross-Calendar Drag (Event von Work → Family ziehen)
- Vaadin-freies `chronogrid-core` (DTO-Layer-Split)
- Maven-Central-Deploy-Ceremony

Davon ist **Bearer/OAuth2** der natürliche Public-Demand-Kandidat
(Google CalDAV ist häufiger Wunsch), und **Maven Central Deploy**
der natürliche Release-Polish-Kandidat. Beide kein Pipeline-Block,
beide planbar nach Iter 4 wenn sich Demand zeigt.
