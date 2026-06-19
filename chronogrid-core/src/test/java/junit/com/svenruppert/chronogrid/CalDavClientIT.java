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
import com.svenruppert.chronogrid.client.RemoteEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CalDavClient — PUT / GET / DELETE / REPORT against caldav-testbench")
class CalDavClientIT {

  private static CalDavFixture fixture;
  private static CalDavClient client;

  private static final Instant WINDOW_START = Instant.parse("2026-06-01T00:00:00Z");
  private static final Instant WINDOW_END = Instant.parse("2026-07-01T00:00:00Z");

  @BeforeAll
  static void startTestbench() throws IOException {
    fixture = CalDavFixture.startWithCalendars("personal");
    URI collection = fixture.baseUri().resolve("/calendars/personal/");
    client = new CalDavClient(collection);
  }

  @AfterAll
  static void stopTestbench() {
    if (fixture != null) fixture.close();
  }

  @BeforeEach
  void resetInteractions() {
    fixture.interactions().clear();
  }

  @Test
  @DisplayName("PUT new returns 201 + ETag, GET retrieves the body")
  void putNewThenGet() {
    String uid = "client-it-" + UUID.randomUUID();
    String body = sampleIcal(uid, "Client IT");

    String etag = client.putNew(client.eventUri(uid), body);
    assertFalse(etag.isBlank(), "PUT must return a non-empty ETag");

    RemoteEvent fetched = client.get(uid);
    assertEquals(etag, fetched.etag());
    assertTrue(fetched.iCalBody().contains("UID:" + uid));
    assertTrue(fetched.iCalBody().contains("SUMMARY:Client IT"));
  }

  @Test
  @DisplayName("REPORT calendar-query returns inserted events in window")
  void reportInsideRange() {
    String uid = "report-" + UUID.randomUUID();
    client.putNew(client.eventUri(uid), sampleIcal(uid, "Reported"));

    List<RemoteEvent> events = client.findInRange(WINDOW_START, WINDOW_END);
    assertTrue(events.stream().anyMatch(e -> e.iCalBody().contains("UID:" + uid)),
        "REPORT must surface the freshly inserted event");
  }

  @Test
  @DisplayName("PUT with stale If-Match raises ConcurrentModificationException")
  void putWithStaleEtagConflicts() {
    String uid = "conflict-" + UUID.randomUUID();
    URI target = client.eventUri(uid);
    String firstEtag = client.putNew(target, sampleIcal(uid, "First"));

    String secondEtag = client.putUpdate(target, sampleIcal(uid, "Second"), firstEtag);
    assertFalse(secondEtag.equals(firstEtag), "ETag must change on update");

    assertThrows(ConcurrentModificationException.class,
        () -> client.putUpdate(target, sampleIcal(uid, "Third"), firstEtag),
        "Second update with original ETag must collide");
  }

  @Test
  @DisplayName("DELETE removes the resource and a subsequent GET is 404")
  void deleteRemovesResource() {
    String uid = "delete-" + UUID.randomUUID();
    URI target = client.eventUri(uid);
    String etag = client.putNew(target, sampleIcal(uid, "Doomed"));

    client.delete(target, etag);

    assertThrows(java.util.NoSuchElementException.class, () -> client.get(uid));
  }

  @Test
  @DisplayName("InteractionLog records the REPORT request the client issued")
  void reportShowsUpInInteractionLog() {
    client.findInRange(WINDOW_START, WINDOW_END);

    boolean sawReport = fixture.interactions().entries().stream()
        .anyMatch(entry -> "REPORT".equals(entry.method()));
    assertTrue(sawReport, "REPORT must be visible in the InteractionLog");
  }

  private static String sampleIcal(String uid, String summary) {
    return IcalFixtures.event(uid)
        .summary(summary)
        .starts(Instant.parse("2026-06-14T10:00:00Z"))
        .ends(Instant.parse("2026-06-14T11:00:00Z"))
        .build();
  }
}
