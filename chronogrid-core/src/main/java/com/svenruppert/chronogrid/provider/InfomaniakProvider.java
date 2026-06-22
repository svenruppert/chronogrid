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

package com.svenruppert.chronogrid.provider;

import com.svenruppert.chronogrid.mapping.CssColorNames;

import java.net.URI;
import java.util.Locale;

/**
 * Infomaniak kSuite / Calendar CalDAV.
 *
 * <p><strong>Quirks (covered by BUG #13):</strong>
 *
 * <ul>
 *   <li>Round-trips COLOR correctly at the data layer (Sven's
 *       export shows {@code COLOR:brown} preserved verbatim).</li>
 *   <li>Infomaniak's own web UI does NOT render per-event colour
 *       at all — verified by Sven's manual inspection. This is a
 *       provider-UI feature gap; BUG #13 is filed as
 *       ⚫ verworfen because the limitation isn't fixable from
 *       our side.</li>
 *   <li>Snap-to-nearest still applied: if Infomaniak ever ships
 *       per-event colour in their UI, we'd want named tokens
 *       there too, and the snap doesn't hurt the data layer.</li>
 * </ul>
 *
 * <p>Identified by hostname suffix {@code infomaniak.com} and
 * {@code ksuite.infomaniak.com} variants.
 */
public final class InfomaniakProvider implements CalDavProviderProfile {

  @Override
  public String id() {
    return "infomaniak";
  }

  @Override
  public boolean matches(URI uri) {
    if (uri == null) return false;
    String host = uri.getHost();
    if (host == null) return false;
    host = host.toLowerCase(Locale.ROOT);
    return host.endsWith("infomaniak.com")
        || host.endsWith("infomaniak.ch");
  }

  @Override
  public String formatColor(String hex) {
    // Same as Nextcloud: snap to nearest named for forward-
    // compatibility (cheap; harmless if Infomaniak's UI still
    // shows nothing).
    return CssColorNames.toNameOrNearest(hex);
  }

  @Override
  public boolean writeDescriptionMarker() {
    return false;
  }
}
