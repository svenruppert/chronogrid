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

import com.svenruppert.chronogrid.state.VaadinSessionCalendarStateStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke coverage for {@link VaadinSessionCalendarStateStore} in
 * isolation — no live Vaadin session. The session-backed integration
 * path (writes from the view actually land on the session, and reads
 * from external test code observe them) is covered by
 * {@code ChronoGridBrowserlessTest} which boots a mock UI.
 */
@DisplayName("VaadinSessionCalendarStateStore — no-session fallback behaviour")
class VaadinSessionCalendarStateStoreTest {

  @Test
  @DisplayName("readServers / readSubscriptions yield empty lists without a session")
  void readListsNoSession() {
    VaadinSessionCalendarStateStore store = new VaadinSessionCalendarStateStore();
    assertEquals(List.of(), store.readServers());
    assertEquals(List.of(), store.readSubscriptions());
  }

  @Test
  @DisplayName("readNDays falls back to the supplied default without a session")
  void readNDaysNoSession() {
    VaadinSessionCalendarStateStore store = new VaadinSessionCalendarStateStore();
    assertEquals(7, store.readNDays(7));
    assertEquals(21, store.readNDays(21));
  }

  @Test
  @DisplayName("writes are silent no-ops without a session (must not throw)")
  void writesNoSession() {
    VaadinSessionCalendarStateStore store = new VaadinSessionCalendarStateStore();
    // Each of these would NPE if it tried to dereference null session;
    // the impl must check for null and skip the write.
    store.writeServers(List.of());
    store.writeSubscriptions(List.of());
    store.writeNDays(11);
    // No exception → contract met. Verify reads stay empty.
    assertEquals(List.of(), store.readServers());
    assertEquals(List.of(), store.readSubscriptions());
    assertEquals(99, store.readNDays(99));
  }

  @Test
  @DisplayName("public SESSION_KEY constants match the expected attribute names")
  void sessionKeyConstants() {
    // Critical: the BrowserlessTest fixtures poke these keys directly.
    // Drift would silently desync test setup from store reads.
    assertEquals("calendar.subscriptions",
        VaadinSessionCalendarStateStore.SESSION_KEY_SUBSCRIPTIONS);
    assertEquals("calendar.servers",
        VaadinSessionCalendarStateStore.SESSION_KEY_SERVERS);
    assertEquals("calendar.nav.nDays",
        VaadinSessionCalendarStateStore.SESSION_KEY_NDAYS);
    assertEquals("calendar.nav.focalDay",
        VaadinSessionCalendarStateStore.SESSION_KEY_FOCAL_DAY);
  }

  @Test
  @DisplayName("Planning-Feature #6: readFocalDay falls back to the supplied default without a session")
  void readFocalDayNoSession() {
    VaadinSessionCalendarStateStore store = new VaadinSessionCalendarStateStore();
    LocalDate fallback = LocalDate.of(2026, 9, 15);
    assertEquals(fallback, store.readFocalDay(fallback));
  }

  @Test
  @DisplayName("Planning-Feature #6: writeFocalDay is a silent no-op without a session (must not throw)")
  void writeFocalDayNoSession() {
    VaadinSessionCalendarStateStore store = new VaadinSessionCalendarStateStore();
    store.writeFocalDay(LocalDate.of(2026, 9, 15));
    store.writeFocalDay(null);
    // Read still returns the supplied fallback — no session means no
    // round-trip is possible.
    LocalDate fallback = LocalDate.of(2026, 7, 4);
    assertEquals(fallback, store.readFocalDay(fallback));
  }
}
