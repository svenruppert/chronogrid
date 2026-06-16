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
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EntryMapper — TZID preservation across the round-trip")
class EntryMapperTimezoneTest {

  private static final String BERLIN_EVENT = """
      BEGIN:VCALENDAR
      VERSION:2.0
      PRODID:-//flow-template//test//EN
      BEGIN:VEVENT
      UID:tz-berlin-1
      SUMMARY:Standup
      DTSTART;TZID=Europe/Berlin:20260720T100000
      DTEND;TZID=Europe/Berlin:20260720T110000
      END:VEVENT
      END:VCALENDAR
      """;

  @Test
  @DisplayName("read: TZID parameter is captured as caldavTzid custom property")
  void tzidIsCapturedOnRead() {
    Entry entry = new EntryMapper(ZoneOffset.UTC).toEntry(new RemoteEvent(
        URI.create("http://host/cal/tz.ics"), "\"e1\"", BERLIN_EVENT));
    assertEquals("Europe/Berlin",
        entry.getCustomProperty(EntryMapper.CUSTOM_TZID));
  }

  @Test
  @DisplayName("read: 10:00 Berlin renders as 10:00 LocalDateTime (not converted to UTC)")
  void berlinTimeStaysBerlinTimeOnRead() {
    Entry entry = new EntryMapper(ZoneOffset.UTC).toEntry(new RemoteEvent(
        URI.create("http://host/cal/tz.ics"), "\"e1\"", BERLIN_EVENT));
    assertEquals(LocalDateTime.of(2026, Month.JULY, 20, 10, 0), entry.getStart());
    assertEquals(LocalDateTime.of(2026, Month.JULY, 20, 11, 0), entry.getEnd());
  }

  @Test
  @DisplayName("write: TZID parameter is preserved on DTSTART / DTEND")
  void tzidIsPreservedOnWrite() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/tz.ics"), "\"e1\"", BERLIN_EVENT));

    String written = mapper.toICalendarText(entry);
    // Biweekly serialises a global-ID assignment as "TZID=/Europe/Berlin"
    // (RFC 7986 leading-slash form); a VTIMEZONE-backed one as
    // "TZID=Europe/Berlin". Both are valid CalDAV — iCloud accepts both.
    boolean preserved = written.contains("TZID=Europe/Berlin")
        || written.contains("TZID=/Europe/Berlin");
    assertTrue(preserved,
        "round-tripped iCal must carry TZID=Europe/Berlin (with or without "
            + "leading slash); got:\n" + written);
  }

  @Test
  @DisplayName("write: no TZID → behaves as plain UTC, like before")
  void noTzidWritesPlainUtc() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("plain-utc");
    entry.setTitle("Plain");
    entry.setStart(LocalDateTime.of(2026, Month.JULY, 20, 14, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JULY, 20, 15, 0));

    String written = mapper.toICalendarText(entry);
    assertTrue(!written.contains("TZID="),
        "no caldavTzid → no TZID parameter in output");
  }
}
