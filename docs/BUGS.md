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

## Übersicht

Schneller Überblick — Snapshot der `**Status:**`-Zeilen aus den
einzelnen Einträgen unten. Bei jedem Status-Wechsel **diese
Tabelle parallel aktualisieren**.

| # | Titel | Status | Commit / Notiz |
|---|---|---|---|
| #1 | Per-Event-Farbe wird nicht übernommen | ✅ behoben | `246dade` + `b2e4938` |
| #2 | Per-Event-Farbe geht beim iCloud-Edit verloren | ✅ behoben | `7ac73ca` |
| #3 | Datumsselektor friert bei Klick auf Tag mit Farbbalken ein | ✅ behoben | `e4302f4` |
| #4 | Multi-Kalender + Reload: alle Termine verschwinden | ✅ behoben | `d0abe01` |
| #5 | Per-Event-Farbe nicht sichtbar bei timed Events | ✅ behoben (iCloud-verifiziert; Nextcloud-Roundtrip → #12) | `d25e377` |
| #6 | Verbindungsmanagement-UX ungenügend | 🟡 erfasst — UX, evtl. besser als Feature-Planning-Eintrag | — |
| #7 | DESCRIPTION-Marker nur bei iCloud, COLOR bei anderen | ✅ behoben | `d25e377` |
| #8 | Abonnieren/De-Abonnieren von Kalendern muss einfacher werden | 🟡 erfasst — eng verwandt mit #6, evtl. zusammenfassen | — |
| #9 | Notifikationen passen nicht zum Mehrverbindungs-Konzept | 🔬 analysiert | — |
| #10 | Fetch über mehrere Verbindungen parallel/async + Fortschrittsbalken | 🔬 analysiert | — |
| #11 | Neuer Nextcloud-Termin erscheint dort als AllDay trotz gesetzter Uhrzeit | 🧪 fertig, Tests laufen — wartet auf Browser-Smoke-Test | (pending commit) |
| #12 | Per-Event-Farbe auf Nextcloud: Reader-Hex + UI-Refresh + Writer snap-to-nearest CSS3 named | ✅ behoben | `40a9b7b` + `f28d694` + `c12bb38` |

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

**Status:** ✅ behoben 2026-06-21 in `7ac73ca` (Hybrid: RFC-COLOR + DESCRIPTION-Marker + lokaler Per-UID-Store). Sven-verifiziert: Farbe bleibt nach iCloud-UI-Edit erhalten. Die zwei vorigen Anläufe (`0bd1243` X-Sidechannel, `bb35954` Diagnose-Lauf) sind in der Analyse als Iterationsgeschichte offen dokumentiert.
**Filed:** 2026-06-21

### Analyse

**Iterationsverlauf (drei Anläufe an demselben Tag):**

**Versuch 1 (`0bd1243`) — `X-CHRONOGRID-COLOR`-Sidechannel als
zusätzliche X-Property.** Hypothese basierte auf RFC 5545 §3.8.8.2,
das vorschreibt: Implementierungen MUST preserve unknown X-
properties. Empirisch widerlegt — iCloud strippt eigene
X-Properties beim User-Edit-Rewrite identisch wie `COLOR`.

**Diagnose-Lauf (uncommitted).** Diagnostic-Logger in
`EntryMapper.toEntry` dumpt COLOR + X-CG-Status + alle X-
Properties. Empirische Belege aus Svens iCloud-Test:

```
[BUG#2 DIAG] uid=D58ABB6F-... (unedittiert)
   COLOR=absent X-CG=absent other-X=[X-APPLE-TRAVEL-ADVISORY-BEHAVIOR=AUTOMATIC]
[BUG#2 DIAG] uid=4b3b9c54-... (nach iCloud-Edit)
   COLOR=absent X-CG=absent other-X=[]
```

Selbst Apples **eigene** X-Property (`X-APPLE-TRAVEL-ADVISORY-…`)
ist nach dem iCloud-Edit weg. Apple-Doku bestätigt nachträglich:
> „Apple filtert nicht alle X-Properties, sondern behält eigene
> wie `X-APPLE-STRUCTURED-LOCATION` oder `X-APPLE-TRAVEL-ADVISORY`.
> Eigene Erweiterungen werden jedoch fast immer gelöscht."

X-Properties sind tot. Apples Whitelist akzeptiert nur eigene.

**Versuch 2 (dieser Commit) — Hybrid: COLOR + DESCRIPTION-
Suffix-Marker + lokaler Per-UID-Store.** Apples eigene Doku nennt
DESCRIPTION als „Feld, das bei UI-Edits unangetastet bleibt". Wir
hängen einen diskreten Marker ans Ende der DESCRIPTION:

```
<Nutzer-Notiztext>

[chronogrid-color: #ff0000]
```

`EntryMapper`:
- **Reader-Kette:** RFC-7986-`COLOR` → DESCRIPTION-Marker (Apple-
  resilient) → null (= Fallback aus dem lokalen Store, macht
  `ChronoGrid`).
- **Writer:** schreibt `COLOR` PLUS appendet den Marker an die
  DESCRIPTION (canonical format mit Leerzeilen-Separator).
- **Round-trip-Idempotenz:** Reader strippt den Marker bevor
  `entry.setDescription()` den UI-sichtbaren Wert setzt; Writer
  re-appendet beim nächsten Save.

`CalendarStateStore`:
- **Neuer Per-UID-Store** (`readEntryColour` / `writeEntryColour` /
  `clearEntryColour`). Default-Impl no-op, `VaadinSessionCalendar
  StateStore` persistiert in einer `Map<String,String>` als
  Session-Attribut `calendar.entryColours`.
- **Garantierte Auffangschicht** — auch wenn der User den
  Marker in iCloud aus der DESCRIPTION löscht, hat unsere App
  noch die Farbe im lokalen Store.

`ChronoGrid.rangeWithStatus`:
- **Overlay-Peek** vor der Subscription-Override-Stage: wenn
  `CUSTOM_ENTRY_COLOR` weder von COLOR noch vom Marker gesetzt
  wurde, schlägt der Store nach und re-runt `applyColours`.
- **Save-Mirror** in `persistSave`: spiegelt die User-Pick-Farbe
  bzw. ein Clear in den Store. Delete-Pfad räumt die Map auf.

**Resilienz-Matrix:**

| Pfad | RFC-COLOR | DESC-Marker | Local-Store | Ergebnis |
|---|---|---|---|---|
| In-App-only Round-trip | ✓ | ✓ | ✓ | Farbe |
| Nach iCloud-Edit (Description unberührt) | ✗ | ✓ | ✓ | Farbe (über Marker) |
| Nach iCloud-Edit + User löscht Description | ✗ | ✗ | ✓ | Farbe (über Store) |
| Anderer Browser/Installation (gleiche iCloud) | ✓ | ✓ | ✗ | Farbe |
| Anderer Browser/Installation, nach iCloud-Edit | ✗ | ✓ | ✗ | Farbe (über Marker) |
| Anderer Browser, iCloud-Edit + Marker gelöscht | ✗ | ✗ | ✗ | Default-Farbe (akzeptiert, sehr seltener Pfad) |

Nur die letzte Zeile ist der unrettbare Pfad — und auch dort
verliert man nur die Per-Event-Farbe, die Kalender-Quell-Farbe
bleibt. Pragmatisch akzeptabel.

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

5'. Im iCalendar-Body nach Save aus unserer App steht
    `COLOR:#ff0000` UND am Ende der DESCRIPTION der Marker
    `[chronogrid-color: #ff0000]`.
6'. iCloud strippt `COLOR` beim Edit, behält die DESCRIPTION
    verbatim → Marker bleibt drin.
7'. Beim Re-Read in unserer App liest der Reader den Marker und
    stellt die rote Farbe wieder her, strippt den Marker
    gleichzeitig aus der UI-sichtbaren DESCRIPTION.
8'. **Zusätzlich:** lokaler Per-UID-Store als Auffangschicht falls
    der User den Marker beim iCloud-Edit aus der Description
    entfernt — dann zieht die Farbe aus dem Store.

### Touchpoints

- `chronogrid-core: mapping/EntryMapper.java#COLOUR_MARKER_PREFIX`
  + `COLOUR_MARKER_PATTERN` — Marker-Konstante + forgiving regex
- `chronogrid-core: mapping/EntryMapper.java#toEntry` — Reader:
  Parser strippt Marker aus DESCRIPTION + setzt `CUSTOM_ENTRY_COLOR`
  als COLOR-Fallback
- `chronogrid-core: mapping/EntryMapper.java#toICalendarText` —
  Writer: schreibt `COLOR` + ruft `composeDescription` um den
  Marker anzuhängen
- `chronogrid-core: mapping/EntryMapper.java#composeDescription`
  (neu, public) — Canonical-Format-Helper
- `chronogrid-core: state/CalendarStateStore.java` — neue
  Default-Methoden `readEntryColour` / `writeEntryColour` /
  `clearEntryColour` (no-op defaults für externe Implementierer)
- `chronogrid: state/VaadinSessionCalendarStateStore.java` —
  Override mit Session-Attribut `calendar.entryColours`
- `chronogrid: ui/ChronoGrid.java#rangeWithStatus` — Overlay-Peek
  liest Store falls COLOR + Marker beide fehlen
- `chronogrid: ui/ChronoGrid.java#persistSave` — Mirror-Write in
  den Store auf jedem Save
- `chronogrid: ui/ChronoGrid.java#confirmAndDelete` — Cleanup im
  Store auf Delete
- `chronogrid-core: test/EntryMapperColourSidechannelTest.java`
  (komplett neu geschrieben) — 10 Tests: Write-COLOR-und-Marker,
  Description-Format, Marker-standalone, Read-prefer-COLOR,
  Read-fallback-Marker, Marker-only-strip, kein-Marker,
  composeDescription-Roundtrip, ohne-Farbe, Write-Read-Roundtrip

### Größe

M. Multi-Layer-Change (Core-Mapper + State-Store-Interface + UI-
Wiring + 10 neue Tests). Geschätzt 90 Minuten inkl. Tests +
SpotBugs + Diagnose-Lauf, plus dritten Browser-Smoke-Test.

### Risiko / offene Fragen

- **User editiert den Marker per Hand aus der Beschreibung.** Dann
  verliert er die Farbe — außer der lokale Store hat sie noch.
  Wenn auch der Store leer ist (z.B. zweiter Browser, Session
  abgelaufen), ist die Farbe verloren. Akzeptable Kosten für
  eine verlorene Edge-Case-Optimierung.
- **DESCRIPTION-Marker sichtbar in iCloud-UI.** Der User sieht
  `[chronogrid-color: #ff0000]` als letzte Zeile seiner Notiz.
  Diskret aber nicht versteckt. Falls Feedback dass es stört:
  v2 könnte Zero-Width-Space-Umrandung versuchen (höchst
  fragil) oder per Mini-CSS in unserer App ausblenden (klappt
  nur in unserer App, nicht in iCloud).
