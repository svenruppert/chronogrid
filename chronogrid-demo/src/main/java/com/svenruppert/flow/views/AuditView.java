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
import com.svenruppert.flow.views.ui.EmptyState;
import com.svenruppert.flow.views.ui.FilterBar;
import com.svenruppert.flow.views.ui.PageHeader;
import com.svenruppert.jsentinel.audit.AuditEvent;
import com.svenruppert.jsentinel.audit.AuditQuery;
import com.svenruppert.jsentinel.audit.JSentinelAuditService;
import com.svenruppert.jsentinel.audit.LoginFailed;
import com.svenruppert.jsentinel.audit.LoginSucceeded;
import com.svenruppert.jsentinel.audit.LogoutPerformed;
import com.svenruppert.jsentinel.audit.RoleAssigned;
import com.svenruppert.jsentinel.audit.RoleRevoked;
import com.svenruppert.jsentinel.audit.SessionCreated;
import com.svenruppert.jsentinel.audit.SessionInvalidated;
import com.svenruppert.jsentinel.audit.UserCreated;
import com.svenruppert.jsentinel.audit.UserDeleted;
import com.svenruppert.jsentinel.authorization.annotations.RequiresPermission;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Renders the in-memory audit ring buffer as a grid. Restricted by
 * {@code @RequiresPermission("audit:read")}.
 */
