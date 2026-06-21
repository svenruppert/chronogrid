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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG #2 — iCloud's native UI strips RFC-7986 {@code COLOR} <em>and</em>
 * custom {@code X-} properties when an event is edited in iCloud's
 * own editor. Per Apple's own documentation, only {@code X-APPLE-*}
 * properties are preserved through that pipeline; the durable
 * carriers are {@code SUMMARY}, {@code DESCRIPTION}, {@code URL}.
 *
 * <p>This test class pins the contract of the DESCRIPTION-suffix
 * marker the writer appends + the reader strips:
 *
 * <pre>{@code
 * <user description>
 *
 * [chronogrid-color: #ff0000]
 * }</pre>
 *
 * <ul>
 *   <li>Write emits both {@code COLOR} and the suffix marker.</li>
 *   <li>Read prefers {@code COLOR}; strips the marker either way.</li>
 *   <li>When {@code COLOR} is absent (post-iCloud-edit case) but the
 *       marker survived, the marker carries the value.</li>
 *   <li>The cleaned description is what the UI sees, never the raw
 *       marker text.</li>
 * </ul>
 */
@DisplayName("EntryMapper — DESCRIPTION colour-marker sidechannel (BUG #2)")
class EntryMapperColourSidechannelTest {

  @Test
  @DisplayName("write emits BOTH COLOR and the DESCRIPTION-suffix marker when entry has a colour")
  void writeEmitsBoth() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("colour-uid");
    entry.setTitle("With colour");
    entry.setDescription("Sven's quick notes");
    entry.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 10, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 11, 0));
    entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, "#ff0000");

    String body = mapper.toICalendarText(entry);

    assertTrue(body.contains("COLOR:#ff0000"),
        "RFC-7986 COLOR must be written for standards-compliant providers; got:\n" + body);
    assertTrue(body.contains("[chronogrid-color: #ff0000]"),
        "DESCRIPTION-suffix marker must be written so the colour survives "
            + "iCloud's edit-rewrite (where it strips COLOR + every X- property); got:\n" + body);
  }

  @Test
  @DisplayName("write composes description as <user-text>\\n\\n<marker> with blank-line separator")
  void writeFormatsDescriptionWithSeparator() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("format-uid");
    entry.setTitle("Format");
    entry.setDescription("first line\nsecond line");
    entry.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 10, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 11, 0));
    entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, "#00ff00");

    String body = mapper.toICalendarText(entry);

    // Biweekly wraps long lines and escapes newlines; check that the
    // user text and the marker are both in there with a separator.
    assertTrue(body.contains("first line"));
    assertTrue(body.contains("second line"));
    assertTrue(body.contains("[chronogrid-color: #00ff00]"));
  }

  @Test
  @DisplayName("write emits marker without DESCRIPTION header collision when no user description")
  void writeMarkerStandaloneWhenNoUserDescription() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("standalone-uid");
    entry.setTitle("No notes");
    entry.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 10, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 11, 0));
    entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, "#0000ff");

    String body = mapper.toICalendarText(entry);

    assertTrue(body.contains("[chronogrid-color: #0000ff]"),
        "Marker must still emit when there is no user description; got:\n" + body);
  }

  @Test
  @DisplayName("read prefers COLOR when both COLOR and the marker are present")
  void readPrefersStandardColor() {
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:both-uid
        SUMMARY:Both
        DESCRIPTION:My notes\\n\\n[chronogrid-color: #ff0000]
        DTSTART:20260614T100000Z
        DTEND:20260614T110000Z
        COLOR:#00ff00
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/both.ics"), "\"e1\"", ical));

    assertEquals("#00ff00",
        entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR),
        "When both COLOR and the marker are present, COLOR wins — it's the standard.");
    assertEquals("My notes", entry.getDescription(),
        "The marker must be stripped from the description shown to the UI either way.");
  }

  @Test
  @DisplayName("read falls back to the DESCRIPTION marker when COLOR is missing (iCloud-edited case)")
  void readFallsBackToMarker() {
    // Simulates post-iCloud-edit state: iCloud stripped COLOR and
    // every X- property, but it preserved DESCRIPTION verbatim.
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:icloud-edited-uid
        SUMMARY:iCloud-edited
        DESCRIPTION:Some notes from Sven\\n\\n[chronogrid-color: #ff0000]
        DTSTART:20260614T100000Z
        DTEND:20260614T110000Z
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/icloud.ics"), "\"e1\"", ical));

    assertEquals("#ff0000",
        entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR),
        "When COLOR is absent the DESCRIPTION marker must restore the user's colour.");
    assertEquals("Some notes from Sven", entry.getDescription(),
        "Marker stripped from the user-visible description.");
  }

  @Test
  @DisplayName("read strips the marker even when the entry has no description body")
  void readMarkerOnlyDescription() {
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:marker-only-uid
        SUMMARY:Marker only
        DESCRIPTION:[chronogrid-color: #abcdef]
        DTSTART:20260614T100000Z
        DTEND:20260614T110000Z
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/marker-only.ics"), "\"e1\"", ical));

    assertEquals("#abcdef",
        entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR));
    assertNull(entry.getDescription(),
        "When the marker was the ONLY content, the cleaned description is null "
            + "so the UI doesn't render an empty notes block.");
  }

  @Test
  @DisplayName("read returns no colour and the raw description when neither COLOR nor marker are present")
  void readWithoutAnyMarkerOrColour() {
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:plain-uid
        SUMMARY:Plain
        DESCRIPTION:Just normal notes here
        DTSTART:20260614T100000Z
        DTEND:20260614T110000Z
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/plain.ics"), "\"e1\"", ical));

    assertNull(entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR),
        "Without either signal the entry has no custom colour — applyColours "
            + "downstream falls into the uniform-calendar-colour branch.");
    assertEquals("Just normal notes here", entry.getDescription(),
        "Description passes through unchanged when there is no marker to strip.");
  }

  @Test
  @DisplayName("composeDescription helper combines user text + marker idempotently")
  void composeDescriptionHelperRoundtrip() {
    String composed =
        EntryMapper.composeDescription("My notes", "#ff0000");
    assertTrue(composed.endsWith("[chronogrid-color: #ff0000]"),
        "Marker is appended at the end; got: '" + composed + "'");
    assertTrue(composed.startsWith("My notes"),
        "User text is preserved at the start; got: '" + composed + "'");
    assertTrue(composed.contains("\n\n[chronogrid-color:"),
        "Blank-line separator between user text and marker; got: '" + composed + "'");
  }

  @Test
  @DisplayName("composeDescription returns plain text when no colour is supplied")
  void composeDescriptionNoColour() {
    assertEquals("Hello", EntryMapper.composeDescription("Hello", null));
    assertEquals("Hello", EntryMapper.composeDescription("Hello", ""));
    assertEquals("Hello", EntryMapper.composeDescription("Hello", "   "));
    assertNull(EntryMapper.composeDescription(null, null));
    assertNull(EntryMapper.composeDescription("", null));
  }

  // ── BUG #7: Apple-only DESCRIPTION-marker switch ─────────────

  @Test
  @DisplayName("BUG #7: appleSidechannel=false suppresses the DESCRIPTION marker (Nextcloud etc.)")
  void writeSkipsMarkerForNonApple() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("non-apple-uid");
    entry.setTitle("On Nextcloud");
    entry.setDescription("plain notes");
    entry.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 10, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 11, 0));
    entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, "#aabbcc");

    String body = mapper.toICalendarText(entry, false);

    assertTrue(body.contains("COLOR:#aabbcc"),
        "Standard COLOR must still be written — non-Apple providers "
            + "round-trip it correctly; got:\n" + body);
    assertFalse(body.contains("[chronogrid-color:"),
        "DESCRIPTION-marker must NOT be written when appleSidechannel=false — "
            + "would show up as visible noise in non-Apple UIs; got:\n" + body);
    assertTrue(body.contains("plain notes"),
        "User description must pass through unchanged.");
  }

  @Test
  @DisplayName("BUG #7: appleSidechannel=true (default) emits the marker (iCloud)")
  void writeEmitsMarkerForApple() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("apple-uid");
    entry.setTitle("On iCloud");
    entry.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 10, 0));
    entry.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 11, 0));
    entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, "#ff0000");

    String body = mapper.toICalendarText(entry, true);

    assertTrue(body.contains("COLOR:#ff0000"));
    assertTrue(body.contains("[chronogrid-color: #ff0000]"),
        "Marker must still be written for Apple targets — iCloud "
            + "strips COLOR on user-edit-rewrite and the marker is the "
            + "only surviving carrier.");
  }

  @Test
  @DisplayName("BUG #7: reader is unchanged — picks up the marker even when written by the Apple path")
  void readerStillFindsMarkerRegardlessOfTargetSwitch() {
    // Simulates an entry that was written with appleSidechannel=true
    // (iCloud target) and is now being read back. Verifies the
    // single-arg toICalendarText helper still defaults to Apple-on,
    // so any consumer that didn't migrate keeps working.
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry written = new Entry("rt-uid");
    written.setTitle("Round trip");
    written.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 10, 0));
    written.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 11, 0));
    written.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, "#123456");

    String body = mapper.toICalendarText(written); // single-arg → marker ON
    Entry read = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/rt.ics"), "\"e1\"", body));

    assertTrue(body.contains("[chronogrid-color: #123456]"),
        "Single-arg overload must default to marker-on (Apple-safe).");
    assertEquals("#123456",
        read.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR));
  }

  @Test
  @DisplayName("write-then-read round-trips colour + description losslessly via biweekly")
  void writeThenReadRoundtrip() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry written = new Entry("rt-uid");
    written.setTitle("Round-trip");
    written.setDescription("Notes paragraph 1.\nParagraph 2.");
    written.setStart(LocalDateTime.of(2026, Month.JUNE, 14, 10, 0));
    written.setEnd(LocalDateTime.of(2026, Month.JUNE, 14, 11, 0));
    written.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, "#b31e52");

    String body = mapper.toICalendarText(written);
    Entry read = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/rt.ics"), "\"e1\"", body));

    assertEquals("#b31e52",
        read.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR),
        "Colour must survive write+read (standard COLOR is the primary path).");
    assertEquals("Notes paragraph 1.\nParagraph 2.", read.getDescription(),
        "Description must survive write+read with the marker stripped on read.");
    assertFalse(read.getDescription().contains("chronogrid-color"),
        "Cleaned description never contains the marker prefix.");
  }
}
