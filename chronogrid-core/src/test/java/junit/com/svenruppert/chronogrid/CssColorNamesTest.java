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
import com.svenruppert.chronogrid.mapping.CssColorNames;
import com.svenruppert.chronogrid.mapping.EntryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.vaadin.stefan.fullcalendar.Entry;

import java.net.URI;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * BUG #12 — Nextcloud writes per-event COLOR as CSS3 named tokens
 * (e.g. {@code COLOR:darkkhaki}) rather than as hex literals. The
 * native HTML5 colour picker only accepts hex, so {@link
 * EntryMapper#toEntry} must normalise named tokens to their
 * canonical {@code #rrggbb} form. Unknown / non-standard tokens
 * must pass through unchanged so the round-trip is lossless.
 */
@DisplayName("CssColorNames + EntryMapper named-colour normalisation (BUG #12)")
class CssColorNamesTest {

  @Test
  @DisplayName("CssColorNames.toHex normalises the canonical CSS3 list")
  void normalisesCanonicalColors() {
    assertEquals("#bdb76b", CssColorNames.toHex("darkkhaki"),
        "Real Nextcloud-observed token in BUG #12 → #bdb76b");
    assertEquals("#ff0000", CssColorNames.toHex("red"));
    assertEquals("#000000", CssColorNames.toHex("black"));
    assertEquals("#ffffff", CssColorNames.toHex("white"));
    assertEquals("#808080", CssColorNames.toHex("gray"));
    assertEquals("#663399", CssColorNames.toHex("rebeccapurple"),
        "Modern (CSS4) names also covered");
  }

  @Test
  @DisplayName("CssColorNames.toHex is case-insensitive + whitespace-tolerant")
  void normalisesCaseAndWhitespace() {
    assertEquals("#bdb76b", CssColorNames.toHex("DarkKhaki"));
    assertEquals("#bdb76b", CssColorNames.toHex("DARKKHAKI"));
    assertEquals("#bdb76b", CssColorNames.toHex("  darkkhaki  "));
  }

  @Test
  @DisplayName("CssColorNames.toHex passes hex inputs through unchanged")
  void hexInputsUnchanged() {
    assertEquals("#ff0000", CssColorNames.toHex("#ff0000"));
    assertEquals("#FF0000", CssColorNames.toHex("#FF0000"),
        "Hex casing preserved — we don't lowercase non-named tokens");
  }

  @Test
  @DisplayName("CssColorNames.toHex passes unknown tokens through unchanged")
  void unknownInputsUnchanged() {
    assertEquals("not-a-colour", CssColorNames.toHex("not-a-colour"));
    assertEquals("rgb(255,0,0)", CssColorNames.toHex("rgb(255,0,0)"),
        "rgb() syntax isn't in our named-list; pass-through preserves the value");
  }

  @Test
  @DisplayName("CssColorNames.toHex handles null and empty input gracefully")
  void nullAndEmpty() {
    assertNull(CssColorNames.toHex(null));
    assertEquals("", CssColorNames.toHex(""));
    assertEquals("   ", CssColorNames.toHex("   "));
  }

  @Test
  @DisplayName("EntryMapper.toEntry stamps CUSTOM_ENTRY_COLOR with hex even when COLOR is a named token")
  void entryMapperNormalisesNamedTokenOnRead() {
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:nextcloud-named-color
        SUMMARY:Zeitslot
        DTSTART:20260611T080000Z
        DTEND:20260611T130000Z
        COLOR:darkkhaki
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("https://nx93157.../personal/x.ics"), "\"e1\"", ical));

    assertEquals("#bdb76b",
        entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR),
        "Nextcloud's `darkkhaki` must arrive as hex so the colour picker can render it");
  }

  // ── Reverse lookup (BUG #12 part-2) ──────────────────────────

  @Test
  @DisplayName("CssColorNames.toName returns the canonical CSS3 token for known hex values")
  void reverseLookupCanonical() {
    assertEquals("olive", CssColorNames.toName("#808000").orElseThrow(),
        "Sven's reproduction: #808000 → olive (matches the Nextcloud export)");
    assertEquals("red", CssColorNames.toName("#ff0000").orElseThrow());
    assertEquals("black", CssColorNames.toName("#000000").orElseThrow());
    assertEquals("white", CssColorNames.toName("#ffffff").orElseThrow());
  }

  @Test
  @DisplayName("CssColorNames.toName is case-insensitive on the hex digits")
  void reverseLookupCaseInsensitive() {
    assertEquals("red", CssColorNames.toName("#FF0000").orElseThrow());
    assertEquals("red", CssColorNames.toName("#fF0000").orElseThrow());
  }

  @Test
  @DisplayName("CssColorNames.toName returns empty for arbitrary hex without a named match")
  void reverseLookupArbitraryHex() {
    org.junit.jupiter.api.Assertions.assertTrue(
        CssColorNames.toName("#f08ee8").isEmpty(),
        "Arbitrary hex from Sven's BUG #12 PUT diagnostic must not match anything");
    org.junit.jupiter.api.Assertions.assertTrue(
        CssColorNames.toName("#123456").isEmpty());
  }

  @Test
  @DisplayName("CssColorNames.toName rejects non-hex inputs")
  void reverseLookupInvalidInputs() {
    org.junit.jupiter.api.Assertions.assertTrue(CssColorNames.toName(null).isEmpty());
    org.junit.jupiter.api.Assertions.assertTrue(CssColorNames.toName("").isEmpty());
    org.junit.jupiter.api.Assertions.assertTrue(CssColorNames.toName("red").isEmpty(),
        "Named token in must NOT round-trip as 'red' — we only accept 7-char hex");
    org.junit.jupiter.api.Assertions.assertTrue(CssColorNames.toName("#abc").isEmpty(),
        "3-char short hex not supported (matches CSS3 list which is all 7-char)");
  }

  @Test
  @DisplayName("EntryMapper writes a named token to non-Apple targets when an exact match exists")
  void writeNamedForNextcloudOnExactMatch() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("nextcloud-uid");
    entry.setTitle("Olive event");
    entry.setStart(java.time.LocalDateTime.of(2026, java.time.Month.JUNE, 11, 8, 0));
    entry.setEnd(java.time.LocalDateTime.of(2026, java.time.Month.JUNE, 11, 13, 0));
    entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, "#808000");

    // preferNamedColors=true → Nextcloud-target write
    String body = mapper.toICalendarText(entry, false, true);

    org.junit.jupiter.api.Assertions.assertTrue(body.contains("COLOR:olive"),
        "Exact hex matches must be written as canonical named tokens so "
            + "Nextcloud's UI renders them; got:\n" + body);
    org.junit.jupiter.api.Assertions.assertFalse(body.contains("COLOR:#808000"),
        "Hex form must NOT appear when a named equivalent exists.");
  }

  @Test
  @DisplayName("EntryMapper keeps hex when preferNamedColors=true but no named equivalent exists")
  void writeFallsBackToHexWhenNoNamedMatch() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("arbitrary-uid");
    entry.setTitle("Arbitrary hex");
    entry.setStart(java.time.LocalDateTime.of(2026, java.time.Month.JUNE, 11, 8, 0));
    entry.setEnd(java.time.LocalDateTime.of(2026, java.time.Month.JUNE, 11, 13, 0));
    entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, "#f08ee8");

    String body = mapper.toICalendarText(entry, false, true);

    org.junit.jupiter.api.Assertions.assertTrue(body.contains("COLOR:#f08ee8"),
        "Arbitrary hex must pass through when no named equivalent exists — "
            + "we don't lose precision via 'nearest-match' guessing.");
  }

  @Test
  @DisplayName("EntryMapper keeps hex when preferNamedColors=false (= Apple target)")
  void writeKeepsHexForApple() {
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = new Entry("apple-uid");
    entry.setTitle("Apple target");
    entry.setStart(java.time.LocalDateTime.of(2026, java.time.Month.JUNE, 11, 8, 0));
    entry.setEnd(java.time.LocalDateTime.of(2026, java.time.Month.JUNE, 11, 13, 0));
    entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, "#808000");

    // appleSidechannel=true, preferNamedColors=false → Apple target
    String body = mapper.toICalendarText(entry, true, false);

    org.junit.jupiter.api.Assertions.assertTrue(body.contains("COLOR:#808000"),
        "Apple target keeps hex (matches the BUG #2 DESCRIPTION-marker "
            + "round-trip semantics and respects user precision); got:\n" + body);
  }

  @Test
  @DisplayName("EntryMapper.toEntry preserves hex COLOR (no double-conversion)")
  void entryMapperPreservesHexOnRead() {
    String ical = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//flow-template//test//EN
        BEGIN:VEVENT
        UID:hex-color
        SUMMARY:Hex
        DTSTART:20260611T080000Z
        DTEND:20260611T130000Z
        COLOR:#ff0000
        END:VEVENT
        END:VCALENDAR
        """;
    EntryMapper mapper = new EntryMapper(ZoneOffset.UTC);
    Entry entry = mapper.toEntry(new RemoteEvent(
        URI.create("http://host/cal/x.ics"), "\"e1\"", ical));

    assertEquals("#ff0000",
        entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR));
  }
}
