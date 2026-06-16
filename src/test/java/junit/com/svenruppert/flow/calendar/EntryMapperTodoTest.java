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
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EntryMapper — VTODO support")
class EntryMapperTodoTest {

  private static final String SIMPLE_VTODO = """
      BEGIN:VCALENDAR
      VERSION:2.0
      PRODID:-//flow-template//test//EN
      BEGIN:VTODO
      UID:buy-milk-1
      SUMMARY:Buy milk
      DESCRIPTION:2 liter, oat
      STATUS:NEEDS-ACTION
      DTSTART:20260615T080000Z
      DUE:20260615T200000Z
      END:VTODO
      END:VCALENDAR
      """;

  @Test
  @DisplayName("VTODO is detected and tagged with caldavKind=vtodo")
  void vtodoIsTagged() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/buy-milk.ics"), "\"e1\"", SIMPLE_VTODO));

    assertTrue(EntryMapper.isTodo(entry),
        "VTODO must be tagged with caldavKind=vtodo");
    assertEquals(EntryMapper.KIND_VTODO,
        entry.getCustomProperty(EntryMapper.CUSTOM_KIND));
  }

  @Test
  @DisplayName("VTODO maps SUMMARY / DESCRIPTION / STATUS into Entry")
  void vtodoCarriesCorePropertiesIntoEntry() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/buy-milk.ics"), "\"e1\"", SIMPLE_VTODO));

    assertEquals("Buy milk", entry.getTitle());
    assertEquals("2 liter, oat", entry.getDescription());
    assertEquals("NEEDS-ACTION",
        entry.getCustomProperty(EntryMapper.CUSTOM_TODO_STATUS));
    assertEquals(LocalDateTime.of(2026, Month.JUNE, 15, 8, 0), entry.getStart());
    assertEquals(LocalDateTime.of(2026, Month.JUNE, 15, 20, 0), entry.getEnd());
  }

  @Test
  @DisplayName("VTODO Entry round-trips back to a VTODO iCalendar text")
  void vtodoRoundtrip() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/buy-milk.ics"), "\"e1\"", SIMPLE_VTODO));

    String written = mapper.toICalendarTodoText(entry);

    assertTrue(written.contains("BEGIN:VTODO"), "VTODO write must emit VTODO component");
    assertTrue(written.contains("SUMMARY:Buy milk"));
    assertTrue(written.contains("STATUS:NEEDS-ACTION"));
    assertTrue(written.contains("DUE:"));
    assertFalse(written.contains("BEGIN:VEVENT"),
        "must not emit VEVENT when entry is a VTODO");
  }

  @Test
  @DisplayName("isTodo() is false for a regular VEVENT Entry")
  void isTodoFalseForVevent() {
    String veventIcal = """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:e1
        SUMMARY:Meeting
        DTSTART:20260615T100000Z
        DTEND:20260615T110000Z
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/m.ics"), "\"e1\"", veventIcal));
    assertFalse(EntryMapper.isTodo(entry));
    assertEquals(EntryMapper.KIND_VEVENT,
        entry.getCustomProperty(EntryMapper.CUSTOM_KIND));
  }
}
