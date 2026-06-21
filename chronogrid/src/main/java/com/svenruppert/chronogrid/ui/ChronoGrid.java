/*
 * Copyright © 2013 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package com.svenruppert.chronogrid.ui;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.chronogrid.client.CalDavClient;
import com.svenruppert.chronogrid.client.CalDavDiscovery;
import com.svenruppert.chronogrid.client.CalDavErrors;
import com.svenruppert.chronogrid.client.DiscoveredCalendar;
import com.svenruppert.chronogrid.service.CalDavConnectionConfig;
import com.svenruppert.chronogrid.service.CalDavProviderPreset;
import com.svenruppert.chronogrid.service.CalDavServerConnection;
import com.svenruppert.chronogrid.service.CalendarService;
import com.svenruppert.chronogrid.service.CalendarSubscription;
import com.svenruppert.chronogrid.state.CalendarStateStore;
import com.svenruppert.chronogrid.state.VaadinSessionCalendarStateStore;
import com.svenruppert.chronogrid.i18n.CalendarMessages;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.shared.Registration;
import org.vaadin.stefan.fullcalendar.CalendarViewImpl;
import org.vaadin.stefan.fullcalendar.CustomCalendarView;
import org.vaadin.stefan.fullcalendar.Entry;
import org.vaadin.stefan.fullcalendar.FullCalendar;
import org.vaadin.stefan.fullcalendar.dataprovider.EntryProvider;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Monthly calendar view backed by the CalDAV-aware
 * {@link CalendarService}. Carries a toolbar with a backend-status
 * indicator and three convenience actions (Settings / Refresh / New
 * event). The Settings dialog lets the user point the calendar at a
 * different CalDAV collection at runtime; the override is kept by
 * the injected {@link CalendarStateStore} so it survives navigation
 * but resets on logout (default Vaadin-session-scoped impl).
 *
 * <p>Route + authorization annotations live on the host-side
 * {@code CalendarRouteView} wrapper — this composite is intended to
 * be embedded by any consumer (own Maven artifact, different host
 * app, headless test). The constructor signature is the seam:
 * inject a {@link CalendarStateStore} and a {@link CalendarMessages}
 * to bridge the consumer's persistence + i18n stacks.
 */
@CssImport("./styles/chronogrid.css")
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
    value = "SE_TRANSIENT_FIELD_NOT_RESTORED",
    justification = "service is intentionally transient — Vaadin Flow does "
        + "not deserialize live view instances in production; on a new HTTP "
        + "session the view's constructor runs and rebuilds the service from "
        + "the injected CalendarStateStore via resolveInitialService().")
