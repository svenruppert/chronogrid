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

import java.net.URI;
import java.util.Locale;

/**
 * Apple iCloud / CloudKit CalDAV.
 *
 * <p><strong>Quirks (covered by BUG #2 + #7):</strong>
 *
 * <ul>
 *   <li>iCloud's data model has no per-event colour concept. Its
 *       Calendar UI doesn't show one and won't preserve a COLOR
 *       property across a user-edit-rewrite — the entire
 *       iCalendar body is rebuilt from iCloud's internal model.</li>
 *   <li>Custom {@code X-} properties (including our
 *       {@code X-CHRONOGRID-COLOR} probe) are also stripped on
 *       user-edit-rewrite — Apple's own docs admit only
 *       {@code X-APPLE-*} properties are preserved.</li>
 *   <li>The only user-data field that survives Apple's rewrite is
 *       {@code DESCRIPTION}. The BUG #2 fix appends a discreet
 *       suffix marker {@code [chronogrid-color: #rrggbb]} that
 *       round-trips through Apple's pipeline.</li>
 * </ul>
 *
 * <p>Hex precision is kept on the write side because Apple's UI
 * doesn't render per-event colours anyway, and the
 * DESCRIPTION-marker round-trip is the actual carrier.
 */
public final class AppleProvider implements CalDavProviderProfile {

  @Override
  public String id() {
    return "apple";
  }

  @Override
  public boolean matches(URI uri) {
    if (uri == null) return false;
    String host = uri.getHost();
    if (host == null) return false;
    host = host.toLowerCase(Locale.ROOT);
    return host.equals("icloud.com") || host.endsWith(".icloud.com");
  }

  @Override
  public String formatColor(String hex) {
    // Apple-write keeps hex; the DESCRIPTION marker is the durable
    // carrier so colour precision is preserved on the round-trip.
    return hex;
  }

  @Override
  public boolean writeDescriptionMarker() {
    return true;
  }
}
