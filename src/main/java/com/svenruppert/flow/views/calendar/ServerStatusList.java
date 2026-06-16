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

package com.svenruppert.flow.views.calendar;

import com.svenruppert.flow.calendar.service.CalDavServerConnection;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact list of per-server status pills for the {@code CalendarView}
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
    extends Composite<HorizontalLayout> {

  public enum State { UNKNOWN, CONNECTED, DISCONNECTED }

  private final Map<String, Span> dotByServerId = new HashMap<>();

  public ServerStatusList() {
    HorizontalLayout root = getContent();
    root.setAlignItems(FlexComponent.Alignment.CENTER);
    root.setSpacing(false);
    root.setPadding(false);
    root.getStyle()
        .set("gap", "var(--lumo-space-xs)")
        .set("flex-wrap", "wrap");
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
    for (CalDavServerConnection server : servers) {
      root.add(buildPill(server));
    }
  }

  /**
   * Updates the dot colour for a single server. No-op when the
   * {@code serverId} isn't in the current list (e.g. server was
   * just removed).
   */
  public void setStatus(String serverId, State state) {
    Span dot = dotByServerId.get(serverId);
    if (dot == null) return;
    dot.getStyle().set("background", colorFor(state));
  }

  // ── internal ────────────────────────────────────────────────────

  private Span buildPill(CalDavServerConnection server) {
    Span dot = new Span();
    dot.getStyle()
        .set("display", "inline-block")
        .set("width", "8px")
        .set("height", "8px")
        .set("border-radius", "50%")
        .set("background", colorFor(State.UNKNOWN))
        .set("flex-shrink", "0");
    dotByServerId.put(server.id(), dot);

    Span name = new Span(server.displayName());
    name.getStyle()
        .set("color", "var(--lumo-body-text-color)")
        .set("font-size", "var(--lumo-font-size-s)");

    Span pill = new Span(dot, name);
    pill.setId("server-pill-" + server.id());
    pill.getElement().setProperty("title", server.baseUri().toString());
    pill.getStyle()
        .set("display", "inline-flex")
        .set("align-items", "center")
        .set("gap", "var(--lumo-space-xs)")
        .set("padding", "2px var(--lumo-space-s)")
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "var(--lumo-border-radius-l)");
    return pill;
  }

  private static String colorFor(State state) {
    return switch (state) {
      case CONNECTED -> "var(--lumo-success-color)";
      case DISCONNECTED -> "var(--lumo-error-color)";
      default -> "var(--lumo-contrast-30pct)";
    };
  }
}
