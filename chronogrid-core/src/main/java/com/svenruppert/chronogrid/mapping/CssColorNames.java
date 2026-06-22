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

import java.util.Locale;
import java.util.Map;

/**
 * CSS3 named-color → 7-char hex lookup table.
 *
 * <p>BUG #12 background: Nextcloud writes the per-event colour as
 * a CSS named token like {@code COLOR:darkkhaki} instead of the
 * hex literal {@code COLOR:#bdb76b}. RFC 7986 §3.8.1.16 permits
 * both — the spec defers to "any CSS colour value". The native
 * HTML5 {@code <input type="color">} picker only accepts hex
 * though, so a {@code darkkhaki} value coming back from CalDAV
 * needs normalising before it can populate the picker.
 *
 * <p>The map is the standard CSS3 list (147 entries, identical
 * across modern browsers). Hardcoded once, in canonical lower-
 * case, hex always 7 chars. {@link #toHex(String)} performs a
 * case-insensitive lookup and returns the input unchanged when it
 * doesn't match (so already-hex inputs and unknown tokens pass
 * through cleanly).
 */
public final class CssColorNames {

  private CssColorNames() { }

  /**
   * Returns the 7-char hex form of {@code raw} if it's a known CSS
   * named colour; otherwise returns {@code raw} unchanged (so hex
   * inputs and unknown tokens pass through). {@code null} returns
   * {@code null}.
   *
   * <p>Matching is case-insensitive and ignores leading/trailing
   * whitespace.
   */
  public static String toHex(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return raw;
    String key = trimmed.toLowerCase(Locale.ROOT);
    return CSS3.getOrDefault(key, raw);
  }

  /**
   * Reverse lookup: returns the canonical CSS3 named token for an
   * EXACT hex match (case-insensitive), or {@link java.util.Optional#empty()}
   * when the input is null/blank/non-hex or has no named equivalent.
   *
   * <p>BUG #12 background: Nextcloud's web UI only renders the
   * per-event colour when {@code COLOR:} carries a named token —
   * arbitrary hex values become invisible in its UI even though
   * the value persists correctly through CalDAV. Writing exact
   * matches as named tokens (e.g. {@code #808000} → {@code olive})
   * keeps Nextcloud-UI users happy without losing precision.
   *
   * <p>Where multiple named tokens map to the same hex (e.g.
   * {@code aqua} == {@code cyan}, {@code gray} == {@code grey}),
   * the result is the first-occurring entry in the canonical
   * CSS3 table. Deterministic for our use, irrelevant for the
   * renderer.
   */
  public static java.util.Optional<String> toName(String hex) {
    if (hex == null) return java.util.Optional.empty();
    String trimmed = hex.trim();
    if (trimmed.length() != 7 || trimmed.charAt(0) != '#') {
      return java.util.Optional.empty();
    }
    String lower = trimmed.toLowerCase(Locale.ROOT);
    for (Map.Entry<String, String> e : CSS3.entrySet()) {
      if (e.getValue().equals(lower)) return java.util.Optional.of(e.getKey());
    }
    return java.util.Optional.empty();
  }

  /**
   * Returns the canonical CSS3 named token closest to {@code hex}
   * by Euclidean RGB distance. Exact matches return the canonical
   * named form (same path as {@link #toName(String)}); arbitrary
   * hex values get approximated to whichever named colour minimises
   * {@code (Δr² + Δg² + Δb²)}.
   *
   * <p>BUG #12 final fix: Sven's reproduction was {@code rgb(107,
   * 189, 136)} = {@code #6bbd88}, an arbitrary value Nextcloud's
   * UI silently dropped. With this method we write the nearest
   * named token instead so the colour pill shows up. The
   * downstream price is precision — the user-picked value gets
   * snapped to the closest of 147 CSS3 colours and round-trips
   * back as the snapped hex. Sven explicitly accepted the
   * trade-off: consistent visualisation in both UIs beats exact
   * hex preservation in our app.
   *
   * <p>Returns {@code hex} unchanged when it's null, blank, or
   * not parseable as 7-char hex — keeps the writer pipeline
   * resilient against bad input.
   */
  public static String toNameOrNearest(String hex) {
    if (hex == null) return null;
    String trimmed = hex.trim();
    if (trimmed.length() != 7 || trimmed.charAt(0) != '#') return hex;
    // Try exact match first — preserves the canonical token when
    // the user-picked value lines up with a named colour exactly.
    java.util.Optional<String> exact = toName(trimmed);
    if (exact.isPresent()) return exact.get();
    int r;
    int g;
    int b;
    try {
      r = Integer.parseInt(trimmed.substring(1, 3), 16);
      g = Integer.parseInt(trimmed.substring(3, 5), 16);
      b = Integer.parseInt(trimmed.substring(5, 7), 16);
    } catch (NumberFormatException e) {
      return hex;
    }
    String bestName = null;
    long bestDist = Long.MAX_VALUE;
    for (Map.Entry<String, String> e : CSS3.entrySet()) {
      String namedHex = e.getValue();
      int r2 = Integer.parseInt(namedHex.substring(1, 3), 16);
      int g2 = Integer.parseInt(namedHex.substring(3, 5), 16);
      int b2 = Integer.parseInt(namedHex.substring(5, 7), 16);
      long dr = (long) r - r2;
      long dg = (long) g - g2;
      long db = (long) b - b2;
      long d = dr * dr + dg * dg + db * db;
      if (d < bestDist) {
        bestDist = d;
        bestName = e.getKey();
      }
    }
    return bestName != null ? bestName : hex;
  }

