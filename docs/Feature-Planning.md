# Feature-Planning

Kurz-Konzepte für die nächste Welle Features. Pro Eintrag eine Idee +
ein knappes technisches Konzept (~10 Zeilen), nicht die volle Skizze.
Die ausgefeilten Designs (geschiffte Features) leben weiter in
[`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md); ein Überblick über die
schon implementierte Funktionalität steht in
[`FEATURES.md`](FEATURES.md).

## Lifecycle

Diese Datei ist die **„in flight"-Pipeline**: enthält ausschließlich
Features, die noch nicht in einem terminalen Zustand sind. Aus
Planning gibt es genau zwei Ausgänge:

```
                        ┌─►  FEATURE_BACKLOG.md   (geschiffft, EN-GB)
Feature-Planning.md  ───┤
                        └─►  Features-Skipped.md  (verworfen, DE)
```

- **Geschiffft** → Eintrag wandert (übersetzt ins britische
  Englisch, im shipped-Schema) in den
  [`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md) und wird *hier*
  gelöscht.
- **Verworfen** → Eintrag wandert in den
  [`Features-Skipped.md`](Features-Skipped.md) mit Datum +
  expliziter Begründung und wird *hier* ebenfalls gelöscht.

In beiden Fällen bleibt die Nummerierung in Planning stabil
(Lücken statt Kompaktierung), so dass Commit-Messages und Notizen
ihre Referenzen behalten. Die drei Dateien Planning + Backlog +
Skipped zusammen sind erschöpfend.

## Status-Vokabular

Das `**Status:**`-Feld eines Eintrags ist die **laufende
Fortschrittsanzeige** für dieses Feature und MUSS bei jedem
nennenswerten Übergang aktualisiert werden — sobald die Arbeit
beginnt, am Ende jeder Implementierungsschicht, beim Erreichen eines
Blockers, beim Übergang in die Test-Phase. Der Status soll mit einem
schnellen Blick verraten, was aktuell läuft, was wartet, und was
mittendrin ist.

| Marker | Bedeutung | Wann setzen |
|---|---|---|
| 🟡 `geplant` | Konzept steht, Implementierung hat nicht begonnen | Default für frisch ausgearbeitete Einträge |
| 🔧 `in Arbeit — <Phase>` | Implementierung läuft; `<Phase>` ist eine ein-Satz-Markierung wie „EntryMapper-Layer", „UI-Wiring", „Tests schreiben" — damit eine nächste Sitzung mitten in der Arbeit weiterlaufen kann | Sobald der erste Commit zur Feature geht, dann bei jedem Layer-/Phasenwechsel |
| 🧪 `fertig, Tests laufen` | Code-Änderungen komplett, Validierung läuft (Unit / Integration / SpotBugs / Reaktor) | Zwischen letzter Code-Änderung und Ship-Commit |
| 🚧 `blockiert: <Grund>` | Arbeit pausiert, externer / interner Grund ist Pflicht — sonst ist nicht klar, was den Eintrag entblockt | Immer wenn eine externe Abhängigkeit oder offene Frage die Arbeit pausiert |
| ⚫ `verworfen — <Begründung>` | Feature wird nicht (mehr) gebaut | Eintrag wandert in [`Features-Skipped.md`](Features-Skipped.md) mit Datum + Begründung und wird hier gelöscht. **NICHT** in den Backlog (der ist nur geschiffft) |
| 🔵 `designed in BACKLOG` | Spezifikation lebt im BACKLOG als bereits geschiffftes Vorgängerfeature | Selten — nur wenn ein Planning-Eintrag eine Verfeinerung eines BACKLOG-Eintrags ist |

Für nicht-triviale Features, die sich über mehrere Commits ziehen,
kann unter `### Konzept` zusätzlich eine `### Fortschritt`-Sektion
mit einer Checkbox-Liste der Layer angelegt werden. Diese
Subsektion wird beim Übergang in den Backlog entfernt — die
Commit-Bodies und der Backlog-Eintrag halten dieselbe Information.

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