@Route(value = AuditView.NAV, layout = MainLayout.class)
@RequiresPermission("audit:read")
public class AuditView extends Composite<VerticalLayout>
    implements I18nSupport {

  public static final String NAV = "audit";

  // i18n keys
  private static final String K_HEADING = "audit.heading";
  private static final String K_SUBTITLE = "audit.subtitle";
  private static final String K_REFRESH = "common.refresh";
  private static final String K_COL_TS = "audit.column.timestamp";
  private static final String K_COL_TYPE = "audit.column.type";
  private static final String K_COL_SUBJECT = "audit.column.subject";
  private static final String K_COL_DETAIL = "audit.column.detail";
  private static final String K_FLT_SINCE = "audit.filter.since";
  private static final String K_FLT_UNTIL = "audit.filter.until";
  private static final String K_FLT_TYPE = "audit.filter.type";
  private static final String K_FLT_TYPE_PH = "audit.filter.type.placeholder";
  private static final String K_FLT_SUBJECT = "audit.filter.subject";
  private static final String K_FLT_DETAIL = "audit.filter.detail";
  private static final String K_EMPTY_TITLE = "audit.empty.title";
  private static final String K_EMPTY_BODY = "audit.empty.body";
  private static final String K_ROWCOUNT = "audit.rowcount";
  private static final String K_UNIT_EVENTS_PLAIN = "events";

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  /** Enumerated event types the type-filter chip exposes to operators. */
  private static final List<String> KNOWN_TYPES = List.of(
      "LoginSucceeded", "LoginFailed", "LogoutPerformed",
      "SessionCreated", "SessionInvalidated",
      "RoleAssigned", "RoleRevoked",
      "UserCreated", "UserDeleted");

  private final Grid<AuditEvent> grid = new Grid<>(AuditEvent.class, false);
  private final Span rowCount = new Span();
  private final FilterBar filterBar = new FilterBar();

  // One filter per grid column — combined with AND semantics.
  private final DatePicker sinceFilter;
  private final DatePicker untilFilter;
  private final MultiSelectComboBox<String> typeFilter;
  private final TextField subjectFilter;
  private final TextField detailFilter;

  private final EmptyState emptyState;

  public AuditView() {
    emptyState = new EmptyState(VaadinIcon.RECORDS,
        tr(K_EMPTY_TITLE, "No audit events match"),
        tr(K_EMPTY_BODY,
            "Try clearing the filters above, or wait for new events — "
                + "the ring buffer holds the most recent 256 entries."));

    VerticalLayout root = getContent();
    root.setSizeFull();
    root.setSpacing(false);
    root.getStyle().set("gap", "var(--lumo-space-l)");

    Button refresh = new Button(tr(K_REFRESH, "Refresh"),
        VaadinIcon.REFRESH.create(), e -> refresh());
    refresh.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
    PageHeader header = new PageHeader(
        tr(K_HEADING, "Security audit log"),
        tr(K_SUBTITLE,
            "In-memory ring buffer from JSentinelAuditService — restricted "
                + "to subjects holding the audit:read permission."))
        .withActions(refresh);
    HomeButton.forStandalone(getClass()).ifPresent(header::add);
    root.add(header);

    // ── Column-aligned filters (combined with AND) ────────────
    sinceFilter   = filterBar.addDate(tr(K_FLT_SINCE, "Since"), "yyyy-mm-dd");
    untilFilter   = filterBar.addDate(tr(K_FLT_UNTIL, "Until"), "yyyy-mm-dd");
    typeFilter    = filterBar.addMultiSelect(tr(K_FLT_TYPE, "Type"),
        KNOWN_TYPES, tr(K_FLT_TYPE_PH, "Any type"));
    subjectFilter = filterBar.addText(tr(K_FLT_SUBJECT, "Subject"), "Contains…");
    detailFilter  = filterBar.addText(tr(K_FLT_DETAIL, "Detail"), "Contains…");

    sinceFilter.addValueChangeListener(e -> refresh());
    untilFilter.addValueChangeListener(e -> refresh());
    typeFilter.addValueChangeListener(e -> refresh());
    subjectFilter.addValueChangeListener(e -> refresh());
    detailFilter.addValueChangeListener(e -> refresh());
    filterBar.onClear(this::refresh);
    root.add(filterBar);

    grid.setSizeFull();
    grid.setPageSize(50);
    grid.addColumn(e -> TIMESTAMP.format(e.timestamp()))
        .setHeader(tr(K_COL_TS, "Timestamp")).setWidth("11em").setFlexGrow(0);
    grid.addColumn(e -> e.getClass().getSimpleName())
        .setHeader(tr(K_COL_TYPE, "Type")).setWidth("16em").setFlexGrow(0);
    grid.addColumn(AuditView::subjectOf)
        .setHeader(tr(K_COL_SUBJECT, "Subject")).setWidth("14em").setFlexGrow(0);
    grid.addColumn(AuditView::summaryOf)
        .setHeader(tr(K_COL_DETAIL, "Detail")).setFlexGrow(1);
    root.add(grid);
    root.add(emptyState);
    root.add(rowCount);
    root.setFlexGrow(1, grid);

    refresh();
  }

  private void refresh() {
    JSentinelAuditService audit = JSentinelServiceResolver.securityAuditService();
    AuditQuery query = new AuditQuery(Set.of(), null, null, null, 0);
    List<AuditEvent> events = audit.query(query);
    List<AuditEvent> reversed = new ArrayList<>(events);
    Collections.reverse(reversed);

    // Per-column filters, combined with AND. Each filter is "no-op" when
    // its value is null/empty so the user can refine progressively.
    LocalDate since = sinceFilter.getValue();
    LocalDate until = untilFilter.getValue();
    Set<String> wantedTypes = typeFilter.getValue();
    String subjectNeedle = textValue(subjectFilter);
    String detailNeedle = textValue(detailFilter);

    List<AuditEvent> filtered = reversed.stream()
        .filter(e -> since == null
            || !e.timestamp().atZone(ZoneId.systemDefault())
                  .toLocalDate().isBefore(since))
        .filter(e -> until == null
            || !e.timestamp().atZone(ZoneId.systemDefault())
                  .toLocalDate().isAfter(until))
        .filter(e -> wantedTypes.isEmpty()
            || wantedTypes.contains(e.getClass().getSimpleName()))
        .filter(e -> subjectNeedle.isEmpty()
            || subjectOf(e).toLowerCase().contains(subjectNeedle))
        .filter(e -> detailNeedle.isEmpty()
            || summaryOf(e).toLowerCase().contains(detailNeedle))
        .toList();

    grid.setItems(filtered);
    rowCount.setText(tr(K_ROWCOUNT, "Showing {0} of {1} event(s).",
        filtered.size(), reversed.size()));
    filterBar.setCount(filtered.size(), K_UNIT_EVENTS_PLAIN);
    boolean empty = filtered.isEmpty();
    grid.setVisible(!empty);
    emptyState.setVisible(empty);
    rowCount.setVisible(!empty);
  }

  private static String textValue(TextField field) {
    String v = field.getValue();
    return v == null ? "" : v.trim().toLowerCase();
  }

  private static String subjectOf(AuditEvent event) {
    return switch (event) {
      case LoginSucceeded e -> e.username();
      case LoginFailed e -> e.username();
      case LogoutPerformed e -> e.subjectId();
      case SessionCreated e -> e.subjectId();
      case SessionInvalidated e -> e.subjectId();
      case RoleAssigned e -> e.subjectId();
      case RoleRevoked e -> e.subjectId();
      case UserCreated e -> e.username();
      case UserDeleted e -> e.username();
      default -> "—";
    };
  }

  private static String summaryOf(AuditEvent event) {
    return switch (event) {
      case LoginSucceeded e -> "client=" + nullToDash(e.clientAddress());
      case LoginFailed e -> "client=" + nullToDash(e.clientAddress())
          + " reason=" + nullToDash(e.reason());
      case LogoutPerformed e -> "scope=" + e.scope().name();
      case SessionCreated e -> "session=" + nullToDash(e.sessionId());
      case SessionInvalidated e -> "session=" + nullToDash(e.sessionId())
          + " reason=" + e.reason();
      case RoleAssigned e -> "role=" + e.role();
      case RoleRevoked e -> "role=" + e.role();
      case UserCreated e -> "role=" + nullToDash(e.role());
      case UserDeleted e -> "by=" + nullToDash(e.deletedBy());
      default -> event.toString();
    };
  }

  private static String nullToDash(String value) {
    return value == null ? "—" : value;
  }
}
