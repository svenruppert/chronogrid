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

package junit.com.svenruppert.chronogrid.state;

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
import com.svenruppert.chronogrid.state.CalendarStateStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract-level coverage for {@link CalendarStateStore} via an
 * in-memory stub implementation. Round-trip every key, verify
 * fallbacks when nothing is stored.
 */
@DisplayName("CalendarStateStore — contract: write/read round-trip per key")
class CalendarStateStoreTest {

  private static final URI URI_ONE = URI.create("https://example.test/cal/one/");
  private static final URI URI_TWO = URI.create("https://example.test/cal/two/");

  private static CalendarStateStore newStore() {
    return new InMemoryStore();
  }

  @Test
  @DisplayName("readServers / readSubscriptions yield empty lists by default")
  void listsEmptyByDefault() {
    CalendarStateStore store = newStore();
    assertEquals(List.of(), store.readServers());
    assertEquals(List.of(), store.readSubscriptions());
  }

  @Test
  @DisplayName("writeServers persists exactly what was written")
  void serversRoundTrip() {
    CalendarStateStore store = newStore();
    CalDavServerConnection server = CalDavServerConnection.create(
        "Personal", URI_ONE, "alice", "secret");
    store.writeServers(List.of(server));
    assertEquals(List.of(server), store.readServers());
  }

  @Test
  @DisplayName("writeSubscriptions persists exactly what was written")
  void subscriptionsRoundTrip() {
    CalendarStateStore store = newStore();
    CalendarSubscription sub = new CalendarSubscription(URI_TWO,
        "Family", "#FF0000", true, "server-id");
    store.writeSubscriptions(List.of(sub));
    assertEquals(List.of(sub), store.readSubscriptions());
  }

  @Test
  @DisplayName("readNDays returns the fallback when nothing is written")
  void nDaysFallback() {
    CalendarStateStore store = newStore();
    assertEquals(7, store.readNDays(7));
    assertEquals(14, store.readNDays(14));
  }

  @Test
  @DisplayName("writeNDays persists the value; subsequent read returns it")
  void nDaysRoundTrip() {
    CalendarStateStore store = newStore();
    store.writeNDays(11);
    assertEquals(11, store.readNDays(7));
  }

  @Test
  @DisplayName("writeServers with an empty list yields an empty read")
  void emptyServersStaysEmpty() {
    CalendarStateStore store = newStore();
    store.writeServers(List.of());
    assertTrue(store.readServers().isEmpty());
  }

  @Test
  @DisplayName("Planning-Feature #6: default readFocalDay returns the supplied fallback")
  void focalDayDefaultReturnsFallback() {
    // A bare-minimum impl that doesn't override readFocalDay /
    // writeFocalDay should still answer reads with the fallback — the
    // contract is opt-in for the persistence behaviour.
    CalendarStateStore bare = new CalendarStateStore() {
      @Override public List<CalDavServerConnection> readServers() {
        return List.of();
      }
      @Override public void writeServers(List<CalDavServerConnection> servers) { }
      @Override public List<CalendarSubscription> readSubscriptions() {
        return List.of();
      }
      @Override public void writeSubscriptions(List<CalendarSubscription> subs) { }
      @Override public int readNDays(int fallback) {
        return fallback;
      }
      @Override public void writeNDays(int n) { }
    };
    LocalDate fallback = LocalDate.of(2026, 9, 15);
    assertEquals(fallback, bare.readFocalDay(fallback));
    // The default write impl is a no-op — must not throw.
    bare.writeFocalDay(LocalDate.of(2026, 12, 1));
    assertEquals(fallback, bare.readFocalDay(fallback));
  }

  @Test
  @DisplayName("Planning-Feature #6: an impl that overrides focal-day round-trips the value")
  void focalDayRoundTrip() {
    CalendarStateStore store = newStore();
    LocalDate target = LocalDate.of(2026, 9, 15);
    store.writeFocalDay(target);
    LocalDate fallback = LocalDate.of(2026, 1, 1);
    assertEquals(target, store.readFocalDay(fallback));
  }

  // ── in-memory fixture (one of two reference impls for the contract) ──

  private static final class InMemoryStore implements CalendarStateStore {
    private List<CalDavServerConnection> servers = List.of();
    private List<CalendarSubscription> subscriptions = List.of();
    private Integer nDays;
    private LocalDate focalDay;

    @Override public List<CalDavServerConnection> readServers() {
      return servers;
    }
    @Override public void writeServers(List<CalDavServerConnection> v) {
      this.servers = List.copyOf(v);
    }
    @Override public List<CalendarSubscription> readSubscriptions() {
      return subscriptions;
    }
    @Override public void writeSubscriptions(List<CalendarSubscription> v) {
      this.subscriptions = List.copyOf(v);
    }
    @Override public int readNDays(int fallback) {
      return nDays == null ? fallback : nDays;
    }
    @Override public void writeNDays(int n) {
      this.nDays = n;
    }
    @Override public LocalDate readFocalDay(LocalDate fallback) {
      return focalDay == null ? fallback : focalDay;
    }
    @Override public void writeFocalDay(LocalDate day) {
      this.focalDay = day;
    }
  }
}
