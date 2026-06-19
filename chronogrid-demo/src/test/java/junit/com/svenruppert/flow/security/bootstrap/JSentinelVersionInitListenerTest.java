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

import com.svenruppert.flow.security.bootstrap.JSentinelVersionInitListener;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.session.JSentinelVersionStore;
import com.vaadin.flow.server.ServiceInitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JSentinelVersionInitListener — serviceInit() wires the drift enforcer")
class JSentinelVersionInitListenerTest {

  private JSentinelVersionStore savedStore;

  @BeforeEach
  void capturePrior() {
    savedStore = JSentinelServiceResolver.findJSentinelVersionStore().orElse(null);
  }

  @AfterEach
  void restorePrior() {
    if (savedStore != null) {
      JSentinelServiceResolver.setJSentinelVersionStore(savedStore);
    }
  }

  @Test
  @DisplayName("SPI lookup must succeed in this project — sanity precondition")
  void spiPresent() {
    Optional<JSentinelVersionStore> store =
        JSentinelServiceResolver.findJSentinelVersionStore();
    assertTrue(store.isPresent(),
        "META-INF/services must register a JSentinelVersionStore — drift "
            + "detection would silently no-op without it");
    assertNotNull(store.get());
  }

  @Test
  @DisplayName("with SPI present → exactly one UIInitListener registered")
  void presentSpiRegistersOneListener() {
    RecordingVaadinService service = new RecordingVaadinService();

    new JSentinelVersionInitListener().serviceInit(new ServiceInitEvent(service));

    assertEquals(1, service.captured.size(),
        "with the version store SPI present, exactly one UIInitListener "
            + "must register");
  }

  @Test
  @DisplayName("listener instance is constructable without arguments — Vaadin SPI contract")
  void constructorIsNoArg() {
    JSentinelVersionInitListener instance = new JSentinelVersionInitListener();
    assertNotNull(instance,
        "Vaadin requires a no-arg constructor for VaadinServiceInitListener "
            + "implementations registered via META-INF/services");
  }
}
