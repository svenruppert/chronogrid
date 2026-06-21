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

import com.svenruppert.chronogrid.client.RemoteEvent;
import com.svenruppert.chronogrid.mapping.EntryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.vaadin.stefan.fullcalendar.Entry;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG #2 — iCloud's native UI strips the RFC-7986 {@code COLOR}
 * property when it round-trips an edited event, because its data
 * model has no per-event colour concept. Per RFC 5545 §3.8.8.2
 * implementations must preserve unknown {@code X-} properties, so
 * writing the colour twice — standard {@code COLOR} + sidechannel
 * {@code X-CHRONOGRID-COLOR} — gives cross-provider durability.
 *
 * <p>This test class pins the contract:
 * <ul>
 *   <li>Write path emits both properties when {@code CUSTOM_ENTRY_COLOR}
 *       is set.</li>
 *   <li>Read path prefers {@code COLOR}.</li>
 *   <li>Read path falls back to {@code X-CHRONOGRID-COLOR} when
 *       {@code COLOR} is missing (the iCloud-edited-event case).</li>
 *   <li>Without either property the entry has no custom colour
 *       (uniform with calendar default).</li>
 * </ul>
 */
@DisplayName("EntryMapper — per-event COLOR + X-CHRONOGRID-COLOR sidechannel (BUG #2)")
class EntryMapperColourSidechannelTest {

  @Test
  @DisplayName("write emits BOTH COLOR and X-CHRONOGRID-COLOR when entry has a colour")
  void writeEmitsBoth() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("colour-uid");
    entry.setTitle("With colour");
    entry.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 10, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 11, 0));
    entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, "#ff0000");

    String body = mapper.toICalendarText(entry);

    assertTrue(body.contains("COLOR:#ff0000"),
        "RFC-7986 COLOR must be written for compliance; got:\n" + body);
    assertTrue(body.contains("X-CHRONOGRID-COLOR:#ff0000"),
        "Sidechannel X-CHRONOGRID-COLOR must be written so the value "
            + "survives iCloud's strip-on-edit; got:\n" + body);
  }

  @Test
  @DisplayName("read prefers COLOR when both are present")
  void readPrefersStandardColor() {
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:both-uid
        SUMMARY:Both properties
        DTSTART:20260614T100000Z
        DTEND:20260614T110000Z
        COLOR:#00ff00
        X-CHRONOGRID-COLOR:#ff0000
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/both.ics"), "\"e1\"", ical));

    assertEquals("#00ff00",
        entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR),
        "When both properties are present the standard COLOR wins — "
            + "it's the authoritative value and the sidechannel is just "
            + "for survival across providers.");
  }

  @Test
  @DisplayName("read falls back to X-CHRONOGRID-COLOR when COLOR is missing")
  void readFallsBackToSidechannel() {
    // This simulates the post-iCloud-edit state: iCloud stripped
    // COLOR but preserved the X- property.
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:icloud-edited-uid
        SUMMARY:iCloud-edited event
        DTSTART:20260614T100000Z
        DTEND:20260614T110000Z
        X-CHRONOGRID-COLOR:#ff0000
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/icloud.ics"), "\"e1\"", ical));

    assertEquals("#ff0000",
        entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR),
        "When COLOR is absent the sidechannel takes over so the "
            + "user's pick survives iCloud's native-UI strip.");
  }

  @Test
  @DisplayName("no colour properties → no CUSTOM_ENTRY_COLOR (uniform fallback)")
  void readWithNeitherIsClean() {
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:nocolour-uid
        SUMMARY:Plain event
        DTSTART:20260614T100000Z
        DTEND:20260614T110000Z
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/plain.ics"), "\"e1\"", ical));

    assertNull(
        entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR),
        "Without either COLOR or X-CHRONOGRID-COLOR the entry must "
            + "have no CUSTOM_ENTRY_COLOR, so CalendarService.applyColours "
            + "falls into the uniform-calendar-colour branch.");
  }

  @Test
  @DisplayName("a blank X-CHRONOGRID-COLOR is treated as absent")
  void blankSidechannelIgnored() {
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:blank-uid
        SUMMARY:Blank sidechannel
        DTSTART:20260614T100000Z
        DTEND:20260614T110000Z
        X-CHRONOGRID-COLOR:
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/blank.ics"), "\"e1\"", ical));

    assertNull(
        entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR),
        "Empty / whitespace X-CHRONOGRID-COLOR must not yield "
            + "CUSTOM_ENTRY_COLOR — same forgiving rule as the COLOR path.");
  }
}
