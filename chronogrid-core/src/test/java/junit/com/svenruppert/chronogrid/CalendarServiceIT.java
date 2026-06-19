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
import com.svenruppert.chronogrid.mapping.EntryMapper;
import com.svenruppert.chronogrid.service.CalendarService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CalendarService — read/write façade against caldav-testbench")
class CalendarServiceIT {

  private static CalDavFixture fixture;
  private static CalendarService service;
  private static URI collection;

  @BeforeAll
  static void startTestbench() throws IOException {
    fixture = CalDavFixture.startWithCalendars("personal");
    collection = fixture.baseUri().resolve("/calendars/personal/");
    service = new CalendarService(new CalDavClient(collection), ZoneOffset.UTC);
  }

  @AfterAll
  static void stopTestbench() {
    if (fixture != null) fixture.close();
  }

  @Test
  @DisplayName("seeded event is visible through findInRange")
  void seededEventVisibleInRange() throws Exception {
    String uid = "seeded-" + UUID.randomUUID();
    fixture.putEvent("personal", uid + ".ics",
        IcalFixtures.event(uid)
            .summary("Seeded")
            .starts(Instant.parse("2026-06-14T10:00:00Z"))
            .ends(Instant.parse("2026-06-14T11:00:00Z")));

    LocalDateTime from = LocalDateTime.of(2026, Month.JUNE, 1, 0, 0);
    LocalDateTime to = LocalDateTime.of(2026, Month.JULY, 1, 0, 0);

    List<Entry> entries = service.findInRange(from, to).toList();
    assertTrue(entries.stream().anyMatch(e -> uid.equals(e.getId())),
        "Expected the seeded UID to appear in CalendarService.findInRange");
  }

  @Test
  @DisplayName("save(new) → findById round-trips the entry with fresh ETag/href")
  void saveNewThenFindById() {
    String uid = "save-" + UUID.randomUUID();
    Entry draft = new Entry(uid);
    draft.setTitle("Hand-written");
    draft.setDescription("via CalendarService.save");
    draft.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 12, 0));
    draft.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 13, 0));

    Entry persisted = service.save(draft);
    assertTrue(EntryMapper.readEtag(persisted).isPresent(),
        "save must stamp the persisted Entry with an ETag");
    assertTrue(EntryMapper.readHref(persisted).isPresent(),
        "save must stamp the persisted Entry with a href");

    Optional<Entry> roundTripped = service.findById(uid);
    assertTrue(roundTripped.isPresent());
    assertEquals("Hand-written", roundTripped.get().getTitle());
  }

  @Test
  @DisplayName("save(update) replaces title and yields a new ETag")
  void saveUpdateRotatesEtag() {
    String uid = "update-" + UUID.randomUUID();
    Entry draft = new Entry(uid);
    draft.setTitle("v1");
    draft.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 14, 0));
    draft.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 15, 0));
    Entry v1 = service.save(draft);
    String etagV1 = EntryMapper.readEtag(v1).orElseThrow();

    v1.setTitle("v2");
    Entry v2 = service.save(v1);
    String etagV2 = EntryMapper.readEtag(v2).orElseThrow();
    assertNotEquals(etagV1, etagV2, "ETag must rotate on update");

    Entry refetched = service.findById(uid).orElseThrow();
    assertEquals("v2", refetched.getTitle());
  }

  @Test
  @DisplayName("delete removes the entry; findById returns empty afterwards")
  void deleteRemovesEntry() {
    String uid = "doomed-" + UUID.randomUUID();
    Entry draft = new Entry(uid);
    draft.setTitle("Doomed");
    draft.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 16, 0));
    draft.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 17, 0));
    Entry persisted = service.save(draft);

    service.delete(persisted);

    assertFalse(service.findById(uid).isPresent());
  }
}
