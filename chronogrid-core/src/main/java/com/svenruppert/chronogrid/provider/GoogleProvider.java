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

/**
 * Google Calendar's CalDAV façade.
 *
 * <p>Google publishes a CalDAV endpoint at
 * {@code https://apidata.googleusercontent.com/caldav/v2/<calendar-id>/events}
 * authenticated exclusively via OAuth 2.0 Bearer tokens (Basic-Auth
 * with app-specific passwords was retired in 2022). The CalDavClient
 * Bearer-Auth variant introduced in Planning-Feature #9 Schicht 2
 * supplies the token; the provider profile here captures Google's
 * CalDAV-side quirks.
 *
 * <p><strong>Quirks:</strong>
 *
 * <ul>
 *   <li><strong>VTODO unsupported.</strong> Google Calendar's CalDAV
 *       endpoint rejects VTODO components (415 Unsupported Media
 *       Type or silent discard depending on the path). VTODOs live
 *       in the separate Google Tasks API which is intentionally out
 *       of scope for Planning #9. {@link #supportsTodos()} returns
 *       {@code false}; the editor dims VTODO controls for
 *       Google-backed subscriptions.</li>
 *   <li><strong>COLOR property ignored.</strong> Google's CalDAV
 *       round-trips {@code COLOR} as-is at the data layer (the
 *       value comes back on a subsequent REPORT) but the Google
 *       Calendar web UI does not render it — Google's own UI uses
 *       per-calendar colours, not per-event. ChronoGrid's own
 *       renderer still honours the value, so pass-through (no snap-
 *       to-named like Nextcloud needs) preserves precision for our
 *       UI without losing anything on the wire.</li>
 *   <li><strong>No DESCRIPTION-suffix marker.</strong> Google does
 *       not strip COLOR or X-properties on user-edit-rewrite (unlike
 *       Apple iCloud), so the BUG #2 DESCRIPTION suffix is not
 *       needed and would only show up as visible noise in Google's
 *       UI.</li>
 *   <li><strong>Identified by hostname.</strong>
 *       {@code apidata.googleusercontent.com} is the canonical
 *       endpoint; the path-based test (
 *       {@code /caldav/v2/}) would false-positive on any service
 *       that happens to use those tokens. Hostname-matching is
 *       conservative — every Google CalDAV URI goes through that
 *       host.</li>
 * </ul>
 *
 * <p>The {@link #defaultTimezone()} stays at JVM-system default —
 * Google accepts any valid IANA tz-database identifier on
 * {@code DTSTART;TZID=...}, so no special-casing is needed (unlike
 * a hypothetical legacy provider that demands UTC).
 */
public final class GoogleProvider implements CalDavProviderProfile {

  @Override
  public String id() {
    return "google";
  }

  @Override
  public boolean matches(URI uri) {
    if (uri == null) return false;
    String host = uri.getHost();
    return host != null && host.endsWith("googleusercontent.com");
  }

  @Override
  public String formatColor(String hex) {
    // Pass-through: Google's CalDAV round-trips arbitrary hex on
    // the wire. The Google web UI ignores it but ChronoGrid's own
    // renderer honours it, so no precision loss here.
    return hex;
  }

  @Override
  public boolean writeDescriptionMarker() {
    return false;
  }

  @Override
  public boolean supportsTodos() {
    return false;
  }
}
