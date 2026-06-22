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

import com.svenruppert.chronogrid.provider.CalDavProviderProfile;
import com.svenruppert.chronogrid.provider.ProviderRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@link ProviderRegistry} URI-resolution contract +
 * each provider's per-quirk behaviour. Encodes the BUG #2 / #7
 * / #12 / #13 decisions so a regression flips a test rather than
 * silently mis-serialising COLOR or the DESCRIPTION marker.
 */
@DisplayName("ProviderRegistry + per-provider profiles")
class ProviderRegistryTest {

  // ── URI resolution ─────────────────────────────────────────────

  @Test
  @DisplayName("Apple URIs resolve to AppleProvider regardless of pod hostname")
  void resolvesApple() {
    assertEquals("apple", ProviderRegistry.forUri(
        URI.create("https://caldav.icloud.com/")).id());
    assertEquals("apple", ProviderRegistry.forUri(
        URI.create("https://p124-caldav.icloud.com:443/270995419/calendars/CAL/")).id());
    assertEquals("apple", ProviderRegistry.forUri(
        URI.create("https://p-prod-caldav.icloud.com/foo/")).id());
  }

  @Test
  @DisplayName("Nextcloud URIs resolve via the /remote.php/dav path suffix")
  void resolvesNextcloud() {
    assertEquals("nextcloud", ProviderRegistry.forUri(URI.create(
        "https://nx93157.your-storageshare.de/remote.php/dav/calendars/sven.ruppert/personal/"))
        .id());
    assertEquals("nextcloud", ProviderRegistry.forUri(URI.create(
        "https://cloud.example.com/remote.php/dav/calendars/foo/"))
        .id());
  }

  @Test
  @DisplayName("Infomaniak URIs resolve via the infomaniak.com/.ch hostname suffix")
  void resolvesInfomaniak() {
    assertEquals("infomaniak", ProviderRegistry.forUri(
        URI.create("https://caldav.infomaniak.com/foo/")).id());
    assertEquals("infomaniak", ProviderRegistry.forUri(
        URI.create("https://ksuite.infomaniak.ch/cal/")).id());
  }

  @Test
  @DisplayName("Unknown URIs fall back to GenericProvider — no throw")
  void fallsBackToGeneric() {
    assertEquals("generic", ProviderRegistry.forUri(
        URI.create("https://baikal.example.com/cal/")).id());
    assertEquals("generic", ProviderRegistry.forUri(
        URI.create("https://radicale.example.com/")).id());
  }

  // ── Per-provider COLOR formatting ──────────────────────────────

  @Test
  @DisplayName("AppleProvider preserves hex AND emits the DESCRIPTION marker")
  void applePreservesHexAndMarker() {
    CalDavProviderProfile apple = ProviderRegistry.forUri(
        URI.create("https://caldav.icloud.com/"));
    assertEquals("#ff0000", apple.formatColor("#ff0000"));
    assertEquals("#6bbd88", apple.formatColor("#6bbd88"),
        "Apple keeps arbitrary hex — DESCRIPTION marker carries precision through round-trips");
    assertTrue(apple.writeDescriptionMarker(),
        "Apple needs the BUG #2 sidechannel marker for user-edit-rewrite survival");
  }

  @Test
  @DisplayName("NextcloudProvider snaps hex to nearest CSS3 named, no marker")
  void nextcloudSnapsAndNoMarker() {
    CalDavProviderProfile nc = ProviderRegistry.forUri(URI.create(
        "https://nx93157.../remote.php/dav/calendars/foo/"));
    assertEquals("olive", nc.formatColor("#808000"),
        "Exact match keeps the canonical CSS3 token");
    assertEquals("darkseagreen", nc.formatColor("#6bbd88"),
        "Sven's BUG #12 reproduction — #6bbd88 → darkseagreen");
    assertFalse(nc.writeDescriptionMarker(),
        "Nextcloud round-trips COLOR; marker would just pollute DESCRIPTION");
  }

  @Test
  @DisplayName("InfomaniakProvider snaps to nearest (forward-compat), no marker")
  void infomaniakSnapsAndNoMarker() {
    CalDavProviderProfile im = ProviderRegistry.forUri(
        URI.create("https://caldav.infomaniak.com/foo/"));
    assertEquals("brown", im.formatColor("#a52a2a"),
        "Exact match — Sven's iCal-Export reproduction");
    assertFalse(im.writeDescriptionMarker());
  }

  @Test
  @DisplayName("GenericProvider preserves hex + no marker (standards-compliant default)")
  void genericPreservesHexAndNoMarker() {
    CalDavProviderProfile gen = ProviderRegistry.forUri(
        URI.create("https://baikal.example.com/cal/"));
    assertEquals("#ff0000", gen.formatColor("#ff0000"));
    assertEquals("#6bbd88", gen.formatColor("#6bbd88"));
    assertFalse(gen.writeDescriptionMarker());
  }

  // ── ID stability ───────────────────────────────────────────────

  @Test
  @DisplayName("Profile IDs are stable, lowercased, and listed for log-grep")
  void idStability() {
    assertNotNull(ProviderRegistry.forUri(URI.create("https://caldav.icloud.com/")).id());
    assertEquals("apple", ProviderRegistry.forUri(
        URI.create("https://caldav.icloud.com/")).id());
    assertEquals("nextcloud", ProviderRegistry.forUri(URI.create(
        "https://x.example.com/remote.php/dav/")).id());
    assertEquals("infomaniak", ProviderRegistry.forUri(
        URI.create("https://infomaniak.com/")).id());
    assertEquals("generic", ProviderRegistry.forUri(
        URI.create("https://generic.example.org/")).id());
  }
}
