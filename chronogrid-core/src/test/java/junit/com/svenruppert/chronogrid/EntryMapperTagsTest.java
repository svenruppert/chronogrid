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
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EntryMapper — VEVENT CATEGORIES ↔ Entry tags roundtrip (Feature #3)")
class EntryMapperTagsTest {

  @Test
  @DisplayName("parses CATEGORIES and normalises casing + whitespace")
  void parsesCategories() {
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:tag-uid
        SUMMARY:With tags
        DTSTART:20260614T100000Z
        DTEND:20260614T110000Z
        CATEGORIES:Work, Client-ACME ,  Deep-Focus
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/tag.ics"), "\"etag-1\"", ical));

    Set<String> tags = EntryMapper.readTags(entry);
    assertEquals(Set.of("work", "client-acme", "deep-focus"), tags);
  }

  @Test
  @DisplayName("writes a single CATEGORIES line with the normalised values")
  void writesCategories() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("round-trip");
    entry.setTitle("Round trip");
    entry.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 10, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 11, 0));
    Set<String> tags = new LinkedHashSet<>();
    tags.add("Work");
    tags.add("client-acme");
    tags.add("");           // dropped by normaliser
    EntryMapper.writeTags(entry, tags);

    String body = mapper.toICalendarText(entry);
    assertTrue(body.contains("CATEGORIES:work,client-acme"),
        "expected CATEGORIES with normalised tags; got:\n" + body);
  }

  @Test
  @DisplayName("clearing tags removes the CATEGORIES line")
  void clearingTagsRemovesCategories() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("clear-uid");
    entry.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 10, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 11, 0));
    EntryMapper.writeTags(entry, Set.of("temp"));
    EntryMapper.writeTags(entry, Set.of());

    String body = mapper.toICalendarText(entry);
    assertFalse(body.contains("CATEGORIES"),
        "expected no CATEGORIES line after clearing; got:\n" + body);
  }

  @Test
  @DisplayName("empty / blank input on writeTags clears the custom property")
  void emptyInputClears() {
    Entry entry = new Entry("blank-uid");
    EntryMapper.writeTags(entry, Set.of("retained"));
    assertEquals(Set.of("retained"), EntryMapper.readTags(entry));

    EntryMapper.writeTags(entry, null);
    assertTrue(EntryMapper.readTags(entry).isEmpty());
  }
}
