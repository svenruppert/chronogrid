# Features-Skipped

**Diese Datei ist das Verzeichnis der verworfenen Features.** Jeder
Eintrag hier beschreibt ein Feature, das im
[`Feature-Planning.md`](Feature-Planning.md) gelandet ist, dort
betrachtet wurde, und am Ende **nicht** geschiffft wird — entweder
aus Aufwand-zu-Nutzen-Gründen, technischer Unmachbarkeit, weil eine
andere Entscheidung das Feature obsolet gemacht hat, oder weil sich
die Anforderung verändert hat.

Die Datei ist auf Deutsch (gleiche Stimme wie `Feature-Planning.md`,
da es sich um eine interne historische Notiz handelt — nicht um
öffentliche Webseiten-Inhalte wie der
[`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md)).

## Lifecycle

```
Feature-Planning.md  ─►  Features-Skipped.md   (diese Datei)
   (in flight)            (verworfen)
```

Ein Eintrag wandert hierher, sobald sein `**Status:**` in Planning
auf `⚫ verworfen` gesetzt wird. Geschiffft-Features wandern
stattdessen in [`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md). Die drei
Dateien zusammen (Planning + Backlog + Skipped) sind erschöpfend —
ein Feature kann nur in einer der drei stehen.

## Eintragsschema

Jeder Eintrag hat die folgende Struktur:

- `## #N — <Titel>` — Nummerierung unabhängig von Planning + Backlog
- `**Status:** ⚫ verworfen YYYY-MM-DD`
- `**Filed:** YYYY-MM-DD` — Datum, an dem die Idee erstmals in
  Planning landete
- `> **Original:** <verbatim Wortlaut, falls vorhanden>`
- `### Konzept` — der Konzept-Block aus Planning, übernommen damit
  ein:e Leser:in sieht, was überlegt wurde
- `### Verworfen, weil` — Pflichtfeld; die Latte ist „würde ein
  zukünftiges Ich beim Lesen verstehen, warum wir es nicht
  geschiffft haben?" (Aufwand, Scope, abgelöst durch andere
  Entscheidung, technisch nicht machbar, …)
- `### Historie` — chronologische Bullet-Liste:
  - `YYYY-MM-DD — filed`
  - `YYYY-MM-DD — <Zwischenphase / Entscheidung>` (optional)
  - `YYYY-MM-DD — verworfen`

## Wiederbelebung

Wenn ein verworfenes Feature später doch wieder relevant wird:

1. Das Feature wird als **frischer Eintrag** in
   `Feature-Planning.md` neu angelegt (neue Nummer im Planning-
   Sequenzraum, kein Wiederverwenden der alten Nummer).
2. Der Skipped-Eintrag bleibt **stehen** als historische Notiz.
3. Eine zusätzliche Zeile in der `### Historie` hier dokumentiert
   die Wiederbelebung:
   `- YYYY-MM-DD — wiederbelebt als Planning #<NEU>`

Damit bleibt nachvollziehbar, dass die Idee schon einmal überlegt
und verworfen wurde, und warum sich die Bewertung geändert hat.

---

*Noch keine Einträge.*
