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

package junit.com.svenruppert.flow.security.bootstrap;

import com.svenruppert.flow.security.bootstrap.BootstrapWiring;
import com.svenruppert.jsentinel.bootstrap.BootstrapMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("BootstrapWiring — lazy singleton + pipeline wiring")
class BootstrapWiringTest {

  @Test
  @DisplayName("DEFAULT_MODE is PERSISTENT_FILE — guards against config-mode drift")
  void defaultModeConstant() {
    assertEquals(BootstrapMode.PERSISTENT_FILE, BootstrapWiring.DEFAULT_MODE);
  }

  @Test
  @DisplayName("DEFAULT_TOKEN_FILE resolves via AppStoragePaths — ends in jsentinel/bootstrap.token")
  void defaultTokenFileConstant() {
    Path actual = BootstrapWiring.DEFAULT_TOKEN_FILE;
    // The path is now dynamic — depends on -Dapp.storage.dir. We
    // assert the invariant suffix instead of the literal default.
    assertEquals("bootstrap.token",
        actual.getFileName().toString());
    assertEquals("jsentinel",
        actual.getParent().getFileName().toString());
  }

  @Test
  @DisplayName("MIN_PASSWORD_LENGTH is 12 — surfaced in the SetupView helper text")
  void minPasswordLengthConstant() {
    assertEquals(12, BootstrapWiring.MIN_PASSWORD_LENGTH);
  }

  @Test
  @DisplayName("instance() returns the same wiring across calls")
  void instanceIsCached() {
    BootstrapWiring a = BootstrapWiring.instance();
    BootstrapWiring b = BootstrapWiring.instance();
    assertSame(a, b, "instance() must cache the wiring");
  }

  @Test
  @DisplayName("instance() exposes a non-null stateService + bootstrapService")
  void wiringHasBothServices() {
    BootstrapWiring wiring = BootstrapWiring.instance();
    assertNotNull(wiring.stateService(),
        "BootstrapStateService must be wired");
    assertNotNull(wiring.bootstrapService(),
        "InitialAdminBootstrapService must be wired");
  }
}