- **Lokaler Store ist Vaadin-Session-scoped.** Verfällt mit der
  Session. Bei langlaufendem Use Case (Persistenz über Sessions
  hinweg): in `AppStoragePaths` als JSON-Datei persistieren —
  Folge-Erweiterung, hier nicht im Scope.
- **Andere Apps zeigen `[chronogrid-color: …]` als Müll.** Acceptable
  — Apple Calendar / Thunderbird zeigen den Marker als Teil der
  Notiz, was er ja technisch auch ist. Die Per-Event-Farbe
  bleibt eine chronogrid-spezifische Funktionalität, die in
  fremden Clients keine Wirkung hat.
- **Performance: Regex auf jeder DESCRIPTION pro Read.** Trivial
  (regex ist kurz, anchored am Ende, fast immer Cache-Miss bei
  Events ohne Marker).

---

## #3 — Datumsselektor friert ein wenn ein Tag mit Farbbalken angeklickt wird

> **Original:** Wenn ich einen Tag in den Datumsselektor anklicke
> der einen bunten Balken hat, friert die Anwendung ein und ich
> muss ein Pagereload machen.

**Status:** ✅ behoben 2026-06-21 in `e4302f4` (MutationObserver attributeFilter + Re-Entrancy-Guard). Sven-verifiziert: Klick auf farbigen Tag schließt das Popover, App reagiert wieder.
**Filed:** 2026-06-21

### Analyse

Hochwahrscheinlich ein **MutationObserver-Endlosloop** im
Popover-Walker den ich für Feature #5 (Per-day appointment dots)
geschrieben habe.

Code-Stelle: `CalendarNavigationBar.POPOVER_WALKER_JS` Zeile 600:

```js
o.observe(node.shadowRoot, {
    subtree: true,
    childList: true,
    attributes: true,        // ← ungefilterter Attribute-Listener
    characterData: true
});
```

Klick-Sequenz die zum Freeze führt:

1. User klickt Tag mit Balken im Popover-Calendar.
2. Vaadin's `<vaadin-month-calendar>` markiert die Zelle als
   selected/focused → ändert `part`-Attribut der Zelle.
3. Mein MutationObserver feuert (lauscht auf JEDE
   Attribute-Änderung im Subtree).
4. `paintAll()` läuft → walks all cells → ruft `paint(c)` auf
   jeder Zelle → `paint(c)` setzt selber wieder Attribute
   (`cell.setAttribute('data-cg-events', …)`) und
   inline-styles (`cell.style.setProperty('--cg-day-bg', …)`).
5. Das Setzen unserer Attribute triggert wieder den
   MutationObserver.
6. **Endlosschleife im JS-Thread** → Browser eingefroren → nur
   noch Page-Reload hilft.

**Fix-Pfad:**

(A) `attributeFilter: ['part']` setzen — der Observer feuert nur
noch wenn Vaadin das `part`-Attribut (z.B. selected/focused)
ändert. Unsere `data-cg-events` Schreibvorgänge ignoriert er.

(B) Zusätzlich Re-Entrancy-Guard: vor `paintAll()` den Observer
mit `obs.disconnect()` pausieren, nach Abschluss mit
`obs.observe(...)` wieder anhängen. Doppelte Sicherung gegen
zukünftige Selektor-Erweiterungen.

(C) Selbst-Attribute-Filter im paint(): wenn der gewünschte
Wert dem aktuellen entspricht, gar nicht setzen — Mutation
würde sonst per identitäts-Schreiben weiter feuern. Eher
Mikrooptimierung als Pflicht-Fix.

Empfehlung: (A) + (B) zusammen, das ist die robuste Lösung.

### Betroffene Features

- **FEATURE_BACKLOG.md #5** — *Per-day appointment dots inside
  the date-picker popover*: das Feature ist direkt der Verursacher.
  Bug wurde durch die MutationObserver-Logik aus dem `b1c2429`
  Commit eingeschleppt. Acceptance-Signal „Scrolling months
  inside the popover re-paints via the recursive MutationObserver
  chain" gilt weiter, muss aber um „und Klicks auf Tageszellen
  triggern KEINEN Endlosloop" ergänzt werden.

### Reproduktion

1. App starten, Kalender mit Einträgen (= farbige Balken im
   Popover).
2. „Zu Datum springen"-DatePicker öffnen → mindestens ein Tag
   hat einen Balken.
3. Auf diesen Tag klicken.
4. Browser-Tab friert ein, Vaadin-Loading-Indicator dreht
   endlos, App reagiert nicht mehr.
5. DevTools Performance: ein einzelner JS-Task läuft seit
   mehreren Sekunden ohne Yield → typischer Sync-Loop.

**Erwartet:** Klick auf den Tag → Date wird gewählt → Popover
schließt → Hauptkalender springt zum gewählten Datum.

**Tatsächlich:** Klick → Freeze.

### Touchpoints

- `chronogrid: ui/CalendarNavigationBar.java#POPOVER_WALKER_JS`
  (Zeile ~600) — MutationObserver-Setup ohne attributeFilter +
  ohne Re-Entrancy-Guard.

### Größe

S. Zwei JS-String-Anpassungen in einer einzigen Konstante. 15
Minuten inkl. Browser-Smoke-Test. Tests sind schwierig
(JS-Verhalten lässt sich headless nicht reproduzieren) — wir
verlassen uns auf die manuelle Verifikation.

### Risiko / offene Fragen

- **AttributeFilter könnte zu restriktiv sein.** Wenn Vaadin
  irgendwann zusätzlich `aria-selected` oder andere Attribute
  für relevante Zustandsänderungen nutzt, müssten wir die
  Liste erweitern. v1 startet mit `['part']` und erweitert nach
  Bedarf.
- **Disconnect/Reconnect könnte einen Render-Frame verpassen.**
  Wenn zwischen disconnect und re-observe ein Cell-Update
  passiert, sehen wir's nicht. In der Praxis: paintAll() läuft
  synchron → die einzigen Mutations die wir verpassen sind die
  von paintAll selbst → genau die, die wir verpassen WOLLEN.
  Akzeptabel.

---

## #4 — Multi-Kalender + Reload: alle Termine verschwinden aus der UI

> **Original:** Wenn ich mehr als einen Kalender verbunden habe,
> dann bringt ein Re-Load nichts mehr - Alle Termine weg in der
> UI.

**Status:** ✅ behoben 2026-06-21 in `d0abe01` (`resolveInitialService` prüft jetzt zuerst Multi-Server-State, fällt erst dann auf Legacy-Single-Connection zurück). Sven-verifiziert: nach Route-Navigation/Reload bleiben alle verbundenen Kalender im Service-Client-Set.
**Filed:** 2026-06-21

### Analyse

Ursache durch Diagnose-Logger (BUG#4 DIAG) eindeutig
identifiziert. Die ursprünglichen drei Hypothesen waren alle
falsch — die tatsächliche Bruchstelle ist eine **Inkonsistenz
zwischen zwei parallel laufenden Connection-State-Modellen** in
`CalendarStateStore`:

| State-Modell | API | Inhalt |
|---|---|---|
| Legacy single-server | `readConnection()` | EINE CalDavConnectionConfig (URI + Auth + optional `additionalCollections` aus *einer* Discovery) |
| Multi-server | `readServers()` + `readSubscriptions()` | N CalDavServerConnections + N CalendarSubscriptions |

Im Smoke-Test-Log sah man die Mismatch nach UI-Remount glasklar:

```
[BUG#4 DIAG] fetched=1 visibleSet=[nx93157, CAL2, CAL1]
   sampleHref=https://nx93157.../personal/170...ics
CalendarService ready: primary=https://nx93157.../personal/ readClients=1
```

- Der CalendarService hatte nur **einen** Client (`readClients=1`,
  primary=nx93157).
- Der Visibility-Filter (`visibleUris()`) listete aber **drei**
  URIs (Nextcloud + zwei iCloud-Kalender).
- Folge: Nur Termine aus dem einen Service-Client kamen rein,
  die anderen wurden gar nie abgefragt. Im Multi-Cal-Setup
  hieß das: alles bis auf den zuletzt verbundenen Server weg.

**Root Cause:** `ChronoGrid.resolveInitialService(store)` baut den
Service ausschließlich aus `store.readConnection()`:

```java
private static CalendarService resolveInitialService(CalendarStateStore store) {
    return store.readConnection()
        .map(cfg -> CalendarService.fromConfig(cfg, ZoneId.systemDefault()))
        .orElseGet(ChronoGrid::defaultServiceFromPreset);
}
```

`store.readConnection()` reflektiert nur die zuletzt-applizierte
Single-Server-Connection. Multi-Server-State (`readServers` +
`readSubscriptions`) wird ignoriert. Auf jedem UI-Remount
(Route-Navigation zurück zu Calendar, F5, neue Vaadin-UI) wird der
Service damit auf 1 Client kollabiert.

`rebuildServiceFromSubscriptions()` (die multi-server-aware
Methode) wird nur auf Subscription-Apply / Subscription-Remove
aufgerufen, nicht auf UI-Mount.

**Fix:** `resolveInitialService` muss zuerst auf den Multi-Server-
State prüfen und dann auf den Legacy-State fallen:

```java
private static CalendarService resolveInitialService(CalendarStateStore store) {
    List<CalendarSubscription> subs = store.readSubscriptions();
    if (!subs.isEmpty()) {
        return CalendarService.fromConnections(
            store.readServers(), subs, ZoneId.systemDefault());
    }
    return store.readConnection()
        .map(cfg -> CalendarService.fromConfig(cfg, ZoneId.systemDefault()))
        .orElseGet(ChronoGrid::defaultServiceFromPreset);
}
```

Dabei ist `fromConnections` die existierende Multi-Server-Factory
(Zeile 126 in `CalendarService`) — die Logik braucht keine
Erweiterung, nur den richtigen Einstiegspunkt auf UI-Mount.

### Betroffene Features

- **FEATURE_BACKLOG.md #1, #2, #3, #4, #5, #6** — der Bug verhindert
  jeden Read-Pfad in der App; **alle** Features die Termine
  rendern sind effektiv unbenutzbar. Keine direkte Feature-
  Spezifität — das ist Infrastruktur-Layer (CalDAV-Multi-
  Client-Subscription-Fetch).

### Reproduktion

1. App starten, mehr als einen iCloud-Kalender als Subscriptions
   konfigurieren.
2. Termine im Grid sichtbar — passt.
3. Reload-Button in der Toolbar klicken (Refresh-Icon).
4. **Tatsächlich:** Termine verschwinden aus der UI. Auch
   weiteres Reload bringt sie nicht zurück.

**Erwartet:** Reload re-fetcht die Termine aus allen
konfigurierten Subscriptions; UI zeigt sie wieder an.

### Touchpoints

- `chronogrid: ui/ChronoGrid.java#resolveInitialService` (~Zeile
  1218) — **Bug-Stelle**: las nur `readConnection()`, nicht
  `readSubscriptions()`. Fix: erst Multi-Server-State prüfen.
- `chronogrid-core: service/CalendarService.java#fromConnections`
  (Zeile 126 ff.) — Multi-Server-Factory, war schon korrekt
  vorhanden, wurde aber von `resolveInitialService` nicht
  angesteuert.
- `chronogrid: ui/ChronoGrid.java#rebuildServiceFromSubscriptions`
  (~Zeile 1379) — wurde auf Subscription-Änderungen aufgerufen
  und arbeitet schon richtig; bleibt unverändert.

### Größe

S. Eine Methode (~5 Zeilen) ergänzt um eine Multi-Server-
Vorab-Prüfung. Geschätzt 15 Minuten inkl. Diagnose-Cleanup +
SpotBugs.

### Risiko / offene Fragen

- **Tag-Universe wird auf UI-Remount erst nach erstem Fetch neu
  aufgebaut.** Subscriptions-State bleibt erhalten (per
  Session-Attribut), aber das in `tagUniverse` gesammelte
  Tag-Set startet leer und wächst beim nächsten Fetch. Cosmetisch
  — Tag-Filter zeigt initial keine Tags bis der erste
  fanOut-Pass durch ist. Akzeptabel.
- **Diagnose-Logger entfernt.** Die `[BUG#4 DIAG]`-Zeile im
  `rangeWithStatus` war ein temporäres Diagnose-Werkzeug und
  wird mit dem Fix-Commit entfernt.
- **Persistenz-Trade-off:** Multi-Server-State (Subscriptions +
  Servers) lebt nur in der Vaadin-Session. Browser-Cookie-Reload
  oder Session-Timeout bringt alles zurück auf den Legacy-Pfad.
  Folge-Erweiterung: persistente Speicherung in
  `AppStoragePaths` als JSON. Nicht für diesen Fix.

---

## #5 — Per-Event-Farbe nicht sichtbar bei timed Events (nur bei Tagesterminen erkennbar)

> **Original:** Ein Kalendereintrag mit einer eigenen Farbe -
> keine Farbkennung der eigenen Farbe wenn der Termin kein
> Tagestermin ist.

**Status:** ✅ behoben 2026-06-22 in `d25e377` (CSS-Fix: TimeGrid-Border-Reduktion + `.fc-event-main` Transparency). Sven-verifiziert auf iCloud-Terminen: Fill der eigenen Farbe in Week/Day-View jetzt sichtbar.
**Filed:** 2026-06-21

> **Cross-Provider-Hinweis:** Beim Smoke-Test fiel auf dass
> Nextcloud-Termine die Farbe gar nicht erst durchreichen — der
> Termin „verliert die Farbe" bzw. „kommt wohl nicht in
> Nextcloud an". Das ist KEIN CSS-Sichtbarkeitsproblem (was #5
> war) sondern ein Persist-/Roundtrip-Problem auf der Nextcloud-
> Seite. Tracking erweitert in **BUG #12**.

