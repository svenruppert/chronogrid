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

package junit.com.svenruppert.chronogrid.auth;

import com.svenruppert.chronogrid.auth.GoogleOAuthCredentials;
import com.svenruppert.chronogrid.auth.OAuthFlowRegistry;
import com.svenruppert.chronogrid.auth.OAuthFlowRegistry.OAuthFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OAuthFlowRegistry — Planning-Feature #9 Schicht 4")
class OAuthFlowRegistryTest {

  @BeforeEach
  void clean() {
    OAuthFlowRegistry.reset();
  }

  private static OAuthFlow flow() {
    return new OAuthFlow(
        "verifier-abc-must-be-long-enough-for-pkce",
        "client-id.apps.googleusercontent.com",
        "client-secret",
        "https://app/oauth/callback/google",
        (creds, err) -> { });
  }

  @Test
  @DisplayName("register + consume round-trips the flow exactly once")
  void registerThenConsume() {
    OAuthFlow f = flow();
    OAuthFlowRegistry.register("state-1", f);
    assertTrue(OAuthFlowRegistry.isRegistered("state-1"));
    OAuthFlow back = OAuthFlowRegistry.consume("state-1");
    assertEquals(f, back);
    assertFalse(OAuthFlowRegistry.isRegistered("state-1"),
        "consume must remove the entry — replay attacks would otherwise "
            + "succeed if Google's redirect happens twice for some reason");
    assertNull(OAuthFlowRegistry.consume("state-1"),
        "second consume must return null");
  }

  @Test
  @DisplayName("consume(unknown) returns null instead of throwing")
  void consumeUnknownReturnsNull() {
    assertNull(OAuthFlowRegistry.consume("never-registered"));
  }

  @Test
  @DisplayName("blank state and null flow are rejected fast")
  void rejectsInvalidInputs() {
    assertThrows(IllegalArgumentException.class,
        () -> OAuthFlowRegistry.register("", flow()));
    assertThrows(IllegalArgumentException.class,
        () -> OAuthFlowRegistry.register(null, flow()));
    assertThrows(IllegalArgumentException.class,
        () -> OAuthFlowRegistry.register("state", null));
  }

  @Test
  @DisplayName("OAuthFlow record rejects blank parameters")
  void flowRecordValidates() {
    assertThrows(IllegalArgumentException.class,
        () -> new OAuthFlow("", "ci", "cs", "r", (c, e) -> { }));
    assertThrows(IllegalArgumentException.class,
        () -> new OAuthFlow("v", "", "cs", "r", (c, e) -> { }));
    assertThrows(IllegalArgumentException.class,
        () -> new OAuthFlow("v", "ci", "", "r", (c, e) -> { }));
    assertThrows(IllegalArgumentException.class,
        () -> new OAuthFlow("v", "ci", "cs", "", (c, e) -> { }));
    assertThrows(IllegalArgumentException.class,
        () -> new OAuthFlow("v", "ci", "cs", "r", null));
  }

  @Test
  @DisplayName("two concurrent flows can coexist under distinct states")
  void multipleStatesCoexist() {
    OAuthFlow a = flow();
    OAuthFlow b = flow();
    OAuthFlowRegistry.register("state-a", a);
    OAuthFlowRegistry.register("state-b", b);
    assertTrue(OAuthFlowRegistry.isRegistered("state-a"));
    assertTrue(OAuthFlowRegistry.isRegistered("state-b"));
    assertEquals(a, OAuthFlowRegistry.consume("state-a"));
    assertTrue(OAuthFlowRegistry.isRegistered("state-b"),
        "consuming one flow must not touch the other");
    assertEquals(b, OAuthFlowRegistry.consume("state-b"));
  }

  @Test
  @DisplayName("flow's onComplete callback receives the typed credentials object on success")
  void onCompleteReceivesTypedResult() {
    AtomicReference<GoogleOAuthCredentials> credsBox = new AtomicReference<>();
    AtomicReference<String> errBox = new AtomicReference<>();
    OAuthFlow f = new OAuthFlow("verifier", "ci.apps.googleusercontent.com",
        "cs", "https://r", (c, e) -> {
          credsBox.set(c);
          errBox.set(e);
        });
    GoogleOAuthCredentials want = new GoogleOAuthCredentials(
        "ci.apps.googleusercontent.com", "cs", "1//refresh-xyz");
    f.onComplete().accept(want, null);
    assertNotNull(credsBox.get());
    assertEquals(want, credsBox.get());
    assertNull(errBox.get());
  }
}
