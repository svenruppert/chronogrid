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
import java.util.List;

/**
 * Static lookup table that resolves a target {@link URI} to the
 * {@link CalDavProviderProfile} that should handle it. Walks the
 * known-provider list in declaration order and picks the first
 * profile whose {@link CalDavProviderProfile#matches(URI)} accepts
 * the URI. {@link GenericProvider} sits last and matches
 * everything, so the registry never throws.
 *
 * <p>Order matters: more specific predicates must come before
 * more general ones. Nextcloud is matched via path-suffix
 * ({@code /remote.php/dav}) which a generic CalDAV server could
 * theoretically also expose; Apple and Infomaniak are matched
 * via hostname so no overlap there.
 *
 * <p>Read-only by design — no runtime registration. If a new
 * provider needs a profile, add a class, add it to the {@link
 * #PROFILES} list, ship a release. Bug-fix-style updates rather
 * than a plugin API means no class-loader surprises and the
 * existing code can keep walking a {@code List<...>} literal.
 */
public final class ProviderRegistry {

  private ProviderRegistry() { }

  /**
   * Declaration order matters — most-specific predicates first,
   * {@link GenericProvider} last.
   */
  private static final List<CalDavProviderProfile> PROFILES = List.of(
      new AppleProvider(),
      new NextcloudProvider(),
      new InfomaniakProvider(),
      new GenericProvider()
  );

  /**
   * Returns the first {@link CalDavProviderProfile} whose
   * {@code matches} predicate accepts {@code uri}. Always returns
   * a non-null profile — {@link GenericProvider} catches anything
   * the specific sniffers don't recognise.
   */
  public static CalDavProviderProfile forUri(URI uri) {
    for (CalDavProviderProfile p : PROFILES) {
      if (p.matches(uri)) return p;
    }
    // Unreachable in practice — GenericProvider.matches() returns
    // true unconditionally — but kept as defensive code in case
    // someone reorders the list and forgets the fallback.
    throw new IllegalStateException(
        "ProviderRegistry has no fallback for URI " + uri);
  }
}