> **Folge-Anforderung (UX, behoben am gleichen Tag):** Sven fragte
> nach dem #5-Fix warum timed Events im Month-View nur einen Dot
> in Kalender-Farbe zeigen (keine eigene Farbe sichtbar). Das ist
> FullCalendar's Default-Verhalten für list-item-Rendering im
> DayGrid — bewusste Design-Entscheidung weil im Month-View kein
> Platz für 5+ Klötze pro Tag wäre. Lösung: timed Events im
> Month-View bekommen einen 2-px Border in der eigenen Farbe,
> Dot bleibt in der Kalender-Farbe. Auf einen Blick: gefüllter
> Block = AllDay, Border+Dot = timed. CSS-only-Fix in
> `chronogrid.css`, ein Block (Selector
> `.fc-daygrid-event.fc-daygrid-dot-event`).

### Fix-Notiz

Defensive CSS-Erweiterung in `chronogrid.css` deckt Hypothesen A
und B gleichzeitig ab:

1. `.fc-timegrid-event` — Border auf top/right/bottom auf 2 px
   reduziert (statt 3 px global), Left-Border bleibt bei 4 px
   für die Provenienz-Kennung. Plus `background-clip: padding-box`,
   damit der Fill nicht unter dem Border verschwindet.
2. `.fc-timegrid-event .fc-event-main` (und `.fc-v-event
   .fc-event-main`) — `background-color: transparent`, damit das
   innere FullCalendar-Wrapper-Element den outer Fill nicht
   überlagert.

Hypothese C (FullCalendar liest `color` statt `backgroundColor`)
ist nicht adressiert — `applyColours` setzt schon
`setBackgroundColor` korrekt, und FullCalendar v6's TimeGrid
respektiert das per Default. Falls C nach dem Smoke-Test doch
zutrifft, würde der Fill weiter unsichtbar bleiben → dann fallback
auf `setColor(individualColor)` mit explizitem `setBorderColor`-
Re-Apply als zusätzliche Maßnahme.

### Analyse

FullCalendar v6 rendert AllDay- und Timed-Events durch
**unterschiedliche** CSS-Klassen mit unterschiedlichem Box-Modell:

| Event-Typ | Klasse | Wo gerendert | Farb-Mechanismus |
|---|---|---|---|
| AllDay-Termin | `.fc-daygrid-event` | DayGrid (Month + AllDay-Row) | Background-Fill der Box |
| Timed-Termin | `.fc-timegrid-event` | TimeGrid-Spalten (Day/Week/N-days) | Background-Fill + meist Border-Left als Provenance-Kennung |

Unsere CSS-Regeln (aus BACKLOG #4 für stärkere Borders):

```css
.fc .fc-event             { border-width: 3px; }
.fc .fc-timegrid-event    { border-left-width: 4px; }
```

**Hypothese A — Border verschlingt den Fill bei kleinen Timed-
Events.** Ein 30-Minuten-Termin in der TimeGrid ist typisch
~30–40 px hoch. Mit `border-width: 3px` rundherum und
`border-left-width: 4px` bleibt für den Fill nur eine schmale
Mittelfläche — bei dunklen Border-Farben optisch fast
unsichtbar. **Wahrscheinlichkeit: hoch.**

**Hypothese B — TimeGrid-Renderer setzt eigene
background-color-Inline-Styles, die unsere `setBackgroundColor`
überschreiben.** Manche FullCalendar-Add-ons (inkl. Vaadin
Stefan) rendern Timed-Events über `event.classNames`-Hooks oder
inline-style-Overrides. Wenn das Inline-Style aus dem TimeGrid-
Pfad nach unserem `setBackgroundColor` ankommt, hätten wir den
Fill verloren. **Wahrscheinlichkeit: mittel.**

**Hypothese C — `applyColours` setzt `setBackgroundColor` aber
nicht `setColor`.** FullCalendar's `setColor()` setzt sowohl
backgroundColor als auch borderColor. Wenn der TimeGrid-Renderer
auf `color` (statt `backgroundColor`) schaut, sehen wir nichts.
**Wahrscheinlichkeit: niedrig** (FullCalendar-Doku sagt
backgroundColor wirkt für beide Pfade), aber prüfen.

**Verifikation (Sven kann das selbst machen):**

- Termin mit eigener Farbe im Week-View ansehen (= TimeGrid).
- DevTools Elements: Rechtsklick auf den Event-Block →
  Computed Styles → `background-color`.
- Wenn `background-color` = unsere Farbe → Hypothese A
  (Border erstickt den Fill optisch).
- Wenn `background-color` = Kalender-Farbe oder undefined →
  Hypothese B/C (Style-Konflikt).

### Betroffene Features

- **FEATURE_BACKLOG.md #1** — *Per-event colour with a
  calendar-coloured edge stripe*: das Feature, das hier
  unsichtbar bleibt. Acceptance-Signal galt für AllDay-Events
  aber nicht für Timed-Events.
- **FEATURE_BACKLOG.md #4** — *Stronger event borders for visible
  provenance*: vermutlicher Verursacher von Hypothese A (3px-
  Border in der kleinen TimeGrid-Box).

### Reproduktion

1. Termin mit eigener Farbe (z.B. Rot) anlegen, **nicht** als
   Tagestermin (z.B. 10:00–11:00).
2. In Day- oder Week-View wechseln.
3. **Tatsächlich:** Termin sieht aus wie ein normaler Kalender-
   Termin, keine erkennbare Farbabweichung.

**Erwartet:** Termin-Box hat sichtbaren roten Fill, blauen
(Kalender-)Border drumherum.

### Touchpoints (vermutet)

- `chronogrid: src/main/resources/META-INF/resources/frontend/styles/chronogrid.css`
  — wenn Hypothese A: TimeGrid-spezifische Regeln müssen den
  Fill respektieren (z.B. `.fc-timegrid-event { padding-top: 2px; }`
  oder `border-width` reduzieren)
- `chronogrid-core: service/CalendarService.java#applyColours`
  (Zeile 225–236) — wenn Hypothese B/C: zusätzlich `setColor()`
  rufen oder via JS-Hook das Inline-Style setzen
- FullCalendar v6 docs zu eventColor / backgroundColor /
  borderColor in TimeGrid

### Größe

S–M, abhängig von Hypothese. A → CSS-Tweak (S, 15 min). B/C →
müsste den TimeGrid-Render-Path durchgehen + ggf. Vaadin Stefan
Add-on inspizieren (M, 30–60 min).

### Risiko / offene Fragen

- **A-Fix könnte BACKLOG #4 abschwächen.** Wenn wir den Border
  bei TimeGrid-Events reduzieren, ist die Provenance-Kennung
  in TimeGrid weniger prominent. Trade-off: ohne sichtbare
  eigene Farbe ist die Provenance-Kennung sowieso nutzlos —
  besser etwas weniger Border als gar kein Fill.
- **B/C könnte einen JS-Hook brauchen.** Wenn FullCalendar bzw.
  das Vaadin Stefan Add-on `setBackgroundColor` in TimeGrid
  ignoriert, müssen wir per `eventDidMount`-Callback (JS) das
  background per Inline-Style aufzwingen. Nicht-trivial, ähnlich
  wie der Popover-Walker in Feature #5.


---

## #6 — Verbindungsmanagement-UX ungenügend: hinzufügen + entfernen muss intuitiver werden

> **Original:** Das Verbindungsmanagement ist ungenügend. Es muss
> intuitiv sein eine neue Verbindung herzustellen und eine zu
> entfernen.

**Status:** 🟡 erfasst — UX-Anforderung, braucht Konkretisierung
**Filed:** 2026-06-21

### Analyse

Sven beschreibt das Problem auf UX-Ebene, nicht auf Code-Ebene.
Die aktuellen Touchpoints sind drei Dialoge in der Toolbar:

- **Settings** (`openSettingsDialog`) — Single-Connection-Editor
  für die Legacy `CalDavConnectionConfig`. Nutzt das alte
  Single-Server-Modell.
- **Connections** (`openConnectionsDialog`, `ConnectionsDialog`) —
  Multi-Server-Liste. Anlegen einer neuen Server-Connection
  inkl. Discovery + Auth.
- **Subscriptions** (`openSubscriptionsDialog`,
  `SubscriptionsDialog`) — Liste der abonnierten Kalender pro
  Server, Visibility + Farb-Override.

Das ist effektiv ein **drei-Dialog-Workflow** für einen Vorgang
den der User als „eine Verbindung hinzufügen" wahrnimmt:

1. Connections-Dialog öffnen → neuen Server anlegen (URL, User,
   Passwort, Discovery startet) → Server-Eintrag fertig.
2. Subscriptions-Dialog öffnen → Kalender vom neuen Server
   einzeln aktivieren → endlich sieht man Termine.

Außerdem: ein **vierter** historischer Pfad (Settings-Dialog für
die Legacy-Single-Connection) existiert daneben — Quelle der
Verwirrung und auch der Auslöser für BUG #4 (zwei parallel
laufende State-Modelle).

