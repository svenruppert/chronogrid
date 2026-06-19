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

package com.svenruppert.flow.views;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.i18n.I18nSupport;
import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.views.main.PushDemoView;
import com.svenruppert.flow.views.ui.BrandMark;
import com.svenruppert.flow.views.ui.LocaleSwitcher;
import com.svenruppert.flow.views.ui.ThemeSwitcher;
import com.svenruppert.jsentinel.authorization.api.AuthorizationService;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.svenruppert.jsentinel.logout.LogoutScope;
import com.svenruppert.jsentinel.logout.LogoutService;
import com.svenruppert.jsentinel.logout.SubjectId;
import com.svenruppert.jsentinel.logout.vaadin.DefaultVaadinLogoutGateway;
import com.svenruppert.jsentinel.logout.vaadin.VaadinLogoutService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Application shell — pure layout, no own {@code @Route}.
 *
 * <p>The drawer is split into up to three {@link SideNav} sections.
 * Each section is built fresh on every navigation
 * ({@link #beforeEnter(BeforeEnterEvent)}) and is only attached when
 * at least one of its links is visible for the current subject — so
 * an anonymous visitor sees only the <b>Public</b> section, a
 * regular user gets <b>Public</b> + <b>Application</b>, an admin
 * gets all three including <b>Administration</b>. There are no
 * empty section headers.
 *
 * <p>The navbar carries a single auth-action button — "Sign in" for
 * anonymous visitors, "Sign out" for logged-in users — rebuilt via
 * the same {@code beforeEnter} pass through a {@link Div} slot.
 */
public class MainLayout extends AppLayout
    implements HasLogger, BeforeEnterObserver, I18nSupport {

  // i18n keys
  private static final String K_NAV_SECTION_PUBLIC = "nav.section.public";
  private static final String K_NAV_SECTION_APPLICATION = "nav.section.application";
  private static final String K_NAV_SECTION_ADMINISTRATION = "nav.section.administration";
  private static final String K_NAV_HOME = "nav.home";
  private static final String K_NAV_SECURITY = "nav.security.features";
  private static final String K_NAV_DASHBOARD = "nav.dashboard";
  private static final String K_NAV_ABOUT = "nav.about";
  private static final String K_NAV_YOUTUBE = "nav.youtube";
  private static final String K_NAV_PUSHDEMO = "nav.pushDemo";
  private static final String K_NAV_CALENDAR = "nav.calendar";
  private static final String K_NAV_AUDIT = "nav.audit";
  private static final String K_NAV_SESSIONS = "nav.sessions";
  private static final String K_NAV_ROLES = "nav.roles";
  private static final String K_SIGN_IN = "common.signIn";
  private static final String K_SIGN_OUT = "common.signOut";

  private static final LogoutService LOGOUT_SERVICE =
      new VaadinLogoutService<>(
          SubjectStores.subjectStore(), AppUser.class,
          new DefaultVaadinLogoutGateway(),
          "/" + AppLoginView.NAV,
          /* closeVaadinSession= */ true,
          /* invalidateHttpSession= */ true);

  private final Div authActionSlot = new Div();
  private final Div drawerSlot = new Div();

  public MainLayout() {
    BrandMark brand = new BrandMark();

    authActionSlot.add(buildAuthActionButton());
    drawerSlot.add(buildDrawer());

    DrawerToggle toggle = new DrawerToggle();
    toggle.setAriaLabel("Toggle navigation");
    addToNavbar(toggle, brand);

    Div navbarTail = new Div(
        new ThemeSwitcher(),
        new LocaleSwitcher(),
        authActionSlot);
    navbarTail.getStyle().set("display", "flex");
    navbarTail.getStyle().set("align-items", "center");
    navbarTail.getStyle().set("gap", "var(--lumo-space-s)");
    navbarTail.getStyle().set("padding-right", "var(--lumo-space-m)");
    // Push the tail group to the very right of the navbar.
    navbarTail.getStyle().set("margin-left", "auto");
    addToNavbar(true, navbarTail);

    addToDrawer(drawerSlot);

    // Subtle visual polish for the navbar.
    getElement().getStyle().set("--vaadin-app-layout-navbar-border-bottom",
        "1px solid var(--lumo-contrast-10pct)");
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    authActionSlot.removeAll();
    authActionSlot.add(buildAuthActionButton());
    drawerSlot.removeAll();
    drawerSlot.add(buildDrawer());
  }

  // ── Drawer ─────────────────────────────────────────────────────

  private Component buildDrawer() {
    Set<String> grants = currentGrants();
    Div container = new Div();
    container.getStyle().set("padding", "var(--lumo-space-s)");
    container.getStyle().set("display", "flex");
    container.getStyle().set("flex-direction", "column");
    container.getStyle().set("gap", "var(--lumo-space-m)");

    // Public — always.
    container.add(section(tr(K_NAV_SECTION_PUBLIC, "Public"),
        item(tr(K_NAV_HOME, "Home"), VaadinIcon.HOME,
            PublicHomeView.class, null, grants),
        item(tr(K_NAV_ABOUT, "About"), VaadinIcon.INFO_CIRCLE,
            AboutView.class, null, grants),
        item(tr(K_NAV_YOUTUBE, "Youtube"), VaadinIcon.PLAY_CIRCLE,
            YoutubeView.class, null, grants),
        item(tr(K_NAV_SECURITY, "Security features"), VaadinIcon.SHIELD,
            SecurityFeaturesView.class, null, grants)));

    // Application — any subject with app:view.
    SideNav app = section(tr(K_NAV_SECTION_APPLICATION, "Application"),
        item(tr(K_NAV_DASHBOARD, "Dashboard"), VaadinIcon.DASHBOARD,
            DashboardView.class, "app:view", grants),
        item(tr(K_NAV_CALENDAR, "Calendar"), VaadinIcon.CALENDAR,
            CalendarRouteView.class, "calendar:read", grants),
        item(tr(K_NAV_PUSHDEMO, "Push demo"), VaadinIcon.BELL,
            PushDemoView.class, "app:view", grants));
    if (app != null) container.add(app);

    // Administration — only when at least one admin permission is held.
    SideNav admin = section(tr(K_NAV_SECTION_ADMINISTRATION, "Administration"),
        item(tr(K_NAV_AUDIT, "Audit log"), VaadinIcon.RECORDS,
            AuditView.class, "audit:read", grants),
        item(tr(K_NAV_SESSIONS, "Active sessions"), VaadinIcon.USERS,
            SessionsView.class, "admin:sessions", grants),
        item(tr(K_NAV_ROLES, "Role administration"), VaadinIcon.SHIELD,
            AdminRolesView.class, "admin:roles", grants));
    if (admin != null) container.add(admin);

    return container;
  }

  /**
   * Builds a {@link SideNav} section with the supplied items.
   * Items that resolved to {@code null} (permission denied) are
   * filtered out. Returns {@code null} when no item survives — caller
   * skips attaching the section header.
   */
  private static SideNav section(String label, SideNavItem... items) {
    SideNav nav = new SideNav();
    nav.setLabel(label);
    int attached = 0;
    for (SideNavItem item : items) {
      if (item != null) {
        nav.addItem(item);
        attached++;
      }
    }
    return attached == 0 ? null : nav;
  }

  /**
   * Builds a {@link SideNavItem} when the current subject holds the
   * required permission (or when no permission is required). Returns
   * {@code null} otherwise — {@link #section(String, SideNavItem...)}
   * drops nulls.
   */
  private static SideNavItem item(String label, VaadinIcon icon,
                                  Class<? extends Component> target,
                                  String requiredPermission,
                                  Set<String> grants) {
    if (requiredPermission != null && !grants.contains(requiredPermission)) {
      return null;
    }
    SideNavItem nav = new SideNavItem(label, target, icon.create());
    return nav;
  }

  /**
   * Pulls the permission names the current subject holds — empty set
   * when nobody is signed in. Permission checks in the drawer
   * compare against this set instead of issuing one
   * {@code authorizationService.permissionsFor(...)} call per item.
   */
  private static Set<String> currentGrants() {
    return SubjectStores.subjectStore().currentSubject(AppUser.class)
        .map(MainLayout::permissionsOf)
        .orElseGet(Set::of);
  }

  private static Set<String> permissionsOf(AppUser subject) {
    AuthorizationService<AppUser> authz = JSentinelServiceResolver.authorizationService();
    Set<String> names = new LinkedHashSet<>();
    authz.permissionsFor(subject).permissionNames()
        .forEach(name -> names.add(name.value()));
    return names;
  }

  // ── Navbar auth action ─────────────────────────────────────────

  private static Button buildAuthActionButton() {
    boolean loggedIn = SubjectStores.subjectStore()
        .currentSubject(AppUser.class).isPresent();
    if (loggedIn) {
      Button signOut = new Button(
          com.svenruppert.flow.i18n.I18n.tr(K_SIGN_OUT, "Sign out"),
          VaadinIcon.SIGN_OUT.create(), e -> logout());
      signOut.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
      return signOut;
    }
    Button signIn = new Button(
        com.svenruppert.flow.i18n.I18n.tr(K_SIGN_IN, "Sign in"),
        VaadinIcon.SIGN_IN.create(),
        e -> UI.getCurrent().navigate(AppLoginView.class));
    signIn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
    return signIn;
  }

  private static void logout() {
    SubjectId subjectId = SubjectStores.subjectStore().currentSubject(AppUser.class)
        .map(u -> SubjectId.of(String.valueOf(u.id())))
        .orElse(SubjectId.of("anonymous"));
    LOGOUT_SERVICE.logout(subjectId, LogoutScope.CurrentSession);
  }
}
