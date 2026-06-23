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

import com.svenruppert.chronogrid.service.CalDavServerConnection;
import com.svenruppert.chronogrid.service.CalendarSubscription;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Per-user state the {@code ChronoGrid} keeps between navigations:
 * the list of configured servers + subscriptions, the N-days slider
 * preference, the focal day, tag filter and per-entry colour
 * fall-backs.
 *
 * <p>The view reads/writes state through this interface only — no
 * direct {@code VaadinSession.getAttribute(...)} calls live in the
 * view any more. That keeps {@code ChronoGrid} portable outside
 * this Vaadin-Session-scoped host: another consumer can plug in a
 * database-backed store, an in-memory test store, or anything else
 * implementing this contract.
 *
 * <p>The default implementation {@link VaadinSessionCalendarStateStore}
 * is wired in by {@code ChronoGrid}'s no-arg constructor.
 */
public interface CalendarStateStore {

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

  /**
   * BUG #2 local colour-fallback store. iCloud's native UI strips
   * both RFC-7986 {@code COLOR} and custom {@code X-} properties on
   * user-edit-rewrite — neither survives the round-trip through
   * Apple's own editor. The DESCRIPTION-suffix marker survives if
   * the user doesn't edit the description, but a careful local
   * store gives an unconditional fallback: per-UID colour kept
   * outside CalDAV so iCloud's behaviour can't touch it.
   *
   * <p>{@link #readEntryColour(String)} returns {@link Optional#empty()}
   * when no colour was ever stored for that UID. The default impl
   * here returns empty; the Vaadin-session impl persists per
   * UI session.
   */
  default Optional<String> readEntryColour(String entryUid) {
    return Optional.empty();
  }

  default void writeEntryColour(String entryUid, String colour) {
    // no-op default
  }

  default void clearEntryColour(String entryUid) {
    // no-op default
  }

  /**
   * Planning-Feature #6 hook: the user's focal day — the date the
   * UI should keep visible when the view mode switches. Default
   * impl returns {@code fallback} so external stores that don't
   * persist this opt in transparently.
   *
   * <p>The Vaadin-session impl ({@code VaadinSessionCalendarStateStore})
   * persists the value across navigations so a user who jumped to
   * 2026-09-15 in Week-view and then switched to Month-view lands
   * in September, not on today's month.
   */
  default java.time.LocalDate readFocalDay(java.time.LocalDate fallback) {
    return fallback;
  }

  default void writeFocalDay(java.time.LocalDate day) {
    // no-op default
  }
}
