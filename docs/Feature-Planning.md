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

---

## #7 — Connection-Manager UX-Refactor

> **Original:** zusammengeführt aus den drei
> [`BUGS.md`](BUGS.md)-Einträgen #6, #8, #9 — alle drei beschreiben
> dieselbe UX-Story rund um „eine CalDAV-Verbindung aufsetzen,
> Kalender abonnieren, den Multi-Server-Zustand verständlich
> kommunizieren". Da es keine echten Bugs sind sondern eine
> User-Story-getriebene UX-Überarbeitung, leben sie hier als ein
> einziges Feature weiter (BUGS-Einträge sind als ⚫ verworfen mit
> Verweis hierher markiert).

**Status:** 🟡 geplant — Design-Schritt fehlt noch (siehe „Risiko"
unten)

### Konzept

Aktuell verteilt sich das Verbindungs- und Subscription-Handling auf
drei Toolbar-Buttons mit überlappenden Verantwortlichkeiten:

| Button | Dialog | Zweck heute |
|---|---|---|
| **Settings** | `openSettingsDialog` | Legacy-Single-Connection-Editor (`CalDavConnectionConfig`) — Quelle von BUG #4 |
| **Connections** | `ConnectionsDialog` | Multi-Server-Liste: anlegen / entfernen / Auth + Discovery |
| **Subscriptions** | `SubscriptionsDialog` | Pro-Server-Kalenderliste: Visibility-Toggle + Farb-Override |

Für den User ist „eine Verbindung hinzufügen" *ein* Vorgang — die
Realität ist ein zwei- bis dreistufiger Dialog-Lauf, und der
Legacy-Settings-Pfad führt zu Konsistenzproblemen mit dem
Multi-Server-State.

**Ziel:** ein einziger zusammenhängender Touchpoint, der Connect +
Subscribe + De-Subscribe + Status-Anzeige + Disconnect als einen
zusammenhängenden Flow zeigt. Konkrete Bausteine, die der Design-
Schritt einordnen muss:

1. **Connection-Wizard** für „neue Verbindung": 1. URL eingeben →
   2. Credentials → 3. Discovery-Ergebnis als Kalender-Checkliste
   → Fertig (Default-Visible? Spec offen — siehe Risiko unten).
2. **Single „Connection Manager"-Dialog** mit Server-Liste oben +
   integrierten Pro-Server-Subscription-Cards (statt zwei Dialoge).
3. **Quick-Toggle in der Toolbar**: Color-Dot + Name + Checkbox
   für jede Subscription direkt sichtbar, ohne Dialog.
4. **Atomares „Remove server"** räumt Server + Subscriptions +
   lokale BUG-#2-Farb-Fallbacks in einem Schritt.
5. **Re-Discovery-Button** im Manager: „Neue Kalender suchen" →
   automatisches Hinzufügen mit Default-visible=false.