public class ChronoGrid extends Composite<VerticalLayout>
    implements HasLogger {

  private static final long serialVersionUID = 1L;

  /** @deprecated route lives on the host wrapper; this constant is
   *     kept for the BrowserlessTest fixtures only. */
  @Deprecated
  public static final String NAV = "calendar";

  // Session-attribute keys re-exported for backward compatibility —
  // the canonical home is now VaadinSessionCalendarStateStore. Test
  // fixtures and any host code that pokes the session directly should
  // migrate to the store API.
  /** @deprecated use {@link VaadinSessionCalendarStateStore#SESSION_KEY_CONNECTION}. */
  @Deprecated
  public static final String SESSION_KEY_CONNECTION =
      VaadinSessionCalendarStateStore.SESSION_KEY_CONNECTION;
  /** @deprecated use {@link VaadinSessionCalendarStateStore#SESSION_KEY_SUBSCRIPTIONS}. */
  @Deprecated
  public static final String SESSION_KEY_SUBSCRIPTIONS =
      VaadinSessionCalendarStateStore.SESSION_KEY_SUBSCRIPTIONS;
  /** @deprecated use {@link VaadinSessionCalendarStateStore#SESSION_KEY_SERVERS}. */
  @Deprecated
  public static final String SESSION_KEY_SERVERS =
      VaadinSessionCalendarStateStore.SESSION_KEY_SERVERS;
  /** @deprecated use {@link VaadinSessionCalendarStateStore#SESSION_KEY_NDAYS}. */
  @Deprecated
  public static final String SESSION_KEY_NDAYS =
      VaadinSessionCalendarStateStore.SESSION_KEY_NDAYS;

  public static final String STATUS_BADGE_ID = ConnectionStatusBadge.ID;

  /** Health-probe interval used both as poll cadence and probe window. */
  static final int HEALTH_POLL_INTERVAL_MS = 15_000;

  /** @deprecated mirror of {@link ConnectionStatusBadge.State} kept for tests. */
  @Deprecated
  public enum ConnectionState { UNKNOWN, CONNECTED, DISCONNECTED }

  // i18n keys
  private static final String K_HEADING = "calendar.heading";
  private static final String K_SUBTITLE = "calendar.subtitle";
  private static final String K_DIALOG_TITLE_NEW = "calendar.dialog.title.new";
  private static final String K_DIALOG_TITLE_EDIT = "calendar.dialog.title.edit";
  private static final String K_FIELD_TITLE = "calendar.field.title";
  private static final String K_FIELD_DESCRIPTION = "calendar.field.description";
  private static final String K_FIELD_LOCATION = "calendar.field.location";
  private static final String K_FIELD_URL = "calendar.field.url";
  private static final String K_FIELD_START = "calendar.field.start";
  private static final String K_FIELD_END = "calendar.field.end";
  private static final String K_ACTION_SAVE = "calendar.action.save";
  private static final String K_ACTION_DELETE = "calendar.action.delete";
  private static final String K_ACTION_CANCEL = "calendar.action.cancel";
  private static final String K_DELETE_CONFIRM_TITLE = "calendar.dialog.delete.title";
  private static final String K_DELETE_CONFIRM_BODY = "calendar.dialog.delete.body";
  private static final String K_NOTIFY_CONFLICT = "calendar.notify.conflict";
  // toolbar / settings
  private static final String K_TB_BACKEND = "calendar.toolbar.backend";
  private static final String K_TB_SETTINGS = "calendar.toolbar.settings";
  private static final String K_TB_REFRESH = "calendar.toolbar.refresh";
  private static final String K_TB_NEW = "calendar.toolbar.newEvent";
  private static final String K_TB_SUBSCRIPTIONS = "calendar.toolbar.subscriptions";
  private static final String K_TB_CONNECTIONS = "calendar.toolbar.connections";
  private static final String K_TB_TAG_FILTER = "calendar.toolbar.tagFilter";
  private static final String K_TB_TAG_FILTER_PLACEHOLDER = "calendar.toolbar.tagFilter.placeholder";
  private static final String K_NOTIFY_SUB_REMOVED = "calendar.notify.subscription.removed";
  private static final String K_NOTIFY_SUB_HIDDEN = "calendar.notify.subscription.hidden";
  private static final String K_NOTIFY_SUB_SHOWN = "calendar.notify.subscription.shown";
  private static final String K_SET_TITLE = "calendar.settings.title";
  private static final String K_SET_URI = "calendar.settings.uri";
  private static final String K_SET_USER = "calendar.settings.username";
  private static final String K_SET_PASS = "calendar.settings.password";
  private static final String K_SET_TEST = "calendar.settings.test";
  private static final String K_SET_DISCOVER = "calendar.settings.discover";
  private static final String K_SET_PICKER_LABEL = "calendar.settings.picker.label";
  private static final String K_SET_PICKER_PLACEHOLDER = "calendar.settings.picker.placeholder";
  private static final String K_SET_HINT = "calendar.settings.hint";
  private static final String K_SET_PRESETS_LABEL = "calendar.settings.presets.label";
  private static final String K_PROVIDER_PREFIX = "calendar.settings.provider.";  // + id + .label / .hint
  private static final String K_NOTIFY_BAD_URI = "calendar.notify.badUri";
  private static final String K_NOTIFY_TEST_OK = "calendar.notify.testOk";
  private static final String K_NOTIFY_TEST_FAIL = "calendar.notify.testFail";
  private static final String K_NOTIFY_APPLIED = "calendar.notify.applied";
  private static final String K_NOTIFY_DISCOVERY_FAIL = "calendar.notify.discoveryFail";
  private static final String K_NOTIFY_DISCOVERY_EMPTY = "calendar.notify.discoveryEmpty";
  private static final String K_NOTIFY_DISCOVERY_FOUND = "calendar.notify.discoveryFound";
  private static final String K_NOTIFY_SAVE_FAILED = "calendar.notify.saveFailed";
  private static final String K_NOTIFY_DELETE_FAILED = "calendar.notify.deleteFailed";
  private static final String K_NOTIFY_RECONNECTED = "calendar.notify.reconnected";
  private static final String K_ERROR_UNAUTHORIZED = "calendar.error.unauthorized";
  private static final String K_ERROR_NOT_FOUND = "calendar.error.notFound";
  private static final String K_ERROR_TIMEOUT = "calendar.error.timeout";
  private static final String K_ERROR_NETWORK = "calendar.error.network";
  private static final String K_ERROR_SERVER = "calendar.error.server";
  private static final String K_ERROR_MALFORMED = "calendar.error.malformed";
  private static final String K_ERROR_GENERIC = "calendar.error.generic";
  private static final String K_STATUS_CONNECTED = "calendar.status.connected";
  private static final String K_STATUS_DISCONNECTED = "calendar.status.disconnected";
  private static final String K_STATUS_UNKNOWN = "calendar.status.unknown";

  private final ZoneId displayZone = ZoneId.systemDefault();
  private transient CalendarService service;
  private final CalendarStateStore stateStore;
  private final CalendarMessages messages;
  private final FullCalendar calendar;
  private final ServerStatusList serverStatusList = new ServerStatusList();
  private final ConnectionStatusBadge connectionBadge;
  private transient Registration pollRegistration;
  private CalendarNavigationBar navigationBar;
  private transient CustomCalendarView[] nDaysCustomViews;
  private com.vaadin.flow.component.combobox.MultiSelectComboBox<String> tagFilter;
  // Universe of all tags seen since the last refresh. The combobox
  // populates from this; growing/shrinking it triggers a setItems
  // refresh on the combo. Synchronized — touched from background
  // streams as well as from the UI thread.
  private final java.util.NavigableSet<String> tagUniverse =
      java.util.Collections.synchronizedNavigableSet(new java.util.TreeSet<>());

  public ChronoGrid() {
    this(new VaadinSessionCalendarStateStore());
  }

  public ChronoGrid(CalendarStateStore stateStore) {
    this(stateStore, null, resolveInitialService(stateStore));
  }

  public ChronoGrid(CalendarStateStore stateStore, CalendarMessages messages) {
    this(stateStore, messages, resolveInitialService(stateStore));
  }

  ChronoGrid(CalendarService service) {
    this(new VaadinSessionCalendarStateStore(), null, service);
  }

  ChronoGrid(CalendarStateStore stateStore, CalendarService service) {
    this(stateStore, null, service);
  }

  ChronoGrid(CalendarStateStore stateStore, CalendarMessages messages,
               CalendarService service) {
    this.stateStore = stateStore;
    // When the caller doesn't supply a CalendarMessages adapter, run
    // with fallback strings only. The host wrapper (CalendarRouteView)
    // plugs in a host-i18n-backed adapter that uses I18nSupport.tr.
    this.messages = messages != null
        ? messages
        : CalendarMessages.fallbackOnly();
    this.connectionBadge = new ConnectionStatusBadge(
        this.messages, this::onReconnect);
    this.service = service;

    VerticalLayout root = getContent();
    root.setSizeFull();
    root.setPadding(false);
    root.setSpacing(false);
    root.addClassName("chronogrid");

    // Visual frame around the whole calendar view — gives a clear
    // card-like boundary against the surrounding MainLayout. All
    // visual rules live in styles/chronogrid.css.
    com.vaadin.flow.component.html.Div frame =
        new com.vaadin.flow.component.html.Div();
    frame.setSizeFull();
    frame.addClassName("chronogrid__frame");
    root.add(frame);
    root.expand(frame);

    frame.add(buildHeader());
    frame.add(buildToolbar());

    EntryProvider<Entry> provider = EntryProvider.fromCallbacks(
        query -> rangeWithStatus(query.getStart(), query.getEnd()),
        this::lookupWithStatus);

    CustomCalendarView[] customViews =
        new CustomCalendarView[
            CalendarNavigationBar.MAX_N_DAYS];
    for (int i = 0; i < CalendarNavigationBar.MAX_N_DAYS; i++) {
      customViews[i] = buildNDaysView(i + 1);
    }
    this.nDaysCustomViews = customViews;

    // Disable FullCalendar's built-in toolbar entirely. Passing
    // `headerToolbar: false` through initialOptions removes the
    // default left/center/right groups (prev/next/today and the
    // view-switcher buttons). Our CalendarNavigationBar replaces
    // all of it.
    ObjectNode initialOptions =
        JsonMapper.builder().build().createObjectNode();
    initialOptions.put("headerToolbar", false);

    // Direct construction — the FullCalendarBuilder API is fully
    // @Deprecated(since="7.2.0") and its build() method only chains
    // the same public setters we now call here. The dedicated
    // boolean/Locale/height setters are also @Deprecated in 7.2 —
    // setOption(Option, ...) / setHeight(String) is the current API.
    calendar = new FullCalendar(initialOptions);
    calendar.setCustomCalendarViews(customViews);
    calendar.setEntryProvider(provider);
    calendar.setOption(FullCalendar.Option.LOCALE,
        com.vaadin.flow.component.UI.getCurrent().getLocale());
    calendar.changeView(CalendarViewImpl.DAY_GRID_MONTH);
    calendar.setSizeFull();
    calendar.setOption(FullCalendar.Option.SELECTABLE, true);
    calendar.setHeight("100%");
    calendar.addClassName("chronogrid__calendar");

    calendar.addTimeslotsSelectedListener(event -> {
      Entry draft = new Entry(UUID.randomUUID().toString());
      draft.setStart(event.getStart());
      draft.setEnd(event.getEnd());
      draft.setAllDay(event.isAllDay());
      openEditor(draft, true);
    });

    calendar.addEntryClickedListener(event -> {
      Entry clicked = event.applyChangesOnEntry();
      openEditor(clicked, false);
    });

    calendar.addEntryDroppedListener(event -> {
      Entry updated = event.applyChangesOnEntry();
      persistMove(updated);
    });

    calendar.addEntryResizedListener(event -> {
      Entry updated = event.applyChangesOnEntry();
      persistMove(updated);
    });

    this.navigationBar = buildNavigationBar();
    frame.add(navigationBar);

    calendar.addDatesRenderedListener(e ->
        navigationBar.setIntervalLabel(formatVisibleInterval()));

    frame.add(calendar);
    refreshBackendStatus();
    markUnknown();

    addAttachListener(e -> {
      com.vaadin.flow.component.UI ui = e.getUI();
      ui.setPollInterval(HEALTH_POLL_INTERVAL_MS);
      pollRegistration = ui.addPollListener(p -> probeQuietly());
    });
    addDetachListener(e -> {
      if (pollRegistration != null) {
        pollRegistration.remove();
        pollRegistration = null;
      }
      e.getUI().setPollInterval(-1);
    });
  }

  // ── header / toolbar ───────────────────────────────────────────

  private com.vaadin.flow.component.Component buildHeader() {
    com.vaadin.flow.component.html.H1 title =
        new com.vaadin.flow.component.html.H1(messages.tr(K_HEADING, "Calendar"));
    com.vaadin.flow.component.html.Paragraph subtitle =
        new com.vaadin.flow.component.html.Paragraph(messages.tr(K_SUBTITLE,
            "Events live on the configured CalDAV collection. "
                + "Drag, drop, resize, or click an entry to edit; "
                + "select a timeslot to create a new event."));
    com.vaadin.flow.component.html.Div root =
        new com.vaadin.flow.component.html.Div(title, subtitle);
    root.addClassName("chronogrid__page-header");
    return root;
  }

  private HorizontalLayout buildToolbar() {
    Span backendLabel = new Span(messages.tr(K_TB_BACKEND, "Servers:"));
    backendLabel.addClassName("chronogrid-secondary-text");

    HorizontalLayout status = new HorizontalLayout(
        backendLabel, serverStatusList, connectionBadge);
    status.setAlignItems(FlexComponent.Alignment.CENTER);
    status.setSpacing(true);

    Button settings = new Button(messages.tr(K_TB_SETTINGS, "Settings"),
        VaadinIcon.COG.create(), e -> openSettingsDialog());
    settings.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    Button connections = new Button(messages.tr(K_TB_CONNECTIONS, "Connections"),
        VaadinIcon.CONNECT.create(), e -> openConnectionsDialog());
    connections.setId("calendar-toolbar-connections");
    connections.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    Button subscriptions = new Button(messages.tr(K_TB_SUBSCRIPTIONS, "Subscriptions"),
        VaadinIcon.LAYOUT.create(), e -> openSubscriptionsDialog());
    subscriptions.setId("calendar-toolbar-subscriptions");
    subscriptions.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    Button refresh = new Button(messages.tr(K_TB_REFRESH, "Refresh"),
        VaadinIcon.REFRESH.create(), e -> {
          calendar.getEntryProvider().refreshAll();
          notifyInfo(messages.tr(K_NOTIFY_APPLIED, "Reloaded from {0}",
              service.collectionUri().toString()));
        });
    refresh.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    Button newEvent = new Button(messages.tr(K_TB_NEW, "New event"),
        VaadinIcon.PLUS.create(), e -> openNewEventEditor());
    newEvent.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    // Tag filter (Feature #3): multi-select combobox. Items are
    // populated from the running tag universe; selecting one or
    // more tags filters the calendar grid across all subscriptions.
    tagFilter = new com.vaadin.flow.component.combobox.MultiSelectComboBox<>(
        messages.tr(K_TB_TAG_FILTER, "Filter by tag"));
    tagFilter.setPlaceholder(messages.tr(K_TB_TAG_FILTER_PLACEHOLDER,
        "Tags…"));
    tagFilter.setId("calendar-toolbar-tag-filter");
    tagFilter.setWidth("220px");
    java.util.Set<String> savedFilter = stateStore.readTagFilter();
    refreshTagUniverse();
    if (!savedFilter.isEmpty()) {
      // Make sure the persisted filter renders even if its tags have
      // not yet been observed in the current grid range.
      tagUniverse.addAll(savedFilter);
      tagFilter.setItems(snapshotTagUniverse());
      tagFilter.setValue(savedFilter);
    }
    tagFilter.addValueChangeListener(e -> {
      java.util.Set<String> chosen = e.getValue() == null
          ? java.util.Set.of()
          : java.util.Collections.unmodifiableSet(
              new java.util.LinkedHashSet<>(e.getValue()));
      stateStore.writeTagFilter(chosen);
      calendar.getEntryProvider().refreshAll();
    });

    HorizontalLayout actions = new HorizontalLayout(
        tagFilter, settings, connections, subscriptions, refresh, newEvent);
    actions.setAlignItems(FlexComponent.Alignment.END);
    actions.setSpacing(true);

    HorizontalLayout bar = new HorizontalLayout(status, actions);
    bar.setWidthFull();
    bar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    bar.setAlignItems(FlexComponent.Alignment.CENTER);
    return bar;
  }

  // ── navigation bar wiring ──────────────────────────────────────

  private CalendarNavigationBar buildNavigationBar() {
    int initialN = readNDaysPreference();
    CalendarNavigationBar.NavigationCallbacks nav =
        new CalendarNavigationBar.NavigationCallbacks(
            calendar::previous,    // ⏮ paging back: full interval
            this::onSlideBack,     // ◀ slide back: one day
            calendar::today,       // Today
            this::onSlideForward,  // ▶ slide forward: one day
            calendar::next);       // ⏭ paging forward: full interval
    CalendarNavigationBar bar = new CalendarNavigationBar(
        messages,
        LocalDateTime.now().toLocalDate(),
        CalendarNavigationBar.ViewMode.MONTH,
        initialN,
        nav,
        date -> {
          if (date != null) calendar.gotoDate(date);
        },
        this::applyViewMode,
        this::applyNDays);
    bar.setDayDotsProvider(this::coloursForRange);
    return bar;
  }

  /**
   * Feature #5 (Popover-internal): groups every entry that
   * overlaps {@code [from, to]} (inclusive) by its event-day, and
   * collects the calendar-source colour set per day. The result
   * drives the in-popover dot indicator the
   * {@link CalendarNavigationBar} pushes to the DatePicker.
   *
   * <p>Single CalDAV call covering the whole window — much cheaper
   * than per-day calls when the popover spans 60+ visible days
   * (month ± 1). Multi-day entries contribute their colour to
   * every day they cover.
   */
  java.util.Map<java.time.LocalDate, java.util.Set<String>>
      coloursForRange(java.time.LocalDate from, java.time.LocalDate to) {
    if (from == null || to == null || service == null || to.isBefore(from)) {
      return java.util.Collections.emptyMap();
    }
    java.time.LocalDateTime fromDt = from.atStartOfDay();
    java.time.LocalDateTime toDt = to.plusDays(1).atStartOfDay();
    java.util.Map<java.time.LocalDate, java.util.Set<String>> out =
        new java.util.HashMap<>();
    try {
      rangeWithStatus(fromDt, toDt).forEach(e -> {
        String colour = e.getColor();
        if (colour == null || colour.isBlank()) return;
        java.time.LocalDateTime startDt = e.getStart();
        java.time.LocalDateTime endDt = e.getEnd();
        if (startDt == null) return;
        java.time.LocalDate dayStart = startDt.toLocalDate();
        java.time.LocalDate dayEnd = endDt != null
            ? endDt.toLocalDate()
            : dayStart;
        // FullCalendar's convention: an end at midnight closes the
        // *previous* day. Trim so an event 14:00–15:00 doesn't spill
        // into the next day's bucket.
        if (endDt != null && endDt.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
            && dayEnd.isAfter(dayStart)) {
          dayEnd = dayEnd.minusDays(1);
        }
        for (java.time.LocalDate d = dayStart;
             !d.isAfter(dayEnd) && !d.isAfter(to);
             d = d.plusDays(1)) {
          if (d.isBefore(from)) continue;
          out.computeIfAbsent(d, k -> new java.util.LinkedHashSet<>()).add(colour);
        }
      });
      return out;
    } catch (RuntimeException ex) {
      logger().info("coloursForRange({}..{}) failed: {}", from, to, ex.toString());
      return java.util.Collections.emptyMap();
    }
  }

  private void onSlideBack() {
    calendar.getCurrentIntervalStart()
        .ifPresent(d -> calendar.gotoDate(d.minusDays(1)));
  }

  private void onSlideForward() {
    calendar.getCurrentIntervalStart()
        .ifPresent(d -> calendar.gotoDate(d.plusDays(1)));
  }

  private void applyViewMode(CalendarNavigationBar.ViewMode mode) {
    switch (mode) {
      case DAY -> calendar.changeView(CalendarViewImpl.TIME_GRID_DAY);
      case WEEK -> calendar.changeView(CalendarViewImpl.TIME_GRID_WEEK);
      case MONTH -> calendar.changeView(CalendarViewImpl.DAY_GRID_MONTH);
      case N_DAYS -> applyNDays(readNDaysPreference());
      default -> calendar.changeView(CalendarViewImpl.DAY_GRID_MONTH);
    }
  }

  private void applyNDays(int n) {
    storeNDaysPreference(n);
    if (nDaysCustomViews != null
        && n >= CalendarNavigationBar.MIN_N_DAYS
        && n <= CalendarNavigationBar.MAX_N_DAYS) {
      calendar.changeView(nDaysCustomViews[n - 1]);
    }
  }

  private static CustomCalendarView buildNDaysView(
      int days) {
    ObjectNode settings =
        JsonMapper.builder().build().createObjectNode();
    settings.put("type", "timeGrid");
    ObjectNode duration = settings.putObject("duration");
    duration.put("days", days);
    // Column header: short weekday + dd.mm so each rolling-window day is
    // identifiable at a glance ("Mo 14.06" / "Sa 21.06").
    ObjectNode dayHeaderFormat =
        settings.putObject("dayHeaderFormat");
    dayHeaderFormat.put("weekday", "short");
    dayHeaderFormat.put("day", "2-digit");
    dayHeaderFormat.put("month", "2-digit");
    return new CustomCalendarView
        .AnonymousCustomCalendarView("nDays" + days, settings);
  }

  private String formatVisibleInterval() {
    LocalDate start = calendar.getCurrentIntervalStart().orElse(LocalDate.now());
    LocalDate end = calendar.getCurrentIntervalEnd().orElse(start);
    java.time.format.DateTimeFormatter monthYear =
        java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy",
            java.util.Locale.getDefault());
    if (start.getMonth() == end.minusDays(1).getMonth()
        && start.getYear() == end.minusDays(1).getYear()) {
      return start.format(monthYear);
    }
    java.time.format.DateTimeFormatter dayMonth =
        java.time.format.DateTimeFormatter.ofPattern("d MMM",
            java.util.Locale.getDefault());
    return start.format(dayMonth) + " – " + end.minusDays(1).format(dayMonth)
        + " " + end.minusDays(1).getYear();
  }

  private int readNDaysPreference() {
    int raw = stateStore.readNDays(7);
    if (raw < CalendarNavigationBar.MIN_N_DAYS
        || raw > CalendarNavigationBar.MAX_N_DAYS) {
      return 7;
    }
    return raw;
  }

  private void storeNDaysPreference(int n) {
    stateStore.writeNDays(n);
  }

  private void refreshBackendStatus() {
    serverStatusList.setServers(stateStore.readServers());
    probeAllServers();
  }

  /**
   * Probes every configured server via its first subscription —
   * cheap REPORT for the ±1 min window. Updates the per-server
   * {@link ServerStatusList} pill colour. Called on attach, on the
   * periodic poll, and after every Settings/Subscriptions change
   * that may have rewired the server list.
   */
  private void probeAllServers() {
    java.util.List<CalendarSubscription> subs = stateStore.readSubscriptions();
    for (CalDavServerConnection server : stateStore.readServers()) {
      CalendarSubscription target = subs.stream()
          .filter(s -> server.id().equals(s.serverId()))
          .findFirst()
          .orElse(null);
      if (target == null) {
        serverStatusList.setStatus(server.id(), ServerStatusList.State.UNKNOWN);
        continue;
      }
      ServerStatusList.State state;
      try {
        CalDavClient probe = server.hasAuth()
            ? new CalDavClient(target.uri(), server.username(), server.password())
            : new CalDavClient(target.uri());
        probe.findInRange(java.time.Instant.now().minusSeconds(60),
            java.time.Instant.now().plusSeconds(60));
        state = ServerStatusList.State.CONNECTED;
      } catch (RuntimeException ex) {
        state = ServerStatusList.State.DISCONNECTED;
      }
      serverStatusList.setStatus(server.id(), state);
    }
  }

  // ── connection-state machine ───────────────────────────────────

  /** Visible for tests. */
  public ConnectionState connectionState() {
    return switch (connectionBadge.state()) {
      case CONNECTED -> ConnectionState.CONNECTED;
      case DISCONNECTED -> ConnectionState.DISCONNECTED;
      default -> ConnectionState.UNKNOWN;
    };
  }

  private void markConnected() {
    connectionBadge.markConnected();
  }

  private void markDisconnected(String reason) {
    connectionBadge.markDisconnected(reason);
  }

  private void markUnknown() {
    connectionBadge.markUnknown();
  }

  /** Wired into {@link ConnectionStatusBadge} as the reconnect hook. */
  private void onReconnect() {
    notifyInfo(messages.tr(K_NOTIFY_RECONNECTED,
        "Backend is back online — reloading."));
    calendar.getEntryProvider().refreshAll();
  }

  private static String shortReason(Throwable ex) {
    String msg = ex.getMessage();
    if (msg == null) return ex.getClass().getSimpleName();
    return msg.length() > 140 ? msg.substring(0, 137) + "…" : msg;
  }

  // ── status-aware fetch wrappers ────────────────────────────────

  private Stream<Entry> rangeWithStatus(LocalDateTime from, LocalDateTime to) {
    try {
      var fetched = this.service.findInRange(from, to).toList();
      markConnected();
      java.util.Set<URI> visible = visibleUris();
      java.util.Map<URI, String> colorByCollection = colorBySubscriptionUri();
      java.util.stream.Stream<Entry> stream = fetched.stream();
      if (visible != null) {
        stream = stream.filter(e -> {
          // Visibility check is per-collection. Use the entry's
          // href (set by EntryMapper) to find its source URI; if
          // the URI matches one of the visible subscriptions, keep
          // it.
          java.util.Optional<URI> href =
              com.svenruppert.chronogrid.mapping.EntryMapper.readHref(e);
          if (href.isEmpty()) return true;
          URI src = href.get();
          for (URI v : visible) {
            if (src.toString().startsWith(v.toString())) return true;
          }
          return false;
        });
      }
      if (!colorByCollection.isEmpty()) {
        stream = stream.peek(e -> applySubscriptionColor(e, colorByCollection));
      }
      // Harvest tag universe on every fetch + apply the active
      // tag filter (Feature #3). Universe grows monotonically until
      // the next refreshAll.
      stream = stream.peek(this::harvestTags);
      java.util.Set<String> active = stateStore.readTagFilter();
      if (!active.isEmpty()) {
        stream = stream.filter(e -> matchesAnyTag(e, active));
      }
      return stream;
    } catch (RuntimeException ex) {
      logger().info("findInRange failed against {}: {}",
          service.collectionUri(), ex.toString());
      markDisconnected(shortReason(ex));
      return Stream.empty();
    }
  }

  /**
   * Source of truth for entry colour at render time: the
   * subscription record. Overrides the {@code CalendarService}
   * palette-by-hash default so user picks in the Subscriptions
   * dialog take effect immediately.
   */
  private java.util.Map<URI, String> colorBySubscriptionUri() {
    java.util.Map<URI, String> out = new java.util.HashMap<>();
    for (CalendarSubscription s : stateStore.readSubscriptions()) {
      if (s.color() != null && !s.color().isBlank()) {
        out.put(s.uri(), s.color());
      }
    }
    return out;
  }

  /**
   * Harvests every tag carried by an Entry into the running
   * {@link #tagUniverse}. The combobox setItems-refresh is debounced
   * onto the UI thread via {@link com.vaadin.flow.component.UI#access};
   * we only push when the universe actually grew.
   */
  private void harvestTags(Entry entry) {
    java.util.Set<String> tags = com.svenruppert.chronogrid.mapping.EntryMapper
        .readTags(entry);
    if (tags.isEmpty()) return;
    boolean grew;
    synchronized (tagUniverse) {
      int before = tagUniverse.size();
      tagUniverse.addAll(tags);
      grew = tagUniverse.size() != before;
    }
    if (grew) {
      com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
      if (ui != null && tagFilter != null) {
        ui.access(() -> tagFilter.setItems(snapshotTagUniverse()));
      }
    }
  }

  private static boolean matchesAnyTag(Entry entry, java.util.Set<String> filter) {
    java.util.Set<String> tags = com.svenruppert.chronogrid.mapping.EntryMapper
        .readTags(entry);
    if (tags.isEmpty()) return false;
    for (String t : tags) {
      if (filter.contains(t)) return true;
    }
    return false;
  }

  private java.util.List<String> snapshotTagUniverse() {
    synchronized (tagUniverse) {
      return java.util.List.copyOf(tagUniverse);
    }
  }

  /**
   * Refreshes the tag universe on demand — used on construction and
   * after explicit grid refreshes. Walks the persisted filter so the
   * combobox renders existing selections even before the next fetch
   * round has surfaced their values.
   */
  private void refreshTagUniverse() {
    java.util.Set<String> saved = stateStore.readTagFilter();
    if (saved.isEmpty()) return;
    synchronized (tagUniverse) {
      tagUniverse.addAll(saved);
    }
    if (tagFilter != null) {
      tagFilter.setItems(snapshotTagUniverse());
    }
  }

  private static void applySubscriptionColor(Entry entry,
                                             java.util.Map<URI, String> colors) {
    java.util.Optional<URI> href =
        com.svenruppert.chronogrid.mapping.EntryMapper.readHref(entry);
    if (href.isEmpty()) return;
    String src = href.get().toString();
    for (java.util.Map.Entry<URI, String> e : colors.entrySet()) {
      if (src.startsWith(e.getKey().toString())) {
        entry.setColor(e.getValue());
        return;
      }
    }
  }

  private Entry lookupWithStatus(String id) {
    try {
      Entry found = this.service.findById(id).orElse(null);
      if (found != null) markConnected();
      return found;
    } catch (RuntimeException ex) {
      logger().info("findById({}) failed against {}: {}", id,
          service.collectionUri(), ex.toString());
      markDisconnected(shortReason(ex));
      return null;
    }
  }

  /**
   * Lightweight liveness probe — used by the periodic poll and after
   * Save in the Settings dialog. Updates the connection badge as a
   * side effect; never throws.
   */
  private void probeQuietly() {
    LocalDateTime now = LocalDateTime.now();
    try {
      this.service.findInRange(now.minusMinutes(1), now.plusMinutes(1)).count();
      markConnected();
    } catch (RuntimeException ex) {
      markDisconnected(shortReason(ex));
    }
    // Refresh each server's individual status pill alongside the
    // overall connection badge — cheap (one short REPORT per server)
    // and keeps the toolbar honest about which backend is which.
    probeAllServers();
  }

  // ── settings dialog ────────────────────────────────────────────

  Dialog openSettingsDialog() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(messages.tr(K_SET_TITLE, "CalDAV server settings"));
    dialog.setWidth("520px");

    CalDavConnectionConfig current = currentConfig();

    TextField uri = new TextField(messages.tr(K_SET_URI, "Collection URI"));
    uri.setValue(current.collectionUri().toString());
    uri.setWidthFull();
    uri.setPlaceholder("http://127.0.0.1:5232/calendars/personal/");

    TextField username = new TextField(messages.tr(K_SET_USER, "Username (optional)"));
    username.setValue(current.username() == null ? "" : current.username());
    username.setWidthFull();

    PasswordField password = new PasswordField(messages.tr(K_SET_PASS, "Password (optional)"));
    password.setValue(current.password() == null ? "" : current.password());
    password.setWidthFull();

    CheckboxGroup<DiscoveredCalendar> picker = new CheckboxGroup<>();
    picker.setLabel(messages.tr(K_SET_PICKER_LABEL, "Discovered calendars"));
    picker.setItemLabelGenerator(DiscoveredCalendar::displayName);
    picker.setWidthFull();
    picker.setVisible(false);
    picker.addValueChangeListener(e -> {
      java.util.Set<DiscoveredCalendar> selected = e.getValue();
      if (selected != null && !selected.isEmpty()) {
        DiscoveredCalendar first = selected.iterator().next();
        uri.setValue(first.href().toString());
      }
    });

    Button discover = new Button(messages.tr(K_SET_DISCOVER, "Discover calendars"),
        VaadinIcon.SEARCH.create());
    discover.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    discover.addClickListener(e -> withBusyButton(discover,
        () -> runDiscovery(uri.getValue(), username.getValue(),
            password.getValue(), picker)));

    String defaultHint = messages.tr(K_SET_HINT,
        "Username + password are sent as HTTP Basic auth on every request. "
            + "Leave blank for the local caldav-testbench. Pick a provider "
            + "preset above for a one-click URL + provider-specific hint.");
    Span hint = new Span(defaultHint);
    hint.addClassName("chronogrid-secondary-text");

    HorizontalLayout presets = buildPresetRow(uri, hint, defaultHint);

    VerticalLayout form = new VerticalLayout(
        presets, uri, username, password, discover, picker, hint);
    form.setPadding(false);
    form.setSpacing(true);
    dialog.add(form);

    Button test = new Button(messages.tr(K_SET_TEST, "Test connection"),
        VaadinIcon.CONNECT.create());
    test.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    test.addClickListener(e -> withBusyButton(test,
        () -> probeConnection(uri.getValue(),
            username.getValue(), password.getValue())));

    Button save = new Button(messages.tr(K_ACTION_SAVE, "Save"), e -> {
      if (applyConfig(uri.getValue(), username.getValue(), password.getValue(),
          picker.getValue())) {
        dialog.close();
      }
    });
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancel = new Button(messages.tr(K_ACTION_CANCEL, "Cancel"), e -> dialog.close());
    cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    HorizontalLayout actions = new HorizontalLayout(test, cancel, save);
    actions.setAlignItems(FlexComponent.Alignment.CENTER);
    dialog.getFooter().add(actions);
    dialog.open();
    return dialog;
  }

  private HorizontalLayout buildPresetRow(TextField uri, Span hint,
                                          String defaultHint) {
    Span label = new Span(messages.tr(K_SET_PRESETS_LABEL, "Quick connect:"));
    label.addClassName("chronogrid-secondary-text");

    HorizontalLayout row = new HorizontalLayout(label);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);

    for (CalDavProviderPreset preset : CalDavProviderPreset.DEFAULTS) {
      String labelKey = K_PROVIDER_PREFIX + preset.id() + ".label";
      String hintKey = K_PROVIDER_PREFIX + preset.id() + ".hint";
      Button btn = new Button(messages.tr(labelKey, preset.label()),
          preset.icon().create(),
          e -> {
            uri.setValue(preset.entryUri());
            hint.setText(messages.tr(hintKey, preset.hint()));
          });
      btn.setId("calendar-provider-" + preset.id());
      btn.getElement().setProperty("title",
          messages.tr(labelKey, preset.label()));
      btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      row.add(btn);
    }
    return row;
  }

  private void runDiscovery(String rawUri, String user, String pass,
                            CheckboxGroup<DiscoveredCalendar> picker) {
    URI startUri = parseUri(rawUri);
    if (startUri == null) return;
    try {
      List<DiscoveredCalendar> found =
          new CalDavDiscovery().discover(startUri, user, pass);
      if (found.isEmpty()) {
        picker.setItems(List.of());
        picker.setVisible(false);
        notifyError(messages.tr(K_NOTIFY_DISCOVERY_EMPTY,
            "No calendars found at {0}", startUri.toString()));
        return;
      }
      picker.setItems(found);
      picker.setValue(java.util.Set.of(found.get(0)));
      picker.setVisible(true);
      notifyInfo(messages.tr(K_NOTIFY_DISCOVERY_FOUND,
          "Found {0} calendar(s)", String.valueOf(found.size())));
    } catch (RuntimeException ex) {
      logger().info("Discovery against {} failed: {}", startUri, ex.toString());
      notifyError(messages.tr(K_NOTIFY_DISCOVERY_FAIL,
          "Discovery failed: {0}", friendlyError(ex)));
    }
  }

  private void probeConnection(String rawUri, String user, String pass) {
    URI parsed = parseUri(rawUri);
    if (parsed == null) return;
    CalDavConnectionConfig probeConfig =
        new CalDavConnectionConfig(parsed, user, pass).normalised();
    CalendarService probe = CalendarService.fromConfig(probeConfig, displayZone);
    try {
      probe.findInRange(LocalDateTime.now().minusHours(1),
          LocalDateTime.now().plusHours(1)).count();
      notifyInfo(messages.tr(K_NOTIFY_TEST_OK, "Connection OK"));
    } catch (RuntimeException ex) {
      logger().info("Probe against {} failed: {}", parsed, ex.toString());
      notifyError(messages.tr(K_NOTIFY_TEST_FAIL,
          "Connection failed: {0}", friendlyError(ex)));
    }
  }

  private boolean applyConfig(String rawUri, String user, String pass,
                              java.util.Set<DiscoveredCalendar> picked) {
    URI parsed = parseUri(rawUri);
    if (parsed == null) return false;
    // Each Settings.Save adds (or refreshes) ONE server entry.
    // Subscriptions get stamped with that server's id.
    CalDavServerConnection server = upsertServer(parsed, user, pass);
    mergeSubscriptionsWithServer(parsed, picked, server.id());

    // Keep a back-compat single-cal config in the session for any
    // callers still reading SESSION_KEY_CONNECTION.
    List<URI> additional = new java.util.ArrayList<>();
    if (picked != null) {
      for (DiscoveredCalendar c : picked) {
        if (!c.href().equals(parsed)) additional.add(c.href());
      }
    }
    storeConfig(new CalDavConnectionConfig(
        parsed, user, pass, additional).normalised());

    rebuildServiceFromSubscriptions();
    calendar.getEntryProvider().refreshAll();
    String summary = additional.isEmpty()
        ? parsed.toString()
        : parsed + " (+" + additional.size() + " more)";
    notifyInfo(messages.tr(K_NOTIFY_APPLIED, "Connected to {0}", summary));
    return true;
  }

  /**
   * Looks for an existing server entry with the same baseUri; if
   * found, updates its credentials in place (so re-saving Settings
   * doesn't pile up duplicates). Otherwise creates a fresh entry
   * with a new id.
   */
  private CalDavServerConnection upsertServer(URI baseUri,
                                              String user, String pass) {
    java.util.List<CalDavServerConnection> existing = stateStore.readServers();
    java.util.List<CalDavServerConnection> updated = new java.util.ArrayList<>();
    CalDavServerConnection matched = null;
    for (CalDavServerConnection s : existing) {
      if (s.baseUri().equals(baseUri)) {
        matched = new CalDavServerConnection(s.id(), s.displayName(),
            s.baseUri(), user, pass);
        updated.add(matched);
      } else {
        updated.add(s);
      }
    }
    if (matched == null) {
      matched = CalDavServerConnection.create(null, baseUri, user, pass);
      updated.add(matched);
    }
    storeServers(updated);
    return matched;
  }

  /**
   * Merges the Discover/CheckboxGroup selection into the session
   * subscriptions list, stamping each new subscription with the
   * given {@code serverId}. Subscriptions from OTHER servers stay
   * untouched (so adding a second server appends, not replaces).
   * Subscriptions from THIS server that are no longer in the
   * picked set get dropped (the user explicitly unticked them).
   * Surviving subscriptions keep their existing visibility flag.
   */
  private void mergeSubscriptionsWithServer(URI primary,
                                            java.util.Set<DiscoveredCalendar> picked,
                                            String serverId) {
    java.util.List<CalendarSubscription> kept = new java.util.ArrayList<>();
    java.util.Map<URI, CalendarSubscription> existingFromServer =
        new java.util.HashMap<>();
    for (CalendarSubscription s : stateStore.readSubscriptions()) {
      if (serverId.equals(s.serverId())) {
        existingFromServer.put(s.uri(), s);
      } else {
        kept.add(s); // subscriptions from other servers — untouched
      }
    }

    if (picked == null || picked.isEmpty()) {
      // No picker selection — single primary URI from this server.
      String dn = primary.getHost() == null
          ? primary.toString() : primary.getHost() + primary.getPath();
      CalendarSubscription prior = existingFromServer.get(primary);
      boolean visible = prior == null || prior.visible();
      kept.add(new CalendarSubscription(primary, dn, null, visible, serverId));
      storeSubscriptions(kept);
      return;
    }

    // Primary first, then the rest from this server.
    DiscoveredCalendar primaryPick = null;
    for (DiscoveredCalendar c : picked) {
      if (c.href().equals(primary)) primaryPick = c;
    }
    if (primaryPick != null) {
      kept.add(toSubscription(primaryPick, existingFromServer, serverId));
    }
    for (DiscoveredCalendar c : picked) {
      if (c.href().equals(primary)) continue;
      kept.add(toSubscription(c, existingFromServer, serverId));
    }
    storeSubscriptions(kept);
  }

  private static CalendarSubscription toSubscription(
      DiscoveredCalendar c,
      java.util.Map<URI, CalendarSubscription> existingByUri,
      String serverId) {
    CalendarSubscription prior = existingByUri.get(c.href());
    boolean visible = prior == null || prior.visible();
    String displayName = c.displayName() == null ? c.href().toString() : c.displayName();
    return new CalendarSubscription(c.href(), displayName, c.color(), visible, serverId);
  }

  private URI parseUri(String raw) {
    if (raw == null || raw.isBlank()) {
      notifyError(messages.tr(K_NOTIFY_BAD_URI, "Collection URI must not be empty"));
      return null;
    }
    try {
      URI u = new URI(raw.trim());
      if (u.getScheme() == null || u.getHost() == null) {
        throw new URISyntaxException(raw, "scheme + host required");
      }
      return u;
    } catch (URISyntaxException use) {
      notifyError(messages.tr(K_NOTIFY_BAD_URI, "Invalid URI: {0}", use.getReason()));
      return null;
    }
  }

  // ── editor dialog (new + edit) ─────────────────────────────────

  private void openNewEventEditor() {
    LocalDateTime base = LocalDateTime.now()
        .withMinute(0).withSecond(0).withNano(0).plusHours(1);
    Entry draft = new Entry(UUID.randomUUID().toString());
    draft.setStart(base);
    draft.setEnd(base.plusHours(1));
    openEditor(draft, true);
  }

  private void openEditor(Entry entry, boolean isNew) {
    java.util.List<CalendarSubscription> visibleSubs = stateStore.readSubscriptions().stream()
        .filter(CalendarSubscription::visible)
        .toList();
    EventEditorDialog editor = new EventEditorDialog(messages,
        entry, isNew,
        visibleSubs,
        stateStore.readServers(),
        visibleSubs.isEmpty() ? null : visibleSubs.get(0).uri(),
        (toSave, targetUri) -> persistSave(toSave, isNew, targetUri),
        this::confirmAndDelete);
    editor.open();
  }

  // ── write helpers ──────────────────────────────────────────────

  private void persistSave(Entry entry, boolean isNew, URI targetCollection) {
    try {
      Entry persisted = service.save(entry, targetCollection);
      markConnected();
      if (isNew) {
        calendar.getEntryProvider().refreshAll();
      } else {
        calendar.getEntryProvider().refreshItem(persisted);
      }
    } catch (ConcurrentModificationException cme) {
      markConnected();
      logger().info("Conflict saving entry {} — reloading", entry.getId());
      notifyConflict();
      calendar.getEntryProvider().refreshAll();
    } catch (RuntimeException ex) {
      logger().info("Saving entry {} failed: {}", entry.getId(), ex.toString());
      markDisconnected(shortReason(ex));
      notifyError(messages.tr(K_NOTIFY_SAVE_FAILED, "Could not save: {0}", friendlyError(ex)));
    }
  }

  private void persistMove(Entry entry) {
    try {
      service.save(entry);
      markConnected();
      calendar.getEntryProvider().refreshItem(entry);
    } catch (ConcurrentModificationException cme) {
      markConnected();
      logger().info("Conflict moving entry {} — reloading", entry.getId());
      notifyConflict();
      calendar.getEntryProvider().refreshAll();
    } catch (RuntimeException ex) {
      logger().info("Moving entry {} failed: {}", entry.getId(), ex.toString());
      markDisconnected(shortReason(ex));
      notifyError(messages.tr(K_NOTIFY_SAVE_FAILED, "Could not save: {0}", friendlyError(ex)));
      calendar.getEntryProvider().refreshAll();
    }
  }

  // ── state-store-backed config helpers ──────────────────────────

  private static CalendarService resolveInitialService(CalendarStateStore store) {
    return store.readConnection()
        .map(cfg -> CalendarService.fromConfig(cfg, ZoneId.systemDefault()))
        .orElseGet(ChronoGrid::defaultServiceFromPreset);
  }

  /**
   * Fallback when the {@link CalendarStateStore} has no stored
   * connection and no explicit service was constructor-injected.
   * Constructs an anonymous {@link CalendarService} pointing at the
   * first {@link CalDavProviderPreset#DEFAULTS} entry — i.e. the
   * Apple iCloud quick-connect URL. The user will need to enter
   * credentials in the Settings dialog before any data flows.
   *
   * <p>Tests that need a different default (e.g. a local testbench
   * port) should construct {@code ChronoGrid} via the explicit
   * {@code ChronoGrid(CalendarStateStore, CalendarMessages,
   * CalendarService)} constructor instead of going through the
   * no-arg route entry point.
   */
  private static CalendarService defaultServiceFromPreset() {
    URI entryUri = URI.create(CalDavProviderPreset.DEFAULTS.get(0).entryUri());
    return new CalendarService(entryUri);
  }

  private CalDavConnectionConfig currentConfig() {
    return stateStore.readConnection()
        .orElseGet(() -> CalDavConnectionConfig.anonymous(service.collectionUri()));
  }

  private void storeConfig(CalDavConnectionConfig config) {
    stateStore.writeConnection(config);
  }

  // ── subscriptions + servers (state-store backed) ───────────────

  /**
   * @deprecated kept for the BrowserlessTest fixtures. New callers
   *     should resolve a {@link CalendarStateStore} explicitly.
   */
  @Deprecated
  public static java.util.List<CalendarSubscription> readSubscriptions() {
    return new VaadinSessionCalendarStateStore().readSubscriptions();
  }

  private void storeSubscriptions(java.util.List<CalendarSubscription> subs) {
    stateStore.writeSubscriptions(subs);
  }

  /**
   * @deprecated kept for the BrowserlessTest fixtures. New callers
   *     should resolve a {@link CalendarStateStore} explicitly.
   */
  @Deprecated
  public static java.util.List<CalDavServerConnection> readServers() {
    return new VaadinSessionCalendarStateStore().readServers();
  }

  private void storeServers(java.util.List<CalDavServerConnection> servers) {
    stateStore.writeServers(servers);
  }

  private java.util.Set<URI> visibleUris() {
    java.util.List<CalendarSubscription> subs = stateStore.readSubscriptions();
    if (subs.isEmpty()) return null; // null = no filter (back-compat single-cal)
    java.util.Set<URI> out = new java.util.HashSet<>();
    for (CalendarSubscription cs : subs) {
      if (cs.visible()) out.add(cs.uri());
    }
    return out;
  }

  Dialog openConnectionsDialog() {
    ConnectionsDialog dialog = new ConnectionsDialog(messages,
        stateStore.readServers(), stateStore.readSubscriptions());
    dialog.open();
    return dialog.getContent();
  }

  private void openSubscriptionsDialog() {
    SubscriptionsDialog dialog = new SubscriptionsDialog(messages,
        stateStore.readSubscriptions(),
        stateStore.readServers(),
        this::toggleSubscriptionVisible,
        this::changeSubscriptionColor,
        this::removeSubscription);
    dialog.open();
  }

  private void changeSubscriptionColor(URI uri, String color) {
    java.util.List<CalendarSubscription> updated = new java.util.ArrayList<>();
    for (CalendarSubscription cs : stateStore.readSubscriptions()) {
      if (cs.uri().equals(uri)) {
        updated.add(cs.withColor(color));
      } else {
        updated.add(cs);
      }
    }
    storeSubscriptions(updated);
    calendar.getEntryProvider().refreshAll();
  }

  private void toggleSubscriptionVisible(URI uri, boolean visible) {
    java.util.List<CalendarSubscription> updated = new java.util.ArrayList<>();
    String name = null;
    for (CalendarSubscription cs : stateStore.readSubscriptions()) {
      if (cs.uri().equals(uri)) {
        updated.add(cs.withVisible(visible));
        name = cs.displayName();
      } else {
        updated.add(cs);
      }
    }
    storeSubscriptions(updated);
    calendar.getEntryProvider().refreshAll();
    String label = name == null ? uri.toString() : name;
    notifyInfo(visible
        ? messages.tr(K_NOTIFY_SUB_SHOWN, "Showing “{0}” again.", label)
        : messages.tr(K_NOTIFY_SUB_HIDDEN, "Hidden “{0}” from view.", label));
  }

  private void removeSubscription(URI uri) {
    java.util.List<CalendarSubscription> kept = new java.util.ArrayList<>();
    String name = null;
    for (CalendarSubscription cs : stateStore.readSubscriptions()) {
      if (cs.uri().equals(uri)) {
        name = cs.displayName();
        continue;
      }
      kept.add(cs);
    }
    storeSubscriptions(kept);
    pruneOrphanServers();
    rebuildServiceFromSubscriptions();
    calendar.getEntryProvider().refreshAll();
    String label = name == null ? uri.toString() : name;
    notifyInfo(messages.tr(K_NOTIFY_SUB_REMOVED, "Disconnected from “{0}”.", label));
  }

  /**
   * Drops {@link CalDavServerConnection}s that no surviving
   * subscription references — the user fully disconnected from that
   * server, so its credentials should not linger in the session.
   */
  private void pruneOrphanServers() {
    java.util.Set<String> referenced = new java.util.HashSet<>();
    for (CalendarSubscription s : stateStore.readSubscriptions()) {
      if (s.serverId() != null) referenced.add(s.serverId());
    }
    java.util.List<CalDavServerConnection> kept = new java.util.ArrayList<>();
    for (CalDavServerConnection s : stateStore.readServers()) {
      if (referenced.contains(s.id())) kept.add(s);
    }
    storeServers(kept);
  }

  /**
   * Rewires {@link #service} from the current servers + subscriptions
   * lists. Each subscription's {@code serverId} routes its REPORT /
   * PUT requests through the matching server's credentials.
   */
  private void rebuildServiceFromSubscriptions() {
    java.util.List<CalendarSubscription> subs = stateStore.readSubscriptions();
    if (subs.isEmpty()) {
      // No subscriptions left — fall back to the preset default
      // so the next discovery flow has a usable entry point.
      this.service = defaultServiceFromPreset();
      refreshBackendStatus();
      return;
    }
    this.service = CalendarService.fromConnections(
        stateStore.readServers(), subs, displayZone);
    refreshBackendStatus();
  }

  // ── notifications ──────────────────────────────────────────────

  private void notifyConflict() {
    Notification n = Notification.show(
        messages.tr(K_NOTIFY_CONFLICT,
            "This event was changed by someone else. Reloading."),
        4000,
        Notification.Position.MIDDLE);
    n.addThemeVariants(NotificationVariant.LUMO_WARNING);
  }

  /**
   * Marks {@code button} disabled and visually "busy" for the
   * duration of {@code action}. Discovery and probe calls are
   * synchronous on the JDK HttpClient (~1–3 s for iCloud) — without
   * this hint users click the button repeatedly thinking nothing
   * happened.
   */
  private static void withBusyButton(Button button, Runnable action) {
    button.setEnabled(false);
    button.getElement().setAttribute("aria-busy", "true");
    try {
      action.run();
    } finally {
      button.setEnabled(true);
      button.getElement().removeAttribute("aria-busy");
    }
  }

  private void confirmAndDelete(Entry entry, Runnable closeEditor) {
    Dialog confirm = new Dialog();
    confirm.setHeaderTitle(messages.tr(K_DELETE_CONFIRM_TITLE, "Delete event?"));
    String title = entry.getTitle() == null || entry.getTitle().isBlank()
        ? "(untitled)" : entry.getTitle();
    Span body = new Span(messages.tr(K_DELETE_CONFIRM_BODY,
        "Delete the event “{0}”? This action cannot be undone.",
        title));
    confirm.add(body);

    Button doDelete = new Button(messages.tr(K_ACTION_DELETE, "Delete"), ev -> {
      try {
        service.delete(entry);
        markConnected();
        calendar.getEntryProvider().refreshAll();
      } catch (ConcurrentModificationException cme) {
        markConnected();
        notifyConflict();
        calendar.getEntryProvider().refreshAll();
      } catch (RuntimeException ex) {
        logger().info("Deleting entry {} failed: {}",
            entry.getId(), ex.toString());
        markDisconnected(shortReason(ex));
        notifyError(messages.tr(K_NOTIFY_DELETE_FAILED,
            "Could not delete: {0}", friendlyError(ex)));
      }
      confirm.close();
      closeEditor.run();
    });
    doDelete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

    Button cancelDelete = new Button(messages.tr(K_ACTION_CANCEL, "Cancel"),
        ev -> confirm.close());
    cancelDelete.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    HorizontalLayout deleteActions = new HorizontalLayout(cancelDelete, doDelete);
    confirm.getFooter().add(deleteActions);
    confirm.open();
  }

  private String friendlyError(Throwable ex) {
    String detail = CalDavErrors.detail(ex);
    return switch (CalDavErrors.classify(ex)) {
      case UNAUTHORIZED -> messages.tr(K_ERROR_UNAUTHORIZED,
          "Authentication rejected. For Apple iCloud, make sure you used an "
              + "app-specific password from appleid.apple.com — the regular "
              + "Apple ID password is blocked by Apple's 2FA. Detail: {0}",
          detail);
      case NOT_FOUND -> messages.tr(K_ERROR_NOT_FOUND,
          "Resource not found. Check the Collection URI, or run Discover to "
              + "find the right one. Detail: {0}", detail);
      case TIMEOUT -> messages.tr(K_ERROR_TIMEOUT,
          "The server did not respond in time. Check network / firewall. "
              + "Detail: {0}", detail);
      case NETWORK -> messages.tr(K_ERROR_NETWORK,
          "Could not reach the server. Check the URL and your network. "
              + "Detail: {0}", detail);
      case SERVER -> messages.tr(K_ERROR_SERVER,
          "The CalDAV server reported an error. Try again later. "
              + "Detail: {0}", detail);
      case MALFORMED -> messages.tr(K_ERROR_MALFORMED,
          "The server response could not be parsed. Detail: {0}", detail);
      default -> messages.tr(K_ERROR_GENERIC,
          "Something went wrong: {0}", detail);
    };
  }

  private static void notifyInfo(String text) {
    Notification n = Notification.show(text, 3000, Notification.Position.BOTTOM_START);
    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  private static void notifyError(String text) {
    Notification n = Notification.show(text, 4500, Notification.Position.BOTTOM_START);
    n.addThemeVariants(NotificationVariant.LUMO_ERROR);
  }
}