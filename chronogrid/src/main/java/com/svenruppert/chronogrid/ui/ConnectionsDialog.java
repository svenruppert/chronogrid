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
import com.svenruppert.chronogrid.client.CalDavErrors;
import com.svenruppert.chronogrid.i18n.CalendarMessages;
import com.svenruppert.chronogrid.service.CalDavServerConnection;
import com.svenruppert.chronogrid.service.CalendarSubscription;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists all configured CalDAV server connections with per-server
 * live status (Connected / Disconnected / Unknown). Status is
 * probed once when the dialog is opened: pick the first
 * subscription belonging to the server, build a fresh
 * {@link CalDavClient} with the server's credentials, and issue a
 * narrow {@code REPORT calendar-query} for the current ±1 min
 * window. A clean 207 = Connected; any throw = Disconnected
 * (tooltip shows the {@link CalDavErrors.Kind}).
 */
public final class ConnectionsDialog
    extends Composite<Dialog> implements HasLogger {

  private final CalendarMessages messages;

  private static final String K_TITLE = "calendar.connections.title";
  private static final String K_EMPTY = "calendar.connections.empty";
  private static final String K_COL_NAME = "calendar.connections.col.name";
  private static final String K_COL_URI = "calendar.connections.col.uri";
  private static final String K_COL_AUTH = "calendar.connections.col.auth";
  private static final String K_COL_STATUS = "calendar.connections.col.status";
  private static final String K_AUTH_YES = "calendar.connections.auth.yes";
  private static final String K_AUTH_NO = "calendar.connections.auth.no";
  private static final String K_STATUS_CONNECTED = "calendar.status.connected";
  private static final String K_STATUS_DISCONNECTED = "calendar.status.disconnected";
  private static final String K_STATUS_UNKNOWN = "calendar.status.unknown";
  private static final String K_ACTION_CLOSE = "calendar.action.cancel";

  /** @deprecated kept for tests — prefer the {@link CalendarMessages}-taking variant. */
  @Deprecated
  public ConnectionsDialog(List<CalDavServerConnection> servers,
                           List<CalendarSubscription> subscriptions) {
    this(CalendarMessages.fallbackOnly(), servers, subscriptions);
  }

  public ConnectionsDialog(CalendarMessages messages,
                           List<CalDavServerConnection> servers,
                           List<CalendarSubscription> subscriptions) {
    this.messages = messages;
    Dialog dialog = getContent();
    dialog.setHeaderTitle(messages.tr(K_TITLE, "Server connections"));
    dialog.setWidth("780px");
    logger().info("ConnectionsDialog open: {} server(s), {} subscription(s)",
        servers.size(), subscriptions.size());

    if (servers.isEmpty()) {
      Span empty = new Span(messages.tr(K_EMPTY,
          "No CalDAV servers configured yet. Use Settings → Discover "
              + "calendars to add one."));
      empty.addClassName("chronogrid-secondary-text");
      dialog.add(empty);
    } else {
      dialog.add(buildGrid(servers, subscriptions));
    }

    Button close = new Button(messages.tr(K_ACTION_CLOSE, "Cancel"),
        e -> dialog.close());
    close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    dialog.getFooter().add(close);
  }

  private VerticalLayout buildGrid(List<CalDavServerConnection> servers,
                                   List<CalendarSubscription> subs) {
    Map<String, CalendarSubscription> firstSubByServer = new HashMap<>();
    for (CalendarSubscription s : subs) {
      if (s.serverId() != null
          && !firstSubByServer.containsKey(s.serverId())) {
        firstSubByServer.put(s.serverId(), s);
      }
    }

    Grid<CalDavServerConnection> grid = new Grid<>(CalDavServerConnection.class, false);
    grid.setItems(servers);
    grid.setAllRowsVisible(true);

    grid.addColumn(CalDavServerConnection::displayName)
        .setHeader(messages.tr(K_COL_NAME, "Name"))
        .setAutoWidth(true).setFlexGrow(0);

    grid.addColumn(s -> s.baseUri().toString())
        .setHeader(messages.tr(K_COL_URI, "Base URI"))
        .setAutoWidth(true).setFlexGrow(1);

    grid.addColumn(s -> s.hasAuth()
            ? messages.tr(K_AUTH_YES, "Basic ({0})", s.username())
            : messages.tr(K_AUTH_NO, "anonymous"))
        .setHeader(messages.tr(K_COL_AUTH, "Auth"))
        .setAutoWidth(true).setFlexGrow(0);

    grid.addComponentColumn(s -> {
      CalendarSubscription probeTarget = firstSubByServer.get(s.id());
      return renderStatusPill(probe(s, probeTarget));
    }).setHeader(messages.tr(K_COL_STATUS, "Status"))
        .setAutoWidth(true).setFlexGrow(0);

    VerticalLayout layout = new VerticalLayout(grid);
    layout.setPadding(false);
    layout.setSpacing(false);
    return layout;
  }

  private Span renderStatusPill(ProbeResult result) {
    Span pill = new Span();
    pill.setId("connections-status-" + (result.serverId == null ? "x" : result.serverId));
    switch (result.state) {
      case CONNECTED -> {
        pill.setText(messages.tr(K_STATUS_CONNECTED, "Connected"));
        pill.getElement().setAttribute("theme", "badge success");
      }
      case DISCONNECTED -> {
        pill.setText(messages.tr(K_STATUS_DISCONNECTED, "Disconnected"));
        pill.getElement().setAttribute("theme", "badge error");
        if (result.detail != null) {
          pill.getElement().setProperty("title", result.detail);
        }
      }
      default -> {
        pill.setText(messages.tr(K_STATUS_UNKNOWN, "Unknown"));
        pill.getElement().setAttribute("theme", "badge contrast");
      }
    }
    return pill;
  }

  private static ProbeResult probe(CalDavServerConnection server,
                                   CalendarSubscription probeTarget) {
    if (probeTarget == null) {
      return new ProbeResult(server.id(), State.UNKNOWN, null);
    }
    URI uri = probeTarget.uri();
    CalDavClient client = server.hasAuth()
        ? new CalDavClient(uri, server.username(), server.password())
        : new CalDavClient(uri);
    try {
      Instant now = Instant.now();
      client.findInRange(now.minusSeconds(60), now.plusSeconds(60));
      return new ProbeResult(server.id(), State.CONNECTED, null);
    } catch (RuntimeException ex) {
      String kind = CalDavErrors.classify(ex).name();
      return new ProbeResult(server.id(), State.DISCONNECTED, kind);
    }
  }

  public void open() {
    getContent().open();
  }

  // ── internal ────────────────────────────────────────────────────

  private enum State { UNKNOWN, CONNECTED, DISCONNECTED }

  private record ProbeResult(String serverId, State state, String detail) { }
}
