# Blog-Post-Konzept

**„From feature to add-on — Wie aus einem Vaadin-Demo eine
wiederverwendbare Komponente wird (am Beispiel von ChronoGrid)"**

Konzept-Dokument für den Blog-Post über die Extraktion von ChronoGrid
aus dem ursprünglichen CalDAV-Demo-Projekt. Nicht der Post selbst —
die Storyline, die Beats und die Code-Excerpts, die im Post landen
sollen.

Begleitendes Material:
- [`CHRONOGRID_EXTRACTION.md`](CHRONOGRID_EXTRACTION.md) — die
  technische Design-Rationale (post-hoc geschrieben)
- [`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md) — geparkte Folge-Ideen

---

## 1. Konzept

### Die zentrale Spannung

Eine Funktion in einem Demo zu implementieren ist **nicht dasselbe**
wie eine Komponente zu bauen. Ein Feature im Demo darf alles vom Host
voraussetzen: Routing, Auth, i18n, Session-Storage, Theme. Eine
Komponente kann nichts davon — sie muss diese Dinge als **Seams**
herausziehen und vom Konsumenten injizieren lassen.

Der Post zeigt, wie aus einer hardwired CalDAV-Calendar-View in einem
jSentinel-gesicherten Vaadin-Demo eine drop-in-fähige Vaadin-Flow-
Add-on-Komponente wird — **ChronoGrid**. Drei Phasen, alle Tests grün,
nichts wegwerfen.

### Zielgruppe

- **Senior Java/Vaadin-Devs**, die ein bestehendes Feature
  wiederverwendbar machen wollen (z. B. weil mehrere interne Projekte
  Calendar-Views brauchen)
- **Library-Autoren**, die noch nie für Maven Central publiziert
  haben und einen worked-example wollen
- **Vaadin-Flow-Add-on-Entwickler**, die sich am Standard-Pattern
  (Add-on-Modul + Demo-Modul im Reactor) orientieren

### Was der Leser nach dem Post weiß

1. **Welche Couplings einen Demo-View an seinen Host binden** —
   konkret: 8 Stellen, die in ChronoGrid migriert wurden.
2. **Warum „in-place decouple, dann extrahieren" die richtige
   Reihenfolge ist** — und welche Risiken die umgekehrte Reihenfolge
   hat.
3. **Wie man drei Integration-Seams sauber zieht**: persistence
   (`StateStore`), i18n (`Messages`), und der optionale fully-built
   Service.
4. **Wie ein Multi-Modul-Maven-Reactor mit Demo-als-Integration-
   Harness aufgesetzt wird** — und warum das dem oft empfohlenen
   Repo-Split überlegen ist.
5. **Welche Maven-Hürden auf dem Weg zu Maven Central warten** —
   `<scm>`/`<developers>` Pflicht, Sources/Javadoc-Jars, license-
   maven-plugin-Quirks, GPG-Signing.
6. **Wie eine Breaking-Rename-Ceremony abläuft** — Beispiel an
   ChronoGrid: 00.10.x → 01.00.0, vollständiger Namespace-Move,
   GitHub-Release-Migration.

### Hook (erster Absatz)

> Eine Calendar-View ist in zwei Stunden geschrieben. Eine wieder-
> verwendbare Calendar-Komponente braucht drei Iterationen, ein
> Multi-Modul-Maven-Setup und ein gutes Auge für Seams. Dieser Post
> zeigt am Beispiel von ChronoGrid, wie aus einer hardwired Vaadin-
> Flow-View ein publish-ready Add-on wird — ohne Big-Bang-Rewrite,
> ohne Test-Verlust.

### Anti-Hook (was der Post NICHT ist)

- **Kein „Hello-World-Vaadin-Add-on"-Tutorial** — wer noch nie eine
  Vaadin-Composite gebaut hat, ist hier zu früh.
- **Kein CalDAV-Protokoll-Tutorial** — das CalDAV-Detail kommt aus
  einem anderen Post; hier ist es Beispiel-Backend, nicht Lehrstoff.
- **Kein Maven-Central-Deployment-Guide** — das Setup wird gezeigt,
  die Sonatype-Ceremony (Credentials, GPG, Promote) bleibt
  separate Reading.

---

## 2. Story-Bogen (Drei Akte)

### Akt I — Die unsichtbaren Couplings

Ausgangslage: `CalendarView` lebt im Demo, ist mit `@Route`,
`@VisibleFor`, `MainLayout`, `I18nSupport`, direkten
`VaadinSession.getAttribute(…)`-Calls und einer statischen
`CalendarServiceProvider`-Klasse verdrahtet. Funktioniert tadellos —
aber **niemand sonst kann das benutzen**.

**Beat**: Eine Tabelle der 8 Coupling-Stellen mit Zeilennummern aus
dem damaligen `CalendarView.java`. Visuell: „here's everything that
makes this not-a-component".

**Conclusion des Akts**: „Diese 8 Stellen sind unsere TODO-Liste."

### Akt II — Die Seams

Drei Abstraktionen werden eingezogen — alle in-place, ohne dass die
Demo nur eine Sekunde nicht-läuft:

1. `CalendarStateStore` — Interface über die 4 Session-Keys
2. `CalendarMessages` — funktionales Interface über den i18n-Lookup
3. `CalendarRouteView` — Host-side Wrapper mit den Annotationen

Plus: `PageHeader` (Host-UI-Brick) wird durch eine lokale CSS-Klasse
ersetzt, `CalendarServiceProvider` wird hinter `defaultServiceFromPreset()`
gekapselt.

**Beat**: Code-Diff einer Methode VORHER/NACHHER — z. B.
`readNDaysPreference()`. Vorher: `VaadinSession.getCurrent().getAttribute(...)`.
Nachher: `stateStore.readNDays(7)`.

**Conclusion des Akts**: „284 Tests grün, kein User merkt was —
aber strukturell ist die View jetzt portabel."

### Akt III — Vom Demo zum Reactor

Single-Modul wird zum Reactor mit drei Modulen:
- `chronogrid-core` (headless CalDAV)
- `chronogrid` (Vaadin-Add-on)
- `chronogrid-demo` (Consumer + Integration-Harness)

Das publishable Triplet (main + sources + javadoc) entsteht,
Sonatype-Setup steht, Major-Bump 00.10.x → 01.00.0 nach Rebrand.

**Beat**: Reactor-Tree als ASCII-Diagramm — zeigt visuell, dass die
Module einander **kennen aber nicht durchdringen**.

**Conclusion**: „Push, Tag, Release — ChronoGrid v00.10.00 ist online,
Maven-Central-Deploy ist eine Sonatype-Credentials-Ceremony entfernt."

---

## 3. Vorgehen / Section-by-Section

### S1. Was du dafür brauchst

- Vaadin Flow 25.x oder neuer
- JDK 21+ (Code-Beispiele auf 26)
- Ein bestehendes Demo / Feature, das du extrahieren willst
- Maven-Wrapper im Projekt

### S2. Die Coupling-Audit

Was den Code an den Host bindet:

| Coupling | Wie man's findet | Im ChronoGrid-Fall |
|---|---|---|
| `@Route` / `@VisibleFor` | `grep -rn "@Route\|@VisibleFor"` | CalendarView.java:86–87 |
| `implements I18nSupport` | grep `implements.*I18nSupport` | CalendarView.java:96 |
| `VaadinSession.getAttribute(...)` | grep `VaadinSession.getCurrent` | 16 Stellen |
| Host-UI-Bricks (`PageHeader`, `MainLayout`) | grep imports `com.svenruppert.flow.views.ui.*` | PageHeader-Aufruf |
| Static-Holder (`*Provider`) | grep `class.*Provider` | CalendarServiceProvider |

**Take-away**: „Bevor du etwas extrahierst, schreib die Liste auf. 8
Couplings klingen viel; nach der Audit weißt du, dass es 8 sind und
nicht 50."

### S3. Phase 1 — In-place Decouple (kein pom-Change)

**Warum diese Reihenfolge?**

Wer zuerst die Module aufsetzt und dann decouplen will, hat zwei
Probleme gleichzeitig: einen kaputten Reactor UND einen kaputten View.
Wer zuerst decouplet, hat nach jedem Schritt einen grünen Build.

**Konkret**: drei kleine Interfaces einziehen:

```java
public interface CalendarStateStore {
    Optional<CalDavConnectionConfig> readConnection();
    void writeConnection(CalDavConnectionConfig cfg);
    // ... 6 weitere read/write Pärchen
}

@FunctionalInterface
public interface CalendarMessages {
    String tr(String key, String fallback, Object... args);

    static CalendarMessages fallbackOnly() { ... }
}
```

Beide bekommen Default-Impls (`VaadinSessionCalendarStateStore`,
`fallbackOnly()`) damit existierende Tests ohne Änderung weiterlaufen.

**Beat im Post**: Code-Snippet mit dem migrierten
`readNDaysPreference()` — drei Zeilen Diff, klare Wirkung. Plus ein
Sequence-Diagramm das zeigt: vorher → CalendarView → VaadinSession;
nachher → CalendarView → StateStore-Interface → impl → VaadinSession.

### S4. Phase 1 — Die Wrapper-View

Die ANNOTATIONEN gehören nicht auf die Komponente, sondern auf einen
Host-side Wrapper:

```java
@Route(value = "calendar", layout = MainLayout.class)
@VisibleFor(USER)
public final class CalendarRouteView extends Composite<Component>
        implements I18nSupport {
    public CalendarRouteView() {
        var store    = new VaadinSessionCalendarStateStore();
        var messages = (CalendarMessages) (k, fb, args) -> tr(k, fb, args);
        var view     = new ChronoGrid(store, messages);
        getContent().getElement().appendChild(view.getElement());
    }
}
```

**Beat im Post**: „Vier Zeilen Code in einem 16-Zeilen-Wrapper trennen
deine Komponente von jedem Host-Detail."

### S5. Phase 2 — Reactor

Single-Modul → Maven-Reactor mit drei Sub-Modulen:

```
chronogrid-parent                    (pom — Reactor-Root)
├── chronogrid-core                  (jar — headless CalDAV)
├── chronogrid                       (jar — Vaadin Add-on)
└── chronogrid-demo                  (war — Consumer + IT-Harness)
```

**Warum die Demo behalten?** Sie ist die kostenlose End-to-End-
Integration-Test-Suite. Ein Add-on-Repo ohne Demo verliert die
`@RouteView`-Tests, die ein abstraktes Test-Harness nie hätte.

**Mechanik**:
- `git mv src demo/src`, `git mv package.json demo/package.json`,
  `git mv pom.xml demo/pom.xml`
- Neuer Reactor-Root mit `packaging=pom`, `<modules>`-Liste
- Sub-Modul-Poms erben vom selben Parent (`com.svenruppert:dependencies`)

**Beat**: Diff des Root-Poms — vorher 906 Zeilen War-Pom, nachher 52
Zeilen Reactor-Pom.

### S6. Phase 2 — Die Carve-outs

Per-Modul-Tabelle: was wandert wohin?

| Datei | Zielmodul | Rationale |
|---|---|---|
| `calendar/client/CalDav*.java` | chronogrid-core | Headless, JDK-only |
| `calendar/mapping/EntryMapper.java` | chronogrid-core | Biweekly, headless |
| `calendar/service/CalendarService.java` | chronogrid-core | Result&lt;T,E&gt;-Boundary |
| `views/CalendarView.java` | chronogrid | Vaadin Composite |
| `views/calendar/*Dialog.java` | chronogrid | Vaadin Sub-Components |
| `views/CalendarRouteView.java` | chronogrid-demo | Host-spezifischer Route-Wrapper |

**Beat**: Eine wichtige Subtilität: `EntryMapper` referenziert
`org.vaadin.stefan.fullcalendar.Entry` — das zieht Vaadin transitiv in
`chronogrid-core`. Pragmatische Entscheidung: akzeptieren (eine echte
Trennung würde einen separaten DTO-Layer brauchen — Phase-4 Material).

### S7. Phase 3 — Cleanup + Publish-Prep

**Maven Central Pflicht-Metadaten**:

| Pom-Element | Im Code | Maven-Central-relevant |
|---|---|---|
| `<name>` | „ChronoGrid — CalDAV core" | ✓ |
| `<description>` | inkl. Tagline | ✓ |
| `<url>` | github.com/svenruppert/chronogrid | ✓ |
| `<licenses>` | EUPL 1.2 | ✓ |
| `<scm>` | git URL + connection | ✓ |
| `<developers>` | name + email | ✓ |
| `<organization>` | optional, empfohlen | ⚪ |
| `<issueManagement>` | optional, empfohlen | ⚪ |

Plus: Sources-Jar + Javadoc-Jar via `maven-source-plugin` und
`maven-javadoc-plugin` mit expliziten `<executions>` an die `package`-
Phase. GPG-Signing über das parent-vererbte `_release_sign-artifacts`-
Profil.

**Beat**: Fallstrick mit `license-maven-plugin`. Wenn das Plugin im
Build aktiv ist und schon Quelltexte mit anderem Header hat, **fügt
es einen zweiten Header oben drauf**. Das wiederum lässt Checkstyle
über trailing whitespace meckern. Aus dem Schmerz gelernt: in
ChronoGrid bleibt `license-maven-plugin` in den Add-on-Modulen
deaktiviert.

### S8. Phase 3 — Sources, Javadoc, Repo-URL

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-source-plugin</artifactId>
    <executions>
        <execution>
            <id>attach-sources</id>
            <goals><goal>jar-no-fork</goal></goals>
        </execution>
    </executions>
</plugin>
```

Analog `maven-javadoc-plugin` mit `<doclint>none</doclint>`-Toleranz.

Verifikation: nach `./mvnw -DskipTests install` liegen in jedem Add-on-
Modul drei Jars im target/:

```
chronogrid/target/
├── chronogrid-01.00.00-SNAPSHOT.jar
├── chronogrid-01.00.00-SNAPSHOT-sources.jar
└── chronogrid-01.00.00-SNAPSHOT-javadoc.jar
```

**Beat**: „Ohne dieses Triplet weist Sonatype OSSRH den Release
zurück."

### S9. Release-Ceremony

Sequenz:

1. `./mvnw clean install` → 284/284 green
2. SpotBugs + Checkstyle pro Modul → 0 findings
3. `git tag -a v00.10.00 -m "..."`
4. `git push origin main && git push origin v00.10.00`
5. `gh release create v00.10.00 --notes-file ...`
6. `./mvnw versions:set -DnewVersion=00.10.01-SNAPSHOT` (oder
   manuell — bei Inter-Modul-Deps muss man eh die Pärchen anfassen)
7. Bump-Commit + Push

**Beat**: Was nicht im Release-Tag steckt, kann später nicht
nachgereicht werden. Tags sind unveränderlich; Release-Notes hingegen
schon — am Tag-Commit selbst hängt nur der Code.

### S10. Der Rebrand-Sonderfall

Wenn der Produktname sich ändert (CalendarView → ChronoGrid), ist das
**eine Breaking-Change** und gehört in einen Major-Bump (00.x.x →
01.0.0).

Mechanik in ChronoGrid:

1. Verzeichnisse umbenennen (`calendar-component/` → `chronogrid/`)
2. Java-Package-Move (`com.svenruppert.vaadin.calendar.*` →
   `com.svenruppert.chronogrid.*`) — via `perl -i -pe` über Imports
   und Package-Decls
3. Klassen umbenennen wo Brand-relevant (`CalendarView` →
   `ChronoGrid`). Support-Klassen (`CalendarMessages`,
   `CalendarStateStore`) behalten ihre Domänen-Namen.
4. CSS-Top-Level-Klassen (`.calendar-view` → `.chronogrid`); Sub-
   Component-CSS bleibt (modular, intern)
5. Repo-URLs in poms + README updaten
6. Major-Bump
7. Tag + Release am v00.10.00 als „Pre-Rebrand-Snapshot" mit
   Historical-Note-Header

**Beat**: 176 Files changed, alle Tests grün, kein User-visible
Verhaltensbruch — nur Coordinates. Das ist semver-by-the-book.

### S11. Mono-Repo vs. Split

Häufige Debatte: gehört das Add-on in ein eigenes Repo? Antwort: Bei
**einem Maintainer** und **bei Demo-als-Integration-Harness**: nein.

Tabelle der Trade-offs (Mono-Repo vs Split):

| | Mono-Repo | Split |
|---|---|---|
| Setup-Kosten | 0 | 1–2 Stunden |
| End-to-end-Tests | ✅ frei mit dabei | ❌ neu aufsetzen |
| Cross-cutting PR | 1 PR | 2 PRs in 2 Repos |
| Add-on-Konsumenten-Klon | + Demo-Ballast | nur Add-on |

**Take-away**: „Erst splitten wenn ein konkretes Demand-Signal
auftaucht — bis dahin gewinnt Mono-Repo."

---

## 4. Code-Excerpts für den Post

Die im Post gezeigten Code-Stellen (jeweils mit `<details>`-Toggle
falls länger als 15 Zeilen):

1. **`CalendarStateStore`-Interface** (komplett) — als Vorlage für
   eigene Persistence-Seams
2. **`readNDaysPreference()` Vorher/Nachher** (5 Zeilen vs. 5 Zeilen)
3. **`CalendarRouteView` Wrapper** (komplett — 16 Zeilen)
4. **Reactor-Root-pom.xml** (nur `<modules>` + Description)
5. **maven-source-plugin + maven-javadoc-plugin Executions** (15
   Zeilen)
6. **`gh release create v00.10.00` Befehl** (mit `--notes-file`)
7. **Migration-Skript: bulk perl rewrite der Package-Imports** (eine
   Zeile, aber didaktisch wertvoll):
   ```bash
   find chronogrid-core/src -name "*.java" -print0 | xargs -0 perl -i -pe \
     's|\bcom\.svenruppert\.vaadin\.calendar\b|com.svenruppert.chronogrid|g'
   ```

---

## 5. Takeaways (am Ende des Posts)

- **Eine Demo ist kein Add-on** — eine bewusste Coupling-Audit + drei
  Seams sind das, was den Unterschied macht.
- **In-place decouple, dann extrahieren** — niemals beides
  gleichzeitig.
- **Demo behalten als Integration-Harness** — die end-to-end-Tests
  sind ihren Modul-Slot wert.
- **Maven-Central-Pflichtmetadaten früh hinzufügen** — ist ein
  einmaliger Setup-Schritt, der später nicht nachgereicht werden
  will.
- **Rebrand → Major-Bump** — und der alte Tag bleibt als historischer
  Marker stehen.

---

## 6. Optional follow-up posts

Aus dem Backlog (`FEATURE_BACKLOG.md`) ergeben sich natürliche
Follow-ups:

1. **„Color of an event vs. color of the calendar"** — die per-event-
   colour + calendar-stripe Geschichte aus #1 des Backlogs.
2. **„Maven Central deploy ceremony"** — die Sonatype-Credentials-,
   GPG- und Promote-Choreographie, die in diesem Post bewusst
   ausgeklammert wurde.
3. **„VTODO support in a CalDAV add-on"** — die Todo-Erweiterung
   neben VEVENT.
4. **„Decoupling from FullCalendar's Entry record"** — wie aus einem
   transitive-Vaadin-pulling-headless-Modul ein wirklich Vaadin-freier
   Core wird (Phase-4-Material).

---

## 7. Meta — was beim Schreiben zu beachten ist

- **Code-Pfade kontextualisieren**: nach dem Rebrand zeigen alle
  Pfade auf `chronogrid-core/`, `chronogrid/`, `chronogrid-demo/` —
  nicht auf die historischen `calendar-caldav/` etc.
- **Versionen im Lauftext**: `v00.10.00` (Pre-Rebrand-Tag) ist der
  Anker; ab `v01.00.00` redet der Post von ChronoGrid.
- **Snapshots zeigen**: GitHub-URLs sind stabil (`/tree/v00.10.00/...`
  und `/tree/main/...`); der Post sollte konkrete Permalinks auf
  Code-Stellen einbetten, nicht „blob"-URLs ohne Ref.
- **Tabellen sparsam**: vier Tabellen reichen (Couplings, Carve-out-
  Mapping, Pom-Pflichtfelder, Mono-vs-Split). Mehr macht den Post
  schwerer scannbar.
- **Mermaid-Diagramme**: das Sequence-Diagramm „CalendarView →
  StateStore → impl → VaadinSession" lohnt sich. Der Reactor-Baum
  bleibt ASCII (Mermaid-Tree-Rendering ist immer fragwürdig).
