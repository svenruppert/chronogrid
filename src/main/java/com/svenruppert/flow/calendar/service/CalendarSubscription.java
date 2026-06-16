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

package com.svenruppert.flow.calendar.service;

import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

/**
 * One CalDAV calendar the user is subscribed to during a Vaadin
 * session. {@code visible} is a UI-only toggle — flipped from the
 * Subscriptions dialog without re-fetching; the Service still
 * queries the server for every subscribed URI, the View filters
 * invisible ones out before rendering. Disconnecting (the X button
 * in the dialog) removes the record from the session list entirely;
 * the next REPORT round skips that URI for real.
 *
 * <p>{@code color} carries the server-supplied {@code calendar-color}
 * (or a deterministic palette fallback) so the Subscriptions dialog
 * can draw a swatch and the calendar entries inherit the same hue.
 *
 * <p>{@code serverId} references the owning
 * {@link CalDavServerConnection} so the service knows which
 * credentials to use when talking to this URI. May be {@code null}
 * for legacy single-server scenarios (the testbench default).
 */
public record CalendarSubscription(URI uri,
                                   String displayName,
                                   String color,
                                   boolean visible,
                                   String serverId)
    implements Serializable {

  public CalendarSubscription {
    Objects.requireNonNull(uri, "uri must not be null");
  }

  /** Back-compat 4-arg constructor; {@code serverId} = {@code null}. */
  public CalendarSubscription(URI uri, String displayName,
                              String color, boolean visible) {
    this(uri, displayName, color, visible, null);
  }

  public CalendarSubscription withVisible(boolean newVisible) {
    return new CalendarSubscription(uri, displayName, color, newVisible, serverId);
  }

  public CalendarSubscription withColor(String newColor) {
    return new CalendarSubscription(uri, displayName, newColor, visible, serverId);
  }
}
