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

import com.svenruppert.flow.security.bootstrap.PersistenceBootstrapExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("PersistenceBootstrapExtension — layer-2 Eclipse-Store wiring (order=10)")
class PersistenceBootstrapExtensionTest {

  private final PersistenceBootstrapExtension ext = new PersistenceBootstrapExtension();

  @Test
  @DisplayName("contributeAudit binds storeBacked(auditEventStore) + logging")
  void auditBindsStoreAndLogging() {
    RecordingBootstraps.RecAudit audit = new RecordingBootstraps.RecAudit();
    ext.contributeAudit(audit);

    assertEquals(1, audit.storeBackedCalls.size(),
        "storeBacked(AuditEventStore) must be called exactly once");
    assertNotNull(audit.storeBackedCalls.get(0),
        "the bound AuditEventStore must be non-null");
    assertEquals(1, audit.loggingCalls,
        "logging() must be chained immediately after storeBacked");
    // Persistence layer must NOT install a competing ring buffer
    assertEquals(0, audit.ringBufferSizes.size());
  }

  @Test
  @DisplayName("contributeSessions binds storeBacked(sessionStore) — nothing else")
  void sessionsBindStoreOnly() {
    RecordingBootstraps.RecSession session = new RecordingBootstraps.RecSession();
    ext.contributeSessions(session);

    assertEquals(1, session.storeBackedCalls.size());
    assertNotNull(session.storeBackedCalls.get(0));
    // Persistence does NOT touch version-store or resolver — that's layer 3's job
    assertEquals(0, session.versionStoreCalls.size());
    assertEquals(0, session.resolverCalls.size());
  }

  @Test
  @DisplayName("contributeCredentials is a no-op — persistence doesn't choose hashing")
  void credentialsUntouched() {
    RecordingBootstraps.RecCredentials cred = new RecordingBootstraps.RecCredentials();
    ext.contributeCredentials(cred);

    assertEquals(0, cred.hashingCalls.size());
    assertEquals(0, cred.storeCalls.size());
    assertEquals(0, cred.pbkdf2Calls);
    assertEquals(0, cred.modernCalls);
  }

  @Test
  @DisplayName("order() is 10 — between defaults (0) and hardening (20)")
  void orderIsTen() {
    assertEquals(10, ext.order());
  }
}
