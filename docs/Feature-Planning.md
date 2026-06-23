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

## #9 — Google Calendar via OAuth 2.0 (CalDAV + Bearer-Token)

> **Original:** Aus dem Design-Gespräch 2026-06-23. Anforderung:
> lesender + schreibender Zugriff auf Google Calendar für
> ChronoGrid, als zusätzlicher Provider neben Apple iCloud,
> Nextcloud und Infomaniak. Memory `project_blog_focus_icloud`
> hat Google bisher explizit als „separate follow-up" geführt —
> jetzt offiziell auf der Pipeline.

**Status:** 🧪 fertig, Tests laufen — Schichten 1-7 ausgeliefert
(`962197a`, `a755a69`, `c2517e8`, `94dcf95`). Schicht 8 (Bug-Buffer)
ist inhärent abhängig vom realen Smoke-Test mit echtem Google-
Konto: 27 neue Unit-Tests laufen grün, aber Provider-Quirks die
sich erst bei tatsächlichem Google-Roundtrip zeigen können
hier nicht antizipiert werden. Wartet auf Sven-Verifikation
gegen ein Google-Cloud-Console-Projekt.

### Konzept

**Strategie-Entscheidung: Weg A (CalDAV + OAuth-Bibliothek).**
Google bietet einen CalDAV-Endpunkt unter
`https://apidata.googleusercontent.com/caldav/v2/<calendar-id>/events`,
der mit Standard-CalDAV-Clients funktioniert — **aber**: Basic-Auth
+ App-spezifisches Passwort wie bei iCloud geht NICHT. Google hat
„Less Secure Apps" 2022 endgültig abgeschaltet, OAuth 2.0 ist
Pflicht.

Der CalDAV-Pfad bleibt die richtige Wahl weil:

