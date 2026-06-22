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
  public static final String SESSION_KEY_TAG_FILTER = "calendar.tagFilter";
  public static final String SESSION_KEY_ENTRY_COLOURS = "calendar.entryColours";
  public static final String SESSION_KEY_FOCAL_DAY = "calendar.nav.focalDay";

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

  @Override
  public java.util.Set<String> readTagFilter() {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) return java.util.Set.of();
    Object raw = session.getAttribute(SESSION_KEY_TAG_FILTER);
    if (raw instanceof java.util.Set<?> set) {
      java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
      for (Object o : set) {
        if (o instanceof String s && !s.isBlank()) out.add(s);
      }
      return java.util.Collections.unmodifiableSet(out);
    }
    return java.util.Set.of();
  }

  @Override
  public java.util.Optional<String> readEntryColour(String entryUid) {
    if (entryUid == null) return java.util.Optional.empty();
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) return java.util.Optional.empty();
    java.util.Map<String, String> map = readEntryColourMap(session);
    return java.util.Optional.ofNullable(map.get(entryUid));
  }

  @Override
  public void writeEntryColour(String entryUid, String colour) {
    if (entryUid == null) return;
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) return;
    java.util.Map<String, String> next =
        new java.util.HashMap<>(readEntryColourMap(session));
    if (colour == null || colour.isBlank()) {
      next.remove(entryUid);
    } else {
      next.put(entryUid, colour);
    }
    session.setAttribute(SESSION_KEY_ENTRY_COLOURS,
        java.util.Collections.unmodifiableMap(next));
  }

  @Override
  public void clearEntryColour(String entryUid) {
    writeEntryColour(entryUid, null);
  }

  @SuppressWarnings("unchecked")
  private static java.util.Map<String, String> readEntryColourMap(
      VaadinSession session) {
    Object raw = session.getAttribute(SESSION_KEY_ENTRY_COLOURS);
    if (raw instanceof java.util.Map<?, ?> map) {
      java.util.Map<String, String> out = new java.util.HashMap<>();
      for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
        if (e.getKey() instanceof String k
            && e.getValue() instanceof String v) {
          out.put(k, v);
        }
      }
      return out;
    }
    return java.util.Map.of();
  }

  @Override
  public java.time.LocalDate readFocalDay(java.time.LocalDate fallback) {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) return fallback;
    Object raw = session.getAttribute(SESSION_KEY_FOCAL_DAY);
    return raw instanceof java.time.LocalDate d ? d : fallback;
  }

  @Override
  public void writeFocalDay(java.time.LocalDate day) {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) return;
    if (day == null) {
      session.setAttribute(SESSION_KEY_FOCAL_DAY, null);
    } else {
      session.setAttribute(SESSION_KEY_FOCAL_DAY, day);
    }
  }

  @Override
  public void writeTagFilter(java.util.Set<String> tags) {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) return;
    if (tags == null || tags.isEmpty()) {
      session.setAttribute(SESSION_KEY_TAG_FILTER, null);
      return;
    }
    java.util.LinkedHashSet<String> snapshot = new java.util.LinkedHashSet<>(tags);
    session.setAttribute(SESSION_KEY_TAG_FILTER,
        java.util.Collections.unmodifiableSet(snapshot));
  }
}