**Konkrete UX-Probleme** (vermutet, basierend auf der Architektur
und den BUG-Berichten der letzten Stunden):

1. **Zwei State-Modelle nebeneinander** (Legacy `Connection` +
   Multi-Server `Subscriptions`). Verschwindet implizit für den
   User, aber führt zu inkonsistentem Verhalten (siehe BUG #4).
2. **Add-Pfad nicht ein-klick.** Aktuell: Connections-Dialog →
   Server hinzufügen → Discovery warten → Subscriptions-Dialog
   → Kalender einzeln aktivieren. Wenig Geleit.
3. **Entfernen-Pfad nicht intuitiv.** Subscription entfernen
   räumt die Subscription weg, aber wenn das der letzte Kalender
   eines Servers war, bleibt der Server-Eintrag. `pruneOrphan
   Servers()` exists, aber UX-mäßig nicht klar.
4. **Status-Anzeige** der einzelnen Verbindungen ist über
   `ServerStatusList` da, aber nicht stark mit dem Add-/Remove-
   Flow gekoppelt.

**Mögliche Lösungsrichtung** (sehr offen, braucht Designgespräch):

- **Ein einziger „Connection Manager"-Dialog** mit Liste aller
  Server + integrierten Pro-Server-Subscription-Cards.
- **Wizard für „neue Verbindung"**: 1. URL eingeben → 2.
  Credentials → 3. Kalender aus Discovery aktivieren →
  Direkt-Fertig.
- **Legacy `readConnection()`-Pfad endgültig deprecaten** sobald
  alle Caller auf `readSubscriptions()` umgestellt sind.
- **„Remove server"-Button räumt alles auf** (Server, deren
  Subscriptions, deren lokal-gestapelten Farben aus dem BUG-#2
  Store) — atomare Operation aus User-Sicht.

### Betroffene Features

- **FEATURE_BACKLOG.md #1, #2, #3, #4, #5, #6** — UX des Connection-
  Managements ist eine Vorbedingung für alle Features. Wenn der
  User die Verbindung nicht zuverlässig herstellen kann, hilft
  kein Feature.
- **Indirekt verwandt mit BUG #4** — der Legacy-Pfad war Ursache
  von BUG #4. Eine konsequente Migration weg vom Legacy-Pfad
  würde solche Konsistenz-Probleme strukturell ausschließen.

### Reproduktion

Subjektiv. Sven berichtet generell: das Hinzufügen + Entfernen
fühlt sich nicht intuitiv an. Konkrete Friction-Points während
des Test-Setups dieser Session erkennbar im Log:

- Mehrere `CalendarService ready`-Lines mit wechselnden
  `readClients` als der User Connections + Subscriptions
  umkonfigurierte.
