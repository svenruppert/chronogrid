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

package junit.com.svenruppert.flow.security.services;

import com.svenruppert.flow.security.services.PasswordPreflight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PasswordPreflight — local blocklist + HIBP fallback")
class PasswordPreflightTest {

  @Test
  @DisplayName("HIBP_ENABLED_PROPERTY constant guards against silent rename")
  void hibpPropertyConstant() {
    assertEquals("app.hibp.enabled",
        PasswordPreflight.HIBP_ENABLED_PROPERTY);
  }

  @Test
  @DisplayName("HIBP_TIMEOUT is 5 seconds — defends against accidental shortening")
  void hibpTimeoutConstant() {
    assertEquals(java.time.Duration.ofSeconds(5),
        PasswordPreflight.HIBP_TIMEOUT);
  }

  @Test
  @DisplayName("with HIBP disabled (Surefire default), local rejection still wins")
  void hibpDisabledLocalRejectStillFires() {
    // Surefire sets app.hibp.enabled=false. Local blocklist remains
    // authoritative regardless of the HIBP toggle.
    assertFalse(PasswordPreflight.isAcceptable("password123"));
  }

  @Test
  @DisplayName("with HIBP disabled, a non-blocklisted random password is accepted "
      + "without ever opening an HttpClient")
  void hibpDisabledRandomPasses() {
    // If this test takes >1s, the HIBP HttpClient was constructed
    // anyway — that would mean the disable check is wrong.
    long start = System.nanoTime();
    boolean ok = PasswordPreflight.isAcceptable("zR9q!sX5#kP2@dM7L");
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    assertTrue(ok);
    assertTrue(elapsedMs < 1000,
        "HIBP disabled — call must short-circuit, but took "
            + elapsedMs + "ms (HttpClient probably ran)");
  }

  @Test
  @DisplayName("null is rejected — kill the null-check mutant")
  void nullIsRejected() {
    assertFalse(PasswordPreflight.isAcceptable(null));
  }

  @Test
  @DisplayName("empty string is rejected")
  void emptyIsRejected() {
    assertFalse(PasswordPreflight.isAcceptable(""));
  }

  @ParameterizedTest
  @DisplayName("known-bad passwords from the local blocklist are rejected")
  @ValueSource(strings = {
      "password", "password1", "password123",
      "qwerty", "qwerty123", "letmein",
      "admin", "admin123", "administrator",
      "welcome", "welcome1",
      "12345678", "123456789", "abc12345",
      "iloveyou", "monkey", "dragon",
      "hunter2", "trustno1"})
  void blocklistedPasswordsRejected(String pw) {
    assertFalse(PasswordPreflight.isAcceptable(pw),
        "expected '" + pw + "' to be flagged by the local blocklist");
  }

  @Test
  @DisplayName("a long random-looking password not on the list is accepted")
  void acceptableLongPasswordPasses() {
    assertTrue(PasswordPreflight.isAcceptable("zR9q!sX5#kP2@dM7L"));
  }
}
