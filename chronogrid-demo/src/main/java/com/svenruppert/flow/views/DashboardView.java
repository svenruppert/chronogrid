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

import com.svenruppert.flow.i18n.I18nSupport;
import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.model.UserDirectoryProvider;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.flow.security.roles.VisibleFor;
import com.svenruppert.flow.security.services.SessionStoreProvider;
import com.svenruppert.flow.views.ui.EmptyState;
import com.svenruppert.flow.views.ui.MetricTile;
import com.svenruppert.flow.views.ui.PageHeader;
import com.svenruppert.flow.views.ui.TemplateBrand;
import com.svenruppert.jsentinel.audit.AuditEvent;
import com.svenruppert.jsentinel.audit.AuditQuery;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.svenruppert.jsentinel.session.SessionStatus;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

import static com.svenruppert.flow.security.roles.AuthorizationRole.ADMIN;
import static com.svenruppert.flow.security.roles.AuthorizationRole.USER;

/**
 * Post-login landing page. Page header + metric-tile row + recent
 * activity. Restricted by {@code @VisibleFor(USER)}.
 */
@Route(value = DashboardView.NAV, layout = MainLayout.class)
@VisibleFor(USER)
public class DashboardView extends Composite<VerticalLayout>
    implements I18nSupport {

  public static final String NAV = "dashboard";

  // i18n keys
  private static final String K_WELCOME = "dashboard.heading.welcome";
  private static final String K_SUBTITLE = "dashboard.subtitle";
  private static final String K_TILE_USERS_LABEL = "dashboard.tile.users.label";
  private static final String K_TILE_USERS_HINT_SINGULAR = "dashboard.tile.users.hint.singular";
  private static final String K_TILE_USERS_HINT_PLURAL = "dashboard.tile.users.hint.plural";
  private static final String K_TILE_SESS_LABEL = "dashboard.tile.sessions.label";
  private static final String K_TILE_SESS_HINT_EMPTY = "dashboard.tile.sessions.hint.empty";
  private static final String K_TILE_SESS_HINT_HEALTHY = "dashboard.tile.sessions.hint.healthy";
  private static final String K_TILE_AUDIT_LABEL = "dashboard.tile.audit.label";
  private static final String K_TILE_AUDIT_HINT = "dashboard.tile.audit.hint";
  private static final String K_TILE_DRIFT_LABEL = "dashboard.tile.drift.label";
  private static final String K_TILE_DRIFT_VALUE = "dashboard.tile.drift.value";
  private static final String K_TILE_DRIFT_HINT = "dashboard.tile.drift.hint";
  private static final String K_ACT_HEADING = "dashboard.activity.heading";
  private static final String K_ACT_EMPTY_TITLE = "dashboard.activity.empty.title";
  private static final String K_ACT_EMPTY_BODY = "dashboard.activity.empty.body";

  public DashboardView() {
    Optional<AppUser> result = SubjectStores.subjectStore()
        .currentSubject(AppUser.class);
    String displayName = result.map(AppUser::name).orElse("Guest");
    Set<AuthorizationRole> roles = result.map(AppUser::roles).orElse(Set.of());

    VerticalLayout root = getContent();
    root.setSpacing(false);
    root.setPadding(false);
    root.getStyle().set("gap", "var(--lumo-space-l)");
    root.setAlignItems(FlexComponent.Alignment.STRETCH);

    root.add(buildHeader(displayName, roles));
    root.add(buildMetricsRow());
    root.add(buildRecentActivityCard());
  }

  // ── Header ─────────────────────────────────────────────────────

  private PageHeader buildHeader(String displayName, Set<AuthorizationRole> roles) {
    PageHeader header = new PageHeader(
        tr(K_WELCOME, "Welcome, {0}", displayName),
        tr(K_SUBTITLE,
            "Drawer entries appear based on the permissions your roles grant. "
                + "Sign in as the regular user to see the admin tiles disappear."));
    if (!roles.isEmpty()) {
      HorizontalLayout badges = new HorizontalLayout();
      badges.setSpacing(true);
      badges.setAlignItems(FlexComponent.Alignment.CENTER);
      for (AuthorizationRole role : roles) {
        Span badge = new Span(role.name());
        badge.getElement().getThemeList().add(
            "badge " + (role == ADMIN ? "error" : "success"));
        badges.add(badge);
      }
      header.withActions(badges);
    }
    return header;
  }

  // ── Metrics row ────────────────────────────────────────────────

  private FlexLayout buildMetricsRow() {
    long users = safeCount(() ->
        UserDirectoryProvider.directory().all().count());
    long activeSessions = safeCount(() ->
        SessionStoreProvider.sessionStore().findAll().stream()
            .filter(s -> s.status() == SessionStatus.ACTIVE)
            .count());
    long recentAudit = safeCount(() ->
        JSentinelServiceResolver.securityAuditService()
            .query(new AuditQuery(Set.of(), null, null, null, 0)).size());

    FlexLayout row = new FlexLayout(
        new MetricTile(VaadinIcon.USERS,
            tr(K_TILE_USERS_LABEL, "Registered users"),
            String.valueOf(users),
            users == 1
                ? tr(K_TILE_USERS_HINT_SINGULAR, "Only the bootstrap admin so far")
                : tr(K_TILE_USERS_HINT_PLURAL, "+0 in the last 24h")),
        new MetricTile(VaadinIcon.CONNECT,
            tr(K_TILE_SESS_LABEL, "Active sessions"),
            String.valueOf(activeSessions),
            activeSessions == 0
                ? tr(K_TILE_SESS_HINT_EMPTY, "No-one currently signed in")
                : tr(K_TILE_SESS_HINT_HEALTHY, "All look healthy")),
        new MetricTile(VaadinIcon.RECORDS,
            tr(K_TILE_AUDIT_LABEL, "Audit events"),
            String.valueOf(recentAudit),
            tr(K_TILE_AUDIT_HINT, "Since service start")),
        new MetricTile(VaadinIcon.SHIELD,
            tr(K_TILE_DRIFT_LABEL, "Drift detection"),
            tr(K_TILE_DRIFT_VALUE, "Armed"),
            tr(K_TILE_DRIFT_HINT, "Phase-4c JSentinelVersion store wired")));
    row.setFlexWrap(FlexLayout.FlexWrap.WRAP);
    row.getStyle().set("gap", "var(--lumo-space-l)");
    row.getChildren().forEach(c ->
        c.getElement().getStyle().set("flex", "1 1 200px"));
    return row;
  }

  // ── Recent activity ────────────────────────────────────────────

  private Div buildRecentActivityCard() {
    Div card = new Div();
    card.addClassName(TemplateBrand.CSS_CARD);
    card.getStyle().set("display", "flex");
    card.getStyle().set("flex-direction", "column");
    card.getStyle().set("gap", "var(--lumo-space-m)");

    H3 h = new H3(tr(K_ACT_HEADING, "Recent activity"));
    h.getStyle().set("margin", "0");
    h.getStyle().set("font-size", "1.125rem");
    h.getStyle().set("font-weight", "600");
    card.add(h);

    var events = safeQuery();
    if (events.isEmpty()) {
      card.add(new EmptyState(VaadinIcon.RECORDS,
          tr(K_ACT_EMPTY_TITLE, "No audit events yet"),
          tr(K_ACT_EMPTY_BODY,
              "Events appear here as soon as someone signs in or an "
                  + "admin grants or revokes a role.")));
      return card;
    }
    int limit = Math.min(5, events.size());
    for (int i = events.size() - limit; i < events.size(); i++) {
      var event = events.get(i);
      Div row = new Div();
      row.getStyle().set("display", "flex");
      row.getStyle().set("justify-content", "space-between");
      row.getStyle().set("padding", "var(--lumo-space-s) 0");
      row.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)");
      Span type = new Span(event.getClass().getSimpleName());
      type.getElement().getThemeList().add("badge contrast");
      Span when = new Span(event.timestamp().toString());
      when.addClassName(TemplateBrand.CSS_MUTED);
      row.add(type, when);
      card.add(row);
    }
    return card;
  }

  private static List<AuditEvent> safeQuery() {
    try {
      return JSentinelServiceResolver.securityAuditService()
          .query(new AuditQuery(Set.of(), null, null, null, 0));
    } catch (RuntimeException e) {
      return java.util.List.of();
    }
  }

  private static long safeCount(LongSupplier op) {
    try {
      return op.getAsLong();
    } catch (RuntimeException e) {
      return 0L;
    }
  }
}