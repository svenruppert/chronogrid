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

package junit.com.svenruppert.chronogrid;

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

import com.svenruppert.caldav.testsupport.CalDavFixture;
import com.svenruppert.caldav.testsupport.IcalFixtures;
import com.svenruppert.chronogrid.client.CalDavClient;
import com.svenruppert.chronogrid.client.CalDavError;
import com.svenruppert.chronogrid.client.CalDavErrors;
import com.svenruppert.chronogrid.service.CalendarService;
import com.svenruppert.functional.result.Result;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.vaadin.stefan.fullcalendar.Entry;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CalendarService — Result-bearing API")
class CalendarServiceResultTest {

  private static CalDavFixture fixture;
  private static CalendarService liveService;
  private static CalendarService deadService;

  @BeforeAll
  static void start() throws IOException {
    fixture = CalDavFixture.startWithCalendars("personal");
    URI collection = fixture.baseUri().resolve("/calendars/personal/");
    liveService = new CalendarService(new CalDavClient(collection), ZoneOffset.UTC);
    // Points at a closed port — every call must fail at the wire.
    URI dead = URI.create("http://127.0.0.1:1/calendars/none/");
    deadService = new CalendarService(new CalDavClient(dead), ZoneOffset.UTC);
  }

  @AfterAll
  static void stop() {
    if (fixture != null) fixture.close();
  }

  @Test
  @DisplayName("findInRangeAsResult against a working backend → success")
  void liveServiceFindRangeIsSuccess() {
    Result<Stream<Entry>, CalDavError> result = liveService.findInRangeAsResult(
        LocalDateTime.of(2026, Month.JUNE, 1, 0, 0),
        LocalDateTime.of(2026, Month.JULY, 1, 0, 0));
    assertTrue(result.isSuccess(),
        "live service must return Result.success");
  }

  @Test
  @DisplayName("findInRangeAsResult against an unreachable backend → failure(NETWORK)")
  void deadServiceFindRangeIsNetworkFailure() {
    Result<Stream<Entry>, CalDavError> result = deadService.findInRangeAsResult(
        LocalDateTime.of(2026, Month.JUNE, 1, 0, 0),
        LocalDateTime.of(2026, Month.JULY, 1, 0, 0));
    assertTrue(result.isFailure(), "unreachable service must yield failure");
    CalDavError err = result.fold(s -> null, e -> e);
    assertEquals(CalDavErrors.Kind.NETWORK, err.kind(),
        "ConnectException must classify as NETWORK");
  }

  @Test
  @DisplayName("BACKLOG-#9 follow-up: findInRangeWithStatus survives one failing client and returns the surviving entries + a per-failure CalDavError")
  void mixedClientsYieldPartialSuccess() throws Exception {
    // Seed one event on the live testbench so the live client has
    // something to return — otherwise the test couldn't distinguish
    // "live succeeded with 0 entries" from "live also failed".
    String uid = "partial-fail-" + UUID.randomUUID();
    fixture.putEvent("personal", uid + ".ics",
        com.svenruppert.caldav.testsupport.IcalFixtures.event(uid)
            .summary("survivor")
            .starts(java.time.Instant.parse("2026-06-15T10:00:00Z"))
            .ends(java.time.Instant.parse("2026-06-15T11:00:00Z")));

    URI liveUri = fixture.baseUri().resolve("/calendars/personal/");
    URI deadUri = URI.create("http://127.0.0.1:1/calendars/none/");
    CalDavClient live = new CalDavClient(liveUri);
    CalDavClient dead = new CalDavClient(deadUri);
    CalendarService mixed = new CalendarService(
        live, java.util.List.of(live, dead), java.time.ZoneOffset.UTC);

    com.svenruppert.chronogrid.service.FanOutOutcome outcome =
        mixed.findInRangeWithStatus(
            LocalDateTime.of(2026, Month.JUNE, 1, 0, 0),
            LocalDateTime.of(2026, Month.JULY, 1, 0, 0));

    assertTrue(outcome.hasFailures(),
        "dead client at port 1 must surface as a failure entry");
    assertEquals(1, outcome.failures().size(),
        "exactly one client failed");
    assertEquals(CalDavErrors.Kind.NETWORK,
        outcome.failures().get(deadUri).kind(),
        "ConnectException at port 1 must classify as NETWORK");
    // The live client's entry must survive the dead client's failure
    // — partial-failure isolation in action.
    assertTrue(outcome.entries().stream().anyMatch(e -> uid.equals(e.getId())),
        "live client's seeded event must still surface despite the dead client; "
            + "got " + outcome.entries().size() + " entries");
  }

  @Test
  @DisplayName("BACKLOG-#9 follow-up: legacy findInRange still throws on any client failure (backwards-compat contract)")
  void legacyFindInRangeStillThrowsOnPartialFailure() throws Exception {
    URI liveUri = fixture.baseUri().resolve("/calendars/personal/");
    URI deadUri = URI.create("http://127.0.0.1:1/calendars/none/");
    CalDavClient live = new CalDavClient(liveUri);
    CalDavClient dead = new CalDavClient(deadUri);
    CalendarService mixed = new CalendarService(
        live, java.util.List.of(live, dead), java.time.ZoneOffset.UTC);

    org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
        () -> mixed.findInRange(
            LocalDateTime.of(2026, Month.JUNE, 1, 0, 0),
            LocalDateTime.of(2026, Month.JULY, 1, 0, 0)).count(),
        "legacy findInRange must still throw on any client failure — "
            + "existing callers depend on that contract");
  }

  @Test
  @DisplayName("saveAsResult new entry → success, refetch returns same UID")
  void saveNewIsSuccess() throws Exception {
    String uid = "result-save-" + UUID.randomUUID();
    fixture.putEvent("personal", uid + ".ics",
        IcalFixtures.event(uid).summary("seed")
            .starts(Instant.parse("2026-06-14T10:00:00Z"))
            .ends(Instant.parse("2026-06-14T11:00:00Z")));

    Result<java.util.Optional<Entry>, CalDavError> result =
        liveService.findByIdAsResult(uid);
    assertTrue(result.isSuccess());
    assertEquals(uid, result.fold(opt -> opt.orElseThrow().getId(), e -> null));
  }
}
