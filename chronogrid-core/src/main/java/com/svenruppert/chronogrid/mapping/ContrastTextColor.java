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

package com.svenruppert.chronogrid.mapping;

/**
 * Picks {@code #000000} (black) or {@code #ffffff} (white) as the
 * readable text colour on a given background. Used by
 * {@code CalendarService.applyColours} (BUG #15) so a user-picked
 * pale fill like {@code lightyellow} doesn't end up with white
 * text that's invisible against it.
 *
 * <p><strong>Algorithm:</strong> WCAG-style relative luminance via
 * the standard sRGB → linear-RGB transfer function, then a
 * contrast-ratio comparison against black and white. The colour
 * with the higher contrast ratio wins. This is the same rule the
 * WCAG 2.x guidelines use to decide "should text be light or dark
 * on this background".
 *
 * <p>The threshold is implicit in the comparison — there's no
 * magic 0.5-luminance break-point that would flicker on
 * mid-luminance colours. Borderline cases (luminance ≈ 0.18, the
 * mathematical "equal-contrast" point) deterministically pick
 * black because that scores the same contrast and we prefer black
 * in ties for typographic conventions.
 *
 * <p>Inputs that don't parse as 7-char {@code #rrggbb} fall back
 * to white — same as FullCalendar's default — so the pipeline
 * stays resilient against unknown / malformed colour tokens.
 * Such cases should be normalised to hex via
 * {@link CssColorNames#toHex(String)} before reaching here.
 */
public final class ContrastTextColor {

  private ContrastTextColor() { }

  private static final String BLACK = "#000000";
  private static final String WHITE = "#ffffff";

  /**
   * Returns {@code "#000000"} or {@code "#ffffff"} based on which
   * one yields the higher WCAG contrast ratio against
   * {@code backgroundHex}. Defaults to white on invalid input so
   * the FullCalendar default rendering is preserved.
   */
  public static String pickFor(String backgroundHex) {
    if (backgroundHex == null) return WHITE;
    String trimmed = backgroundHex.trim();
    if (trimmed.length() != 7 || trimmed.charAt(0) != '#') return WHITE;
    int r;
    int g;
    int b;
    try {
      r = Integer.parseInt(trimmed.substring(1, 3), 16);
      g = Integer.parseInt(trimmed.substring(3, 5), 16);
      b = Integer.parseInt(trimmed.substring(5, 7), 16);
    } catch (NumberFormatException e) {
      return WHITE;
    }
    double bgLum = relativeLuminance(r, g, b);
    // L for black is 0, L for white is 1, so the contrast ratios
    // simplify to: contrast-vs-black = (bgLum + 0.05) / 0.05,
    // contrast-vs-white = 1.05 / (bgLum + 0.05). Black wins when
    // bgLum > 0.179 (= cube-root threshold per WCAG).
    return bgLum > 0.179 ? BLACK : WHITE;
  }

  /**
   * sRGB → relative luminance, per WCAG 2.x §1.4.3. Public for
   * tests; not part of the documented API.
   */
  public static double relativeLuminance(int r, int g, int b) {
    double rs = channelToLinear(r);
    double gs = channelToLinear(g);
    double bs = channelToLinear(b);
    return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
  }

  private static double channelToLinear(int byteValue) {
    double v = byteValue / 255.0;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  }
}
