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

/*-
 * #%L
 * Calendar — CalDAV headless
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2013 - 2026 Sven Ruppert
 * %%
 * Licensed under the EUPL, Version 1.1 or – as soon they will be
 * approved by the European Commission - subsequent versions of the
 * EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 * #L%
 */

import com.svenruppert.chronogrid.service.CalDavConnectionConfig;
import com.svenruppert.chronogrid.service.CalDavServerConnection;
import com.svenruppert.chronogrid.service.CalendarSubscription;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Per-user state the {@code ChronoGrid} keeps between navigations:
 * the active CalDAV connection config, the list of configured
 * servers and subscriptions, and the N-days slider preference.
 *
 * <p>The view reads/writes state through this interface only — no
 * direct {@code VaadinSession.getAttribute(...)} calls live in the
 * view any more. That keeps {@code ChronoGrid} portable outside
 * this Vaadin-Session-scoped host: another consumer can plug in a
 * database-backed store, an in-memory test store, or anything else
 * implementing this contract.
 *
 * <p>The default implementation {@link VaadinSessionCalendarStateStore}
 * preserves the legacy attribute-key behaviour bit-for-bit and is
 * wired in by {@code ChronoGrid}'s no-arg constructor.
 */
public interface CalendarStateStore {

  Optional<CalDavConnectionConfig> readConnection();

  void writeConnection(CalDavConnectionConfig cfg);

  List<CalDavServerConnection> readServers();

  void writeServers(List<CalDavServerConnection> servers);

  List<CalendarSubscription> readSubscriptions();

  void writeSubscriptions(List<CalendarSubscription> subs);

  int readNDays(int fallback);

  void writeNDays(int n);

  /**
   * Feature #3 — cross-calendar tag filter. The set of tag values
   * (normalised lower-case) the user has chosen in the toolbar
   * filter. An entry passes the filter when its tags intersect this
   * set; an empty set means "no filter" (every entry passes).
   *
   * <p>Default impls return the empty set and silently drop writes
   * so existing {@code CalendarStateStore} implementations need no
   * changes when the feature toggles to default-off in their host.
   */
  default Set<String> readTagFilter() {
    return Set.of();
  }

  default void writeTagFilter(Set<String> tags) {
    // no-op default — overridden by VaadinSessionCalendarStateStore
  }
}
