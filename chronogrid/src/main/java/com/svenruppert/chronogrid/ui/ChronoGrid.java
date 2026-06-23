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
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
  private static final String K_TB_REFRESH = "calendar.toolbar.refresh";
  private static final String K_TB_NEW = "calendar.toolbar.newEvent";
  private static final String K_TB_TAG_FILTER = "calendar.toolbar.tagFilter";
  private static final String K_TB_TAG_FILTER_PLACEHOLDER = "calendar.toolbar.tagFilter.placeholder";
  private static final String K_TB_MANAGER = "calendar.toolbar.manager";
  // Schicht 1 of Planning-Feature #7: Quick-Toggle dropdown.
  private static final String K_TB_VISIBILITY = "calendar.toolbar.visibility";
  private static final String K_VISIBILITY_BULK_ON = "calendar.toolbar.visibility.bulkOn";
  private static final String K_VISIBILITY_BULK_OFF = "calendar.toolbar.visibility.bulkOff";
  private static final String K_VISIBILITY_EMPTY = "calendar.toolbar.visibility.empty";
  private static final String K_VISIBILITY_HEADER = "calendar.toolbar.visibility.header";
  private static final String K_NOTIFY_SUB_REMOVED = "calendar.notify.subscription.removed";
  private static final String K_NOTIFY_SUB_HIDDEN = "calendar.notify.subscription.hidden";
  private static final String K_NOTIFY_SUB_SHOWN = "calendar.notify.subscription.shown";
  // Note: the K_SET_* / K_PROVIDER_PREFIX constants and the legacy
  // K_TB_SETTINGS/K_TB_CONNECTIONS/K_TB_SUBSCRIPTIONS toolbar keys
  // are gone with Schicht 3b. The Connection Manager + Wizard own
  // their own i18n key namespace (calendar.manager.*, calendar.wizard.*).
  // The CalDavProviderPreset id is consumed by ConnectionWizardDialog
  // directly — that's the single remaining consumer of the legacy
  // calendar.settings.provider.<id>.* bundle keys; they live there
  // unchanged for backward compatibility of those keys.
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

  /**
   * Planning-Feature #6: the user's focal day — the calendar date the
   * UI should keep visible across view-mode switches. Default at mount
   * is whatever the {@link CalendarStateStore} returns (today on a
   * fresh session). Every explicit navigation gesture updates this
   * field; {@link #applyViewMode} then re-anchors the calendar with
   * {@code gotoDate(focalDay)} after the view change so the user
   * stays on the same day instead of jumping back to today.
   *
   * <p>Update points:
   * <ul>
   *   <li>DatePicker value change → picked date</li>
   *   <li>Slide ± (one-day shift) → new gotoDate target</li>
   *   <li>Page ± (full-interval) → captured via armed datesRendered listener</li>
   *   <li>Today button → {@code LocalDate.now()}</li>
   * </ul>
   */
  private LocalDate focalDay;

  /**
   * Latch armed by page ± wrappers; the next {@code datesRendered}
   * event consumes it and reads the new interval-start into
   * {@link #focalDay}. Keeps page-navigation tracking out of the
   * synchronous (and possibly stale) {@code getCurrentIntervalStart()}
   * read-path while still letting all other gestures update focal
   * explicitly with a date computed in Java.
   */
  private transient boolean armFocalCapture;

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
    this.focalDay = stateStore.readFocalDay(LocalDate.now());

    // Planning-Feature #7 Schicht 5: one-shot legacy migration.
    // Run BEFORE anything reads servers/subscriptions so the rest
    // of the constructor sees the migrated multi-server state.
    migrateLegacyConnectionIfNeeded();

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

    calendar.addDatesRenderedListener(e -> {
      navigationBar.setIntervalLabel(formatVisibleInterval());
      // Planning-Feature #6: page-back / page-forward use FullCalendar's
      // native paging; the post-move interval-start only becomes visible
      // here. The latch is only armed by those two wrappers — all other
      // gestures update focalDay synchronously with a Java-computed date,
      // so we don't accidentally overwrite the focal value on every
      // gotoDate(focalDay) we issue from applyViewMode.
      if (armFocalCapture) {
        armFocalCapture = false;
        calendar.getCurrentIntervalStart().ifPresent(this::setFocalDay);
      }
    });

    // Anchor the initial visible window on the persisted focal day so a
    // page reload returns the user to where they were, not to today.
    if (focalDay != null && !focalDay.equals(LocalDate.now())) {
      calendar.gotoDate(focalDay);
    }

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

    // Planning-Feature #7 Schicht 3b: the Connection Manager fully
    // replaces the three legacy toolbar buttons (Settings, Connections,
    // Subscriptions). Their methods + dialog classes are gone — what's
    // left is one entry point that does the full add/edit/remove flow.
    Button manager = new Button(messages.tr(K_TB_MANAGER, "Connection Manager"),
        VaadinIcon.SERVER.create(), e -> openConnectionManagerDialog());
    manager.setId("calendar-toolbar-manager");
    manager.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

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

    Button visibilityToggle = buildVisibilityToggle();

    HorizontalLayout actions = new HorizontalLayout(
        tagFilter, manager, visibilityToggle, refresh, newEvent);
    actions.setAlignItems(FlexComponent.Alignment.END);
    actions.setSpacing(true);

    HorizontalLayout bar = new HorizontalLayout(status, actions);
    bar.setWidthFull();
    bar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    bar.setAlignItems(FlexComponent.Alignment.CENTER);
    return bar;
  }

  /**
   * Planning-Feature #7 Schicht 1 — the Quick-Toggle dropdown. A
   * single toolbar button shows the current visible-vs-total count
   * (e.g. "Visible (3/7)") and opens a {@link com.vaadin.flow.component.popover.Popover}
   * with a one-row-per-subscription toggle list plus bulk "show all"
   * / "hide all" buttons. Rebuilt on every open so the list reflects
   * fresh state and labels stay in sync after toggles from the
   * legacy {@code SubscriptionsDialog} (which we keep around during
   * the transition until Schicht 3 wires the Manager-Dialog).
   *
   * <p>Toggle handlers delegate to the existing
   * {@link #toggleSubscriptionVisible(URI, boolean)} so persistence
   * and notifications match the legacy path. Bulk-toggle uses
   * {@link #setAllSubscriptionsVisible(boolean)} to coalesce into a
   * single write + refresh instead of N calls.
   */
  private Button buildVisibilityToggle() {
    Button trigger = new Button(VaadinIcon.EYE.create());
    trigger.setId("calendar-toolbar-visibility");
    trigger.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    com.vaadin.flow.component.popover.Popover popover =
        new com.vaadin.flow.component.popover.Popover();
    popover.setTarget(trigger);
    popover.setWidth("280px");

    Runnable refreshLabel = () -> {
      List<CalendarSubscription> subs = stateStore.readSubscriptions();
      long visible = subs.stream().filter(CalendarSubscription::visible).count();
      trigger.setText(messages.tr(K_TB_VISIBILITY,
          "Visible ({0}/{1})",
          String.valueOf(visible),
          String.valueOf(subs.size())));
    };

    Runnable rebuildContent = () -> {
      popover.removeAll();
      List<CalendarSubscription> subs = stateStore.readSubscriptions();

      Span header = new Span(
          messages.tr(K_VISIBILITY_HEADER, "Calendars in view"));
      header.getStyle()
          .set("font-weight", "600")
          .set("padding", "var(--lumo-space-xs) 0");

      if (subs.isEmpty()) {
        Span empty = new Span(
            messages.tr(K_VISIBILITY_EMPTY, "No subscriptions yet"));
        empty.addClassName("chronogrid-secondary-text");
        VerticalLayout emptyLayout = new VerticalLayout(header, empty);
        emptyLayout.setPadding(false);
        emptyLayout.setSpacing(false);
        popover.add(emptyLayout);
        return;
      }

      Button bulkOn = new Button(
          messages.tr(K_VISIBILITY_BULK_ON, "Show all"),
          e -> {
            setAllSubscriptionsVisible(true);
            popover.close();
            refreshLabel.run();
          });
      bulkOn.addThemeVariants(ButtonVariant.LUMO_TERTIARY,
          ButtonVariant.LUMO_SMALL);
      Button bulkOff = new Button(
          messages.tr(K_VISIBILITY_BULK_OFF, "Hide all"),
          e -> {
            setAllSubscriptionsVisible(false);
            popover.close();
            refreshLabel.run();
          });
      bulkOff.addThemeVariants(ButtonVariant.LUMO_TERTIARY,
          ButtonVariant.LUMO_SMALL);

      HorizontalLayout bulkRow = new HorizontalLayout(bulkOn, bulkOff);
      bulkRow.setSpacing(true);
      bulkRow.setPadding(false);

      VerticalLayout rows = new VerticalLayout();
      rows.setPadding(false);
      rows.setSpacing(false);
      for (CalendarSubscription s : subs) {
        rows.add(buildVisibilityRow(s, refreshLabel));
      }

      VerticalLayout layout = new VerticalLayout(header, bulkRow, rows);
      layout.setPadding(false);
      layout.setSpacing(false);
      popover.add(layout);
    };

    popover.addOpenedChangeListener(e -> {
      if (e.isOpened()) rebuildContent.run();
    });

    refreshLabel.run();
    return trigger;
  }

  /**
   * One row in the visibility popover: a colour-dot plus the
   * calendar's display-name plus a Checkbox tied to its
   * {@code visible} flag. Delegates the toggle to
   * {@link #toggleSubscriptionVisible(URI, boolean)} so persistence,
   * refreshAll and the legacy notify-pair fire exactly once.
   */
  private com.vaadin.flow.component.Component buildVisibilityRow(
      CalendarSubscription s, Runnable refreshLabel) {
    com.vaadin.flow.component.html.Div dot =
        new com.vaadin.flow.component.html.Div();
    dot.getStyle()
        .set("width", "10px")
        .set("height", "10px")
        .set("border-radius", "50%")
        .set("background-color", s.color() != null ? s.color() : "transparent")
        .set("flex-shrink", "0");

    Span name = new Span(s.displayName());
    name.getStyle().set("flex", "1");

    com.vaadin.flow.component.checkbox.Checkbox check =
        new com.vaadin.flow.component.checkbox.Checkbox(s.visible());
    check.setId("visibility-toggle-" + s.uri().hashCode());
    check.addValueChangeListener(ev -> {
      toggleSubscriptionVisible(s.uri(), ev.getValue());
      refreshLabel.run();
    });

    HorizontalLayout row = new HorizontalLayout(dot, name, check);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setWidthFull();
    row.setSpacing(true);
    row.setPadding(false);
    row.getStyle().set("padding", "var(--lumo-space-xs) 0");
    return row;
  }

  /**
   * Coalesces a "show all / hide all" bulk toggle into a single
   * {@link CalendarStateStore} write plus one {@code refreshAll},
   * instead of the N writes + N refreshes you'd get by calling
   * {@link #toggleSubscriptionVisible(URI, boolean)} in a loop.
   * No per-subscription notification — the bulk action is itself
   * the signal.
   */
  private void setAllSubscriptionsVisible(boolean visible) {
    java.util.List<CalendarSubscription> updated = new java.util.ArrayList<>();
    boolean anyChange = false;
    for (CalendarSubscription cs : stateStore.readSubscriptions()) {
      if (cs.visible() == visible) {
        updated.add(cs);
      } else {
        updated.add(cs.withVisible(visible));
        anyChange = true;
      }
    }
    if (!anyChange) return;
    storeSubscriptions(updated);
    calendar.getEntryProvider().refreshAll();
  }

  // ── navigation bar wiring ──────────────────────────────────────

  private CalendarNavigationBar buildNavigationBar() {
    int initialN = readNDaysPreference();
    CalendarNavigationBar.NavigationCallbacks nav =
        new CalendarNavigationBar.NavigationCallbacks(
            this::onPageBack,      // ⏮ paging back: full interval
            this::onSlideBack,     // ◀ slide back: one day
            this::onToday,         // Today
            this::onSlideForward,  // ▶ slide forward: one day
            this::onPageForward);  // ⏭ paging forward: full interval
    CalendarNavigationBar bar = new CalendarNavigationBar(
        messages,
        focalDay,
        CalendarNavigationBar.ViewMode.MONTH,
        initialN,
        nav,
        date -> {
          if (date != null) {
            setFocalDay(date);
            calendar.gotoDate(date);
          }
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
        // The popover dots signal "which calendar has entries on
        // which day" — so the source-of-truth colour is the
        // calendar's, not any per-event override. Prefer the
        // persistent CUSTOM_CALENDAR_COLOR (always set by
        // applyColours / applySubscriptionOverride) over
        // entry.getColor() — the latter holds the per-event fill
        // when the user picked one and the calendar colour
        // otherwise; reading it would make a single user-coloured
        // event flip the day's dot away from its calendar's
        // colour. BUG #1 fix companion.
        String colour = e.getCustomProperty(
            com.svenruppert.chronogrid.service.CalendarService
                .CUSTOM_CALENDAR_COLOR);
        if (colour == null || colour.isBlank()) colour = e.getColor();
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
    calendar.getCurrentIntervalStart().ifPresent(d -> {
      LocalDate next = d.minusDays(1);
      setFocalDay(next);
      calendar.gotoDate(next);
    });
  }

  private void onSlideForward() {
    calendar.getCurrentIntervalStart().ifPresent(d -> {
      LocalDate next = d.plusDays(1);
      setFocalDay(next);
      calendar.gotoDate(next);
    });
  }

  private void onToday() {
    setFocalDay(LocalDate.now());
    calendar.today();
  }

  private void onPageBack() {
    armFocalCapture = true;
    calendar.previous();
  }

  private void onPageForward() {
    armFocalCapture = true;
    calendar.next();
  }

  private void applyViewMode(CalendarNavigationBar.ViewMode mode) {
    switch (mode) {
      case DAY -> calendar.changeView(CalendarViewImpl.TIME_GRID_DAY);
      case WEEK -> calendar.changeView(CalendarViewImpl.TIME_GRID_WEEK);
      case N_DAYS -> applyNDays(readNDaysPreference());
      default -> calendar.changeView(CalendarViewImpl.DAY_GRID_MONTH);
    }
    // Planning-Feature #6: re-anchor the new view on the user's focal
    // day so a switch from "Week of 2026-09-15" to "Month" lands in
    // September 2026, not on today's month. Issued after every
    // changeView (N-days included — applyNDays calls changeView too).
    if (focalDay != null) {
      calendar.gotoDate(focalDay);
    }
  }

  private void setFocalDay(LocalDate day) {
    if (day == null) return;
    this.focalDay = day;
    stateStore.writeFocalDay(day);
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
      // BUG #2 local-store overlay: if neither RFC-7986 COLOR nor
      // the DESCRIPTION-suffix marker survived (the classic
      // iCloud-user-edit case), look up the local per-UID colour
      // store as the unconditional fallback. Runs BEFORE the
      // subscription override so applyColours below sees the
      // restored CUSTOM_ENTRY_COLOR and emits the split-colour
      // (fill = own, border = calendar).
      stream = stream.peek(e -> {
        if (e.getCustomProperty(
            com.svenruppert.chronogrid.mapping.EntryMapper.CUSTOM_ENTRY_COLOR) != null) return;
        String uid = e.getId();
        if (uid == null) return;
        stateStore.readEntryColour(uid).ifPresent(c -> {
          e.setCustomProperty(
              com.svenruppert.chronogrid.mapping.EntryMapper.CUSTOM_ENTRY_COLOR, c);
          String calColour = e.getCustomProperty(
              com.svenruppert.chronogrid.service.CalendarService
                  .CUSTOM_CALENDAR_COLOR);
          if (calColour != null && !calColour.isBlank()) {
            com.svenruppert.chronogrid.service.CalendarService
                .applyColours(e, calColour);
          }
        });
      });
      if (!colorByCollection.isEmpty()) {
        stream = stream.peek(e ->
            com.svenruppert.chronogrid.service.CalendarService
                .applySubscriptionOverride(e, colorByCollection));
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
    // BUG #11: without an explicit setAllDay(false) the Stefan-
    // FullCalendar Entry leaves isAllDay() ambiguous (null/true),
    // which EntryMapper.toICalendarText then interprets as allDay
    // and emits DTSTART;VALUE=DATE — so Nextcloud (and any other
    // CalDAV consumer that respects RFC 5545) renders the new
    // event as a day-long block instead of the requested timed
    // slot. Drag-select path already does this correctly via
    // event.isAllDay() from the calendar listener; the toolbar
    // "New event" path needs the same explicit init.
    draft.setAllDay(false);
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
      // BUG #2 local-store mirror: keep the per-UID colour map in
      // sync with what the user just picked. If the user un-set
      // the colour, drop the stored value too. Read side overlays
      // from here when neither RFC-7986 COLOR nor the description
      // marker survived the CalDAV round-trip.
      String pickedColour = persisted.getCustomProperty(
          com.svenruppert.chronogrid.mapping.EntryMapper.CUSTOM_ENTRY_COLOR);
      if (pickedColour != null && !pickedColour.isBlank()) {
        stateStore.writeEntryColour(persisted.getId(), pickedColour);
      } else {
        stateStore.clearEntryColour(persisted.getId());
      }
      // BUG #12 part-2: refreshItem(persisted) was not picking up
      // colour changes reliably in FullCalendar v6 — Sven reported
      // "wenn ich die Farbe eines Events geändert habe, dann muss
      // ich einen reload machen damit die dann angezeigt wird".
      // Switch to refreshAll() for every save: slightly heavier
      // but guaranteed to repaint the entry's background/border
      // CSS variables. The render pipeline isn't expensive for
      // typical week/month ranges.
      calendar.getEntryProvider().refreshAll();
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
    // BUG #4 fix: multi-server state lives in
    // readSubscriptions() + readServers(); the legacy single-server
    // state lives in readConnection(). When the user has more than
    // one server connected, only the multi-server state is
    // authoritative — readConnection() ends up reflecting just the
    // most-recently-applied connection. Re-mounting the ChronoGrid
    // (new UI instance via route navigation, F5, re-login) was
    // therefore rebuilding the service with only ONE client while
    // visibleUris() correctly enumerated the full subscription set
    // — the other calendars never got queried.
    //
    // Restore multi-server state first; fall back to the legacy
    // single-connection path; final fallback is the preset default.
    java.util.List<CalendarSubscription> subs = store.readSubscriptions();
    if (!subs.isEmpty()) {
      return CalendarService.fromConnections(
          store.readServers(), subs, ZoneId.systemDefault());
    }
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

  /**
   * Planning-Feature #7 Schicht 5 — one-shot legacy migration.
   *
   * <p>Pre-multi-server sessions stored a single
   * {@link CalDavConnectionConfig} under the legacy
   * {@code calendar.connection.config} session key. The Connection
   * Manager + Wizard + Quick-Toggle work exclusively against the
   * multi-server state (servers + subscriptions). Without a
   * migration step, an existing user who upgrades would see an
   * empty Connection Manager + lose their stored credentials.
   *
   * <p>The migration runs at most once per session (idempotent: it
   * checks for an existing server/subscription before doing
   * anything) and converts the legacy single-connection into one
   * {@link CalDavServerConnection} + one {@link CalendarSubscription}
   * stamped with that server's id.
   *
   * <p><b>Risk-mitigation:</b> the legacy session key is NOT
   * deleted by this migration. Both stores stay readable so a
   * mid-deploy rollback can re-mount the legacy connection without
   * data loss. A future major release will pull the legacy key
   * once telemetry shows zero readers.
   */
  private void migrateLegacyConnectionIfNeeded() {
    if (!stateStore.readSubscriptions().isEmpty()) return;
    if (!stateStore.readServers().isEmpty()) return;
    java.util.Optional<CalDavConnectionConfig> legacy = stateStore.readConnection();
    if (legacy.isEmpty()) return;

    CalDavConnectionConfig cfg = legacy.get();
    URI collection = cfg.collectionUri();
    // Use the collection URI as the server's baseUri too — the user
    // can refine via the Connection-Manager's Re-discover later.
    CalDavServerConnection server = CalDavServerConnection.create(
        "Migrated", collection,
        cfg.username() == null ? "" : cfg.username(),
        cfg.password() == null ? "" : cfg.password());
    // Pick a deterministic default colour for the migrated entry —
    // the user can re-colour via the Connection-Manager's inline
    // picker any time.
    CalendarSubscription sub = new CalendarSubscription(
        collection,
        "Migrated calendar",
        "#1F77B4",
        true,
        server.id());
    stateStore.writeServers(java.util.List.of(server));
    stateStore.writeSubscriptions(java.util.List.of(sub));
    rebuildServiceFromSubscriptions();
    logger().info("Migrated legacy single-server connection to multi-server state: {}",
        collection);
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

  /**
   * Planning-Feature #7 Schicht 2 — opens the {@link ConnectionWizardDialog}.
   * Wires the three injected callbacks: {@code discoveryFn} runs the
   * exact same {@code CalDavDiscovery#discover} the legacy Settings
   * dialog uses (so behaviour is identical), {@code probeFn} does a
   * narrow {@code findInRange} via a throwaway {@link CalendarService},
   * and {@code onComplete} delegates to the existing
   * {@link #applyConfig} so the upsertServer + mergeSubscriptions
   * + activate path is shared with the legacy save-from-Settings.
   */
  Dialog openConnectionWizard() {
    ConnectionWizardDialog wizard = new ConnectionWizardDialog(
        messages,
        (uri, creds) -> new CalDavDiscovery().discover(uri, creds[0], creds[1]),
        creds -> {
          URI parsed = parseUri(creds[0]);
          if (parsed == null) return false;
          CalDavConnectionConfig probeConfig =
              new CalDavConnectionConfig(parsed, creds[1], creds[2]).normalised();
          try {
            CalendarService.fromConfig(probeConfig, displayZone)
                .findInRange(LocalDateTime.now().minusHours(1),
                    LocalDateTime.now().plusHours(1)).count();
            return true;
          } catch (RuntimeException ex) {
            logger().info("Wizard probe against {} failed: {}",
                parsed, ex.toString());
            return false;
          }
        },
        result -> applyConfig(result.uri().toString(),
            result.username(), result.password(),
            result.selectedCalendars()),
        (isError, msg) -> {
          if (isError) notifyError(msg); else notifyInfo(msg);
        });
    wizard.open();
    return wizard.getContent();
  }

  /**
   * Planning-Feature #7 Schicht 3: the Apple-Calendar-style
   * Connection Manager. Vertical server tabs on the left, per-server
   * subscription list on the right with inline visibility-toggle +
   * colour-picker + per-sub remove. Footer "+ Add server" delegates
   * to {@link #openConnectionWizard()}; per-server actions are
   * Re-discover and Remove server (atomic).
   */
  Dialog openConnectionManagerDialog() {
    ConnectionManagerDialog manager = new ConnectionManagerDialog(
        messages,
        () -> stateStore.readServers(),
        () -> stateStore.readSubscriptions(),
        this::openConnectionWizard,
        this::rediscoverServer,
        this::removeServerAtomic,
        this::toggleSubscriptionVisible,
        this::changeSubscriptionColor,
        this::removeSubscription);
    manager.open();
    return manager.getContent();
  }

  /**
   * Re-runs CalDAV discovery against the given server's URL +
   * credentials and merges any newly-found calendars as
   * {@code visible=false} subscriptions (so a Nextcloud workspace
   * that grew by 5 calendars overnight doesn't suddenly flood the
   * grid). Pre-existing subscriptions are left untouched.
   */
  private void rediscoverServer(String serverId) {
    CalDavServerConnection server = stateStore.readServers().stream()
        .filter(s -> s.id().equals(serverId)).findFirst().orElse(null);
    if (server == null) return;
    try {
      List<DiscoveredCalendar> found = new CalDavDiscovery().discover(
          server.baseUri(), server.username(), server.password());
      java.util.Set<URI> known = stateStore.readSubscriptions().stream()
          .filter(s -> serverId.equals(s.serverId()))
          .map(CalendarSubscription::uri)
          .collect(java.util.stream.Collectors.toSet());
      List<CalendarSubscription> appended =
          new java.util.ArrayList<>(stateStore.readSubscriptions());
      int added = 0;
      for (DiscoveredCalendar dc : found) {
        if (known.contains(dc.href())) continue;
        appended.add(new CalendarSubscription(dc.href(),
            dc.displayName(), dc.color(), false, serverId));
        added++;
      }
      if (added > 0) {
        storeSubscriptions(appended);
        rebuildServiceFromSubscriptions();
        calendar.getEntryProvider().refreshAll();
      }
      notifyInfo(messages.tr(K_NOTIFY_DISCOVERY_FOUND,
          "Found {0} calendar(s)", String.valueOf(found.size())));
    } catch (RuntimeException ex) {
      logger().info("Rediscovery against {} failed: {}",
          server.baseUri(), ex.toString());
      notifyError(messages.tr(K_NOTIFY_DISCOVERY_FAIL,
          "Discovery failed: {0}", friendlyError(ex)));
    }
  }

  /**
   * Atomic "remove server" — drops the server entry AND every
   * subscription that referenced it AND every per-entry colour
   * fallback persisted for those subscriptions' URIs. The user
   * thinks of "disconnect this account" as a single action; the
   * UX in the manager dialog should match.
   */
  private void removeServerAtomic(String serverId) {
    java.util.List<CalendarSubscription> kept = new java.util.ArrayList<>();
    java.util.List<URI> droppedUris = new java.util.ArrayList<>();
    for (CalendarSubscription cs : stateStore.readSubscriptions()) {
      if (serverId.equals(cs.serverId())) {
        droppedUris.add(cs.uri());
      } else {
        kept.add(cs);
      }
    }
    storeSubscriptions(kept);
    pruneOrphanServers();
    // Clear per-URI colour fallbacks for every dropped sub — the
    // BUG-#2 local store would otherwise hold colour entries for
    // URIs the user just disconnected from.
    for (URI uri : droppedUris) {
      stateStore.clearEntryColour(uri.toString());
    }
    rebuildServiceFromSubscriptions();
    calendar.getEntryProvider().refreshAll();
    notifyInfo(messages.tr(K_NOTIFY_SUB_REMOVED,
        "Disconnected from “{0}”.", serverId));
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
        // BUG #2 cleanup: don't leave the colour ghosted in the
        // local store after the event itself is gone.
        if (entry.getId() != null) {
          stateStore.clearEntryColour(entry.getId());
        }
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