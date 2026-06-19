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

import com.svenruppert.flow.security.bootstrap.HardeningBootstrapExtension;
import com.svenruppert.jsentinel.credential.password.PasswordHashingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("HardeningBootstrapExtension — layer-3 hardening (order=20)")
class HardeningBootstrapExtensionTest {

  private final HardeningBootstrapExtension ext = new HardeningBootstrapExtension();

  @Test
  @DisplayName("contributeCredentials switches hashing to a BouncyCastle-modern service")
  void credentialsSwitchToModern() {
    RecordingBootstraps.RecCredentials cred = new RecordingBootstraps.RecCredentials();
    ext.contributeCredentials(cred);

    assertEquals(1, cred.hashingCalls.size());
    PasswordHashingService installed = cred.hashingCalls.get(0);
    assertNotNull(installed);
    // BC-modern emits Argon2id envelopes — verify by hashing a probe.
    String envelope = installed.hash("probe-12345".toCharArray()).encodedHash();
    assert envelope.contains("argon2id")
        : "expected modern (Argon2id) profile, got envelope: " + envelope;
  }

  @Test
  @DisplayName("contributeSessions wires the version store + subject-id resolver via SPI")
  void sessionsBindDriftDetection() {
    RecordingBootstraps.RecSession session = new RecordingBootstraps.RecSession();
    ext.contributeSessions(session);

    // SPIs are registered through META-INF/services in this project — so the
    // extension's lookups must produce non-empty Optionals and the corresponding
    // builder calls happen exactly once each.
    assertEquals(1, session.versionStoreCalls.size(),
        "JSentinelVersionStore SPI binding missed (or duplicated)");
    assertEquals(1, session.resolverCalls.size(),
        "SubjectIdResolver SPI binding missed (or duplicated)");
  }

  @Test
  @DisplayName("contributeAudit is a no-op — hardening doesn't touch the audit chain")
  void auditUntouched() {
    RecordingBootstraps.RecAudit audit = new RecordingBootstraps.RecAudit();
    ext.contributeAudit(audit);
    assertEquals(0, audit.ringBufferSizes.size());
    assertEquals(0, audit.storeBackedCalls.size());
    assertEquals(0, audit.loggingCalls);
  }

  @Test
  @DisplayName("order() is 20 — runs after persistence (10) and after layer-1 default (0)")
  void orderIsTwenty() {
    assertEquals(20, ext.order());
  }
}
