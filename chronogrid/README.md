# chronogrid

Drop-in Vaadin Flow Composite that renders a CalDAV-backed calendar.
Embeds the FullCalendar2 add-on, talks to any RFC-4791 server via
[`chronogrid-core`](../chronogrid-core), and presents a fully-featured
multi-server / multi-subscription UI with sub-dialogs for connection,
subscription, and event management.

## Coordinates

```xml
<dependency>
    <groupId>com.svenruppert.chronogrid</groupId>
    <artifactId>chronogrid</artifactId>
    <version>00.10.00</version>
</dependency>
```

Brings `chronogrid-core` (headless wire + service) as a transitive
plus `com.vaadin:vaadin-core`, `org.vaadin.stefan:fullcalendar2`, and
the bundled `META-INF/resources/frontend/styles/chronogrid.css`.

## Mounting the view

The component is route-free and authorization-free — the host
application carries those annotations. A typical wrapper:

```java
@Route(value = "calendar", layout = MainLayout.class)
@VisibleFor(USER)
public final class CalendarRouteView extends Composite<Component>
        implements I18nSupport {

    public CalendarRouteView() {
        CalendarStateStore store     = new VaadinSessionCalendarStateStore();
        CalendarMessages   messages  = this::tr;
        ChronoGrid       calendar  = new ChronoGrid(store, messages);
        getContent().getElement().appendChild(calendar.getElement());
    }
}
```

The three injected concerns are the **only** seams a consumer needs:

| Seam | What to plug in |
|---|---|
| `CalendarStateStore` | Persists Connection / Servers / Subscriptions / N-days slider. `VaadinSessionCalendarStateStore` (default) keeps everything on the current Vaadin session. Plug a DB- or file-backed impl for cross-session continuity. |
| `CalendarMessages` | `(key, fallback, args) -> translatedText`. Pass `this::tr` from a host i18n interface, or `CalendarMessages.fallbackOnly()` for English fallbacks only. |
| `CalendarService` *(optional)* | Pre-built service instance. Omit and the view bootstraps from the store's connection config, falling back to `CalDavProviderPreset.DEFAULTS[0]` (Apple iCloud quick-connect). |

## Customising

- **Quick-connect presets** — pass your own list of
  `CalDavProviderPreset` records (Nextcloud, Radicale, mailbox.org…)
  via the settings dialog wiring.
- **Translation keys** — every dialog/button takes its label from
  `CalendarMessages.tr(key, fallback)`. The full key catalogue is the
  set of `K_*` constants on `ChronoGrid` and the sub-components in
  `com.svenruppert.chronogrid.ui`.
- **Styling** — class names follow `.chronogrid__*` and
  `.chronogrid-nav__*` BEM-ish patterns. The bundled CSS in
  `META-INF/resources/frontend/styles/chronogrid.css` is fully
  themable via Lumo custom properties.

## License

Distributed under the [European Union Public Licence 1.2](https://joinup.ec.europa.eu/software/page/eupl).
