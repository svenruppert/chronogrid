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

import com.svenruppert.flow.calendar.client.RemoteEvent;
import com.svenruppert.flow.calendar.mapping.EntryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.vaadin.stefan.fullcalendar.Entry;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EntryMapper — VEVENT ↔ FullCalendar Entry roundtrip")
class EntryMapperTest {

  private static final String SAMPLE_ICAL = """
      BEGIN:VCALENDAR
      VERSION:2.0
      PRODID:-//flow-template//test//EN
      BEGIN:VEVENT
      UID:demo-uid-1
      SUMMARY:Demo summary
      DESCRIPTION:Demo description
      DTSTART:20260614T100000Z
      DTEND:20260614T110000Z
      END:VEVENT
      END:VCALENDAR
      """;

  @Test
  @DisplayName("parses summary / description / UID from VEVENT into Entry")
  void parsesVEvent() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    RemoteEvent remote = new RemoteEvent(
        URI.create("http://host/cal/demo.ics"),
        "\"etag-1\"",
        SAMPLE_ICAL);

    Entry entry = mapper.toEntry(remote);

    assertEquals("demo-uid-1", entry.getId());
    assertEquals("Demo summary", entry.getTitle());
    assertEquals("Demo description", entry.getDescription());
    assertEquals(LocalDateTime.of(2026, Month.JUNE, 14, 10, 0), entry.getStart());
    assertEquals(LocalDateTime.of(2026, Month.JUNE, 14, 11, 0), entry.getEnd());
    assertEquals("\"etag-1\"", EntryMapper.readEtag(entry).orElseThrow());
    assertEquals(URI.create("http://host/cal/demo.ics"),
        EntryMapper.readHref(entry).orElseThrow());
  }

  @Test
  @DisplayName("writes UID/SUMMARY/DESCRIPTION/DTSTART/DTEND back into iCalendar")
  void writesVEvent() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("round-trip-uid");
    entry.setTitle("Round trip");
    entry.setDescription("with body");
    entry.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 10, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 11, 0));

    String body = mapper.toICalendarText(entry);

    assertNotNull(body);
    assertTrue(body.contains("UID:round-trip-uid"));
    assertTrue(body.contains("SUMMARY:Round trip"));
    assertTrue(body.contains("DESCRIPTION:with body"));
    assertTrue(body.contains("DTSTART"));
    assertTrue(body.contains("DTEND"));
  }

  @Test
  @DisplayName("a fresh Entry has neither ETag nor href custom properties")
  void freshEntryHasNoCalDavProperties() {
    Entry entry = new Entry("fresh");
    assertFalse(EntryMapper.readEtag(entry).isPresent());
    assertFalse(EntryMapper.readHref(entry).isPresent());
  }

  @Test
  @DisplayName("all-day events (DATE-only DTSTART) round-trip as allDay=true")
  void allDayRoundtrip() {
    String allDayIcal = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:allday-1
        SUMMARY:Sven on PTO
        DTSTART;VALUE=DATE:20260720
        DTEND;VALUE=DATE:20260725
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/allday.ics"), "\"e1\"", allDayIcal));

    assertTrue(Boolean.TRUE.equals(entry.isAllDay()),
        "VEVENT with DATE-only DTSTART must map to entry.allDay=true");

    String written = mapper.toICalendarText(entry);
    assertTrue(written.contains("VALUE=DATE"),
        "all-day Entry written back must serialise DTSTART as VALUE=DATE");
  }

  @Test
  @DisplayName("LOCATION + URL round-trip through Entry custom properties")
  void locationAndUrlRoundtrip() {
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:loc-1
        SUMMARY:Coffee
        LOCATION:Café am Park
        URL:https://example.com/coffee
        DTSTART:20260614T100000Z
        DTEND:20260614T110000Z
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/loc.ics"), "\"e1\"", ical));

    assertEquals("Café am Park", entry.getCustomProperty(EntryMapper.CUSTOM_LOCATION));
    assertEquals("https://example.com/coffee", entry.getCustomProperty(EntryMapper.CUSTOM_URL));

    String written = mapper.toICalendarText(entry);
    assertTrue(written.contains("LOCATION:Café am Park"));
    assertTrue(written.contains("URL:https://example.com/coffee"));
  }
}
