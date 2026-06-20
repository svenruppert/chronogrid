# Feature Backlog (Post-Freeze)

Sammelstelle für Feature-Ideen, die **außerhalb** des aktuellen Feature-Freeze
liegen (s. Memory `project-feature-freeze`, eingefroren am 2026-06-16 für den
Blog-Post). Sobald der Freeze aufgehoben ist, werden Einträge hier zur
Implementierung gezogen.

Jeder Eintrag folgt dem Schema:

- **Idee** — was soll passieren
- **Motivation** — warum lohnt sich das
- **Skizze** — grober technischer Plan (kein Lock-in)
- **Akzeptanzsignale** — woran erkennt man Fertig
- **Risiken / offene Fragen**

---

## #1 — Pro-Termin-Farbe mit Kalender-Farbstreifen am Rand

**Status:** ✅ shipped 2026-06-20 in `456c886` (see commit body for the implementation map)
**Aufgenommen:** 2026-06-17

### Idee

Jeder einzelne Kalendereintrag soll **eine eigene Farbe** bekommen können
(unabhängig vom Default seines Kalenders). Damit beim gemeinsamen Anzeigen
mehrerer Kalender trotzdem auf einen Blick erkennbar bleibt, **zu welchem
Kalender** ein Termin gehört, trägt jeder Eintrag zusätzlich einen schmalen
**Farbstreifen am Rand** (z. B. linker Border), der **in der Farbe des
zugehörigen Kalenders** gehalten ist.

Visuell ungefähr:

```
┌─┬────────────────────────────────┐
│█│ 10:00  Team-Standup            │   ← Eintragsfüllung = individuelle Farbe
│█│ Room A                          │   ← Streifen links = Farbe des Kalenders
└─┴────────────────────────────────┘
```

### Motivation

Heute übernimmt jeder Eintrag automatisch die Farbe seines Kalenders
(`CalendarService.fanOut` → `entry.setColor(color)` als Fallback). Wer
gleichzeitig mehrere Kalender anzeigt, hat dadurch eine klare
Kalender-Zuordnung — verliert aber jede Möglichkeit, einzelne Termine
visuell hervorzuheben („wichtig" / „privat" / „verschoben"). Mit
Pro-Termin-Farbe + Kalender-Streifen bekommt der Nutzer **beides**:

- semantische Eintragsfarbe (privater Wunsch)
- Kalender-Provenance (Orientierungshilfe in der Multi-Kalender-Ansicht)

### Skizze

**Datenmodell**

- iCalendar: VEVENT-Property `COLOR` (RFC 7986). Wenn vorhanden, gilt sie
  als individuelle Eintragsfarbe; sonst fällt der Eintrag auf die
  Kalender-Default-Farbe zurück.
- `EntryMapper`:
  - **Lesen**: `vevent.getColor()` → `entry.setColor(...)` direkt aus der
    iCal-Quelle übernehmen (bisher wird nur per Subscription-Color
    gefüllt).
  - **Schreiben**: bei Speichern aus dem Editor, wenn der Nutzer eine
    Farbe gewählt hat, `vevent.setColor(value)` setzen.

**Service**

- `CalendarService.fanOut(...)` muss zwischen „Eintrag hat eigene Farbe"
  und „Eintrag erbt Kalender-Farbe" unterscheiden:
  - Custom-Property `caldavEntryColor` markiert eine Quelle „Termin hat
    eigene Farbe gesetzt"; nur diese überschreibt die Fallback-Farbe.
  - Die Kalender-Farbe wandert zusätzlich in eine zweite Custom-Property
    `caldavCalendarColor`, damit die View den Streifen rendern kann —
    unabhängig davon, ob `entry.getColor()` durch eine individuelle Farbe
    überschrieben wurde.

**UI / Rendering**

- `EventEditorDialog` bekommt einen HTML5-`<input type="color">` (analog
  zu `SubscriptionsDialog`) plus einen „Reset auf Kalenderfarbe"-Button.
- Render-Streifen: FullCalendar-`entryDidMount`-Callback **oder** ein
  CSS-only Ansatz via Custom-Property:
  - Inline-Hook am gerenderten Eintragselement: `--entry-stripe-color`
    aus `caldavCalendarColor`.
  - Statische CSS-Regel in `styles/chronogrid.css`:
    ```css
    .fc-event {
      border-left: 4px solid var(--entry-stripe-color, transparent);
    }
    ```
  - Eintragsfarbe (Fill) bleibt FullCalendar-managed über
    `entry.setColor(...)`.

**Tests**

- `EntryMapperTest`: Round-trip einer VEVENT mit `COLOR:#FFAA00`.
- `CalendarServiceResultTest`: Eintrag ohne `COLOR` erbt Kalender-Farbe;
  Eintrag mit `COLOR` behält individuelle Farbe.
- Browserless: Eintrag-Element trägt `--entry-stripe-color`, dessen Wert
  der Kalender-Farbe entspricht.

### Akzeptanzsignale

- ✅ In der Multi-Kalender-Ansicht: zwei Einträge mit **gleicher**
  individueller Farbe, aber aus **unterschiedlichen** Kalendern, sind
  über den unterschiedlich farbigen Streifen weiterhin unterscheidbar.
- ✅ Im Editor: der Nutzer kann eine eigene Farbe wählen **und** auf
  Kalender-Default zurücksetzen.
- ✅ Schreibvorgang persistiert `COLOR` in iCalendar; Re-Read liefert
  dieselbe Farbe.
- ✅ Bestehende Termine ohne `COLOR` zeigen weiterhin die
  Kalender-Default-Farbe und einen Streifen derselben Farbe (kein
  visueller Bruch).

### Risiken / offene Fragen

- **CalDAV-Server-Support für `COLOR`**: iCloud, Radicale, Nextcloud
  haben unterschiedliche Akzeptanz. Bei abweisenden Servern ist ein
  Fallback nötig (Custom X-Property statt RFC 7986?).
- **Kontrast bei dunkler/heller Eintragsfarbe**: Textfarbe muss
  automatisch lesbar bleiben (`color: white` vs. `color: black` je nach
  Luminanz). Existierende Logik in FullCalendar prüfen oder eigene
  Luminanz-Funktion in CSS via `color-mix` / `lab()`.
- **Streifenbreite konsistent halten** über `dayGridMonth` (kompakte
  Events) und `timeGridDay/Week/nDays` (große Blöcke). Evtl. Variante
  je View-Klasse.
- **Default-Farbpalette**: Wenn der Nutzer im Editor eine Farbe wählt,
  schöne Standard-Swatches anbieten (Material? Tailwind? Lumo-Tones?) —
  oder nur freier Color-Picker?