  private static final Map<String, String> CSS3 = Map.ofEntries(
      Map.entry("aliceblue", "#f0f8ff"),
      Map.entry("antiquewhite", "#faebd7"),
      Map.entry("aqua", "#00ffff"),
      Map.entry("aquamarine", "#7fffd4"),
      Map.entry("azure", "#f0ffff"),
      Map.entry("beige", "#f5f5dc"),
      Map.entry("bisque", "#ffe4c4"),
      Map.entry("black", "#000000"),
      Map.entry("blanchedalmond", "#ffebcd"),
      Map.entry("blue", "#0000ff"),
      Map.entry("blueviolet", "#8a2be2"),
      Map.entry("brown", "#a52a2a"),
      Map.entry("burlywood", "#deb887"),
      Map.entry("cadetblue", "#5f9ea0"),
      Map.entry("chartreuse", "#7fff00"),
      Map.entry("chocolate", "#d2691e"),
      Map.entry("coral", "#ff7f50"),
      Map.entry("cornflowerblue", "#6495ed"),
      Map.entry("cornsilk", "#fff8dc"),
      Map.entry("crimson", "#dc143c"),
      Map.entry("cyan", "#00ffff"),
      Map.entry("darkblue", "#00008b"),
      Map.entry("darkcyan", "#008b8b"),
      Map.entry("darkgoldenrod", "#b8860b"),
      Map.entry("darkgray", "#a9a9a9"),
      Map.entry("darkgrey", "#a9a9a9"),
      Map.entry("darkgreen", "#006400"),
      Map.entry("darkkhaki", "#bdb76b"),
      Map.entry("darkmagenta", "#8b008b"),
      Map.entry("darkolivegreen", "#556b2f"),
      Map.entry("darkorange", "#ff8c00"),
      Map.entry("darkorchid", "#9932cc"),
      Map.entry("darkred", "#8b0000"),
      Map.entry("darksalmon", "#e9967a"),
      Map.entry("darkseagreen", "#8fbc8f"),
      Map.entry("darkslateblue", "#483d8b"),
      Map.entry("darkslategray", "#2f4f4f"),
      Map.entry("darkslategrey", "#2f4f4f"),
      Map.entry("darkturquoise", "#00ced1"),
      Map.entry("darkviolet", "#9400d3"),
      Map.entry("deeppink", "#ff1493"),
      Map.entry("deepskyblue", "#00bfff"),
      Map.entry("dimgray", "#696969"),
      Map.entry("dimgrey", "#696969"),
      Map.entry("dodgerblue", "#1e90ff"),
      Map.entry("firebrick", "#b22222"),
      Map.entry("floralwhite", "#fffaf0"),
      Map.entry("forestgreen", "#228b22"),
      Map.entry("fuchsia", "#ff00ff"),
      Map.entry("gainsboro", "#dcdcdc"),
      Map.entry("ghostwhite", "#f8f8ff"),
      Map.entry("gold", "#ffd700"),
      Map.entry("goldenrod", "#daa520"),
      Map.entry("gray", "#808080"),
      Map.entry("grey", "#808080"),
      Map.entry("green", "#008000"),
      Map.entry("greenyellow", "#adff2f"),
      Map.entry("honeydew", "#f0fff0"),
      Map.entry("hotpink", "#ff69b4"),
      Map.entry("indianred", "#cd5c5c"),
      Map.entry("indigo", "#4b0082"),
      Map.entry("ivory", "#fffff0"),
      Map.entry("khaki", "#f0e68c"),
      Map.entry("lavender", "#e6e6fa"),
      Map.entry("lavenderblush", "#fff0f5"),
      Map.entry("lawngreen", "#7cfc00"),
      Map.entry("lemonchiffon", "#fffacd"),
      Map.entry("lightblue", "#add8e6"),
      Map.entry("lightcoral", "#f08080"),
      Map.entry("lightcyan", "#e0ffff"),
      Map.entry("lightgoldenrodyellow", "#fafad2"),
      Map.entry("lightgray", "#d3d3d3"),
      Map.entry("lightgrey", "#d3d3d3"),
      Map.entry("lightgreen", "#90ee90"),
      Map.entry("lightpink", "#ffb6c1"),
      Map.entry("lightsalmon", "#ffa07a"),
      Map.entry("lightseagreen", "#20b2aa"),
      Map.entry("lightskyblue", "#87cefa"),
      Map.entry("lightslategray", "#778899"),
      Map.entry("lightslategrey", "#778899"),
      Map.entry("lightsteelblue", "#b0c4de"),
      Map.entry("lightyellow", "#ffffe0"),
      Map.entry("lime", "#00ff00"),
      Map.entry("limegreen", "#32cd32"),
      Map.entry("linen", "#faf0e6"),
      Map.entry("magenta", "#ff00ff"),
      Map.entry("maroon", "#800000"),
      Map.entry("mediumaquamarine", "#66cdaa"),
      Map.entry("mediumblue", "#0000cd"),
      Map.entry("mediumorchid", "#ba55d3"),
      Map.entry("mediumpurple", "#9370db"),
      Map.entry("mediumseagreen", "#3cb371"),
      Map.entry("mediumslateblue", "#7b68ee"),
      Map.entry("mediumspringgreen", "#00fa9a"),
      Map.entry("mediumturquoise", "#48d1cc"),
      Map.entry("mediumvioletred", "#c71585"),
      Map.entry("midnightblue", "#191970"),
      Map.entry("mintcream", "#f5fffa"),
      Map.entry("mistyrose", "#ffe4e1"),
      Map.entry("moccasin", "#ffe4b5"),
      Map.entry("navajowhite", "#ffdead"),
      Map.entry("navy", "#000080"),
      Map.entry("oldlace", "#fdf5e6"),
      Map.entry("olive", "#808000"),
      Map.entry("olivedrab", "#6b8e23"),
      Map.entry("orange", "#ffa500"),
      Map.entry("orangered", "#ff4500"),
      Map.entry("orchid", "#da70d6"),
      Map.entry("palegoldenrod", "#eee8aa"),
      Map.entry("palegreen", "#98fb98"),
      Map.entry("paleturquoise", "#afeeee"),
      Map.entry("palevioletred", "#db7093"),
      Map.entry("papayawhip", "#ffefd5"),
      Map.entry("peachpuff", "#ffdab9"),
      Map.entry("peru", "#cd853f"),
      Map.entry("pink", "#ffc0cb"),
      Map.entry("plum", "#dda0dd"),
      Map.entry("powderblue", "#b0e0e6"),
      Map.entry("purple", "#800080"),
      Map.entry("rebeccapurple", "#663399"),
      Map.entry("red", "#ff0000"),
      Map.entry("rosybrown", "#bc8f8f"),
      Map.entry("royalblue", "#4169e1"),
      Map.entry("saddlebrown", "#8b4513"),
      Map.entry("salmon", "#fa8072"),
      Map.entry("sandybrown", "#f4a460"),
      Map.entry("seagreen", "#2e8b57"),
      Map.entry("seashell", "#fff5ee"),
      Map.entry("sienna", "#a0522d"),
      Map.entry("silver", "#c0c0c0"),
      Map.entry("skyblue", "#87ceeb"),
      Map.entry("slateblue", "#6a5acd"),
      Map.entry("slategray", "#708090"),
      Map.entry("slategrey", "#708090"),
      Map.entry("snow", "#fffafa"),
      Map.entry("springgreen", "#00ff7f"),
      Map.entry("steelblue", "#4682b4"),
      Map.entry("tan", "#d2b48c"),
      Map.entry("teal", "#008080"),
      Map.entry("thistle", "#d8bfd8"),
      Map.entry("tomato", "#ff6347"),
      Map.entry("turquoise", "#40e0d0"),
      Map.entry("violet", "#ee82ee"),
      Map.entry("wheat", "#f5deb3"),
      Map.entry("white", "#ffffff"),
      Map.entry("whitesmoke", "#f5f5f5"),
      Map.entry("yellow", "#ffff00"),
      Map.entry("yellowgreen", "#9acd32")
  );
}
