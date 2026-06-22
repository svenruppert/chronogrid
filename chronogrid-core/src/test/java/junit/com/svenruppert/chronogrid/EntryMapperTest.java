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

import com.svenruppert.chronogrid.client.RemoteEvent;
import com.svenruppert.chronogrid.mapping.EntryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNull;
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
  @DisplayName("BUG #14: timed events without explicit TZID get the provider default timezone")
  void timedEventGetsProviderDefaultTzid() {
    // Stub provider that pins a deterministic timezone so the
    // test is not host-dependent.
    com.svenruppert.chronogrid.provider.CalDavProviderProfile berlin =
        new com.svenruppert.chronogrid.provider.CalDavProviderProfile() {
          @Override
          public String id() {
            return "berlin-stub";
          }

          @Override
          public boolean matches(URI uri) {
            return false;
          }

          @Override
          public String formatColor(String hex) {
            return hex;
          }

          @Override
          public boolean writeDescriptionMarker() {
            return false;
          }

          @Override
          public java.time.ZoneId defaultTimezone() {
            return java.time.ZoneId.of("Europe/Berlin");
          }
        };

    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("tz-uid");
    entry.setTitle("Berlin event");
    entry.setStart(LocalDateTime.of(2026, Month.JUNE, 11, 8, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JUNE, 11, 13, 0));
    entry.setAllDay(false);

    String body = mapper.toICalendarText(entry, berlin);

    // Biweekly may emit either `TZID=Europe/Berlin` or
    // `TZID=/Europe/Berlin` depending on its internal handling
    // — accept both.
    assertTrue(body.contains("TZID=Europe/Berlin")
        || body.contains("TZID=/Europe/Berlin"),
        "Timed event without explicit CUSTOM_TZID must inherit the "
            + "provider's defaultTimezone() and emit a TZID parameter; got:\n"
            + body);
  }

  @Test
  @DisplayName("BUG #14: all-day events do NOT get a TZID (preserves DATE-only semantics)")
  void allDayEventNoTzid() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("allday-uid");
    entry.setTitle("Sven on PTO");
    entry.setStart(LocalDateTime.of(2026, Month.JULY, 20, 0, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JULY, 25, 0, 0));
    entry.setAllDay(true);

    String body = mapper.toICalendarText(entry,
        new com.svenruppert.chronogrid.provider.GenericProvider());

    assertFalse(body.contains("TZID="),
        "All-day events use VALUE=DATE; emitting a TZID would be a "
            + "spec violation; got:\n" + body);
  }

  @Test
  @DisplayName("BUG #11: explicit setAllDay(false) produces a DATE-TIME DTSTART, not DATE")
  void timedEventEmitsDateTimeNotDate() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("timed-uid");
    entry.setTitle("Zeitslot");
    entry.setStart(LocalDateTime.of(2026, Month.JUNE, 11, 8, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JUNE, 11, 13, 0));
    entry.setAllDay(false);

    String body = mapper.toICalendarText(entry);

    // The smoking gun for BUG #11: when isAllDay is false, the
    // body must contain a DATE-TIME form (T-separator + HHmmss),
    // NOT a DATE-only form (no time component, often with
    // ;VALUE=DATE).
    assertTrue(body.contains("T080000"),
        "Timed events must serialise DTSTART with a time component (T080000); got:\n" + body);
    assertTrue(body.contains("T130000"),
        "Timed events must serialise DTEND with a time component (T130000); got:\n" + body);
    assertFalse(body.contains("VALUE=DATE"),
        "Timed events must NOT emit VALUE=DATE — that's the Nextcloud-renders-"
            + "as-all-day failure mode; got:\n" + body);
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

  @Test
  @DisplayName("VEVENT COLOR (RFC 7986) round-trips through CUSTOM_ENTRY_COLOR")
  void colorRoundtrip() {
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:color-1
        SUMMARY:Painted event
        COLOR:#FFAA00
        DTSTART:20260614T100000Z
        DTEND:20260614T110000Z
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/color.ics"), "\"e1\"", ical));

    assertEquals("#FFAA00",
        entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR),
        "VEVENT COLOR must land on CUSTOM_ENTRY_COLOR for the UI to read");

    String written = mapper.toICalendarText(entry);
    assertTrue(written.contains("COLOR:#FFAA00"),
        "Entry with CUSTOM_ENTRY_COLOR set must serialise COLOR back into iCalendar");
  }

  @Test
  @DisplayName("VEVENT without COLOR yields a null CUSTOM_ENTRY_COLOR")
  void noColorYieldsNull() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/event.ics"), "\"e1\"", SAMPLE_ICAL));

    assertNull(entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR),
        "An entry parsed from a VEVENT without COLOR must NOT carry CUSTOM_ENTRY_COLOR");
  }
}
