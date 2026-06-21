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

**Status:** ✅ behoben 2026-06-21 in `246dade` (Bruchstelle B, Render-Pfad) + `b2e4938` (Bruchstelle A, Save-Pfad). Sven-verifiziert: Pick → Save → Reload → App-Neustart → Re-Login behält die gewählte Farbe.
**Filed:** 2026-06-21

> **Cross-Provider-Hinweis (nicht Teil dieses Bugs):** Wird ein
> Termin in der iCloud-eigenen UI editiert, verwirft iCloud beim
> Zurückschreiben die RFC-7986-`COLOR`-Property — die individuelle
> Farbe ist danach weg. iCloud kennt nur Per-Kalender-Farben in
> seinem Datenmodell. Workaround / Folge-Bug separat tracken
> (siehe ggf. BUG #2).

### Analyse

**Update 2026-06-21 nach dem ersten Browser-Smoke-Test:** Bug
besteht aus **zwei** Bruchstellen, die beide gefixt werden müssen.
Der erste Commit (`246dade`) hat nur die Render-seitige Bruchstelle
behoben — die Save-seitige war noch da. Sven hat das beim Smoke-Test
korrekt aufgedeckt: „Farbe ausgewählt, Speichern gedrückt, Dialog
ist weg, aber die Farbe ist gleich geblieben."

**Bruchstelle (A) — Save-Pfad, EventEditorDialog (Zeile 195–201).**

```java
Element colourPicker = new Element("input");
colourPicker.setAttribute("type", "color");
colourPicker.setProperty("value", initialColour);   // server → client
// ❌ kein addEventListener/addPropertyChangeListener
```

Der native HTML5 `<input type="color">` wird über Vaadin's
Element-API erzeugt. Vaadin synct die `value`-Property **nicht
automatisch** vom Client zurück zum Server für native Elemente —
der Code-Pfad existiert nur für „echte" Vaadin-Komponenten mit
expliziter Property-Sync-Annotation oder für Elemente, deren
DOM-Events mit `addEventData(...)` instrumentiert wurden. Folge:
beim Save-Klick liefert `colourPicker.getProperty("value")`
**immer noch den initialen Server-Wert**, nicht die User-
Auswahl. Die User-Auswahl reicht nie über die Browser-Grenze.

Save-Handler liest also dauerhaft `#1f77b4` (Default) oder den
zuletzt gespeicherten Wert, schreibt das brav in
`CUSTOM_ENTRY_COLOR`, iCalendar trägt's, und nach Reload zeigt der
Dialog wieder den selben Wert.

**Fix (A):** dasselbe Pattern wie der N-days-Slider in
`CalendarNavigationBar` — `addEventListener("change", …)` und
`addEventListener("input", …)`, je mit `addEventData(
"event.target.value")`, in einen mutable Holder
(`String[] currentColour`) cachen. Save liest aus dem Holder
statt aus `getProperty(...)`.

**Bruchstelle (B) — Render-Pfad, ChronoGrid.applySubscriptionColor
(behoben in `246dade`).**

`CalendarService.applyColours(entry, calendarColor)` macht es
korrekt: liest `CUSTOM_ENTRY_COLOR`, splittet in
`setBackgroundColor(individualColor)` + `setBorderColor(
calendarColor)`. Aber `ChronoGrid.applySubscriptionColor(...)`
ruft danach in der Fan-Out-Pipeline `entry.setColor(...)`
unbedingt auf und überschreibt damit Fill + Border mit der
Subscription-Farbe.

**Fix (B):** `applySubscriptionOverride` als neue Methode in
`CalendarService` (chronogrid-core), die intern an `applyColours`
delegiert. Bereits eingebaut in `246dade`.

**Zusammenhang der beiden Bruchstellen:** B war versteckt hinter
A. A allein bedeutete: die Farbe kommt nie als
`CUSTOM_ENTRY_COLOR` am Entry an → `applyColours` rennt immer in
den uniform-Branch (`setColor(calendarColor)`) → es gibt keinen
Per-Event-Override, den B zerstören könnte. Erst wenn A gefixt
ist, wird B sichtbar. Hätte B nicht parallel mit A gefixt werden
müssen, wäre der Bug auch nach dem A-Fix nur teilweise behoben
(Fill wäre korrekt, aber sofort vom Subscription-Override wieder
überschrieben).

Beide Fixes zusammen → komplette Behebung. (A) ist im zweiten
Commit auf dem Bug, (B) war im ersten.

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
schuld. Wenn nein → der Save-Pfad selbst ist defekt. Im konkreten
Fall: COLOR fehlte → Save-Pfad (A) defekt.

### Touchpoints

- `chronogrid: ui/EventEditorDialog.java` (Zeile 195 ff.) — **Bug-Stelle (A)**:
  HTML5-Color-Picker ohne Property-Sync. Fix: `addEventListener
  ("change"/"input", …).addEventData("event.target.value")` + mutable
  Holder, der vom Save-Handler gelesen wird.
- `chronogrid: ui/ChronoGrid.java#applySubscriptionColor` (Zeile 782
  alt) — **Bug-Stelle (B)**, gelöscht. Aufruf-Stelle in
  `rangeWithStatus` (Zeile 690) ruft jetzt die zentrale
  `CalendarService.applySubscriptionOverride`.
- `chronogrid-core: service/CalendarService.java#applyColours` /
  `applySubscriptionOverride` (Zeile 225 ff. / 245 ff.) — die
  korrekte Single-Source-of-Truth-Logik. `applySubscriptionOverride`
  ist im selben Commit wie der B-Fix neu hinzugekommen.
- `chronogrid-core: mapping/EntryMapper.java` — `CUSTOM_ENTRY_COLOR`-
  Konstante und VEVENT-COLOR-Roundtrip; ist korrekt, nicht
  angefasst.

### Größe

S+S. Zwei kleine Punkte:
- (A) ~20 Zeilen in `EventEditorDialog` (Event-Listener +
  mutable Holder). Test fehlt — würde Vaadin-UI-Mock brauchen,
  v1 verlässt sich auf den Browser-Smoke-Test.
- (B) 1 Zeile in `ChronoGrid` (Delegation an
  `applySubscriptionOverride`), neue Methode + 4 Unit-Tests in
  `CalendarServiceColoursTest`.

Geschätzt insgesamt ~60 Minuten inkl. Tests + SpotBugs + zwei
Browser-Smoke-Test-Iterationen.

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

---

## #2 — Per-Event-Farbe geht beim Bearbeiten in der iCloud-Native-UI verloren

> **Original:** Wenn ich den Termin in iCloud Editiere, ist die
> rote Farbe weg.
>
> *(Beobachtet beim Browser-Smoke-Test des [[BUG #1]]-Fix:
> in-app-only-Roundtrip funktioniert, aber sobald der Termin in der
> iCloud-Web-UI bearbeitet wird, kommt er ohne unsere COLOR-
> Property zurück.)*

**Status:** 🧪 fertig, Tests laufen — wartet auf Browser-Smoke-Test
**Filed:** 2026-06-21

### Analyse

**Ursache (provider-seitig, nicht in unserem Code):** iCloud's
Datenmodell für Kalender-Einträge kennt nur Per-Kalender-Farben,
keine Per-Event-Farben. Wenn die iCloud-eigene UI (Web oder
native Apple Apps) einen Termin editiert und über CalDAV
zurückschreibt, verwirft sie die RFC-7986-`COLOR`-Property still,
weil sie sie in ihr Datenmodell nicht abbilden kann. Per RFC 5545
ist das nicht spezifikationskonform (Implementations „MUST be
forward compatible" mit unbekannten Properties), aber es ist die
Realität.

**Unser Standard-Schreibpfad** (vor Fix) schrieb die User-Farbe
nur als `COLOR:#ff0000`-Zeile. Nach iCloud-Edit: Property weg,
beim nächsten `findInRange` liest `EntryMapper.toEntry` keinen
COLOR-Wert, `CUSTOM_ENTRY_COLOR` wird nicht gesetzt,
`CalendarService.applyColours` fällt in den Uniform-Branch
(beide Slots = Kalender-Farbe). Ende: User-Pick weg.

**Lösungsidee — RFC-konformer Sidechannel.** RFC 5545 §3.8.8.2
spezifiziert `X-` Properties als den vorgesehenen Mechanismus
für nicht-standardisierte Erweiterungen und verlangt von
Implementierungen, sie über Round-trips zu erhalten — exakt das,
was iCloud bei COLOR nicht tut, aber bei X-Properties durchaus.
Wir schreiben die User-Farbe also doppelt:

- **`COLOR:#ff0000`** — RFC-7986-Standard, primärer Lesepfad.
  Bleibt drin für Forward-Compat und für Provider, die sie
  korrekt round-trippen.
- **`X-CHRONOGRID-COLOR:#ff0000`** — Sidechannel. Bleibt erhalten
  wenn iCloud `COLOR` strippt.

**Reader-Logik:** prefer COLOR (authoritativ wenn vorhanden),
fallback X-CHRONOGRID-COLOR (recovery für iCloud-Edited-Events).
Nach erneutem Save aus unserer App stehen wieder beide drin.

Wenn iCloud irgendwann den COLOR-Strip stoppt: bestehender Code
funktioniert weiter ohne Änderung (COLOR wird einfach öfter
respektiert, X- bleibt als Backup).

### Betroffene Features

- **FEATURE_BACKLOG.md #1** — *Per-event colour with a
  calendar-coloured edge stripe*: Akzeptanz-Signal „User-Pick
  überlebt CalDAV-Round-trip" galt für unsere App allein,
  aber nicht für Cross-Provider-Edit-Pfade. Der Sidechannel
  schließt diese Lücke ohne API-Change am Feature selbst.
- **FEATURE_BACKLOG.md #5** — *Per-day appointment dots in
  popover*: liest seit dem `246dade`-Fix den `CUSTOM_CALENDAR_COLOR`
  statt `entry.getColor()`, ist also unabhängig von der
  Per-Event-Farbe und damit nicht betroffen.

Nicht betroffen: BACKLOG #2, #3, #4, #6 — keine Berührung mit
dem COLOR-Property-Roundtrip.

### Reproduktion

1. App starten, mit iCloud verbinden.
2. Termin öffnen, eigene Farbe (z.B. Rot) wählen, Save.
3. Im Grid: Termin in Rot — passt. (BUG #1 in-app-Roundtrip ist
   bestätigt.)
4. iCloud-Web-UI öffnen (icloud.com), denselben Termin aufrufen,
   z.B. den Titel ändern, in iCloud speichern.
5. Zurück in unserer App: „Neu laden" klicken oder warten bis
   der nächste `findInRange` läuft.
6. **Vor dem Fix:** Termin steht wieder in der Kalender-Default-
   Farbe (Blau), die rote User-Farbe ist weg.

**Erwartetes Verhalten nach dem Fix:**

5'. Im iCalendar-Body nach Save aus unserer App stehen **beide**
    Zeilen drin: `COLOR:#ff0000` und `X-CHRONOGRID-COLOR:#ff0000`.
6'. iCloud strippt `COLOR` beim Edit, aber `X-CHRONOGRID-COLOR`
    bleibt erhalten.
7'. Beim Re-Read in unserer App liest der Reader den
    Sidechannel und stellt die rote Farbe wieder her.

### Touchpoints

- `chronogrid-core: mapping/EntryMapper.java#X_CG_COLOR` (Konstante,
  neu) — Property-Name `X-CHRONOGRID-COLOR`
- `chronogrid-core: mapping/EntryMapper.java#toEntry` (~Zeile 159) —
  Reader: prefer COLOR, fallback X-CHRONOGRID-COLOR
- `chronogrid-core: mapping/EntryMapper.java#toICalendarText`
  (~Zeile 328) — Writer: `vevent.setColor(...)` und
  `vevent.setExperimentalProperty(X_CG_COLOR, ...)` parallel
- `chronogrid-core: test/EntryMapperColourSidechannelTest.java` (neu) —
  5 Tests für Write-beide, Read-prefer-COLOR, Read-fallback,
  Read-keine-Properties, blank-Sidechannel-ignoriert

### Größe

S. Drei kleine Änderungen in `EntryMapper` plus eine neue Test-
Klasse (5 Tests). Geschätzt 30 Minuten inkl. SpotBugs.

### Risiko / offene Fragen

- **Andere Provider könnten COLOR korrekt round-trippen, aber
  X-Properties nicht.** Unwahrscheinlich (X- gilt seit RFC 5545
  von 2009 als Pflicht), aber theoretisch möglich. In dem Fall
  bliebe COLOR erhalten und die App liest sie als primären Pfad —
  also kein Datenverlust, nur kein Sidechannel-Backup. Akzeptabel.
- **Bytes-Bloat im iCalendar-Body.** Jeder colour-Event trägt jetzt
  ~25 zusätzliche Bytes. Bei tausenden Events in einem Kalender:
  einige KB. Vernachlässigbar gegenüber dem typischen Event-
  Footprint (Description, Location, Recurrence-Rules).
- **iCloud könnte X-Properties später ebenfalls strippen.** Wenn
  das je passiert: kein Sidechannel-Backup mehr verfügbar, BUG
  re-opened, evtl. Workaround über CATEGORIES mit
  Sonder-Prefix (Kollisionsrisiko mit BACKLOG #3-Tags). v1
  hofft auf RFC-Konformität bei X-Properties.
- **Andere Konsumenten (Apple Calendar, Thunderbird) könnten
  `X-CHRONOGRID-COLOR` als Müll anzeigen.** Spec sagt: SHOULD
  ignorieren wenn nicht verstanden. Apple Calendar und
  Thunderbird sind beide spec-konform für X-Properties → keine
  sichtbare Auswirkung.
- **Andere Apps schreiben evtl. ihre eigenen X-Properties für
  Farben** (z.B. `X-APPLE-CALENDAR-COLOR`, `X-OUTLOOK-COLOR`).
  Reader v1 liest sie nicht — Folge-Idee für eine v2 wäre eine
  Cross-Property-Fallback-Kette. Nicht für diesen Fix.
