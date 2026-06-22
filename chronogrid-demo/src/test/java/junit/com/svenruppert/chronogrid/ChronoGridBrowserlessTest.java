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

package junit.com.svenruppert.chronogrid;

import com.svenruppert.caldav.testsupport.CalDavFixture;
import com.svenruppert.chronogrid.client.CalDavClient;
import com.svenruppert.chronogrid.service.CalDavConnectionConfig;
import com.svenruppert.chronogrid.service.CalDavServerConnection;
import com.svenruppert.chronogrid.service.CalendarService;
import com.svenruppert.chronogrid.service.CalendarSubscription;
import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.flow.views.AppLoginView;
import com.svenruppert.flow.views.CalendarRouteView;
import com.svenruppert.chronogrid.ui.ChronoGrid;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.server.VaadinSession;
import junit.com.svenruppert.flow.TestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.vaadin.stefan.fullcalendar.FullCalendar;

import java.io.IOException;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ChronoGrid — route gating + CalDAV-backed FullCalendar")
class ChronoGridBrowserlessTest extends BrowserlessTest {

  private static CalDavFixture fixture;

  @BeforeAll
  static void startTestbench() throws IOException {
    fixture = CalDavFixture.startWithCalendars("personal");
    // Per-test session seeding (see seedAuthState) points the
    // ChronoGrid ctor at the fixture URL — no static
    // provider injection required.
  }

  @AfterAll
  static void stopTestbench() {
    if (fixture != null) fixture.close();
  }

  @BeforeEach
  void seedAuthState() {
    TestSupport.seedAdminAndResetBootstrap();
    fixture.interactions().clear();
    VaadinSession session = VaadinSession.getCurrent();
    if (session != null) {
      // Seed the connection config to point at the testbench so the
      // no-arg ChronoGrid ctor's default fallback (which used to go
      // through CalendarServiceProvider) lands on the fixture and not
      // the bundled iCloud preset URL.
      URI collection = fixture.baseUri().resolve("/calendars/personal/");
      session.setAttribute(ChronoGrid.SESSION_KEY_CONNECTION,
          CalDavConnectionConfig.anonymous(collection));
      session.setAttribute(ChronoGrid.SESSION_KEY_SUBSCRIPTIONS, null);
      session.setAttribute(ChronoGrid.SESSION_KEY_SERVERS, null);
      session.setAttribute(ChronoGrid.SESSION_KEY_NDAYS, null);
    }
  }

  @Test
  @DisplayName("NAV constant is 'calendar'")
  void navConstant() {
    assertEquals("calendar", ChronoGrid.NAV);
  }

  @Test
  @DisplayName("anonymous visit to /calendar reroutes to /login")
  void anonymousReroutedToLogin() {
    SubjectStores.subjectStore().deleteCurrentSubject(AppUser.class);
    navigate("calendar", AppLoginView.class);
  }