6. **Bulk-Aktionen**: „alle ein", „alle aus" pro Server-Sektion.
7. **Status-aware Notifications** (aus BUG #9): jeder
   `notifyInfo/Error`-Aufruf bekommt einen Subject-Slot („Server X,
   Kalender Y"), plus eine neue Multi-Server-Summary-Variante
   („3 Server, 7 Kalender, 42 Termine").
8. **Legacy-`readConnection()`-Pfad endgültig deprecaten** und mit
   einer einmaligen Auto-Migration in den Multi-Server-State
   überführen — eliminiert die strukturelle Ursache von BUG #4.

**Touchpoints (Erkundung):**

- `chronogrid: ui/ChronoGrid.java` — Toolbar (Settings, Connections,
  Subscriptions); `openSettingsDialog`, `pruneOrphanServers`,
  `notifyInfo`/`notifyError`/`notifyConflict`-Helper,
  Refresh-/Apply-Connection-/Subscription-Remove-Handler
- `chronogrid: ui/ConnectionsDialog.java` — geht ggf. komplett auf
  im neuen Connection-Manager-Dialog
- `chronogrid: ui/SubscriptionsDialog.java` — desgleichen
- `chronogrid: ui/ServerStatusList.java` — wird in den neuen
  Manager-Dialog integriert (oder durch Quick-Toggle-Liste in der
  Toolbar ersetzt)
- `chronogrid-core: client/CalDavDiscovery.java` — Discovery-
  Ergebnis braucht eine UI-freundliche Auswahl-Repräsentation
  (heute schon `DiscoveredCalendar`)
- `chronogrid: state/VaadinSessionCalendarStateStore.java` — die
  Legacy-Connection-Migration läuft hier (Read-Once, Write-In-Subs,
  Connection-Key löschen)
- `chronogrid-demo: resources/vaadin-i18n/translations*.properties`
  — neue Wizard-/Manager-/Notification-Keys EN+DE

### Größe

L. Echte UX-Überarbeitung mit Wizard- oder Single-Dialog-Design.
Geschätzt 2–4 Tage Implementation + Smoke-Tests + Tests, plus
einen vorgelagerten Design-Schritt (1–2 Stunden) zur User-Story-
Konkretisierung.

### Risiko / offene Fragen

- **Konkrete User-Story fehlt.** Sven hat „intuitiv" + „einfach
  machbar" gesagt — beides nicht spezifizierbar. Vor dem ersten
  Commit braucht es eine Designgespräch-Sitzung: welche
  Klick-Pfade fühlen sich falsch an? Welche fühlen sich
  *richtig* an als Referenz? Wahrscheinlich am besten anhand
  konkreter Screenshots Apple Calendar / Google Calendar / Thunderbird
  als Referenz-UX abklären.
- **Default-Visibility neuer Kalender:** aktivieren-by-default
  (User sieht sofort alles, Spam-Risiko bei Servern mit 20+
  Kalendern) oder deaktivieren-by-default (kein Spam, aber User
  muss aktiv aktivieren). Vorschlag: aktivieren bei <= 5
  Kalendern, sonst Auswahl-UI im Wizard.
- **Discovery-Trigger für Re-Discovery:** manuell per Button oder
  automatisch (Pull alle X Minuten)? Vorschlag: manuell für v1,
  Auto-Pull als separates Folgeticket falls gewünscht.
- **Migration des Legacy-`readConnection()`-State:** existierende
  User mit gesetztem `connection.config`-Attribut müssen einmalig
  in den Multi-Server-State portiert werden. Risiko: Daten-
  Verlust bei der Migration. Mitigations-Plan: Migration nur
  schreibt, löscht den Legacy-Key NICHT — beide Pfade lesen
  parallel weiter bis der nächste Major-Release den Legacy-Key
  endgültig zieht.
- **Notification-Spam bei 5+ Servern:** Multi-Server-Summaries
  („3 Server, 7 Kalender, 42 Termine") sind kompakt, einzelne
  Pro-Server-Erfolg-Toasts könnten nerven. Smart-Delay: einzelne
  Toasts erst zeigen wenn der Server-Fetch > 1 Sekunde dauert.
- **i18n-Aufwand:** jeder Wizard-Schritt + Notification-Variante
  braucht EN+DE-Übersetzungen — Volumen schätzungsweise +20 bis
  +30 neue Keys.
- **Apple iCloud-Provider:** der Connect-Flow muss die
  app-spezifischen-Passwort-Anforderung sichtbar machen (heute
  als Subtitle im Settings-Dialog, im Wizard-Flow potenziell
  prominenter platzieren) — companion-Blog setzt iCloud als
  Demo-Provider voraus, also UX-mäßig der Default-Pfad.

---

## #8 — Parallel/Async-Fetch über mehrere Verbindungen + Fortschrittsbalken

> **Original:** migriert aus [`BUGS.md`](BUGS.md) #10 — kein Bug,
> sondern eine echte Architektur-Erweiterung, daher hier als
> eigenes Feature weitergeführt (BUGS #10 ist als ⚫ verworfen mit
> Verweis hierher markiert).

**Status:** 🟡 geplant

### Konzept

`CalendarService.fanOut` (chronogrid-core, ~Zeile 191) fragt heute
alle konfigurierten Clients **sequenziell** im Render-Thread ab:

```java
for (int i = 0; i < readClients.size(); i++) {
    CalDavClient client = readClients.get(i);
    for (RemoteEvent remote : op.apply(client)) { ... }
}
```

Wer 5 CalDAV-Server verbunden hat, die jeweils ~800 ms REPORT
brauchen, wartet bis zu 4 Sekunden bis der erste Termin im Grid
erscheint. Bei einem hängenden Server warten die anderen alle mit.

**Lösung in zwei Aspekten:**

**(a) Parallel-Fetch.** Pro Client einen `CompletableFuture`-Pfad
gegen einen dedizierten `Executor` (`Executors.newFixedThreadPool(8)`,
Feld auf der `CalendarService`-Instanz). Sammeln per
`CompletableFuture.allOf(...)` für die synchrone Variante; für die
neue async-API direkt Stream-für-Stream aufläufig per Callback.

**(b) Async UI-Update + Fortschrittsbalken.** Vaadin's `UI#access`
ist nötig um aus Worker-Threads den Push auf die UI zu treiben.
Statt fanOut zu blockieren bis alles da ist:

1. Sofort leer zurückgeben (Grid zeigt nichts).
2. Pro Client async fetchen.
3. Sobald ein Client fertig: `UI.access` → Entries dem
   EntryProvider hinzufügen (oder `refreshAll` triggern) +
   `ProgressBar` um 1/N erhöhen.
4. Wenn alle fertig: `ProgressBar` ausblenden, Summary-
   Notification anzeigen (greift Feature #7 auf).

**Architektur-Implikation:**

- `CalendarService.findInRange(...)` bekommt eine async-API-Variante
  (Vorschlag: `findInRangeAsync(LocalDateTime, LocalDateTime,
  Consumer<RemoteEvent>): CompletableFuture<Void>`). Die existierende
  synchrone API bleibt als Wrapper für Tests + simple Caller.
- `ChronoGrid.rangeWithStatus` ruft die async-Variante; FullCalendar
  Vaadin Add-on muss daraufhin geprüft werden, ob der EntryProvider
  asynchron befüllt werden kann oder ob wir mit
  `calendar.getEntryProvider().refreshAll()` nach jedem Client
  arbeiten müssen (mehr Render-Aufwand, aber sicher kompatibel).
- `ProgressBar`-Komponente in die Toolbar neben den Refresh-Button.
  Smart-Delay: erst nach 500 ms Wartezeit einblenden (vermeidet
  Flackern bei schnellen Servern).

### Touchpoints

- `chronogrid-core: service/CalendarService.java#fanOut` — von
  sequenziell auf parallel umstellen
- `chronogrid-core: service/CalendarService.java` — neuer
  `Executor`-Field + Shutdown-Hook
  (`addSessionDestroyListener` → `executor.shutdown()`)
- `chronogrid-core: service/CalendarService.java#findInRange` /
  `findInRangeAsResult` — neue `findInRangeAsync`-Variante
- `chronogrid: ui/ChronoGrid.java#rangeWithStatus` — async-Pfad,
  `UI.access` für Worker-→-UI-Push
- `chronogrid: ui/ChronoGrid.java` — neue `ProgressBar`-Komponente
  in der Toolbar mit Smart-Delay
- `chronogrid-core: client/CalDavClient.java` — Thread-Safety
  bestätigen (HttpClient ist thread-safe, aber unsere
  Wrapper-Methoden müssen das auch sein)
- `chronogrid-demo: resources/vaadin-i18n/translations*.properties`
  — Progress-Label + neue Summary-Notification EN+DE

### Größe

L. Parallel-Fetch + async UI-Update + ProgressBar ist eine echte
Architektur-Erweiterung. Geschätzt 1–2 Tage Implementation + Tests
(Unit-Tests für Thread-Safety, IT für Multi-Server-Reihenfolge-
Unabhängigkeit).

**Reihenfolge-Empfehlung:** **nach Feature #7** angehen, weil:
- Die Multi-Server-Summary-Notification aus #7 ist der natürliche
  Ort für „N Server geladen, M Termine".
- Die ProgressBar-Position in der Toolbar profitiert vom UI-Refresh,
  den #7 sowieso macht.

### Risiko / offene Fragen

- **Thread-Safety in `CalDavClient`.** Heute Single-Thread-Use,
  muss auf Concurrent-Safety geprüft werden (vor allem
  `RemoteEvent`-Iteratoren / interne State-Felder).
- **Executor-Shutdown.** Vaadin-Session-Lifecycle kennt
  `addSessionDestroyListener`, dort sauber `executor.shutdown()`
  aufrufen. Sonst Thread-Leak pro neuer Session.
- **Order-Sensitivity.** Aktuell garantiert sequenzieller Fetch
  eine deterministische Entry-Reihenfolge. Parallel + async ändert
  die Reihenfolge — FullCalendar-Renderer sollte robust sein
  (Events haben Start-Time-Sortierung), aber Edge-Cases wie
  Recurring-Events könnten flackern.
- **Vaadin-Stefan-Add-on-Async-Support.** Muss geprüft werden ob
  der EntryProvider asynchron nachgeladen werden kann oder ob
  wir komplett auf `refreshAll`-after-each-client ausweichen
  müssen.
- **Fortschrittsbalken-UX.** Nervig bei schnellen Servern,
  hilfreich bei langsamen. Smart-Delay (500 ms) ist Pflicht.
- **Komplexität in Tests.** Parallele async-Tests sind notorisch
  flaky — Awaitility o.ä. wird gebraucht, um auf den
  Future-Abschluss deterministisch zu warten.