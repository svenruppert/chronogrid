# BUGS

Bug-Pipeline analog zu [`Feature-Planning.md`](Feature-Planning.md):
Sven hängt neue Bugs unten als `## BUG - …` an, sie bekommen eine
Nummer in der laufenden Sequenz und werden mit dem Schema unten
ausgearbeitet. Pflichtfeld bei jeder Analyse: die **Betroffenen
Features** mit Cross-Link in den
[`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md) oder
[`Feature-Planning.md`](Feature-Planning.md).

Status-Legende:

| Marker | Bedeutung |
|---|---|
| 🟡 `erfasst` | Neu, noch nicht analysiert |
| 🔬 `analysiert` | Ursache + Fix-Pfad klar, Implementierung wartet |
| 🔧 `in Arbeit — <Phase>` | Fix wird gerade implementiert |
| 🧪 `fertig, Tests laufen` | Code-Änderung komplett, Validierung läuft |
| ✅ `behoben YYYY-MM-DD in <commit>` | Bug ist behoben und geschiffft |
| 🚧 `blockiert: <Grund>` | Pausiert mit Pflicht-Begründung |
| ⚫ `verworfen — <Begründung>` | Wird nicht behoben (kein Bug, by design, zu teuer) |

Lifecycle: behobene und verworfene Bugs **bleiben in dieser Datei**
als historischer Record mit Commit- bzw. Begründungs-Verweis.

---

## #1 — Per-Event-Farbe wird nicht übernommen, der Eintrag bleibt in der Kalender-Farbe

> **Original:** Wenn man eine Farbe für einen Kalendereintrag
> aussucht, wired dieser nicht übernomen. In der Kalenderview wird
> er auch immer noch in der selben Farbe angezeigt.

**Status:** 🧪 fertig, Tests laufen — wartet auf Browser-Smoke-Test
**Filed:** 2026-06-21

### Analyse

Es gibt zwei Code-Pfade, die die Entry-Farbe setzen, und der
zweite hebelt den ersten aus:

1. **Korrekter Pfad** in `CalendarService.applyColours(Entry,
   calendarColor)` (chronogrid-core, Zeile 225):

   ```java
   String individualColor = entry.getCustomProperty(
       EntryMapper.CUSTOM_ENTRY_COLOR);
   if (individualColor != null && !individualColor.isBlank()) {
     entry.setBackgroundColor(individualColor);
     entry.setBorderColor(calendarColor);
   } else {
     entry.setColor(calendarColor);
   }
   ```

   Liest die nutzergewählte Farbe aus `CUSTOM_ENTRY_COLOR` und
   splittet sauber in Fill (eigene Farbe) + Border (Kalender-
   Farbe) — exakt das, was BACKLOG #1 spezifiziert.

2. **Brechender Pfad** in `ChronoGrid.applySubscriptionColor(
   Entry, Map<URI,String>)` (chronogrid, Zeile 782):

   ```java
   for (Map.Entry<URI, String> e : colors.entrySet()) {
     if (src.startsWith(e.getKey().toString())) {
       entry.setColor(e.getValue());   // ← überschreibt ALLES
       return;
     }
   }
   ```

   Wird in der Fan-Out-Pipeline von `rangeWithStatus` (Zeile 690)
   **nach** `applyColours` aufgerufen, um die Per-Subscription-Farb-
   wahl des Nutzers über die CalDAV-Server-seitige Farbe zu legen.
   Ruft jedoch `entry.setColor(...)` (FullCalendar's "set both
   border and background to this colour") — und kippt damit den
   gerade frisch gesetzten Per-Event-Background. Sowohl Fill als
   auch Border tragen ab diesem Moment die Subscription-Farbe.

**Wo der korrekte Save-Pfad endet:** Im `EventEditorDialog`
(chronogrid, Zeilen 226–231) wird die Farbe ordnungsgemäß in
`CUSTOM_ENTRY_COLOR` geschrieben; `EntryMapper.toICalendarText`
schreibt sie als VEVENT-COLOR-Property in die iCalendar-Daten;
beim nächsten `findInRange` liest `EntryMapper.toEntry` sie wieder
in `CUSTOM_ENTRY_COLOR` zurück. **Bis hierhin ist alles korrekt.**
Der Wert geht erst beim Render-Pre-Processing in der ChronoGrid-
Stage verloren.

**Fix-Pfad:** `ChronoGrid.applySubscriptionColor` muss statt
`entry.setColor(subColour)` zu rufen an `CalendarService.applyColours(
entry, subColour)` delegieren. Damit bleibt die per-Event-Layering-
Logik in einer einzigen Code-Stelle, und die Subscription-Farbe
gewinnt korrekt nur über die Kalender-Default-Farbe — nicht über
die nutzergewählte Per-Event-Farbe.

### Betroffene Features

- **FEATURE_BACKLOG.md #1** — *Per-event colour with a
  calendar-coloured edge stripe*: das eigentliche Feature, das hier
  ausgehebelt wird. Die Acceptance-Signals des Eintrags
  (`backgroundColor` = eigene Farbe, `borderColor` = Kalender)
  treffen nach dem Subscription-Override nicht mehr zu.
- **FEATURE_BACKLOG.md #4** — *Stronger event borders for visible
  provenance*: hängt direkt von #1 ab. Der 3 px / 4 px breite
  Border, den #4 dicker rendert, soll die Kalender-Provenienz
  zeigen — der Sinn entfällt, wenn der Border zur selben Farbe wie
  der Fill kollabiert. Ohne #1-Fix ist #4 reine Pixel-Verschwendung.

Nicht direkt betroffen, aber verwandt:

- **FEATURE_BACKLOG.md #5** — *Per-day appointment dots inside the
  date-picker popover*: liest `entry.getColor()` für die Dot-Farbe.
  Nach dem `applySubscriptionColor`-Override kommt dort die
  Subscription-Farbe an (statt einer möglichen Per-Event-Farbe).
  Aus #5-Perspektive ist das tolerierbar — die Dots signalisieren
  „welcher Kalender", nicht „welcher Termin" — aber wenn der
  Fix landet, sollten die Dots ebenfalls die Subscription-Farbe
  weiter zeigen (also nicht die individuelle Event-Farbe ziehen).
  Sicherzustellen: dort weiterhin den `CUSTOM_CALENDAR_COLOR`-
  Wert verwenden, der durch `applyColours` immer gesetzt ist.

### Reproduktion

1. App starten, mit dem iCloud-Test-Kalender verbinden.
2. Einen vorhandenen Termin öffnen (Doppelklick) oder neuen
   anlegen.
3. „Use custom colour" anhaken, im HTML5-Color-Picker eine
   deutlich andere Farbe wählen als die Kalender-Farbe (z.B. Rot,
   wenn der Kalender blau ist).
4. „Save" klicken.
5. Dialog schließt, im Grid bleibt der Termin **in der Kalender-
   Farbe** (Blau), nicht in der nutzergewählten Farbe (Rot).
6. Auch nach Reload + Refresh: gleiche Beobachtung.

**Erwartetes Verhalten** (laut BACKLOG #1):
- Fill der Event-Box: Rot (nutzergewählt)
- Border der Event-Box: Blau (Kalender-Farbe), 3 px breit (BACKLOG #4)

**Tatsächliches Verhalten:**
- Fill: Blau (Kalender-Farbe)
- Border: Blau (gleiche Farbe wie Fill → optisch unsichtbar)

**Verifikation der Datenseite:** Im DevTools / Server-Log nach Save
prüfen, dass die iCalendar-Daten die COLOR-Property tragen
(`COLOR:#ff0000` o.ä.). Wenn ja → Daten-Pfad ok, Render-Pfad ist
schuld. Wenn nein → der Save-Pfad selbst ist defekt.

### Touchpoints

- `chronogrid: ui/ChronoGrid.java#applySubscriptionColor` (Zeile
  782–794) — **primäre Bug-Stelle**, delegiert nicht an
  `CalendarService.applyColours`
- `chronogrid: ui/ChronoGrid.java#rangeWithStatus` (Zeile 690) —
  Aufrufstelle der broken `applySubscriptionColor`
- `chronogrid-core: service/CalendarService.java#applyColours`
  (Zeile 225–236) — der korrekte Pfad, an den delegiert werden muss
- `chronogrid-core: mapping/EntryMapper.java` — `CUSTOM_ENTRY_COLOR`-
  Konstante und VEVENT-COLOR-Roundtrip (zur Verifikation, nicht
  Bug-Stelle)
- `chronogrid: ui/EventEditorDialog.java` (Zeile 226–231) — Save-
  Pfad mit Color-Picker; sollte unverändert bleiben

### Größe

S. Eine Zeile in `applySubscriptionColor` ersetzen
(`entry.setColor(e.getValue())` → `CalendarService.applyColours(
entry, e.getValue())`) plus ein Test, der den Per-Event-vs-
Subscription-Override-Pfad explizit absichert. Geschätzt 30–60
Minuten inkl. Tests + SpotBugs + manueller Verifikation.

### Risiko / offene Fragen

- **Reihenfolge der Stream-Operationen.** Aktuell läuft erst
  `applyColours` in `CalendarService.fanOut`, dann
  `applySubscriptionColor` in `ChronoGrid.rangeWithStatus`. Wenn
  Letztere an `applyColours` delegiert, wird die Methode zweimal
  pro Entry gerufen. Das ist idempotent (lesendes Custom-Property,
  schreibendes Fill+Border), aber unnötig. Mögliche Optimierung:
  `CalendarService.fanOut` so anpassen, dass die Subscription-
  Override schon dort passieren kann — oder die initiale
  `applyColours` aus dem Fan-Out streichen, da die Subscription-
  Override sie sowieso ersetzt. **v1-Empfehlung:** doppelte Calls
  hinnehmen (kosten ~0), Optimierung als Folgeticket.
- **Bestehende Entries ohne `CUSTOM_ENTRY_COLOR`.** `applyColours`
  fällt in dem Fall auf `entry.setColor(calendarColor)` zurück —
  das matched 1:1 das aktuelle Verhalten der broken Methode.
  Keine Regression.
- **Test-Coverage.** `CalendarServiceTest` deckt `applyColours`
  bereits ab (Backlog #1 hat dafür gesorgt). Der neue Test soll die
  Integration in der `ChronoGrid.rangeWithStatus`-Pipeline
  absichern — am ehesten ein Browserless-Test, der eine
  CalendarSubscription mit Custom-Color + einen Entry mit
  Custom-Color durch den Stream-Pfad schickt und prüft, dass
  `getBackgroundColor` und `getBorderColor` getrennt sind.
- **Eigene-Farbe-zurücknehmen-Pfad.** Wenn der Nutzer „Use custom
  colour" ausschaltet, wird `CUSTOM_ENTRY_COLOR` auf `null`
  gesetzt (siehe EventEditorDialog Zeile 243). Nach dem Fix sollte
  `applyColours` bei `null` korrekt auf `setColor(calendarColor)`
  zurückfallen und Border-Color implizit auf den selben Wert
  setzen — ist genau das, was die Methode tut. Kein Sonderpfad
  nötig.
