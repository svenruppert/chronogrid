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

package com.svenruppert.chronogrid.state;

import com.svenruppert.chronogrid.service.CalDavConnectionConfig;
import com.svenruppert.chronogrid.service.CalDavServerConnection;
import com.svenruppert.chronogrid.service.CalendarSubscription;
import com.vaadin.flow.server.VaadinSession;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Default {@link CalendarStateStore} backed by the current
 * {@link VaadinSession}. Survives navigation but resets on logout —
 * the historical demo behaviour.
 *
 * <p>The session-attribute keys exposed as public constants are the
 * project-wide contract: Browserless tests poke them directly to
 * seed and clear state between scenarios, so the values here MUST
 * agree with whatever the legacy {@code ChronoGrid.SESSION_KEY_*}
 * re-exports point at.
 */
public final class VaadinSessionCalendarStateStore
    implements CalendarStateStore, Serializable {

  private static final long serialVersionUID = 1L;

  public static final String SESSION_KEY_CONNECTION = "calendar.connection.config";
  public static final String SESSION_KEY_SUBSCRIPTIONS = "calendar.subscriptions";
  public static final String SESSION_KEY_SERVERS = "calendar.servers";
  public static final String SESSION_KEY_NDAYS = "calendar.nav.nDays";

  @Override
  public Optional<CalDavConnectionConfig> readConnection() {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) return Optional.empty();
    Object raw = session.getAttribute(SESSION_KEY_CONNECTION);
    return raw instanceof CalDavConnectionConfig c
        ? Optional.of(c) : Optional.empty();
  }

  @Override
  public void writeConnection(CalDavConnectionConfig cfg) {
    VaadinSession session = VaadinSession.getCurrent();
    if (session != null) session.setAttribute(SESSION_KEY_CONNECTION, cfg);
  }

  @Override
  public List<CalDavServerConnection> readServers() {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) return List.of();
    Object raw = session.getAttribute(SESSION_KEY_SERVERS);
    if (raw instanceof List<?> list) {
      List<CalDavServerConnection> out = new ArrayList<>();
      for (Object o : list) {
        if (o instanceof CalDavServerConnection cs) out.add(cs);
      }
      return List.copyOf(out);
    }
    return List.of();
  }

  @Override
  public void writeServers(List<CalDavServerConnection> servers) {
    VaadinSession session = VaadinSession.getCurrent();
    if (session != null) {
      session.setAttribute(SESSION_KEY_SERVERS, List.copyOf(servers));
    }
  }

  @Override
  public List<CalendarSubscription> readSubscriptions() {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) return List.of();
    Object raw = session.getAttribute(SESSION_KEY_SUBSCRIPTIONS);
    if (raw instanceof List<?> list) {
      List<CalendarSubscription> out = new ArrayList<>();
      for (Object o : list) {
        if (o instanceof CalendarSubscription cs) out.add(cs);
      }
      return List.copyOf(out);
    }
    return List.of();
  }

  @Override
  public void writeSubscriptions(List<CalendarSubscription> subs) {
    VaadinSession session = VaadinSession.getCurrent();
    if (session != null) {
      session.setAttribute(SESSION_KEY_SUBSCRIPTIONS, List.copyOf(subs));
    }
  }

  @Override
  public int readNDays(int fallback) {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) return fallback;
    Object raw = session.getAttribute(SESSION_KEY_NDAYS);
    return raw instanceof Integer i ? i : fallback;
  }

  @Override
  public void writeNDays(int n) {
    VaadinSession session = VaadinSession.getCurrent();
    if (session != null) session.setAttribute(SESSION_KEY_NDAYS, n);
  }
}
