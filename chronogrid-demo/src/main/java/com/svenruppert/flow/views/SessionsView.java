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
import com.svenruppert.flow.security.services.SessionStoreProvider;
import com.svenruppert.flow.views.ui.EmptyState;
import com.svenruppert.flow.views.ui.FilterBar;
import com.svenruppert.flow.views.ui.PageHeader;
import com.svenruppert.jsentinel.authorization.annotations.RequiresPermission;
import com.svenruppert.jsentinel.session.SessionRecord;
import com.svenruppert.jsentinel.session.SessionStatus;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Admin-only session inventory. Wraps {@link SessionStoreProvider}
 * with a polished filter bar, grid and revoke action.
 *
 * <p>This view deliberately does <strong>not</strong> extend
 * jSentinel's {@code SessionManagementView} — that would prevent a
 * custom toolbar / filter strip. The cost is one custom grid; the
 * benefit is full layout control.
 */
@Route(value = SessionsView.NAV, layout = MainLayout.class)
@RequiresPermission("admin:sessions")
public class SessionsView extends Composite<VerticalLayout>
    implements I18nSupport {

  public static final String NAV = "admin/sessions";

  // i18n keys
  private static final String K_HEADING = "sessions.heading";
  private static final String K_SUBTITLE = "sessions.subtitle";
  private static final String K_REFRESH = "common.refresh";
  private static final String K_COL_SID = "sessions.column.sessionId";
  private static final String K_COL_SUBJECT = "sessions.column.subject";
  private static final String K_COL_TENANT = "sessions.column.tenant";
  private static final String K_COL_STARTED = "sessions.column.started";
  private static final String K_COL_LAST = "sessions.column.lastActivity";
  private static final String K_COL_STATUS = "sessions.column.status";
  private static final String K_COL_ACTION = "sessions.column.action";
  private static final String K_FLT_SID = "sessions.filter.sessionId";
  private static final String K_FLT_SUBJECT = "sessions.filter.subject";
  private static final String K_FLT_TENANT = "sessions.filter.tenant";
  private static final String K_FLT_STARTED = "sessions.filter.started";
  private static final String K_FLT_ACTIVE = "sessions.filter.active";
  private static final String K_FLT_STATUS = "sessions.filter.status";
  private static final String K_FLT_STATUS_PH = "sessions.filter.status.placeholder";
  private static final String K_REVOKE_TITLE = "sessions.action.revoke.title";
  private static final String K_REVOKE_DISABLED = "sessions.action.revoke.disabled";
  private static final String K_EMPTY_TITLE = "sessions.empty.title";
  private static final String K_EMPTY_BODY = "sessions.empty.body";
  private static final String K_UNIT_SESSIONS = "sessions";

  private static final DateTimeFormatter TS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
          .withZone(ZoneId.systemDefault());

  private final Grid<SessionRecord> grid = new Grid<>(SessionRecord.class, false);
  private final FilterBar filterBar = new FilterBar();

  // One filter per column — combined with AND.
  private final TextField sessionIdFilter;
  private final TextField subjectFilter;
  private final TextField tenantFilter;
  private final DatePicker startedSince;
  private final DatePicker activitySince;
  private final ComboBox<SessionStatus> statusFilter;

  private final EmptyState emptyState;

  {
    sessionIdFilter = filterBar.addText(tr(K_FLT_SID, "Session id"), "Contains…");
    subjectFilter   = filterBar.addText(tr(K_FLT_SUBJECT, "Subject"), "Contains…");
    tenantFilter    = filterBar.addText(tr(K_FLT_TENANT, "Tenant"), "Contains…");
    startedSince    = filterBar.addDate(tr(K_FLT_STARTED, "Started on/after"),
        "yyyy-mm-dd");
    activitySince   = filterBar.addDate(tr(K_FLT_ACTIVE, "Active on/after"),
        "yyyy-mm-dd");
    statusFilter    = filterBar.addSingleSelect(tr(K_FLT_STATUS, "Status"),
        SessionStatus.values(), tr(K_FLT_STATUS_PH, "Any status"));
    emptyState = new EmptyState(VaadinIcon.CONNECT,
        tr(K_EMPTY_TITLE, "No sessions match"),
        tr(K_EMPTY_BODY,
            "Try clearing the filters above. The session store is empty "
                + "until someone signs in."));
  }

  public SessionsView() {
    VerticalLayout root = getContent();
    root.setSizeFull();
    root.setSpacing(false);
    root.getStyle().set("gap", "var(--lumo-space-l)");

    Button refresh = new Button(tr(K_REFRESH, "Refresh"),
        VaadinIcon.REFRESH.create(), e -> refresh());
    refresh.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
    PageHeader header = new PageHeader(
        tr(K_HEADING, "Active sessions"),
        tr(K_SUBTITLE,
            "Every session currently tracked by the SessionStore. Revoke "
                + "any session with one click — the change is persisted "
                + "and audited as SessionInvalidated."))
        .withActions(refresh);
    HomeButton.forStandalone(getClass()).ifPresent(header::add);
    root.add(header);

    statusFilter.setItemLabelGenerator(Enum::name);
    sessionIdFilter.addValueChangeListener(e -> refresh());
    subjectFilter.addValueChangeListener(e -> refresh());
    tenantFilter.addValueChangeListener(e -> refresh());
    startedSince.addValueChangeListener(e -> refresh());
    activitySince.addValueChangeListener(e -> refresh());
    statusFilter.addValueChangeListener(e -> refresh());
    filterBar.onClear(this::refresh);
    root.add(filterBar);

    grid.setSizeFull();
    grid.setPageSize(50);
    grid.addColumn(s -> s.sessionId().value())
        .setHeader(tr(K_COL_SID, "Session id")).setWidth("16em").setFlexGrow(0);
    grid.addColumn(s -> s.subjectId().value())
        .setHeader(tr(K_COL_SUBJECT, "Subject")).setWidth("12em").setFlexGrow(0);
    grid.addColumn(s -> s.tenant().value())
        .setHeader(tr(K_COL_TENANT, "Tenant")).setWidth("9em").setFlexGrow(0);
    grid.addColumn(s -> TS.format(s.createdAt()))
        .setHeader(tr(K_COL_STARTED, "Started")).setWidth("12em").setFlexGrow(0);
    grid.addColumn(s -> TS.format(s.lastActivityAt()))
        .setHeader(tr(K_COL_LAST, "Last activity")).setWidth("12em").setFlexGrow(0);
    grid.addComponentColumn(this::renderStatusBadge)
        .setHeader(tr(K_COL_STATUS, "Status")).setWidth("8em").setFlexGrow(0);
    grid.addComponentColumn(this::renderRevokeButton)
        .setHeader(tr(K_COL_ACTION, "Action")).setWidth("8em").setFlexGrow(0);
    root.add(grid);
    root.add(emptyState);
    root.setFlexGrow(1, grid);

    refresh();
  }

  private void refresh() {
    String sidNeedle = textValue(sessionIdFilter);
    String subNeedle = textValue(subjectFilter);
    String tenNeedle = textValue(tenantFilter);
    LocalDate startedAfter = startedSince.getValue();
    LocalDate activeAfter = activitySince.getValue();
    SessionStatus wantedStatus = statusFilter.getValue();

    List<SessionRecord> sessions = SessionStoreProvider.sessionStore().findAll()
        .stream()
        .sorted(Comparator.comparing(SessionRecord::createdAt).reversed())
        .filter(s -> sidNeedle.isEmpty()
            || s.sessionId().value().toLowerCase().contains(sidNeedle))
        .filter(s -> subNeedle.isEmpty()
            || s.subjectId().value().toLowerCase().contains(subNeedle))
        .filter(s -> tenNeedle.isEmpty()
            || s.tenant().value().toLowerCase().contains(tenNeedle))
        .filter(s -> startedAfter == null
            || !s.createdAt().atZone(ZoneId.systemDefault())
                  .toLocalDate().isBefore(startedAfter))
        .filter(s -> activeAfter == null
            || !s.lastActivityAt().atZone(ZoneId.systemDefault())
                  .toLocalDate().isBefore(activeAfter))
        .filter(s -> wantedStatus == null || s.status() == wantedStatus)
        .toList();
    grid.setItems(sessions);
    filterBar.setCount(sessions.size(), K_UNIT_SESSIONS);
    boolean empty = sessions.isEmpty();
    grid.setVisible(!empty);
    emptyState.setVisible(empty);
  }

  private static String textValue(TextField field) {
    String v = field.getValue();
    return v == null ? "" : v.trim().toLowerCase();
  }

  private com.vaadin.flow.component.html.Span renderStatusBadge(SessionRecord s) {
    com.vaadin.flow.component.html.Span span =
        new com.vaadin.flow.component.html.Span(s.status().name());
    String theme = switch (s.status()) {
      case ACTIVE -> "badge success pill";
      case REVOKED -> "badge error pill";
      default -> "badge contrast pill";
    };
    span.getElement().getThemeList().add(theme);
    return span;
  }

  private Button renderRevokeButton(SessionRecord s) {
    Button revoke = new Button(VaadinIcon.BAN.create(), e -> revoke(s));
    revoke.addThemeVariants(ButtonVariant.LUMO_ERROR,
        ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    revoke.setEnabled(s.status() == SessionStatus.ACTIVE);
    revoke.getElement().setAttribute("title",
        s.status() == SessionStatus.ACTIVE
            ? tr(K_REVOKE_TITLE, "Revoke session")
            : tr(K_REVOKE_DISABLED, "Only ACTIVE sessions can be revoked"));
    return revoke;
  }

  private void revoke(SessionRecord record) {
    SessionStoreProvider.sessionStore()
        .save(record.withStatus(SessionStatus.REVOKED));
    refresh();
  }
}