- ✅ Das gesamte Connection-Manager + Wizard + Quick-Toggle +
  Partial-Failure-Isolation-System (BACKLOG #8–#10) bleibt
  unverändert anwendbar
- ✅ `CalDavClient`, `EntryMapper`, `fanOutWithStatus`,
  `CalendarService.fromConnections` werden 1:1 wiederverwendet
- ✅ Provider-Profile-Pattern erweitert sich natürlich um einen
  neuen `GoogleProvider`
- ✅ Multi-Provider-Setup (z.B. iCloud + Google + Nextcloud
  parallel) funktioniert direkt, ohne Architektur-Refactor

Die Alternative (Weg B = native Google Calendar API SDK) wurde
verworfen weil sie ~5-7 Tage statt 2-3 kostet, das
CalDAV-only-Modell bricht und eine zweite parallele Service-
Architektur einführt. Die volle Google-Funktionalität (Reminders,
Conferencing, Visibility) ist für die Blog-Demo-Zielgruppe nicht
relevant.

**Architektur-Änderung in einem Satz:**
`CalDavClient` lernt neben Basic-Auth eine Bearer-Token-Variante;
`CalDavServerConnection` lernt neben (user, password) eine
OAuth-Credentials-Variante; der Wizard lernt einen
„Sign in with Google"-Schritt der durch den Browser zur Google-
OAuth-Page redirected.

**OAuth-Flow im Wizard (Schritt 2):**

```
┌─ Schritt 2: Anmeldedaten ─────────────────┐
│                                            │
│ ● Bei Google anmelden                      │
│   [ Sign in with Google ]                  │
│                                            │
│ ℹ Du wirst zu accounts.google.com         │
│   weitergeleitet. Nach erfolgreicher       │
│   Anmeldung kehrst du automatisch zurück. │
│                                            │
│   ← Zurück    Weiter (gesperrt) →          │
└────────────────────────────────────────────┘
```

Nach Klick: Browser-Redirect zu Google's OAuth-Consent-Page →
User stimmt Calendar-Scope zu → Google redirected zur
`OAuthCallbackView`-Route → Code wird gegen Tokens getauscht →
Wizard-Step wird via Vaadin-Push entsperrt, „Weiter" wird
klickbar.

### Implementierungs-Reihenfolge (Schichten)

1. **Schicht 1 — Google OAuth-Bibliothek ins Projekt** (~1 h).
   Maven-Deps `google-oauth-client-jetty` + `google-api-client`
   in `chronogrid-core/pom.xml`. Versionen pinnen.
2. **Schicht 2 — `CalDavClient` Bearer-Auth-Variante** (~3-4 h).
   Neuer Konstruktor `CalDavClient(URI, Supplier<String> tokenProvider)`.
   Bei jedem Request `Authorization: Bearer <supplier.get()>`
   setzen. Der Supplier ist die Indirektion über die der
   Token-Refresher gecachete/refreshte Access-Tokens nachliefert.
   401-Retry-Pfad: bei Token-expired den Supplier-Call wiederholen
   (refresh fired automatisch) und Request einmal retryen.
3. **Schicht 3 — `GoogleOAuthCredentials` + Token-Refresher**
   (~1 Tag). Neuer Record
   `GoogleOAuthCredentials(clientId, clientSecret, refreshToken,
   transientAccessToken?, expiry?)` als Serialisierbare Variante
   in `CalDavServerConnection` (sealed-interface-Ansatz oder
   `Optional<GoogleOAuthCredentials>`-Feld). `TokenRefresher`-
   Service ruft Google's `oauth2.googleapis.com/token` mit
   `grant_type=refresh_token`. Thread-Safety: ConcurrentHashMap-
   cached pro Server-ID, Mutex pro Refresh um Doppel-Refresh zu
   vermeiden.
4. **Schicht 4 — OAuth-Flow im Wizard** (~1 Tag). Wizard-Schritt 2
   bekommt eine provider-abhängige Variante: für Google ein
   „Sign in with Google"-Button statt User+Pass-Felder. State-
   Parameter + PKCE für CSRF-Schutz. Neue Vaadin-Route
   `OAuthCallbackView` empfängt `?code=...&state=...`, tauscht
   Code gegen Tokens, persistiert die `GoogleOAuthCredentials` in
   `CalDavServerConnection`, wechselt zurück zum Wizard via
   Vaadin-Push.
5. **Schicht 5 — `GoogleProvider implements CalDavProviderProfile`**
   (~3-4 h). Provider-spezifische Quirks abfedern: COLOR-Property
   wird von Google ignoriert → snap-to-named (BUG #12 Pattern),
   `X-WR-*`-Header werden tolerant gelesen. Plus:
   `supportsTodos() = false` weil Google's CalDAV keine VTODOs
   kann (die laufen über Google Tasks API).
6. **Schicht 6 — `CalDavProviderPreset.GOOGLE`** (~1 h). Neuer
   Eintrag in `DEFAULTS` mit Google-Icon, App-Description,
   Wizard-Hint („Du wirst zu accounts.google.com weitergeleitet").
   Provider-Detect: URI-Pattern `*.google.com` oder explizites
   Preset-Button im Wizard.
7. **Schicht 7 — Tests** (~3-4 h). Mock-OAuth-Server
   (`com.github.tomakehurst.wiremock` oder
   `MockHttpTransport` aus `google-http-client-test`) für
   Token-Exchange + Refresh. 401-Retry-Pfad. Token-Expiry-
   Boundary. Provider-Profile-Wiring (Google: VTODOs blockiert,
   COLOR-snap-to-named aktiviert). Browser-OAuth-Flow lässt sich
   in BrowserlessTest nicht voll simulieren — nur die Server-
   Side-Routen + Persistenz testbar.
8. **Schicht 8 — Bug-Fix-Buffer** (~1 Tag). Erfahrung aus iCloud
   + Nextcloud + Infomaniak: pro neuer Provider 2-3 nicht-
   vorhersagbare Quirks (BUG #2 für iCloud, BUG #11 für
   Nextcloud, BUG #13 für Infomaniak). Bei Google realistische
   Kandidaten: ETag-Format, Recurring-Event-Override-Pattern,
   `displayname`-Encoding.

### Touchpoints

- `chronogrid-core: client/CalDavClient.java` — Bearer-Auth-
  Konstruktor + 401-Retry-Pfad
- `chronogrid-core: service/CalDavServerConnection.java` —
  Variant-Auth-Pfad (Basic vs OAuth)
- `chronogrid-core: service/CalDavProviderPreset.java` — neuer
  `GOOGLE`-Eintrag
- `chronogrid-core: provider/GoogleProvider.java` (neu) —
  Provider-Profile mit Google-spezifischen Quirks
- `chronogrid-core: auth/GoogleOAuthCredentials.java` (neu) —
  Token-Träger-Record
- `chronogrid-core: auth/TokenRefresher.java` (neu) — Refresh-
  Logic gegen Google's `oauth2.googleapis.com/token`
- `chronogrid: ui/ConnectionWizardDialog.java` — provider-
  abhängige Variante von Schritt 2 (Google bekommt „Sign in"-
  Button statt User+Pass-Felder)
- `chronogrid-demo: views/OAuthCallbackView.java` (neu) —
  Vaadin-Route die Google's redirect entgegennimmt
- `chronogrid: ui/ChronoGrid.java#openConnectionWizard` — neue
  `BiFunction`/`Function` für Google-spezifisches Token-Exchange-
  Callback an den Wizard injizieren
- `chronogrid-core: pom.xml` — Maven-Deps
  `google-oauth-client-jetty` + `google-api-client`
- `chronogrid-demo: resources/vaadin-i18n/translations*.properties`
  — Google-Preset-Label, Wizard-Hint, OAuth-Callback-Texte

### Größe

**L. Geschätzt 3-5 Tage Implementation.** Spanne wegen Bug-Buffer
und der inhärenten Unvorhersagbarkeit von OAuth-Flow-Edge-Cases
(Browser-Cookie-Verhalten, PKCE-State-Mismatch unter bestimmten
Vaadin-Push-Konstellationen, Token-Refresh-Race-Conditions).

**Plus Sven-Time** (User-Side, einmalig): Google Cloud Console
Setup (Projekt, OAuth-Consent-Screen, Client-ID + Secret,
Redirect-URI-Whitelisting, Calendar-API freischalten) — ca. 30
Minuten. OAuth-Consent-Screen bleibt in „External Testing"-Mode
(max 100 Test-User, kein Google-Verification-Prozess nötig — der
würde Wochen bis Monate dauern).

### Risiko / offene Fragen

- **Google killt CalDAV-API.** Die CalDAV-Schnittstelle ist seit
  Jahren da, aber nirgendwo prominent dokumentiert; Google selbst
  empfiehlt für Neuintegrationen die native Calendar-API.
  Theoretisches Risiko dass sie eines Tages stillgelegt wird.
  Für Blog-Demo-Stand-2026 akzeptabel; falls es kommt, Migration
  zu Weg B (native SDK) ist ein eigenständiges Projekt von
  ähnlicher Größe.
- **Token-Storage-Sicherheit.** Refresh-Tokens sind langlebige
  Credentials. In Session: bei Logout sauber löschen (Schicht 3
  muss sich an `addSessionDestroyListener` hängen). Falls
  Serialized-Session aktiviert wird: Token-Encryption-at-Rest
  nötig (verschoben in eine spätere Hardening-Phase, nicht in
  v1). HIBP-Check entfällt — Tokens sind nicht passwort-artig.
- **OAuth-Consent-Screen-Mode.** „External Testing" mit 100
  Test-Usern ist Blog-tauglich, aber der User muss explizit pro
  Test-User-Email-Adresse autorisiert werden in der Cloud Console
  oder Google bricht mit `access_denied` ab. Doku-Schritt im
  Wizard-Hint plus README.
- **Scope-Granularität.** Google's OAuth-Scope
  `https://www.googleapis.com/auth/calendar` ist Vollzugriff (R+W
  auf alle Calendars). Read-Only wäre `calendar.readonly`. Für
  Schreib-Pfad muss der volle Scope angefragt werden — der User
  muss informierte Consent geben. Im Wizard-Hint klar
  kommunizieren.
- **Quota.** Google Calendar API hat Per-Project-Rate-Limits
  (1.000.000 Reads/Tag, weniger Writes). Für Blog-Demo völlig
  unkritisch; für hypothetischen Produktiveinsatz mit vielen
  Concurrent-Usern ein Topic, das dann auch durch lokales
  Caching abgefedert würde.
- **CalDAV-Quirks** (Erfahrung aus iCloud/Nextcloud/Infomaniak):
  - **VTODO**: Google's CalDAV unterstützt es nicht. `GoogleProvider`
    deklariert `supportsTodos() = false`, der `EventEditorDialog`
    dimmt VTODO-Optionen für Google-Subscriptions aus.
  - **COLOR-Property**: wird von Google ignoriert. Schon abgefedert
    durch BUG #12 Pattern (snap-to-nearest CSS3-named).
  - **Recurring-Event-Overrides**: Google's Pattern für „diese
    Instanz geändert, Serie unverändert" weicht subtil von iCloud
    ab — Edge-Case, in v1 nicht zwingend voll abgedeckt.
- **Apple-iCloud-Provider bleibt Default.** Per Memory
  `project_blog_focus_icloud` ist iCloud der Demo-Pfad für den
  Companion-Blog. Google ist ein zweiter Provider, nicht der
  neue Default. `CalDavProviderPreset.DEFAULTS.get(0)` bleibt
  iCloud.
- **OAuth-Callback-Route + Authentication-Gate.** Die
  `OAuthCallbackView` muss erreichbar sein OHNE eingeloggten
  jSentinel-User (Google redirected den Browser unabhängig vom
  App-Session-State zurück). Aber: erst nach erfolgreichem
  Token-Exchange darf der jSentinel-User die Tokens persistieren.
  Sauberer Pfad: `OAuthCallbackView` als public route, aber
  bindet die ankommenden Tokens an die State-Parameter-gepairte
  Wizard-Session — wenn keine Wizard-Session matched, 401.
- **i18n-Aufwand.** Wizard-Schritt 2 wird provider-abhängig
  (`calendar.wizard.step2.google.*` neue Key-Familie). Plus
  Hint-Texte, Callback-View-Texte, Error-Messages für
  `access_denied` / Token-Refresh-Failure. Geschätzt ~15-20
  neue Keys EN+DE.
