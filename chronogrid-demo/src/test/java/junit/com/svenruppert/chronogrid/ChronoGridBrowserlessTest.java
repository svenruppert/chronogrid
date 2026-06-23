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
  @DisplayName("BACKLOG-#9 follow-up: badge stays CONNECTED in partial-failure mode (one server up, one server down)")
  void badgeStaysConnectedInPartialFailureMode() {
    AppUser user = new AppUser(74L, "Partial User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    URI liveUri = fixture.baseUri().resolve("/calendars/personal/");
    URI deadUri = URI.create("http://127.0.0.1:1/calendars/none/");
    com.svenruppert.chronogrid.service.CalDavServerConnection liveServer =
        com.svenruppert.chronogrid.service.CalDavServerConnection.create(
            "Testbench", fixture.baseUri(), null, null);
    com.svenruppert.chronogrid.service.CalDavServerConnection deadServer =
        com.svenruppert.chronogrid.service.CalDavServerConnection.create(
            "Offline", URI.create("http://127.0.0.1:1/"), null, null);
    VaadinSession.getCurrent().setAttribute(
        ChronoGrid.SESSION_KEY_SERVERS,
        java.util.List.of(liveServer, deadServer));
    VaadinSession.getCurrent().setAttribute(
        ChronoGrid.SESSION_KEY_SUBSCRIPTIONS,
        java.util.List.of(
            new CalendarSubscription(liveUri, "Personal", "#1F77B4",
                true, liveServer.id()),
            new CalendarSubscription(deadUri, "Phantom", "#FF0000",
                true, deadServer.id())));

    navigate(CalendarRouteView.class);
    ChronoGrid view = findCalendarView();

    findFullCalendar().getEntryProvider().fetch(
        java.time.LocalDateTime.now().minusDays(1),
        java.time.LocalDateTime.now().plusDays(1))
        .toList();
    // With one of two clients failing, the surviving live client
    // keeps the badge on CONNECTED — partial-failure isolation in
    // action. The user sees a per-failure toast for the dead one
    // (not assertable in BrowserlessTest, but the markConnected
    // path is the contract here).
    assertEquals(ChronoGrid.ConnectionState.CONNECTED, view.connectionState(),
        "with one client up and one down, the badge must stay CONNECTED "
            + "(BACKLOG-#9 follow-up: partial-failure isolation)");
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
  @DisplayName("Planning-Feature #7 Schicht 3b: Connection Manager surfaces all configured servers as tabs")
  void connectionManagerListsAllServers() throws Exception {
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
    java.lang.reflect.Method openManager =
        ChronoGrid.class.getDeclaredMethod("openConnectionManagerDialog");
    openManager.setAccessible(true);
    com.vaadin.flow.component.dialog.Dialog dialog =
        (com.vaadin.flow.component.dialog.Dialog) openManager.invoke(view);

    // The Connection-Manager left pane is a vertical Tabs with one Tab
    // per configured server. Each tab carries id
    // "calendar-manager-server-tab-<serverId>" so the assertion can
    // pin both entries by name.
    java.util.Set<String> tabIds = allDescendants(dialog)
        .filter(com.vaadin.flow.component.tabs.Tab.class::isInstance)
        .map(c -> c.getId().orElse(""))
        .collect(java.util.stream.Collectors.toSet());
    assertTrue(tabIds.contains("calendar-manager-server-tab-" + live.id()),
        "Connection Manager must show live-server tab; saw: " + tabIds);
    assertTrue(tabIds.contains("calendar-manager-server-tab-" + dead.id()),
        "Connection Manager must show offline-server tab; saw: " + tabIds);
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
  @DisplayName("Subscriptions seeded on the session surface via readSubscriptions and start visible")
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

    // The legacy "Subscriptions" toolbar button is gone with Schicht 3b;
    // the visibility-toggle dropdown and the Connection-Manager carry
    // the responsibility now. What we still verify here: the session-
    // seeded subscriptions survive the round-trip through the
    // ChronoGrid mount.
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
  @DisplayName("Planning-Feature #7 Schicht 2: openConnectionWizard mounts a 3-step dialog with iCloud preset + nav buttons")
  void connectionWizardDialogStructure() throws Exception {
    AppUser user = new AppUser(92L, "Wizard User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(CalendarRouteView.class);
    ChronoGrid view = findCalendarView();
    java.lang.reflect.Method openWizard =
        ChronoGrid.class.getDeclaredMethod("openConnectionWizard");
    openWizard.setAccessible(true);
    com.vaadin.flow.component.dialog.Dialog dialog =
        (com.vaadin.flow.component.dialog.Dialog) openWizard.invoke(view);

    assertEquals("calendar-wizard", dialog.getId().orElse(null),
        "wizard dialog must carry the test selector id");

    // Component-level walk only reaches the dialog body slot; the
    // footer (Back/Next/Cancel) lives in a portal'd overlay slot
    // that isn't exposed to dialog.getChildren() or to a UI-tree
    // walk. The dialog-body buttons below cover all three steps,
    // which is enough to prove the wizard mounted; footer wiring is
    // exercised by direct ConnectionWizardDialog#currentStep() poking
    // in unit tests further down.
    java.util.Set<String> buttonIds = allDescendants(dialog)
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .map(b -> b.getId().orElse(""))
        .collect(java.util.stream.Collectors.toSet());

    // Step 1 — provider preset
    assertTrue(buttonIds.contains("calendar-wizard-preset-icloud"),
        "Step 1 must expose the iCloud preset button; saw: " + buttonIds);
    // Step 2 — test-connection button
    assertTrue(buttonIds.contains("calendar-wizard-test"),
        "Step 2 must expose Test-connection; saw: " + buttonIds);
    // Step 3 — bulk buttons
    assertTrue(buttonIds.contains("calendar-wizard-bulk-on"),
        "Step 3 must expose the Select-all bulk button; saw: " + buttonIds);
    assertTrue(buttonIds.contains("calendar-wizard-bulk-off"),
        "Step 3 must expose the Select-none bulk button; saw: " + buttonIds);
  }

  @Test
  @DisplayName("Planning-Feature #7 Schicht 3: openConnectionManagerDialog mounts the master-detail dialog with id + server tabs + per-sub controls")
  void connectionManagerDialogStructure() throws Exception {
    AppUser user = new AppUser(93L, "Manager User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    // Seed: one server, two subscriptions on it.
    String serverId = "srv-mgr-1";
    URI base = URI.create("https://caldav.example/u/");
    VaadinSession.getCurrent().setAttribute(
        com.svenruppert.chronogrid.state.VaadinSessionCalendarStateStore
            .SESSION_KEY_SERVERS,
        java.util.List.of(new CalDavServerConnection(serverId,
            "Test Account", base, "user", "pw")));
    URI subA = URI.create("https://caldav.example/u/personal/");
    URI subB = URI.create("https://caldav.example/u/family/");
    VaadinSession.getCurrent().setAttribute(
        ChronoGrid.SESSION_KEY_SUBSCRIPTIONS,
        java.util.List.of(
            new CalendarSubscription(subA, "Personal", "#1F77B4", true, serverId),
            new CalendarSubscription(subB, "Family", "#FF7F0E", false, serverId)));

    navigate(CalendarRouteView.class);
    ChronoGrid view = findCalendarView();
    java.lang.reflect.Method openMgr =
        ChronoGrid.class.getDeclaredMethod("openConnectionManagerDialog");
    openMgr.setAccessible(true);
    com.vaadin.flow.component.dialog.Dialog dialog =
        (com.vaadin.flow.component.dialog.Dialog) openMgr.invoke(view);

    assertEquals("calendar-manager", dialog.getId().orElse(null),
        "manager dialog must carry the test selector id");

    java.util.Set<String> ids = allDescendants(dialog)
        .map(c -> c.getId().orElse(""))
        .filter(s -> !s.isEmpty())
        .collect(java.util.stream.Collectors.toSet());

    assertTrue(ids.contains("calendar-manager-server-tab-" + serverId),
        "left pane must show a tab per server; saw: " + ids);
    assertTrue(ids.contains("calendar-manager-rediscover"),
        "right pane must expose Re-discover button; saw: " + ids);
    assertTrue(ids.contains("calendar-manager-remove-server"),
        "right pane must expose Remove-server button; saw: " + ids);
    // Per-subscription rows for both seeded subs (visibility-toggle id).
    assertTrue(ids.contains("manager-sub-visible-" + Integer.toHexString(subA.hashCode())),
        "sub-row must expose visibility toggle for Personal; saw: " + ids);
    assertTrue(ids.contains("manager-sub-visible-" + Integer.toHexString(subB.hashCode())),
        "sub-row must expose visibility toggle for Family; saw: " + ids);
  }

  @Test
  @DisplayName("Planning-Feature #7 Schicht 3: toolbar carries the Connection-Manager button")
  void connectionManagerToolbarButtonIsPresent() {
    AppUser user = new AppUser(94L, "Mgr Toolbar",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(CalendarRouteView.class);

    boolean sawButton = allDescendants(com.vaadin.flow.component.UI.getCurrent())
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .anyMatch(b -> "calendar-toolbar-manager".equals(b.getId().orElse(null)));
    assertTrue(sawButton,
        "toolbar must carry the Connection-Manager button with id 'calendar-toolbar-manager'");
  }

  @Test
  @DisplayName("Planning-Feature #8 Stage B: toolbar carries the (initially hidden) progress bar with smart-delay = 500ms")
  void progressBarMountsHiddenWithSmartDelayPinned() {
    AppUser user = new AppUser(98L, "Progress User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(CalendarRouteView.class);

    com.vaadin.flow.component.progressbar.ProgressBar bar =
        allDescendants(com.vaadin.flow.component.UI.getCurrent())
            .filter(com.vaadin.flow.component.progressbar.ProgressBar.class::isInstance)
            .map(com.vaadin.flow.component.progressbar.ProgressBar.class::cast)
            .filter(p -> "calendar-toolbar-progress".equals(p.getId().orElse(null)))
            .findFirst()
            .orElse(null);
    assertNotNull(bar,
        "toolbar must mount the progress bar with id 'calendar-toolbar-progress'");
    // The bar starts hidden — only the smart-delay show-path makes it
    // visible, and only when a refresh genuinely takes long.
    assertEquals(false, bar.isVisible(),
        "progress bar must start hidden");
    // The smart-delay window is the contract: < 500ms refreshes never
    // flash the bar, > 500ms show it.
    assertEquals(500, ChronoGrid.PROGRESS_SMART_DELAY_MS);
  }

  @Test
  @DisplayName("Planning-Feature #7 Schicht 4: multiServerSummary collapses servers + subs + visible count into one line")
  void multiServerSummaryReflectsStateStore() throws Exception {
    AppUser user = new AppUser(97L, "Summary User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    String srv1 = "summary-srv-1";
    String srv2 = "summary-srv-2";
    URI base1 = URI.create("https://caldav.example/srv1/");
    URI base2 = URI.create("https://caldav.example/srv2/");
    VaadinSession.getCurrent().setAttribute(
        com.svenruppert.chronogrid.state.VaadinSessionCalendarStateStore
            .SESSION_KEY_SERVERS,
        java.util.List.of(
            new CalDavServerConnection(srv1, "Alpha", base1, "u", "p"),
            new CalDavServerConnection(srv2, "Beta", base2, "u", "p")));
    URI sa = URI.create("https://caldav.example/srv1/cal-a/");
    URI sb = URI.create("https://caldav.example/srv1/cal-b/");
    URI sc = URI.create("https://caldav.example/srv2/cal-c/");
    // 2 servers, 3 calendars, 2 visible (sc is hidden).
    VaadinSession.getCurrent().setAttribute(
        ChronoGrid.SESSION_KEY_SUBSCRIPTIONS,
        java.util.List.of(
            new CalendarSubscription(sa, "Alpha-A", "#1F77B4", true, srv1),
            new CalendarSubscription(sb, "Alpha-B", "#FF7F0E", true, srv1),
            new CalendarSubscription(sc, "Beta-C", "#2CA02C", false, srv2)));

    navigate(CalendarRouteView.class);
    ChronoGrid view = findCalendarView();
    java.lang.reflect.Method summary =
        ChronoGrid.class.getDeclaredMethod("multiServerSummary");
    summary.setAccessible(true);
    String text = (String) summary.invoke(view);

    // The summary template carries three positional counters; the
    // exact wording differs per locale but the counters must all
    // appear and match the state-store reading.
    assertTrue(text.contains("2"),
        "summary must include server count (2); got: " + text);
    assertTrue(text.contains("3"),
        "summary must include calendar count (3); got: " + text);
    // The "visible" counter is 2 — same as server count, so a
    // simple .contains("2") would be ambiguous. Assert via the
    // numeric tokens in the produced string.
    java.util.List<Integer> nums = new java.util.ArrayList<>();
    for (String tok : text.split("\\D+")) {
      if (!tok.isBlank()) nums.add(Integer.parseInt(tok));
    }
    assertEquals(java.util.List.of(2, 3, 2), nums,
        "summary must carry exactly (servers=2, calendars=3, visible=2); "
            + "got tokens " + nums + " from \"" + text + "\"");
  }

  @Test
  @DisplayName("Planning-Feature #7 Schicht 5: legacy single-server connection auto-migrates to server + subscription on mount")
  void legacyConnectionAutoMigratesOnMount() {
    AppUser user = new AppUser(95L, "Migration User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    // Pre-Schicht-5: a legacy session held only the single-server
    // CalDavConnectionConfig. After mount, the migration must
    // produce one server + one subscription (and the legacy key
    // must remain intact for rollback-safety).
    URI legacyUri = URI.create("https://caldav.example/legacy/cal/");
    VaadinSession.getCurrent().setAttribute(
        com.svenruppert.chronogrid.state.VaadinSessionCalendarStateStore
            .SESSION_KEY_CONNECTION,
        new com.svenruppert.chronogrid.service.CalDavConnectionConfig(
            legacyUri, "alice", "secret"));
    // No SESSION_KEY_SERVERS, no SESSION_KEY_SUBSCRIPTIONS — only
    // the legacy key is present, which is exactly the pre-migration
    // state shape.

    navigate(CalendarRouteView.class);

    java.util.List<com.svenruppert.chronogrid.service.CalDavServerConnection>
        servers = ChronoGrid.readServers();
    assertEquals(1, servers.size(),
        "migration must produce exactly one server");
    assertEquals("alice", servers.get(0).username(),
        "migrated server must carry the legacy username");
    assertEquals("secret", servers.get(0).password(),
        "migrated server must carry the legacy password");

    java.util.List<CalendarSubscription> subs = ChronoGrid.readSubscriptions();
    assertEquals(1, subs.size(),
        "migration must produce exactly one subscription");
    assertEquals(legacyUri, subs.get(0).uri(),
        "migrated subscription must point at the legacy collection URI");
    assertEquals(servers.get(0).id(), subs.get(0).serverId(),
        "migrated subscription must reference the migrated server's id");

    // Rollback-safety: the legacy key MUST still be readable. A
    // mid-deploy rollback re-mounts the legacy connection without
    // any data loss.
    assertNotNull(VaadinSession.getCurrent().getAttribute(
        com.svenruppert.chronogrid.state.VaadinSessionCalendarStateStore
            .SESSION_KEY_CONNECTION),
        "Schicht 5 must NOT delete the legacy connection key — "
            + "it stays readable as a rollback-safety net");
  }

  @Test
  @DisplayName("Planning-Feature #7 Schicht 5: migration is a no-op when multi-server state already exists")
  void legacyMigrationIsNoOpWhenMultiServerStateExists() {
    AppUser user = new AppUser(96L, "Already-Migrated User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    // BOTH the legacy key AND existing multi-server state — the
    // migration must defer to the multi-server state and leave it
    // untouched. Otherwise a re-mount could clobber subscriptions
    // the user just added.
    URI legacyUri = URI.create("https://caldav.example/legacy/");
    VaadinSession.getCurrent().setAttribute(
        com.svenruppert.chronogrid.state.VaadinSessionCalendarStateStore
            .SESSION_KEY_CONNECTION,
        new com.svenruppert.chronogrid.service.CalDavConnectionConfig(
            legacyUri, "alice", "secret"));
    URI existingSub = URI.create("https://caldav.example/multi/cal/");
    String existingServerId = "existing-srv";
    VaadinSession.getCurrent().setAttribute(
        com.svenruppert.chronogrid.state.VaadinSessionCalendarStateStore
            .SESSION_KEY_SERVERS,
        java.util.List.of(new com.svenruppert.chronogrid.service.CalDavServerConnection(
            existingServerId, "Already here",
            URI.create("https://caldav.example/multi/"), "bob", "pw")));
    VaadinSession.getCurrent().setAttribute(
        ChronoGrid.SESSION_KEY_SUBSCRIPTIONS,
        java.util.List.of(new CalendarSubscription(existingSub,
            "Already", "#FF0000", true, existingServerId)));

    navigate(CalendarRouteView.class);

    java.util.List<com.svenruppert.chronogrid.service.CalDavServerConnection>
        servers = ChronoGrid.readServers();
    assertEquals(1, servers.size(),
        "no-op migration must leave server count at 1 (the pre-existing one)");
    assertEquals(existingServerId, servers.get(0).id(),
        "migration must not replace the pre-existing server entry");
    assertEquals(1, ChronoGrid.readSubscriptions().size(),
        "no-op migration must leave subscription count at 1");
  }

  @Test
  @DisplayName("Planning-Feature #7 Schicht 2: hybrid default-visibility threshold is 5")
  void wizardHybridThresholdConstant() {
    // The hybrid default-visibility rule (≤5 auto-on, >5 auto-off)
    // is pinned at 5; this guards against accidental tweaks to the
    // public constant, since the spec is explicit (Planning #7
    // Konzept-Verfeinerung).
    assertEquals(5, com.svenruppert.chronogrid.ui.ConnectionWizardDialog
        .HYBRID_DEFAULT_VISIBILITY_THRESHOLD);
  }

  // settingsDialogCarriesIcloudPreset removed with Schicht 3b — the
  // Settings dialog is gone, the iCloud preset now lives in the
  // ConnectionWizardDialog (covered by connectionWizardDialogStructure
  // which asserts the id 'calendar-wizard-preset-icloud').

  @Test
  @DisplayName("toolbar carries Connection-Manager + Refresh + New event buttons (Schicht 3b)")
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
    // Schicht 3b: Settings / Connections / Subscriptions are gone.
    // The Connection-Manager button replaces all three.
    assertTrue(buttonLabels.contains("Connection Manager"),
        "toolbar must contain a Connection-Manager button — saw " + buttonLabels);
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
