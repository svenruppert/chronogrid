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

**Status:** 🔬 analysiert
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

**Status:** 🟡 erfasst — Hypothesen vorhanden, brauche Server-Log nach Reload-Klick zur Bestätigung
**Filed:** 2026-06-21

### Analyse

Brauche noch Daten zur sicheren Diagnose, aber mehrere
Bruchstellen sind denkbar. Drei wahrscheinlichste Pfade,
sortiert nach Plausibilität basierend auf bisherigen Logs:

**Hypothese A — Discovery-State-Verlust bei Reload.** In den
früheren Logs sehe ich:

```
CalendarService ready: primary=https://caldav.icloud.com/ readClients=1
  ↓ Discovery
Discovery step 3: found 6 calendar(s) under https://p124-caldav.icloud.com:443/...
CalendarService ready: primary=https://p124-caldav.icloud.com:443/.../ readClients=2
```

Initial ist nur die Legacy-Primary-URI (`caldav.icloud.com`) als
Client da; die liefert HTTP 403. Erst Discovery erweitert auf
die echten Per-Kalender-URIs. Wenn ein Reload-Klick die
Discovery-State zurücksetzt aber nicht neu triggert, hätten wir
wieder readClients=1 mit 403 → keine Entries → UI leer.
**Wahrscheinlichkeit: hoch.**

**Hypothese B — `visibleUris()` Logik filtert alle Subscriptions
weg.** In `ChronoGrid#visibleUris` (Zeile 1280):

```java
if (subs.isEmpty()) return null;       // null = "no filter" - alle durchlassen
Set<URI> out = new HashSet<>();
for (CalendarSubscription cs : subs) {
    if (cs.visible()) out.add(cs.uri());
}
return out;
```

Wenn nach Reload alle Subscriptions als `visible=false` markiert
sind (z.B. weil eine Re-Initialization sie default-unsichtbar
setzt), kommt ein **leeres Set** zurück (nicht null!) und der
Filter in `rangeWithStatus` (Zeile 687–700) wirft alle Entries
raus. **Wahrscheinlichkeit: mittel.**

**Hypothese C — Auth/Session-Verlust bei Reload.** Reload triggert
ein UI-Rebuild, der CalendarService wird neu konstruiert, aber
ohne die korrekten App-Specific-Passwords aus dem State. Folge:
alle Clients geben 401/403 zurück. Im Log wäre das sichtbar als
HTTP-Status-Lines pro Client. **Wahrscheinlichkeit: mittel.**

**Verifikation:** Sven, falls du den Bug reproduzieren willst —
machen, dann Server-Log auf die Sequenz von Lines unmittelbar
nach dem Reload-Click hier reinpasten:
- `CalendarService ready: …` Zeilen (zeigt readClients)
- `REPORT VEVENT … -> N entries` oder `-> HTTP …` (zeigt
  Fetch-Resultat)
- `fanOut across N client(s) produced X entries total`
- `visibleUris` falls relevant — kann ich ad-hoc-Logger
  hinzufügen wenn die obigen nicht reichen

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

### Touchpoints (vermutet)

- `chronogrid: ui/ChronoGrid.java#refresh-Button-Handler`
  (Zeile 383–389) — ruft `calendar.getEntryProvider().refreshAll()`
- `chronogrid: ui/ChronoGrid.java#visibleUris` (Zeile 1280) — wenn
  Hypothese B
- `chronogrid: ui/ChronoGrid.java#rangeWithStatus` (Zeile 660+) —
  Fetch + Filter-Pipeline
- `chronogrid-core: service/CalendarService.java#fanOut` — wenn
  Hypothese A/C
- `chronogrid-core: client/CalDavDiscovery.java` — wenn Hypothese A

### Größe

Unbekannt bis Ursache eindeutig. Wenn Hypothese A → M (Discovery-
State-Persistenz zwischen Reloads). Wenn B → S (`visible()`-Default
korrigieren). Wenn C → M (Auth-State-Management). Erste Klärung
braucht Log-Dump nach Reload-Klick.

### Risiko / offene Fragen

- **Reproduzierbarkeit unsicher.** Tritt der Bug bei jedem
  Reload auf oder nur unter bestimmten Bedingungen (z.B. nur
  nach Auth-Refresh, nur nach Subscriptions-Dialog-Eingriff)?
- **Multi-Account-Frage:** sind die „mehr als einen Kalender"
  zwei iCloud-Accounts oder zwei Kalender desselben Accounts?
  Macht Diagnose unterschiedlich.

---

## #5 — Per-Event-Farbe nicht sichtbar bei timed Events (nur bei Tagesterminen erkennbar)

> **Original:** Ein Kalendereintrag mit einer eigenen Farbe -
> keine Farbkennung der eigenen Farbe wenn der Termin kein
> Tagestermin ist.

**Status:** 🟡 erfasst — Hypothese vorhanden, brauche DevTools-Inspect zur Bestätigung
**Filed:** 2026-06-21

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