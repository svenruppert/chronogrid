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

import com.svenruppert.chronogrid.mapping.ContrastTextColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BUG #15 — WCAG-style contrast pick. Pins the canonical decisions
 * so a regression flips a test rather than silently making event
 * text invisible.
 */
@DisplayName("ContrastTextColor — WCAG-based black/white text pick (BUG #15)")
class ContrastTextColorTest {

  @Test
  @DisplayName("dark backgrounds → white text")
  void darkBackgroundsPickWhite() {
    assertEquals("#ffffff", ContrastTextColor.pickFor("#000000"));
    assertEquals("#ffffff", ContrastTextColor.pickFor("#1f77b4"),
        "ChronoGrid's default-blue palette colour must keep white text");
    assertEquals("#ffffff", ContrastTextColor.pickFor("#8b0000"),
        "darkred = #8b0000 keeps white");
    assertEquals("#ffffff", ContrastTextColor.pickFor("#483d8b"),
        "darkslateblue from Sven's BUG #12 reproduction → white");
  }

  @Test
  @DisplayName("light backgrounds → black text — the BUG #15 fix case")
  void lightBackgroundsPickBlack() {
    assertEquals("#000000", ContrastTextColor.pickFor("#ffffff"));
    assertEquals("#000000", ContrastTextColor.pickFor("#ffffe0"),
        "lightyellow — the canonical BUG #15 reproduction value");
    assertEquals("#000000", ContrastTextColor.pickFor("#ffff00"),
        "pure yellow — white text would be invisible");
    assertEquals("#000000", ContrastTextColor.pickFor("#90ee90"),
        "lightgreen needs black text");
    assertEquals("#000000", ContrastTextColor.pickFor("#ffb6c1"),
        "lightpink needs black text");
  }

  @Test
  @DisplayName("mid-luminance hex — Sven's #6bbd88 from BUG #12 picks black")
  void midLuminanceTrendsBlack() {
    // #6bbd88 is on the border but darkseagreen-ish greens skew
    // toward black being more readable.
    assertEquals("#000000", ContrastTextColor.pickFor("#6bbd88"));
    // #8fbc8f (darkseagreen — what #6bbd88 snaps to for Nextcloud)
    assertEquals("#000000", ContrastTextColor.pickFor("#8fbc8f"));
  }

  @Test
  @DisplayName("invalid / null input falls back to white (= FullCalendar default)")
  void invalidInputFallsBackToWhite() {
    assertEquals("#ffffff", ContrastTextColor.pickFor(null));
    assertEquals("#ffffff", ContrastTextColor.pickFor(""));
    assertEquals("#ffffff", ContrastTextColor.pickFor("not-hex"));
    assertEquals("#ffffff", ContrastTextColor.pickFor("#abc"),
        "3-char hex unsupported — fall back rather than corrupt");
    assertEquals("#ffffff", ContrastTextColor.pickFor("rgb(255,0,0)"));
  }

  @Test
  @DisplayName("relativeLuminance is monotonic — black → 0, white → 1")
  void luminanceMonotonic() {
    assertEquals(0.0, ContrastTextColor.relativeLuminance(0, 0, 0), 0.0001);
    assertEquals(1.0, ContrastTextColor.relativeLuminance(255, 255, 255), 0.0001);
  }
}