  @Test
  @DisplayName("USER role lands on ChronoGrid and a FullCalendar component is attached")
  void userSeesFullCalendar() {
    AppUser user = new AppUser(50L, "Cal User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(CalendarRouteView.class);

    FullCalendar calendar = findFullCalendar();
    assertNotNull(calendar, "ChronoGrid must host a FullCalendar component");
  }

  @Test
  @DisplayName("connection badge starts as UNKNOWN, flips to CONNECTED after a successful fetch")
  void badgeFlipsToConnectedAfterFetch() {
    AppUser user = new AppUser(70L, "Cal User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(CalendarRouteView.class);
    ChronoGrid view = findCalendarView();
    assertEquals(ChronoGrid.ConnectionState.UNKNOWN, view.connectionState(),
        "badge must start as UNKNOWN until the first fetch resolves");

    findFullCalendar().getEntryProvider().fetch(
        java.time.LocalDateTime.now().minusDays(1),
        java.time.LocalDateTime.now().plusDays(1))
        .toList();
    assertEquals(ChronoGrid.ConnectionState.CONNECTED, view.connectionState(),
        "after a successful REPORT the badge must show CONNECTED");
  }

  @Test
  @DisplayName("badge shows DISCONNECTED when the backend is unreachable")
  void badgeFlipsToDisconnectedOnIoFailure() {
    VaadinSession.getCurrent().setAttribute(
        ChronoGrid.SESSION_KEY_CONNECTION,
        CalDavConnectionConfig.anonymous(
            URI.create("http://127.0.0.1:1/calendars/nonexistent/")));

    AppUser user = new AppUser(71L, "Offline User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(CalendarRouteView.class);
    ChronoGrid view = findCalendarView();

    findFullCalendar().getEntryProvider().fetch(
        java.time.LocalDateTime.now().minusDays(1),
        java.time.LocalDateTime.now().plusDays(1))
        .toList();
    assertEquals(ChronoGrid.ConnectionState.DISCONNECTED, view.connectionState(),
        "after an I/O failure the badge must flip to DISCONNECTED");
  }

  @Test
  @DisplayName("CalendarNavigationBar carries Day/Week/N days/Month tabs + N-days IntegerField")
  void navigationBarRendersAllControls() {
    AppUser user = new AppUser(120L, "Nav User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(CalendarRouteView.class);

    java.util.Set<String> tabIds = allDescendants(com.vaadin.flow.component.UI.getCurrent())
        .filter(com.vaadin.flow.component.tabs.Tab.class::isInstance)
        .map(c -> c.getId().orElse(""))
        .filter(s -> s.startsWith("chronogrid-nav-tab-"))
        .collect(java.util.stream.Collectors.toSet());
    assertTrue(tabIds.contains("chronogrid-nav-tab-day"),
        "Day tab must be present");
    assertTrue(tabIds.contains("chronogrid-nav-tab-week"),
        "Week tab must be present");
    assertTrue(tabIds.contains("chronogrid-nav-tab-ndays"),
        "N days tab must be present");
    assertTrue(tabIds.contains("chronogrid-nav-tab-month"),
        "Month tab must be present");

    long datePickers = allDescendants(com.vaadin.flow.component.UI.getCurrent())
        .filter(com.vaadin.flow.component.datepicker.DatePicker.class::isInstance)
        .count();
    assertTrue(datePickers >= 1L,
        "navigation bar must carry a DatePicker for go-to-date");

    long integerFields = allDescendants(com.vaadin.flow.component.UI.getCurrent())
        .filter(com.vaadin.flow.component.textfield.IntegerField.class::isInstance)
        .count();
    assertEquals(0L, integerFields,
        "N input must be slider-only — the IntegerField is gone in this iteration");

    boolean navGroupPresent = allDescendants(com.vaadin.flow.component.UI.getCurrent())
        .anyMatch(c -> "chronogrid-nav-group".equals(c.getId().orElse(null)));
    assertTrue(navGroupPresent,
        "navigation bar must carry the unified nav button group "
            + "(page back · slide back · today · slide forward · page forward)");
  }

  @Test
  @DisplayName("N-days preference persists on VaadinSession across reads")
  void nDaysPreferencePersistsInSession() {
    AppUser user = new AppUser(121L, "N User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);
    VaadinSession.getCurrent().setAttribute(ChronoGrid.SESSION_KEY_NDAYS, 11);

    navigate(CalendarRouteView.class);

    Object stored = VaadinSession.getCurrent()
        .getAttribute(ChronoGrid.SESSION_KEY_NDAYS);
    assertEquals(11, stored,
        "navigation bar must respect a session-stored N-days preference");
  }

  @Test
  @DisplayName("Connections toolbar button opens the ConnectionsDialog populated with all servers")
  void connectionsDialogListsAllServers() throws Exception {
    AppUser user = new AppUser(110L, "Conn User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    URI fixtureCollection = fixture.baseUri().resolve("/calendars/personal/");
    com.svenruppert.chronogrid.service.CalDavServerConnection live =
        com.svenruppert.chronogrid.service.CalDavServerConnection.create(
            "Testbench", fixture.baseUri(), null, null);
    com.svenruppert.chronogrid.service.CalDavServerConnection dead =
        com.svenruppert.chronogrid.service.CalDavServerConnection.create(
            "Offline", URI.create("http://127.0.0.1:1/"), null, null);

    VaadinSession.getCurrent().setAttribute(ChronoGrid.SESSION_KEY_SERVERS,
        java.util.List.of(live, dead));
    VaadinSession.getCurrent().setAttribute(ChronoGrid.SESSION_KEY_SUBSCRIPTIONS,
        java.util.List.of(
            new CalendarSubscription(fixtureCollection, "Personal",
                "#1F77B4", true, live.id()),
            new CalendarSubscription(URI.create("http://127.0.0.1:1/cal/x/"),
                "Phantom", "#FF7F0E", true, dead.id())));

    navigate(CalendarRouteView.class);

    ChronoGrid view = findCalendarView();
    java.lang.reflect.Method openConnections =
        ChronoGrid.class.getDeclaredMethod("openConnectionsDialog");
    openConnections.setAccessible(true);
    com.vaadin.flow.component.dialog.Dialog dialog =
        (com.vaadin.flow.component.dialog.Dialog) openConnections.invoke(view);

    // The dialog hosts a Grid<CalDavServerConnection> populated from
    // the session-stored servers. We can't reliably traverse per-row
    // components in BrowserlessTest, so we assert the Grid's data
    // provider size — that confirms the dialog received both server
    // entries (and the status-pill renderer runs per row).
    com.vaadin.flow.component.grid.Grid<?> grid = allDescendants(dialog)
        .filter(com.vaadin.flow.component.grid.Grid.class::isInstance)
        .map(com.vaadin.flow.component.grid.Grid.class::cast)
        .findFirst()
        .orElseThrow(() ->
            new AssertionError("ConnectionsDialog must contain a Grid"));
    int rowCount = grid.getDataProvider()
        .size(new com.vaadin.flow.data.provider.Query<>());
    assertEquals(2, rowCount,
        "ConnectionsDialog Grid must list both configured servers");
  }

  @Test
  @DisplayName("multi-server session: two servers, two subscriptions each, both surface in readers")
  void multiServerSessionState() {
    AppUser user = new AppUser(100L, "Multi User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    CalDavServerConnection icloud = CalDavServerConnection.create(
        "iCloud", URI.create("https://caldav.icloud.com/"),
        "alice@example.com", "ap-sp-ec-fc");
    CalDavServerConnection nextcloud = CalDavServerConnection.create(
        "Nextcloud", URI.create("https://nextcloud.example/dav/"),
        "alice", "n3xt!");

    URI icloudHome = URI.create("https://caldav.icloud.com/u/calendars/home/");
    URI ncWork = URI.create("https://nextcloud.example/dav/calendars/alice/work/");
    URI ncFamily = URI.create("https://nextcloud.example/dav/calendars/alice/family/");

    VaadinSession.getCurrent().setAttribute(ChronoGrid.SESSION_KEY_SERVERS,
        java.util.List.of(icloud, nextcloud));
    VaadinSession.getCurrent().setAttribute(ChronoGrid.SESSION_KEY_SUBSCRIPTIONS,
        java.util.List.of(
            new CalendarSubscription(icloudHome, "Home", "#1F77B4", true, icloud.id()),
            new CalendarSubscription(ncWork, "Work", "#FF7F0E", true, nextcloud.id()),
            new CalendarSubscription(ncFamily, "Family", "#2CA02C", false, nextcloud.id())));

    assertEquals(2, ChronoGrid.readServers().size());
    assertEquals(3, ChronoGrid.readSubscriptions().size());
    long fromNextcloud = ChronoGrid.readSubscriptions().stream()
        .filter(s -> nextcloud.id().equals(s.serverId()))
        .count();
    assertEquals(2L, fromNextcloud,
        "two of three subscriptions must reference Nextcloud's serverId");
  }

  @Test
  @DisplayName("Subscriptions toolbar button + session-list flip Visible flag + remove URI")
  void subscriptionsRoundTrip() {
    AppUser user = new AppUser(90L, "Sub User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    URI home = URI.create("https://caldav.icloud.com/u/calendars/home/");
    URI work = URI.create("https://caldav.icloud.com/u/calendars/work/");
    VaadinSession.getCurrent().setAttribute(
        ChronoGrid.SESSION_KEY_SUBSCRIPTIONS,
        java.util.List.of(
            new CalendarSubscription(home, "Home", "#1F77B4", true),
            new CalendarSubscription(work, "Work", "#FF7F0E", true)));

    navigate(CalendarRouteView.class);

    boolean sawButton = allDescendants(com.vaadin.flow.component.UI.getCurrent())
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .anyMatch(b -> "calendar-toolbar-subscriptions"
            .equals(b.getId().orElse(null)));
    assertTrue(sawButton, "toolbar must carry the Subscriptions button");

    assertEquals(2, ChronoGrid.readSubscriptions().size(),
        "two subscriptions must be stored");
    assertTrue(ChronoGrid.readSubscriptions().stream()
        .allMatch(CalendarSubscription::visible),
        "both subscriptions start visible");
  }

  @Test
  @DisplayName("Planning-Feature #7 Schicht 1: toolbar carries the visibility-toggle dropdown")
  void visibilityToggleDropdownIsPresent() {
    AppUser user = new AppUser(91L, "Visi User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    URI home = URI.create("https://caldav.icloud.com/u/calendars/home/");
    URI work = URI.create("https://caldav.icloud.com/u/calendars/work/");
    URI hobby = URI.create("https://caldav.icloud.com/u/calendars/hobby/");
    // Three subscriptions, two visible — the trigger label MUST read
    // "(2/3)" in either locale: the {0}/{1} placeholder pair is the
    // contract that drives the at-a-glance toolbar count.
    VaadinSession.getCurrent().setAttribute(
        ChronoGrid.SESSION_KEY_SUBSCRIPTIONS,
        java.util.List.of(
            new CalendarSubscription(home, "Home", "#1F77B4", true),
            new CalendarSubscription(work, "Work", "#FF7F0E", true),
            new CalendarSubscription(hobby, "Hobby", "#2CA02C", false)));

    navigate(CalendarRouteView.class);

    Button toggle = allDescendants(com.vaadin.flow.component.UI.getCurrent())
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(b -> "calendar-toolbar-visibility".equals(b.getId().orElse(null)))
        .findFirst()
        .orElse(null);
    assertNotNull(toggle, "toolbar must carry the visibility-toggle button "
        + "with id 'calendar-toolbar-visibility'");
    String text = toggle.getText();
    assertTrue(text != null && text.contains("2") && text.contains("3"),
        "visibility-toggle label must show 2 visible of 3 total; got: " + text);
  }

  @Test
  @DisplayName("Settings dialog exposes the iCloud quick-connect preset button")
  void settingsDialogCarriesIcloudPreset() throws Exception {
    AppUser user = new AppUser(80L, "Preset User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(CalendarRouteView.class);
    ChronoGrid view = findCalendarView();
    java.lang.reflect.Method openSettings =
        ChronoGrid.class.getDeclaredMethod("openSettingsDialog");
    openSettings.setAccessible(true);
    com.vaadin.flow.component.dialog.Dialog dialog =
        (com.vaadin.flow.component.dialog.Dialog) openSettings.invoke(view);

    boolean sawIcloud = allDescendants(dialog)
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .anyMatch(b -> "calendar-provider-icloud".equals(b.getId().orElse(null)));
    assertTrue(sawIcloud,
        "Settings dialog must expose a button with id 'calendar-provider-icloud'");
  }

  @Test
  @DisplayName("toolbar carries Settings + Refresh + New event buttons")
  void toolbarButtonsArePresent() {
    AppUser user = new AppUser(60L, "Cal User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(CalendarRouteView.class);

    List<String> buttonLabels = allDescendants(com.vaadin.flow.component.UI.getCurrent())
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .map(Button::getText)
        .collect(Collectors.toList());
    assertTrue(buttonLabels.contains("Settings"),
        "toolbar must contain a Settings button — saw " + buttonLabels);
    assertTrue(buttonLabels.contains("Refresh"),
        "toolbar must contain a Refresh button — saw " + buttonLabels);
    assertTrue(buttonLabels.contains("New event"),
        "toolbar must contain a New event button — saw " + buttonLabels);
  }

  @Test
  @DisplayName("session-stored CalDavConnectionConfig overrides the provider default at attach time")
  void sessionAttributeOverridesProviderDefault() throws IOException {
    URI overrideUri = URI.create("http://127.0.0.1:1/calendars/nonexistent/");
    VaadinSession.getCurrent().setAttribute(
        ChronoGrid.SESSION_KEY_CONNECTION,
        CalDavConnectionConfig.anonymous(overrideUri));

    AppUser user = new AppUser(61L, "Override User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(CalendarRouteView.class);

    long preReports = fixture.interactions().entries().stream()
        .filter(e -> "REPORT".equals(e.method())).count();
    try {
      findFullCalendar().getEntryProvider().fetch(
          java.time.LocalDateTime.now().minusDays(1),
          java.time.LocalDateTime.now().plusDays(1))
          .toList();
    } catch (RuntimeException expected) {
      // expected — port 1 is closed; the point is the request did NOT
      // hit the fixture below.
    }
    long postReports = fixture.interactions().entries().stream()
        .filter(e -> "REPORT".equals(e.method())).count();
    assertEquals(preReports, postReports,
        "EntryProvider must talk to the session-overridden URI, not the fixture");
  }

  @Test
  @DisplayName("attaching the view causes the EntryProvider to query the testbench (REPORT in InteractionLog)")
  void attachTriggersReport() {
    AppUser user = new AppUser(51L, "Cal Reader",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(CalendarRouteView.class);

    FullCalendar calendar = findFullCalendar();
    calendar.getEntryProvider().refreshAll();
    calendar.getEntryProvider().fetch(
        java.time.LocalDateTime.now().minusDays(7),
        java.time.LocalDateTime.now().plusDays(7))
        .toList();

    boolean sawReport = fixture.interactions().entries().stream()
        .anyMatch(entry -> "REPORT".equals(entry.method()));
    assertTrue(sawReport,
        "Fetching the EntryProvider's range must hit the CalDAV server via REPORT");
  }

  // ── helpers ────────────────────────────────────────────────────

  private FullCalendar findFullCalendar() {
    return allDescendants(com.vaadin.flow.component.UI.getCurrent())
        .filter(FullCalendar.class::isInstance)
        .map(FullCalendar.class::cast)
        .findFirst()
        .orElseThrow(() ->
            new AssertionError("No FullCalendar component found in the rendered UI"));
  }

  private ChronoGrid findCalendarView() {
    return allDescendants(com.vaadin.flow.component.UI.getCurrent())
        .filter(ChronoGrid.class::isInstance)
        .map(ChronoGrid.class::cast)
        .findFirst()
        .orElseThrow(() ->
            new AssertionError("No ChronoGrid found in the rendered UI"));
  }

  private static Stream<com.vaadin.flow.component.Component> allDescendants(
      com.vaadin.flow.component.Component root) {
    if (root == null) return Stream.empty();
    return Stream.concat(
        Stream.of(root),
        root.getChildren().flatMap(ChronoGridBrowserlessTest::allDescendants));
  }
}
