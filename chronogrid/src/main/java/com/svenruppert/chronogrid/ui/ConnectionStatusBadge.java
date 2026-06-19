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
import com.svenruppert.chronogrid.i18n.CalendarMessages;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Span;

/**
 * Coloured Connected / Disconnected / Unknown pill the
 * {@code ChronoGrid} toolbar carries beside the backend URL.
 * Owns the Lumo theme transitions; the View drives the state
 * transitions through {@link #markConnected()},
 * {@link #markDisconnected(String)} and {@link #markUnknown()} and
 * subscribes to "online again" events via the optional
 * {@code onReconnect} hook configured at construction time.
 */
public final class ConnectionStatusBadge
    extends Composite<Span> implements HasLogger {

  public enum State { UNKNOWN, CONNECTED, DISCONNECTED }

  private static final String K_STATUS_CONNECTED = "calendar.status.connected";
  private static final String K_STATUS_DISCONNECTED = "calendar.status.disconnected";
  private static final String K_STATUS_UNKNOWN = "calendar.status.unknown";

  public static final String ID = "calendar-connection-status";

  private State state = State.UNKNOWN;
  private final Runnable onReconnect;
  private final CalendarMessages messages;

  /** @deprecated tests-only — prefer the constructor that takes a {@link CalendarMessages}. */
  @Deprecated
  public ConnectionStatusBadge(Runnable onReconnect) {
    this(CalendarMessages.fallbackOnly(), onReconnect);
  }

  public ConnectionStatusBadge(CalendarMessages messages, Runnable onReconnect) {
    this.messages = messages;
    this.onReconnect = onReconnect;
    getContent().setId(ID);
    markUnknown();
  }

  public State state() {
    return state;
  }

  public void markConnected() {
    State previous = state;
    state = State.CONNECTED;
    Span s = getContent();
    s.setText(messages.tr(K_STATUS_CONNECTED, "Connected"));
    s.getElement().setAttribute("theme", "badge success");
    s.getElement().setProperty("title", "");
    if (previous != State.CONNECTED) {
      logger().info("ConnectionStatusBadge: {} -> CONNECTED", previous);
    }
    if (previous == State.DISCONNECTED && onReconnect != null) {
      logger().info("ConnectionStatusBadge: triggering onReconnect handler");
      onReconnect.run();
    }
  }

  public void markDisconnected(String reason) {
    State previous = state;
    state = State.DISCONNECTED;
    Span s = getContent();
    s.setText(messages.tr(K_STATUS_DISCONNECTED, "Disconnected"));
    s.getElement().setAttribute("theme", "badge error");
    if (reason != null && !reason.isBlank()) {
      s.getElement().setProperty("title", reason);
    }
    if (previous != State.DISCONNECTED) {
      logger().info("ConnectionStatusBadge: {} -> DISCONNECTED ({})",
          previous, reason == null ? "no reason" : reason);
    }
  }

  public void markUnknown() {
    State previous = state;
    state = State.UNKNOWN;
    Span s = getContent();
    s.setText(messages.tr(K_STATUS_UNKNOWN, "Unknown"));
    s.getElement().setAttribute("theme", "badge contrast");
    s.getElement().setProperty("title", "");
    if (previous != State.UNKNOWN) {
      logger().info("ConnectionStatusBadge: {} -> UNKNOWN", previous);
    }
  }
}
