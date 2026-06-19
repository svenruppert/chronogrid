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

import com.svenruppert.flow.security.bootstrap.JSentinelBootstrapInitListener;
import com.vaadin.flow.server.ServiceInitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JSentinelBootstrapInitListener — serviceInit() wiring")
class JSentinelBootstrapInitListenerTest {

  @BeforeEach
  void resetDone() throws Exception {
    setDone(false);
  }

  @AfterEach
  void restoreDone() throws Exception {
    // Other tests in this package rely on DONE being unset between runs.
    setDone(false);
  }

  @Test
  @DisplayName("serviceInit registers exactly one UIInitListener on the service")
  void registersOneUIInitListener() {
    RecordingVaadinService service = new RecordingVaadinService();
    ServiceInitEvent event = new ServiceInitEvent(service);

    new JSentinelBootstrapInitListener().serviceInit(event);

    assertEquals(1, service.captured.size(),
        "serviceInit must register exactly one UIInitListener");
  }

  @Test
  @DisplayName("DONE flag flips to true after the first serviceInit call")
  void doneFlagFlips() throws Exception {
    assertEquals(false, doneValue(),
        "precondition — DONE was reset");

    new JSentinelBootstrapInitListener()
        .serviceInit(new ServiceInitEvent(new RecordingVaadinService()));

    assertEquals(true, doneValue(),
        "after first serviceInit, DONE must be true — guards against re-install");
  }

  @Test
  @DisplayName("second serviceInit invocation respects the DONE flag (idempotent)")
  void secondCallSkipsPipeline() throws Exception {
    JSentinelBootstrapInitListener listener = new JSentinelBootstrapInitListener();

    RecordingVaadinService firstService = new RecordingVaadinService();
    listener.serviceInit(new ServiceInitEvent(firstService));

    RecordingVaadinService secondService = new RecordingVaadinService();
    listener.serviceInit(new ServiceInitEvent(secondService));

    // Per-event UIInitListener still registers (each request gets a forwarder)
    assertEquals(1, firstService.captured.size());
    assertEquals(1, secondService.captured.size());
    // DONE remains true — the pipeline did not run again
    assertTrue(doneValue(),
        "DONE must remain true after second invocation");
  }

  // ── reflection helpers ──────────────────────────────────────

  private static boolean doneValue() throws Exception {
    Field f = JSentinelBootstrapInitListener.class.getDeclaredField("DONE");
    f.setAccessible(true);
    return ((AtomicBoolean) f.get(null)).get();
  }

  private static void setDone(boolean value) throws Exception {
    Field f = JSentinelBootstrapInitListener.class.getDeclaredField("DONE");
    f.setAccessible(true);
    ((AtomicBoolean) f.get(null)).set(value);
  }
}