- Mehrere `Discovery start`-Sequenzen nacheinander.
- Inkonsistenz zwischen sichtbaren Subscriptions und
  tatsächlich gequerten Clients (BUG #4).

### Touchpoints (Erkundung)

- `chronogrid: ui/ConnectionsDialog.java` — UI für Server-Liste
- `chronogrid: ui/SubscriptionsDialog.java` — UI für Subscription-
  Liste pro Server
- `chronogrid: ui/ChronoGrid.java#openSettingsDialog` — Legacy
  Single-Connection-Editor (Kandidat für Deprecation)
- `chronogrid: ui/ChronoGrid.java#pruneOrphanServers` —
  Aufräum-Routine die schon existiert
- `chronogrid-core: client/CalDavDiscovery.java` — der
  Discovery-Schritt, der eine User-friendliche Auswahl-UI
  bekommen sollte

### Größe

L. Echte UX-Überarbeitung mit Wizard- oder Single-Dialog-Design.
Eher 2–4 Tage Implementierung + Smoke-Tests + Tests.

Empfehlung: **erst BUG #5 + #7 fixen** (kleinere, gezieltere
Fixes), DANACH den Connection-Manager als ein eigenes Feature in
`Feature-Planning.md` umarbeiten (als „Connection-Manager UX-
Refresh") mit echtem Konzept- und Design-Schritt.

### Risiko / offene Fragen

- **Konkrete User-Story fehlt.** „Intuitiv" ist nicht
  spezifizierbar. Sven sollte beschreiben: welche konkrete Aktion
  fühlt sich falsch an? (Bsp: „ich klicke auf Settings, sehe
  einen Connection-Editor, aber die Termine kommen nicht — weil
  ich auch Subscriptions aktivieren muss.")
- **Migrations-Pfad nötig.** Bestehende User mit
  `readConnection()`-State müssen automatisch auf den Multi-
  Server-State portiert werden. Risiko: Daten-Verlust beim
  Upgrade.
- **Vermutlich gehört das eigentlich nach `Feature-Planning.md`.**
  Ein UX-Refresh ist eher ein Feature als ein Bug — sobald die
  Anforderung konkretisiert ist, vorschlag den Eintrag dorthin
  zu migrieren.

---

## #7 — Per-Event-Farbe: DESCRIPTION-Marker nur für iCloud, reguläre COLOR-Property bei anderen Providern

> **Original:** Nur bei iCloud soll die Farbe als Description
> Attribut gespeichert werden. Bei den anderen wie z.B.
> NextCloud wieder als reguläres Attribut, so dass man die Farbe
> dann auch in NextCloud sieht.

**Status:** ✅ behoben 2026-06-21 in `d25e377` (Hostname-Suffix-Match `*.icloud.com` + Parameter durch `EntryMapper.toICalendarText(Entry, boolean appleSidechannel)`). Sven-verifiziert: Nextcloud-Termine kommen ohne Marker, iCloud-Termine behalten ihn.
**Filed:** 2026-06-21

### Fix-Notiz

Hostname-basierter Apple-Provider-Detector + Parametrisierung
des Writer-Pfads:

1. `CalendarService.isAppleProviderUri(URI)` — neuer public-static
   Helper, matcht Hostnamen die auf `icloud.com` enden
   (catches caldav.icloud.com, p124-…, p-prod-… etc.).
2. `EntryMapper.toICalendarText(Entry, boolean appleSidechannel)` —
   Overload. Wenn `false`, DESCRIPTION wird nur mit der User-Notiz
   geschrieben (kein Marker). `toICalendarText(Entry)` bleibt als
   Backward-Compat-Variante mit `appleSidechannel=true` (Apple-safe
   Default).
3. `CalendarService.save` ruft `isAppleProviderUri(target)` und
   gibt den Boolean an den Mapper weiter.

Reader-Pfad ist unverändert: liest weiterhin COLOR zuerst, fällt
auf den Marker zurück wenn präsent, fällt auf den lokalen Store
zurück (BUG #2). Legacy-iCloud-Events mit Marker werden weiter
korrekt gelesen.

### Analyse

Folge-Forderung zu BUG #2's Hybrid-Fix. Aktuell schreibt der
`EntryMapper.toICalendarText` für **jeden** Termin mit eigener
Farbe **beide** Speicher-Pfade:

1. `COLOR:#ff0000` (RFC-7986-Property)
2. `[chronogrid-color: #ff0000]`-Marker am Ende der DESCRIPTION

Das ist Apple-resilient (BUG #2's Hauptmotivation), aber
**verschmutzt die DESCRIPTION für Provider, die `COLOR` korrekt
round-trippen**. Bei Nextcloud / Baikal / Radicale sehen User die
Marker-Zeile in der DESCRIPTION jedes Termins — unnötig.

Sven's Wunsch: bei **Apple/iCloud** Marker einsetzen (Workaround
nötig), bei **anderen Providern** weglassen (Standard-COLOR
reicht und ist in deren UI sichtbar).

**Mögliche Erkennungs-Strategien:**

1. **URI-Pattern-basiert** — Apple-CalDAV-URLs enthalten
   typischerweise `caldav.icloud.com` oder
   `p[0-9]+-caldav.icloud.com`. Beim Write den Target-Client
   prüfen.
2. **Header-basiert** — Apple's CalDAV-Server gibt einen
   `Server`- oder `DAV`-Header zurück, der Apple identifiziert.
   Beim Initial-Connect cachen.
3. **Konfig-flag pro Subscription** — der User markiert die
   Subscription als „Apple-resilient" (oder das Apple-iCloud-
   Preset setzt es default-an).
4. **Probe-Mechanismus** — eine Test-PUT mit Marker schreiben,
   wieder lesen, prüfen ob Marker noch da ist. Aufwändig +
   Round-trip-Kosten.

Empfehlung: **Strategie 1 (URI-Pattern)** als pragmatischer
Schnell-Fix. Regex-Match auf `caldav.icloud.com|*.icloud.com`
beim Schreiben aus dem `targetClient.collectionUri()`. Wenn
match → Marker schreiben, sonst weglassen.

**Reader-Pfad bleibt unverändert** — er liest weiterhin COLOR
und fällt auf den Marker zurück. Falls auf einem nicht-iCloud-
Provider trotzdem mal ein Marker steht (Legacy-Daten aus der
Hybrid-Phase), wird er korrekt geparst und gestrippt.

**Architektur-Implikation:** `EntryMapper.toICalendarText(Entry)`
braucht entweder einen zusätzlichen `boolean
applyAppleSidechannel` Parameter, oder einen Predicate-Funktion.
Sauberer Schnitt: Caller (CalendarService.save) entscheidet,
basierend auf dem `targetClient.collectionUri()`.

### Betroffene Features

- **FEATURE_BACKLOG.md #1** — *Per-event colour with a
  calendar-coloured edge stripe*: Acceptance-Signals erweitern um
  „bei nicht-Apple-Providern wird der Marker NICHT geschrieben".
- **Bezug zu BUG #2** — der Marker als Apple-Workaround wird
  hier auf seinen tatsächlichen Use-Case verengt. Lokaler Per-
  UID-Store bleibt als Absicherung relevant für Apple.

### Reproduktion

1. App mit Nextcloud verbunden.
2. Termin mit eigener Farbe in unserer App speichern.
3. Im Nextcloud-Web-UI denselben Termin öffnen.
4. **Tatsächlich:** DESCRIPTION-Feld zeigt `[chronogrid-color:
   #ff0000]` am Ende. User sieht die Marker-Zeile als
   überflüssige Notiz.
5. **Erwartet:** DESCRIPTION zeigt nur den User-Text; die Farbe
   ist über die normale `COLOR`-Property abrufbar (Nextcloud's
   UI sollte sie korrekt anzeigen — sofern Nextcloud RFC-7986
   `COLOR` unterstützt).

### Touchpoints

- `chronogrid-core: mapping/EntryMapper.java#toICalendarText` —
  Marker-Write-Pfad bedingt machen, abhängig von einem neuen
  Parameter
- `chronogrid-core: service/CalendarService.java#save` —
  Entscheidet basierend auf `targetClient.collectionUri()`
  ob Apple-Marker oder nicht
- `chronogrid-core: client/CalDavClient.java` — ggf.
  `isAppleProvider()`-Helper hinzufügen
- `chronogrid-core: test/EntryMapperColourSidechannelTest.java`
  + neuer Test für den Apple-vs-non-Apple-Switch

### Größe

S. Eine Parameter-Erweiterung in `toICalendarText`, eine
URI-Pattern-Erkennung in `CalendarService.save`, 2 neue Tests.
Geschätzt 30 Minuten.

### Risiko / offene Fragen

- **Provider-Erkennung anhand der URI ist brüchig.** Wenn iCloud
  irgendwann die Domain ändert oder ein User eine Custom-DNS-
  Proxy-Lösung benutzt, schlägt die Erkennung fehl. Pragmatisch:
  Apple-Domain-Pattern reicht für 99% der Fälle.
- **Verifikation, dass Nextcloud `COLOR` tatsächlich anzeigt.**
  Müsste Sven kurz im Nextcloud-Web-UI prüfen — wenn Nextcloud
  COLOR ebenfalls ignoriert, wird der Fix die Farbe schlechter
  sichtbar machen. Aber die Vorfrage ist legitim: zeigt
  Nextcloud die COLOR-Property überhaupt im UI an?
- **Cross-Migration:** wenn ein User einen Termin aus iCloud nach
  Nextcloud kopiert, kommt der Marker mit. Reader strippt ihn,
  aber Nextcloud-User sieht ihn temporär. Akzeptabel.

---

## #8 — Abonnieren und De-Abonnieren von Kalendern muss einfacher werden

> **Original:** Abbonieren und De-Abbonieren muss für den User
> einfach machbar sein.

**Status:** 🟡 erfasst — UX-Anforderung, eng verwandt mit BUG #6
**Filed:** 2026-06-21

### Analyse

Sehr nahe Verwandte zu BUG #6 — „Verbindungsmanagement-UX". Die
Trennung in Svens Aufzählung lässt vermuten: #6 fokussiert auf
*Server-Connection hinzufügen/entfernen* (Auth + Discovery), #8
auf *einzelne Kalender innerhalb eines bereits verbundenen Servers
aktivieren/deaktivieren*.

Aktueller Pfad für Abonnieren:

1. User klickt **Subscriptions** in der Toolbar.
2. `SubscriptionsDialog` öffnet → Liste pro Server mit
   Visibility-Checkbox + Farb-Override.
3. Visibility-Toggle ändert das `visible`-Flag in der
   `CalendarSubscription`, persistiert via
   `stateStore.writeSubscriptions(...)`.
4. `refreshAll()` triggert Re-Fetch — die Visibility-Filter in
   `rangeWithStatus` ziehen, der Kalender wird im Grid ein-
   bzw. ausgeblendet.

Aktueller Pfad für De-Abonnieren:

1. User klickt im SubscriptionsDialog auf den Entfernen-Knopf
   eines Eintrags.
2. Eintrag wird aus der `subscriptions`-Liste gestrichen,
   `pruneOrphanServers` räumt ggf. den verwaisten Server weg.
3. `rebuildServiceFromSubscriptions` + `refreshAll` aktualisieren.

**Wahrscheinliche UX-Probleme** (überschneidend mit BUG #6):

- **Subscribe ist zweistufig**: erst Server connecten, dann
  Subscriptions-Dialog auf und einzeln aktivieren. Default
  beim Connecten ist anscheinend „alle Kalender aktivieren"
  (Sven bitte bestätigen) — aber das Mental Model „ich klicke
  Connect und sehe Termine" ist nicht direkt erfüllt, weil der
  Subscription-Dialog ein extra Schritt ist.
- **Toggle-Granularität**: pro-Kalender ein/aus-Schalten ist
  granular, aber wenn ich „nur die wichtigsten" sehen will,
  muss ich jeden einzeln klicken.
- **Visibility-Discovery**: wenn auf dem Server ein neuer Kalender
  hinzukommt (z.B. iCloud-Account legt eine neue Liste an), holt
  unsere App den nicht automatisch — Discovery läuft nur auf
  Connect.
- **Bulk-Aktionen** fehlen: „alle ein", „alle aus", „nur diesen".

**Mögliche Lösungsrichtung** (offen, braucht Designgespräch):

- **Quick-Toggle in der Toolbar**: kleine Liste der Subscriptions
  mit Color-Dot + Name + Toggle-Checkbox direkt sichtbar, ohne
  Dialog.
- **Re-Discovery-Button** im SubscriptionsDialog: „Neue Kalender
  suchen" + automatisches Hinzufügen mit Default-visible=false.
- **Bulk-Aktionen**: „Alle ein/aus" pro Server-Sektion.

### Betroffene Features

- **FEATURE_BACKLOG.md #3** — *Per-entry tags + cross-calendar
  tag filter*: das Tag-Filter-Konzept setzt voraus dass User
  alle ihre Kalender aktiviert haben. Wenn das Abonnieren
  umständlich ist, leidet auch der Cross-Cal-Tag-Filter.
- **Indirekt**: alle Features die Termine darstellen, da die
  Visibility-Subscription-Logik die Vorfilterung macht.

### Reproduktion

Subjektiv. Sven berichtet das Subscription-Management als
mühsam. Konkrete Friction-Points sollten beim Design-Schritt
katalogisiert werden.

### Touchpoints (Erkundung)

- `chronogrid: ui/SubscriptionsDialog.java` — aktueller Subscribe-
  Editor
- `chronogrid: ui/ChronoGrid.java#applyConfigFromSettings` (~Zeile
  1018 ff.) — Discovery-Apply-Flow, der Subscriptions
  default-aktivierend erzeugt
- `chronogrid: ui/ConnectionsDialog.java` — Server-Liste; hier
  könnte ein „Quick-Subscribe"-UI angebaut werden

### Größe

L. UX-Refactor wie BUG #6 — eher 2–4 Tage Designer-Schritt +
Implementation.

Empfehlung: **BUG #6 + #8 zusammen** als einen Connection-
Manager-UX-Feature in `Feature-Planning.md` migrieren statt zwei
parallele Bug-Tracks. Sobald die User-Story konkret ist, kann
das Feature im normalen Feature-Workflow durch.

### Risiko / offene Fragen

- **Konkrete User-Story fehlt** (wie bei #6). „Einfach machbar"
  ist nicht spezifizierbar; Sven sollte beschreiben welche
  konkreten Klick-Pfade als zu lang empfunden werden.
- **Discovery-Trigger**: soll Re-Discovery automatisch laufen
  (Pull alle X Minuten?) oder manuell?
- **Default-Visibility neuer Kalender**: aktivieren-by-default
  (Spam-Risiko) oder deaktivieren-by-default (User-Aktion
  nötig)?

---

## #9 — Notifikationen passen nicht mehr zum Mehrverbindungs-Konzept

> **Original:** Die Notifikationen passen nicht mehr zu einem
> Mehr-Verbindungskonzept.

**Status:** 🔬 analysiert
**Filed:** 2026-06-21

### Analyse

Die `notifyInfo`/`notifyError`/`notifyConflict`-Aufrufe in
ChronoGrid sind aus der Single-Server-Zeit übrig und referenzieren
„die Verbindung" statt einer konkreten. Konkrete Stellen die
unscharf werden im Multi-Server-Kontext:

| Stelle | Aktuelle Message-Schablone | Multi-Server-Problem |
|---|---|---|
| Refresh-Button | „Reloaded from {0}" mit `service.collectionUri()` | `collectionUri()` ist nur die *primary* Collection — bei 3 verbundenen Kalendern verschweigt die Notification die anderen zwei |
| Apply-Connection | „Connected to {0}" — nur die neue URI | Andere bereits-verbundene Server bleiben implicit; User weiß nicht wie viele Server jetzt aktiv sind |
| Subscription-Remove | „Disconnected from {0}" | Singular OK, aber Kontext fehlt: „wie viele bleiben übrig?" |
| Error-Pfad | Generischer „Could not save: {error}" | Bei mehreren Servern: welcher hatte den Fehler? Welcher Kalender? |
| Connect/Disconnect-Toasts | Single-Server-zentrisch | Kein „Server X verbunden, 4 Kalender abonniert, Y Termine gefunden"-Multi-Server-Summary |

**Lösungsrichtung:**

1. **Status-aware Notifications** — `notifyInfo(...)` erweitern um
   ein optionales Subject („für Server X, Kalender Y"). Auf
   ChronoGrid-Seite überall die Subject-Info mitgeben.
2. **Multi-Server-Summary** als eigene Notification-Variante:
   „Connection-State: 3 Server, 7 Kalender, 42 Termine".
   Auslösen nach Refresh + Connection-Änderungen.
3. **Pro-Server-Status-Updates** — `ServerStatusList` zeigt schon
   pro-Server-Connected/Disconnected-Pills, aber die Notifications
   spiegeln den Detailgrad nicht.
4. **Error-Surfacing** mit Server-Identität — wenn Server X
   einen 403 liefert, „Server X (URL) lehnt ab" statt nur
   „Could not save".

### Betroffene Features

- **FEATURE_BACKLOG.md #5** — *Per-day appointment dots*:
  Notifications könnten erklären „N Server abgefragt, M Termine
  in der Visible-Range".
- **Indirekt alle Features** die mehrere Server berühren —
  Notifications sind die einzige Stelle, an der die App dem
  User Multi-Server-State kommuniziert.

### Reproduktion

Subjektiv. Konkretes Beispiel aus Svens Test-Sitzung:

1. iCloud + Nextcloud verbunden.
2. Refresh-Button klicken.
3. Notification: „Reloaded from
   https://nx93157.../personal/" — verschweigt die zwei iCloud-
   Calendars die parallel geladen wurden.

### Touchpoints

- `chronogrid: ui/ChronoGrid.java#notifyInfo` /
  `notifyError` / `notifyConflict` — die Notification-Helper
- `chronogrid: ui/ChronoGrid.java` Refresh-Button-Handler
  (~Zeile 383)
- `chronogrid: ui/ChronoGrid.java` Apply-Connection-Handler
  (~Zeile 1030)
- `chronogrid: ui/ChronoGrid.java` Subscription-Remove-Handler
  (~Zeile 1354)
- `chronogrid: ui/ChronoGrid.java#persistSave` (~Zeile 1165) —
  Error-Pfad mit Server-Identität anreichern
- i18n-Keys `calendar.notify.*` in `translations*.properties` —
  Templates erweitern um Multi-Server-Slots

### Größe

S–M. Wenn man die Messages nur erweitert und neue i18n-Keys
einführt: S (60 Min). Wenn man eine echte Multi-Server-Summary-
Notification baut: M (2–3 Stunden inkl. Tests).

Empfehlung: **mit BUG #6 + #8 zusammen** im Connection-Manager-
UX-Refactor angehen — die Notifications sind ein Teil der UX-
Story, kein isolierter Bug.

### Risiko / offene Fragen

- **i18n-Aufwand**: jede neue Message-Variante braucht EN+DE-
  Übersetzungen.
- **Notification-Spam**: bei 5+ Servern könnten Multi-Server-
  Summaries zu lang werden. Kollabieren auf „N Server, M
  Termine" als Default, Detail-Link für Drill-down.

---

## #10 — Fetch über mehrere Verbindungen muss parallel + asynchron sein, mit Fortschrittsbalken

> **Original:** Laden der einzelnen Daten über verschiedene
> Verbindungen sollte parallel und asynchron gehen.
> Fortschrittsbalken sind notwendig.

**Status:** 🔬 analysiert
**Filed:** 2026-06-21

### Analyse

Aktueller Code in `CalendarService.fanOut` (chronogrid-core,
~Zeile 191):

```java
private Stream<Entry> fanOut(...) {
    Stream.Builder<Entry> all = Stream.builder();
    int total = 0;
    for (int i = 0; i < readClients.size(); i++) {
        CalDavClient client = readClients.get(i);
        ...
        for (RemoteEvent remote : op.apply(client)) {   // ← SYNCHRON, sequenziell
            ...
        }
    }
    ...
    return all.build();
}
```

Heute: alle Clients werden **sequenziell** abgefragt im selben
Thread. Wer 5 CalDAV-Server hat, die jeweils 800 ms REPORT
brauchen, wartet 4 Sekunden bis ein Termin sichtbar wird —
unzumutbar.

**Zwei Aspekte zu beheben:**

**(a) Parallel-Fetch.** Statt `for client : readClients { sync }`
einen `CompletableFuture`-Pfad pro Client öffnen, dann
`CompletableFuture.allOf(...)` collecten. Threadpool: ein
dedizierter `Executor` (z.B. `Executors.newFixedThreadPool(8)`)
in der `CalendarService`. CalDavClient ist HTTP-basiert und
thread-safe.

**(b) Async UI-Update + Fortschritt.** Vaadin's `UI#access` ist
nötig um aus Worker-Threads die UI zu aktualisieren. Statt
fanOut zu blockieren bis alles da ist:

1. Sofort einen leeren EntryProvider zurückgeben (Grid zeigt
   nichts).
2. Pro Client async fetchen.
3. Sobald Client fertig: `UI.access` → Entries dem Grid hinzufügen
   + Fortschrittsbalken um 1/N erhöhen.
4. Wenn alle fertig: Fortschritt verstecken.

**Architektur-Implikation:**

- `CalendarService.findInRange(...)` müsste eine async API
  bekommen (z.B. `CompletableFuture<Stream<Entry>>` oder eine
  reaktive `Publisher<Entry>`-Variante). Die existierende
  synchrone API kann als wrapper bleiben für Tests + simple
  Callers.
- `ChronoGrid.rangeWithStatus` müsste den FullCalendar
  EntryProvider asynchron befüllen. FullCalendar Vaadin Add-on
  unterstützt das nur eingeschränkt — müsste geprüft werden
  ob die Add-on-API einen Callback für nachgeladene Entries
  hat oder ob wir mit `refreshAll()` arbeiten müssen.
- **Fortschrittsbalken**: Vaadin's `ProgressBar` (determinate)
  oder ein `Notification` mit Counter. Position: in der
  Toolbar neben dem Refresh-Button oder als Toast-Overlay.

### Betroffene Features

- **FEATURE_BACKLOG.md #5** — *Per-day appointment dots in
  popover*: profitiert direkt — Popover-Open ist heute auch
  syn-fetch, mit Multi-Server wäre er besonders langsam.
- **FEATURE_BACKLOG.md #3** — *Tag-Filter*: profitiert weil
  Multi-Cal-Setups häufiger werden wenn Subscribe einfacher ist
  (siehe BUG #8).
- **Indirekt alle Features** die `findInRange` aufrufen.

### Reproduktion

1. Mehr als 2 Server verbunden (z.B. iCloud + Nextcloud).
2. Calendar-Route öffnen.
3. **Tatsächlich:** Spürbare Pause bis Termine erscheinen,
   linear mit der Anzahl der Server.
4. Bei Server-Timeouts: einer hängt → die anderen Server warten
   mit, alles steht.

**Erwartet:** Termine erscheinen sukzessive sobald einzelne
Server antworten. Fortschrittsbalken zeigt „Server 2 von 5
geladen". Hängender Server blockiert die anderen nicht.

### Touchpoints

- `chronogrid-core: service/CalendarService.java#fanOut` (~Zeile
  191) — von sequenziell auf parallel
- `chronogrid-core: service/CalendarService.java` — neuer
  `Executor`-Field + Shutdown-Hook (Vaadin-Session-Lifecycle
  beachten)
- `chronogrid-core: service/CalendarService.java#findInRange` /
  `findInRangeAsResult` — async-API ergänzen
- `chronogrid: ui/ChronoGrid.java#rangeWithStatus` — async-
  Variante, EntryProvider asynchron befüllen
- `chronogrid: ui/ChronoGrid.java` — neue
  ProgressBar-Komponente in der Toolbar
- Vaadin Stefan FullCalendar Add-on Doku — async-Add-Pfad prüfen
- `chronogrid-core: client/CalDavClient.java` — Thread-Safety
  bestätigen (HTTPClient ist thread-safe, aber unsere Wrapper
  ggf. nicht)

### Größe

L. Parallel-Fetch + async UI-Update + ProgressBar ist eine
echte Architektur-Erweiterung. Geschätzt 1–2 Tage Implementation
+ Tests (unit-tests für Thread-Safety, IT für Multi-Server-
Reihenfolge-Unabhängigkeit).

Empfehlung: **erst nach BUG #5 und #7**, weil die hier alleine
nichts Visuelles bringen. Sobald Performance unter Multi-Server
tatsächlich stört, hier ran.

### Risiko / offene Fragen

- **Thread-Safety in CalDavClient.** Heute Single-Thread-Use,
  müsste auf Concurrent-Safety geprüft werden.
- **Executor-Shutdown**: Vaadin-Session-Lifecycle kennt
  `addSessionDestroyListener`, dort sauber `executor.shutdown()`
  aufrufen. Sonst Thread-Leak.
- **Order-Sensitivity**: aktuell garantiert sequenzieller Fetch
  eine deterministische Entry-Reihenfolge. Parallel + async
  ändert die Reihenfolge — FullCalendar-Renderer sollte robust
  sein (Events haben Start-Time-Sortierung intern), aber
  Edge-Cases wie Recurring-Events könnten flackern.
- **Fortschrittsbalken-UX**: nervig bei schnellen Servern,
  hilfreich bei langsamen. Vielleicht erst nach 500 ms Wartezeit
  einblenden (Smart-Delay).
- **Vaadin-Stefan-Add-on-Async-Support**: muss geprüft werden ob
  der EntryProvider asynchron nachgeladen werden kann oder ob
  wir komplett auf `refreshAll`-after-each-client ausweichen
  müssen (würde mehr Rendering-Aufwand bedeuten).

---

## #11 — Neuer Termin auf Nextcloud erscheint dort als Tagestermin trotz gesetzter Uhrzeit

> **Original:** Ich erzeuge in der UI einen Termin für NextCloud
> in der Zeit von A bis B - In NextCloud wird mir dieser als
> Tagesevent angezeigt. Wenn der Termin einmal eine Zeit
> bekommen hat dann geht es in beide Richtungen.

**Status:** 🧪 fertig, Tests laufen — wartet auf Browser-Smoke-Test
**Filed:** 2026-06-21

### Analyse

Sehr aufschlussreich: nach der ersten Erstellung sieht Nextcloud
den Termin als AllDay. Sobald der Termin **einmal** in Nextcloud
mit einer Uhrzeit versehen wurde, funktioniert die bidirektionale
Synchronisation. Daraus folgt: das initiale `PUT` aus unserer App
nach Nextcloud trägt die Zeit-Information **nicht in dem Format**,
das Nextcloud erwartet.

**Hypothese A — `DATE` statt `DATE-TIME` im DTSTART.** iCalendar
unterscheidet zwei DTSTART-Formate:

```
DTSTART:20260614T100000Z          ← DATE-TIME (timed event)
DTSTART;VALUE=DATE:20260614       ← DATE (all-day event)
```

In `EntryMapper.toICalendarText` wird der `DateStart` aus einem
`ICalDate` gebaut über `toIcalDate(start, zone, allDay)`:

```java
private ICalDate toIcalDate(LocalDateTime ldt, ZoneId zone, boolean dateOnly) {
    Instant instant = ldt.atZone(zone).toInstant();
    return new ICalDate(Date.from(instant), !dateOnly);
}
```

Der `!dateOnly` Parameter steuert ob Biweekly das DateTime mit
Zeit-Komponente serialisiert. Wenn `dateOnly=true` (= allDay
true), produziert biweekly ein `DATE`-only Format.

Frage: ist `allDay` in unserem Save-Flow korrekt? Aus
`EventEditorDialog`:

```java
DateTimePicker start = new DateTimePicker(K_FIELD_START, "Start");
start.setValue(entry.getStart());
...
if (start.getValue() != null) entry.setStart(start.getValue());
```

Das `entry.isAllDay()` wird im EventEditorDialog NICHT
explizit gesetzt — also bleibt es vermutlich beim Default-Wert
des `Entry`. Wenn ein NEUER Entry konstruiert wird:

```java
Entry draft = new Entry(UUID.randomUUID().toString());
```

dann ist `draft.isAllDay()` möglicherweise `null` (oder
default-true je nach FullCalendar-Vaadin-Stefan-Semantik).

**Wahrscheinlich**: `entry.isAllDay()` ist auf `null`/`true` bei
neu erzeugten Entries, was unser `toICalendarText` als
`allDay=true` interpretiert und `DTSTART;VALUE=DATE:…` schreibt.
Nextcloud zeigt's korrekt als AllDay an, weil's so auch
gesendet wurde.

Dass die bidirektionale Sync danach klappt: sobald Nextcloud
den Termin mit Uhrzeit setzt, kommt er als korrektes
`DTSTART:YYYYMMDDTHHMMSSZ` zurück, unser Reader setzt
`entry.setAllDay(false)`, und ab da läuft's.

**Fix:** Im Save-Pfad explizit `entry.setAllDay(false)` setzen
wenn `start.getValue()` und `end.getValue()` BEIDE eine
Zeit-Komponente haben (also nicht 00:00:00 beide). Oder
default für neue Entries: `setAllDay(false)`.

**Hypothese B — TZID-Kollision.** Falls wir `DATE-TIME` ohne
TZID schreiben (z.B. "DTSTART:20260614T100000" ohne Z), könnte
Nextcloud das als floating-time interpretieren und
fehlbehandeln. Weniger wahrscheinlich aber prüfen.

**Verifikation:** Server-Log nach Save eines neuen Termins
prüfen — `PUT` body lesen. Wenn `DTSTART;VALUE=DATE:` →
Hypothese A bestätigt. Wenn `DTSTART:` mit Zeit aber Nextcloud
ignoriert → Hypothese B.

### Betroffene Features

- **FEATURE_BACKLOG.md #1, #2, #3, #4, #5** — alle Termin-Render-
  Features sind vom korrekten Datum-Round-trip abhängig. Wenn ein
  neuer Termin als AllDay statt timed gesendet wird, sind seine
  Anzeige in der Quelle UND alle abhängigen Features (z.B.
  Per-Event-Farbe sichtbar auf timed Events — siehe BUG #5)
  betroffen.

### Reproduktion

1. Mit Nextcloud verbinden.
2. In unserer App einen NEUEN Termin anlegen, z.B. 10:00–11:00.
3. Save.
4. Nextcloud-Web-UI öffnen → Termin ansehen.
5. **Tatsächlich:** Nextcloud zeigt ihn als Tagestermin
   (AllDay).
6. In Nextcloud den Termin editieren, Zeit explizit setzen,
   speichern.
7. In unserer App reload.
8. **Ab jetzt:** Termin korrekt als timed event, bidirektional
   funktioniert.

**Erwartet:** Schritt 5 zeigt den Termin mit Zeitangabe
10:00–11:00, nicht als Tagestermin.

### Touchpoints

- `chronogrid: ui/EventEditorDialog.java` (Save-Handler ~Zeile
  234 ff.) — vor `entry.setStart(...)` ggf. explizit
  `entry.setAllDay(false)` setzen
- `chronogrid: ui/ChronoGrid.java#openNewEventEditor` —
  Konstruktion eines neuen `Entry`: ggf. Default-Setting für
  `setAllDay(false)` bei timed-Default
- `chronogrid-core: mapping/EntryMapper.java#toICalendarText`
  (~Zeile 376 ff.) — `boolean allDay = Boolean.TRUE.equals(
  entry.isAllDay());` — prüfen, ob der Default bei null
  korrekt false ist (ja, ist es: `Boolean.TRUE.equals(null) ==
  false`)

### Größe

S. Eine Zeile im EventEditorDialog Save-Handler:
`entry.setAllDay(false);` falls beide DatePicker-Werte eine
sinnvolle Zeit haben. Plus 1 Integration-Test oder
Browser-Smoke-Test. Geschätzt 30 Minuten.

### Risiko / offene Fragen

- **AllDay-vs-Timed-UX:** der EventEditorDialog hat aktuell
  KEINEN expliziten AllDay-Toggle. Sollte er einen bekommen?
  Vermutlich ja — sonst ist der User abhängig vom impliziten
  Verhalten (timed-default bei Uhrzeiten ≠ 00:00).
- **Bestehende AllDay-Events**: wenn der User einen
  bestehenden AllDay-Termin editiert, darf der Fix den
  AllDay-Status nicht überschreiben.
- **Hypothese muss verifiziert werden**: brauche entweder einen
  Server-Log-Dump nach Save (PUT body wäre ideal) oder direkte
  Inspektion über Nextcloud's Roh-iCal-Download.

---

## #12 — Per-Event-Farbe auf Nextcloud: Roundtrip verliert die Farbe in beide Richtungen

> **Original (erste Beobachtung):** In NextCloud kann ich die
> Farbe setzen, die wird dann aber nicht in die UI übernommen bei
> einem Reload/Refresh.
>
> **Erweiterung (BUG #5 Smoke-Test, 2026-06-22):** Selbst der
> einfache Roundtrip in der Gegenrichtung schlägt fehl — wenn
> die Farbe in unserer App auf einem Nextcloud-Termin gesetzt
> wird, „verliert der Termin wieder seine Farbe, bzw. kommt
> wohl nicht in Nextcloud an".

**Status:** ✅ behoben 2026-06-22 in `40a9b7b` (Reader-Hex-Normalisierung) + `f28d694` (UI-Refresh + Writer Hex→Named) + `c12bb38` (Writer snap-to-nearest für arbiträre Hex). Sven-verifiziert: Farben in Nextcloud's eigener UI sichtbar.
**Filed:** 2026-06-21 (Erweiterung + Diagnose 2026-06-22)

> **Snap-to-nearest Auflösung der Nextcloud-UI-Limitation:** Wo
> ursprünglich nur exakte CSS3-Matches als named token geschrieben
> wurden (sonst Hex pass-through), schreibt der Writer bei
> non-Apple-Targets jetzt **immer** einen named token — bei nicht-
> exakten Matches berechnet `CssColorNames.toNameOrNearest` den
> RGB-Distance-nächsten der 147 CSS3 Tokens. Sven's Repro
> `#6bbd88` snappt zu `darkseagreen` (`#8fbc8f`), Nextcloud's UI
> rendert die Farbe.
>
> Trade-off: Präzision wird gegen UI-Konsistenz getauscht. User
> wählt `#6bbd88` in unserer App → wir schreiben
> `COLOR:darkseagreen` → Nextcloud-UI zeigt darkseagreen → beim
> Read-Back `darkseagreen` → `toHex` normalisiert zu `#8fbc8f` →
> Picker zeigt `#8fbc8f`. Beide UIs konsistent, exakter User-Hex
> geht verloren. Sven hat diesen Trade-off explizit gewählt nach
> dem Akzeptiert-Status zurückgekommen.

### 2026-06-22 Diagnose-Update

Sven hat einen iCal-Body eines Nextcloud-Termins geliefert. Inhalt:

```
BEGIN:VEVENT
UID:38da7c48-7b04-48dc-8aaf-a83a6d039c29
SUMMARY:Zeitslot - grün
DESCRIPTION:Hier kommen BEschreibungen rein
COLOR:darkkhaki
CATEGORIES:tag 2
DTSTART;TZID=/Europe/Berlin:20260611T080000
DTEND;TZID=/Europe/Berlin:20260611T130000
END:VEVENT
```

**Diagnose:** Nextcloud schreibt RFC-7986 `COLOR` korrekt — aber
als **CSS3 named token** (`darkkhaki`), nicht als hex literal
(`#bdb76b`). RFC 7986 §3.8.1.16 erlaubt beides; die Spec sagt
„any CSS colour value". Damit waren Hypothesen A (Collection-
Property), B (X-Property) und D (PUT-Strip) **widerlegt**:
Nextcloud round-trippt COLOR korrekt, in beide Richtungen.

**Echter Bruch:** unser nativer HTML5 `<input type="color">`
Picker im `EventEditorDialog` akzeptiert nur `#rrggbb`. Mein
`normaliseColor` Helper warf `darkkhaki` auf den Fallback
`#1f77b4` (Default-Blau) — deshalb sah Sven „Wert vom Kalender"
im Picker, obwohl der Termin im Grid die richtige Farbe hatte
(Grid akzeptiert CSS named colors direkt über
`entry.setBackgroundColor("darkkhaki")`).

Beim Save passierte ggf. Folgendes: Sven öffnete den Dialog,
Picker zeigte `#1f77b4`, Sven änderte ihn (oder nicht), Save
schrieb den Hex-Wert nach Nextcloud, Nextcloud akzeptierte
`COLOR:#xxxxxx`, beim nächsten Read kam der Hex-Wert zurück, der
Grid zeigte Hex. „passiert nichts" könnte heißen: Sven sah keine
sichtbare Änderung weil entweder (a) er nichts geändert hatte
ohne es zu merken (Picker stand auf Default und blieb dort) oder
(b) die UI re-paintete nicht sofort. Müsste mit dem Hex-Fix
geklärt werden.

### Teil-Fix in `<COMMIT>` — Reader-seitige Normalisierung

`EntryMapper.toEntry` normalisiert CSS named tokens jetzt zu Hex
über einen neuen `CssColorNames`-Lookup. Folge: nach jedem Read
ist `CUSTOM_ENTRY_COLOR` immer entweder Hex oder ein unbekannter
Token (pass-through), niemals ein bekannter CSS named token.
Damit zeigt der Picker beim Dialog-Open die tatsächliche Farbe.

`CssColorNames` enthält die volle CSS3-Liste (147 Einträge inkl.
gray/grey-Doppelungen + `rebeccapurple`), case-insensitive,
whitespace-tolerant, mit pass-through für unbekannte Tokens.

### Was noch offen ist

Sven muss verifizieren ob nach diesem Fix:
1. Der Picker bei einem Nextcloud-Termin mit `COLOR:darkkhaki`
   die richtige Khaki-Farbe zeigt (statt Default-Blau).
2. Beim Save einer geänderten Farbe der neue Hex-Wert sichtbar
   in Grid + Nextcloud-UI ankommt.

Wenn (1) klappt aber (2) nicht: liegt ein eigenständiger Save-
Bug vor, dann zurück zur Diagnose.

### 2026-06-22 Folge-Diagnose nach Sven's Save-Test

**Sven's Beobachtungen:** Reader-Fix funktioniert ✓. Aber drei
neue Symptome aufgetaucht:
1. Nach App-side Save: manueller Reload nötig damit die Farbe
   im Grid sichtbar ist.
2. Farbe „kommt nicht in Nextcloud an" — UI zeigt sie nicht.
3. Nextcloud→App-Read funktioniert weiter.

**Diagnose-Logger** im PUT-Pfad (CalDavClient) + Read-Pfad
(EntryMapper) eingebaut. Sven hat die Logs geliefert:

```
[BUG#12 DIAG] PUT(update) → color-line=COLOR:#f08ee8 → HTTP 204
[BUG#12 DIAG READ] uid=... rawColor='#f08ee8' stored=#f08ee8
```

**Was die Daten zeigen:**
- Unser App schreibt `COLOR:#f08ee8` korrekt durch (Hex, kein
  Strip in unserem Code).
- Nextcloud akzeptiert mit HTTP 204 und persistiert den Wert.
- Beim nächsten REPORT kommt `#f08ee8` 1:1 zurück. Roundtrip
  in unserer App funktioniert.

**Ergänzende Diagnose von Sven:** Nextcloud hat einen
„Custom-Farbpicker" der Hex akzeptiert, **aber** Nextcloud's UI
rendert die Farbe NUR wenn die `COLOR:`-Property eine
**CSS named token** enthält (wie `olive`, `red`). Arbiträre
Hex-Werte werden im iCal-Body korrekt persistiert, aber in
Nextcloud's UI als „keine Farbe" dargestellt.

Beweis im iCal-Export den Sven geliefert hat:

```
COLOR:olive   ← user picked #808000 in Nextcloud's Custom-Picker
                 → Nextcloud konvertiert exakte Matches zu named
```

### Drei-teiliger Fix (commit `<COMMIT>`)

**Teil 1 (Reader-Hex-Normalisierung):** schon im ersten Anlauf
implementiert über `CssColorNames.toHex`. EntryMapper.toEntry
normalisiert `darkkhaki` → `#bdb76b` damit der HTML5-Picker
den Wert anzeigen kann.

**Teil 2 (UI-Refresh):** `persistSave` ruft jetzt
`refreshAll()` statt `refreshItem(persisted)`. FullCalendar v6's
`refreshItem` pickt die geänderten Background/Border-CSS-
Variablen nicht zuverlässig auf bei Farb-Updates; refreshAll
ist robust und für die typischen Wochen-/Monatsranges nicht
spürbar teurer.

**Teil 3 (Writer Hex→Named für Nextcloud):** neue
`CssColorNames.toName(hex)`-Reverse-Lookup,
`EntryMapper.toICalendarText` bekommt einen
`preferNamedColors`-Parameter, `CalendarService.save` setzt
ihn auf `!isAppleProviderUri(target)`. Folge:

- Apple-Targets: weiter Hex (was bei iCloud-Pfaden mit
  BUG #2-DESCRIPTION-Marker auch nötig ist; iCloud's eigene UI
  zeigt sowieso keine Per-Event-Farbe).
- Non-Apple-Targets (Nextcloud + alle anderen): bei exaktem
  Hex→Named-Match wird die named-Form geschrieben, was
  Nextcloud's UI dann korrekt rendert. Falls kein exakter
  Match (z.B. arbiträres `#f08ee8`): Hex pass-through.

Round-trip-Konsistenz: User wählt `#808000` in unserer App →
wir schreiben `COLOR:olive` an Nextcloud → Nextcloud's UI zeigt
olive → CalDAV-Read holt `olive` → CssColorNames.toHex
normalisiert zu `#808000` → unser Picker zeigt korrekt
`#808000`. Symmetrisch.

### Was noch offen ist (3. Smoke-Test)

Sven muss verifizieren ob:
- (a) Nach App-side Save erscheint die neue Farbe DIREKT im
  Grid (kein manueller Reload mehr nötig).
- (b) Beim Schreiben eines Hex-Werts der einer CSS named color
  entspricht (z.B. `#808000` = olive) zeigt Nextcloud's UI die
  Farbe.
- (c) Beim Schreiben eines arbiträren Hex-Werts (z.B. `#f08ee8`)
  bleibt der Wert im Grid sichtbar (Nextcloud's UI rendert
  nichts, was OK ist — wir können nicht mehr machen ohne
  Präzisionsverlust per nearest-match).

### Analyse

Bug ist **beidseitig**: weder App→Nextcloud noch
Nextcloud→App preserviert die Farbe. Komplementäre Diagnose-
Frage zu BUG #7 (wo der Schreibpfad Apple-spezifisch parametriert
wurde mit der Annahme „Nextcloud round-trippt COLOR korrekt" —
Sven's Test widerlegt diese Annahme empirisch).

Vier mögliche Bruchstellen (zwei für jede Richtung):

**Richtung App→Nextcloud→App** (neuer Befund, BUG #5 Smoke-Test):

**Hypothese D — Nextcloud strippt RFC-7986 `COLOR` beim PUT.**
Möglicherweise filtert Nextcloud's Validator beim PUT alle
Properties, die er nicht in seinem internen Datenmodell
abbildet — analog zu Apple's bekanntem Verhalten beim
User-Edit, aber bereits beim Server-side-Receive. Da wir
durch BUG #7 für Nextcloud explizit den
DESCRIPTION-Marker WEGGELASSEN haben, gibt es keinen
Fallback — die Farbe wird zwangsläufig verworfen.

**Hypothese E — Server akzeptiert COLOR aber zeigt's nicht in
seiner UI an UND filtert ihn beim Read-Back.** Weniger
wahrscheinlich, weil ein CalDAV-Server beim REPORT eigentlich
den iCal-Body 1:1 weiterleitet, nicht filtert. Aber prüfbar.

**Richtung Nextcloud→App** (ursprünglicher Befund):

**Hypothese A — Nextcloud setzt die Farbe nicht als RFC-7986
`COLOR` auf dem Event, sondern als Property auf der
**Kalender-Collection** (`<x-apple-calendar-color>` o.ä. via
PROPPATCH).** Nextcloud's Per-Event-Color-Feature ist im
CalDAV-Standard NICHT genormt — Nextcloud könnte's
ausschließlich über sein eigenes UI darstellen und gar nicht in
den iCalendar-Body legen.

**Hypothese B — Nextcloud schreibt die Farbe in eine eigene
X-Property** wie `X-NEXTCLOUD-COLOR` oder
`X-APPLE-CALENDAR-COLOR`. Unser EntryMapper liest aktuell nur
`COLOR` und unseren eigenen `[chronogrid-color: …]`-Marker.
Eine Nextcloud-spezifische X-Property würden wir ignorieren.

**Hypothese C — Nextcloud schreibt zwar `COLOR`, aber im
falschen Format** (z.B. CSS named color `red` statt
`#ff0000`). Unser Reader filtert nur `isBlank()`, sollte aber
auch named colors akzeptieren — Test nötig.

**Verifikation:** Brauche von Sven idealerweise zwei iCal-
Bodies aus Nextcloud:

1. **Nach App-Save mit Farbe**: Ein Termin den unsere App
   mit Farbe auf Nextcloud geschrieben hat. iCal-Body via
   Nextcloud-Web-UI → Termin → „iCalendar export" oder
   `curl -u user:pass https://nx93157.../event.ics`.
   - Wenn `COLOR:#xxxxxx` im Body steht → Hypothese D
     ausgeschlossen, der Server hat's gespeichert. Read-back
     liefert dann aber nichts → fokussiere auf C oder
     Reader-Pfad.
   - Wenn `COLOR` fehlt → Hypothese D bestätigt, Nextcloud
     strippt.
2. **Nach Nextcloud-UI-Setze mit Farbe**: Termin in
   Nextcloud's UI Farbe wählen, save, dann iCal-Body
   abrufen.
   - Wenn `COLOR:` da → Hypothese A/B ausgeschlossen,
     Reader-Bug.
   - Wenn nur `X-NEXTCLOUD-COLOR:` o.ä. → Hypothese B.
   - Wenn weder noch im iCal-Body → Hypothese A bestätigt,
     Farbe lebt nur auf der Collection-PROPPATCH-Ebene.

**Quick-Fix-Pfad falls D bestätigt:** den DESCRIPTION-Marker
auch für Nextcloud schreiben (effektiv #7 rückgängig für
Nextcloud), weil die Marker-Noise das geringere Übel als
„Farbe verloren" ist. Das wäre eine Anpassung am
`isAppleProviderUri`-Aufruf in `CalendarService.save` —
entweder „Marker IMMER schreiben" als sicherer Default oder
einen Provider-Profile-Switch der je Server entscheidet.

**Reader-Side-Erweiterung (vermutlich nötig):** zusätzlich zu
COLOR + DESCRIPTION-Marker auch X-NEXTCLOUD-COLOR /
X-APPLE-CALENDAR-COLOR (falls Hypothese B). Liest unsere App
das auch nicht aus PROPPATCH auf der Collection (Hypothese A),
wäre der einzige Pfad: separater PROPFIND auf jede
Collection-Subscription, der die `<x-apple-calendar-color>`
property liest und als Per-Subscription-Farbe in den
`stateStore.writeSubscriptions` Eintrag übernimmt.

### Betroffene Features

- **FEATURE_BACKLOG.md #1** — *Per-event colour with a
  calendar-coloured edge stripe*: aktuell explizit nur
  unsere-App → Provider. Read-back vom Provider wäre die
  Symmetrie.
- **Bezug zu #7**: dort haben wir die Schreibseite Provider-
  spezifisch gemacht (Apple-Marker vs. Standard-COLOR).
  Hier geht's um die Leseseite — wie liest unsere App
  Provider-spezifische Farb-Felder?

### Reproduktion

1. Mit Nextcloud verbinden.
2. In unserer App einen Termin auf dem Nextcloud-Kalender
   anlegen.
3. Nextcloud-Web-UI öffnen.
4. Im Nextcloud-Termin die Farbe ändern (z.B. auf Rot), in
   Nextcloud speichern.
5. In unserer App Reload-Button klicken.
6. **Tatsächlich:** Termin in unserer App in der
   Subscription-Default-Farbe (= Kalender-Quell-Farbe), nicht
   in der in Nextcloud gewählten Rot.

**Erwartet:** Termin in unserer App in Rot — dem in Nextcloud
gesetzten Wert.

### Touchpoints

- `chronogrid-core: mapping/EntryMapper.java#toEntry` (Color-
  Read-Block ~Zeile 222 ff.) — Reader-Kette erweitern um
  zusätzliche X-Properties die Provider statt COLOR verwenden
- `chronogrid-core: client/CalDavDiscovery.java` —
  möglicherweise erweitern um Collection-Level-Color-Read
  (PROPFIND mit `<x-apple-calendar-color>`)
- `chronogrid-core: service/CalendarService.java#fromConnections`
  — wenn Hypothese A: Color aus Collection-PROPFIND in die
  Subscriptions übernehmen
- `chronogrid: state/CalendarStateStore.java#readEntryColour`
  — der lokale Store für BUG #2 wäre eine
  Auffangschicht, wenn der CalDAV-Read den Wert verfehlt —
  aber die Spec sagt der Read sollte den Wert finden, nicht
  fallback

### Größe

S–M, abhängig von Hypothese. Wenn B (X-Property): ~30 Min,
Reader-Erweiterung + Tests. Wenn A (Collection-PROPFIND):
M, neuer Read-Pfad in CalDavClient + Integration in
fromConnections, 1–2 Stunden.

### Risiko / offene Fragen

- **Symmetrie zur Schreibseite:** wenn der User die Farbe in
  unserer App ändert, schreiben wir COLOR + DESCRIPTION-Marker
  (für iCloud) / COLOR only (für Nextcloud). Wenn Nextcloud
  die Schreib-Form unseres Writes liest und seinerseits in
  eine andere Form transformiert, könnten wir round-trip
  Kompatibilität verlieren.
- **Provider-Sniffing wächst.** BUG #7 hat den Apple-Detect
  eingeführt. Wenn #12 einen Nextcloud-Detect bringt + die
  Tag-Filterung evtl. weitere, wird's hilfreich, einen kleinen
  `CalDavProviderProfile`-Enum einzuführen statt überall
  hostname-checks.
- **Apple's Calendar-Collection-Color in iCloud-eigener UI**:
  iCloud hat selbst eine Collection-Level-Color. Wenn unser
  Read-Pfad das auch hochziehen würde, wäre der Default-Color-
  Pfad symmetrisch zu Nextcloud — Konsistenz-Gewinn.