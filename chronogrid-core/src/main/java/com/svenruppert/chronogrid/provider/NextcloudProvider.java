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

/**
 * Nextcloud CalDAV.
 *
 * <p><strong>Quirks (covered by BUG #12):</strong>
 *
 * <ul>
 *   <li>Round-trips COLOR correctly through CalDAV at the data
 *       layer — exact hex written by us comes back as exact hex
 *       on the next REPORT.</li>
 *   <li>BUT Nextcloud's own web UI only renders the colour pill
 *       when COLOR carries a CSS3 named token (e.g.
 *       {@code COLOR:olive}, {@code COLOR:darkseagreen}).
 *       Arbitrary hex values like {@code #6bbd88} get stored
 *       intact but show as "no colour" in Nextcloud's UI.</li>
 *   <li>Sven explicitly chose the precision-vs-consistency
 *       trade-off: snap arbitrary hex to its nearest CSS3 named
 *       neighbour so both UIs render the pill. Exact named
 *       matches stay exact.</li>
 * </ul>
 *
 * <p>Currently identified by a path-suffix probe ({@code
 * remote.php/dav}). Hostname-based detection isn't viable because
 * Nextcloud is self-hosted on arbitrary domains.
 */
public final class NextcloudProvider implements CalDavProviderProfile {

  @Override
  public String id() {
    return "nextcloud";
  }

  @Override
  public boolean matches(URI uri) {
    if (uri == null) return false;
    String path = uri.getPath();
    return path != null && path.contains("/remote.php/dav");
  }

  @Override
  public String formatColor(String hex) {
    // Snap to nearest CSS3 named token; exact matches keep their
    // canonical name. Sven-accepted precision loss for Nextcloud-
    // UI consistency.
    return CssColorNames.toNameOrNearest(hex);
  }

  @Override
  public boolean writeDescriptionMarker() {
    return false;
  }
}
