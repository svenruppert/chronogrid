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
