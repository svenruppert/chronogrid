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
import com.svenruppert.chronogrid.service.CalDavServerConnection;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact list of per-server status pills for the {@code ChronoGrid}
 * toolbar. Each pill is a rounded `Span` carrying:
 *
 * <ul>
 *   <li>A small coloured dot — green {@link State#CONNECTED},
 *       red {@link State#DISCONNECTED}, grey {@link State#UNKNOWN}.</li>
 *   <li>The server's logical {@code displayName}.</li>
 *   <li>A tooltip with the server's {@code baseUri} for users who
 *       want the URL details on hover.</li>
 * </ul>
 *
 * <p>Sven explicitly asked for the URL to be off the toolbar — this
 * component replaces the old {@code Backend: http://…} text. The
 * URL is still discoverable via tooltip and through the dedicated
 * Connections dialog.
 */
public final class ServerStatusList
    extends Composite<HorizontalLayout> implements HasLogger {

  public enum State { UNKNOWN, CONNECTED, DISCONNECTED }

  private final Map<String, Span> dotByServerId = new HashMap<>();
  private final Map<String, State> stateByServerId = new HashMap<>();
  private final Map<String, String> nameByServerId = new HashMap<>();

  public ServerStatusList() {
    HorizontalLayout root = getContent();
    root.setAlignItems(FlexComponent.Alignment.CENTER);
    root.setSpacing(false);
    root.setPadding(false);
    root.addClassName("server-status-list");
  }

  /**
   * Rebuilds the pill row from the given server list. Previous pills
   * are dropped; statuses reset to {@link State#UNKNOWN} until
   * {@link #setStatus(String, State)} is called.
   */
  public void setServers(List<CalDavServerConnection> servers) {
    HorizontalLayout root = getContent();
    root.removeAll();
    dotByServerId.clear();
    stateByServerId.clear();
    nameByServerId.clear();
    for (CalDavServerConnection server : servers) {
      root.add(buildPill(server));
      stateByServerId.put(server.id(), State.UNKNOWN);
      nameByServerId.put(server.id(), server.displayName());
    }
    logger().info("ServerStatusList rebuilt: {} server(s)", servers.size());
  }

  /**
   * Updates the dot colour for a single server. No-op when the
   * {@code serverId} isn't in the current list (e.g. server was
   * just removed).
   */
  public void setStatus(String serverId, State state) {
    Span dot = dotByServerId.get(serverId);
    if (dot == null) return;
    State previous = stateByServerId.put(serverId, state);
    applyDotState(dot, state);
    if (previous != state) {
      logger().info("Server {} ({}): {} -> {}",
          nameByServerId.getOrDefault(serverId, serverId), serverId,
          previous == null ? "UNKNOWN" : previous, state);
    }
  }

  // ── internal ────────────────────────────────────────────────────

  private Span buildPill(CalDavServerConnection server) {
    Span dot = new Span();
    dot.addClassName("server-status-pill__dot");
    applyDotState(dot, State.UNKNOWN);
    dotByServerId.put(server.id(), dot);

    Span name = new Span(server.displayName());
    name.addClassName("server-status-pill__name");

    Span pill = new Span(dot, name);
    pill.setId("server-pill-" + server.id());
    pill.getElement().setProperty("title", server.baseUri().toString());
    pill.addClassName("server-status-pill");
    return pill;
  }

  private static void applyDotState(Span dot, State state) {
    dot.removeClassNames(
        "server-status-pill__dot--connected",
        "server-status-pill__dot--disconnected",
        "server-status-pill__dot--unknown");
    dot.addClassName(switch (state) {
      case CONNECTED -> "server-status-pill__dot--connected";
      case DISCONNECTED -> "server-status-pill__dot--disconnected";
      default -> "server-status-pill__dot--unknown";
    });
  }
}
