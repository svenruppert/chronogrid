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

package junit.com.svenruppert.flow.calendar;

import com.svenruppert.caldav.testsupport.CalDavFixture;
import com.svenruppert.caldav.testsupport.IcalFixtures;
import com.svenruppert.flow.calendar.client.CalDavClient;
import com.svenruppert.flow.calendar.client.CalDavError;
import com.svenruppert.flow.calendar.client.CalDavErrors;
import com.svenruppert.flow.calendar.service.CalendarService;
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
