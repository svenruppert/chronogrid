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

Pipeline ist aktuell leer. Geshippte Features (#6, #7, #8) sind nach
Sven-Verifikation 2026-06-23 ins [`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md)
gewandert — neue Pipeline-Nummern starten ab #9 (Lücken statt
Kompaktierung, monoton aufsteigend).

Neue Konzepte als `## new Feature: …` am Ende dieser Datei anhängen —
sie werden dann nummeriert + mit dem Schema ausgearbeitet.
