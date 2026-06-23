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

**Lifecycle:** behobene (✅) und verworfene (⚫) Bugs wandern in
[`BUGS-Closed.md`](BUGS-Closed.md) als unveränderlicher historischer
Record mit Commit- bzw. Begründungs-Verweis. **Diese Datei führt
ausschließlich noch in-flight Bugs** (Status 🟡 / 🔬 / 🔧 / 🧪 / 🚧)
sowie das ungenutzte Nummer-Skelett.

Die Nummerierung bleibt **monoton aufsteigend über beide Dateien
hinweg**: ein verschobener Bug räumt seine Nummer nicht frei. Die
nächste laufende Nummer ergibt sich aus `max(BUGS.md ∪ BUGS-Closed.md) + 1`.

## Übersicht

Schneller Überblick — Snapshot der `**Status:**`-Zeilen aus den
einzelnen Einträgen unten. Bei jedem Status-Wechsel **diese
Tabelle parallel aktualisieren**. Beim Übergang in einen terminalen
Zustand (✅/⚫) wandert die Zeile zusammen mit dem Eintrag in die
[`BUGS-Closed.md`](BUGS-Closed.md)-Übersicht.

| # | Titel | Status | Commit / Notiz |
|---|---|---|---|

Aktuell keine in-flight Bugs erfasst — die 15 historischen Einträge
sind alle in [`BUGS-Closed.md`](BUGS-Closed.md).

---

## BUG - Wenn man auf die Tagesansicht geht, steht nur der Wochentag aber nicht das Datum
