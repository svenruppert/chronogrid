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
 * Captures the per-provider quirks the rest of chronogrid-core
 * needs to respect when writing iCalendar bodies for a given
 * CalDAV target. Lives in {@code chronogrid-core/provider} because
 * it's a pure data/strategy contract — no Vaadin or UI dependency
 * — so the headless reader/writer stack can use it without
 * pulling component-level code.
 *
 * <p>Implementations are stateless singletons (one per provider).
 * The {@link ProviderRegistry} walks them in declaration order and
 * picks the first whose {@link #matches(URI)} predicate accepts
 * the target URI. The {@link GenericProvider} sits last in the
 * chain and matches everything so the registry never has to throw.
 *
 * <p>Per-bug rationale for each method:
 *
 * <ul>
 *   <li>{@link #formatColor(String)} —
 *       <strong>BUG #2 + BUG #12.</strong> Apple iCloud preserves
 *       arbitrary hex; Nextcloud needs CSS3 named tokens for its UI
 *       to render the pill (snap-to-nearest); Infomaniak doesn't
 *       render colours either way; generic pass-through.</li>
 *   <li>{@link #writeDescriptionMarker()} —
 *       <strong>BUG #2 + BUG #7.</strong> Apple's user-edit
 *       pipeline strips both COLOR and X-properties, so the
 *       DESCRIPTION suffix marker is the only sidechannel that
 *       survives. Non-Apple providers don't need it (and the
 *       marker would show up as visible noise in their UI).</li>
 * </ul>
 *
 * <p>Future expansion points (commented examples; not yet
 * implemented):
 *
 * <ul>
 *   <li>{@code defaultTimezone()} — Sven's BUG #14 will need
 *       per-provider timezone hints if some servers reject
 *       unknown TZIDs.</li>
 *   <li>{@code supportsPerEventColourInOwnUi()} — already useful
 *       for surfacing a tooltip ("colour set, but {provider}'s UI
 *       doesn't render it") in the editor for known-blind
 *       providers (Infomaniak).</li>
 * </ul>
 */
public interface CalDavProviderProfile {

  /**
   * Stable identifier used in logs, tests, and future
   * Settings-dialog drop-downs. Lower-case, no spaces.
   */
  String id();

  /**
   * Hostname-suffix-based URI matcher. Implementations should be
   * conservative (true → false) to avoid mis-classifying a target
   * — false-positives propagate as wrong serialisation choices.
   */
  boolean matches(URI collectionUri);

  /**
   * Returns the {@code COLOR} property value to write for this
   * provider. The input is the canonical {@code #rrggbb} hex from
   * {@code CUSTOM_ENTRY_COLOR}. Providers may:
   *
   * <ul>
   *   <li>Pass through (Apple, Generic) — preserves precision.</li>
   *   <li>Snap to nearest CSS3 named token (Nextcloud) — exact
   *       matches keep the canonical name; arbitrary hex snaps to
   *       whichever named colour minimises Euclidean RGB
   *       distance.</li>
   * </ul>
   *
   * <p>Returns the input unchanged for null / blank / malformed.
   */
  String formatColor(String hex);

  /**
   * Whether to append the BUG #2 DESCRIPTION suffix marker
   * ({@code [chronogrid-color: #rrggbb]}) so the colour survives a
   * provider-side strip-on-edit. Only Apple needs this; every
   * other provider would render the marker as visible noise in
   * its own UI.
   */
  boolean writeDescriptionMarker();
}
