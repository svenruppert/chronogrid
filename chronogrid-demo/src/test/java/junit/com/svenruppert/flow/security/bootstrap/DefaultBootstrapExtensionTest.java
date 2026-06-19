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

import com.svenruppert.flow.security.bootstrap.DefaultBootstrapExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("DefaultBootstrapExtension — layer-1 defaults (order=0)")
class DefaultBootstrapExtensionTest {

  private final DefaultBootstrapExtension ext = new DefaultBootstrapExtension();

  @Test
  @DisplayName("contributeAudit installs a 256-entry ring buffer + logging")
  void auditDefaults() {
    RecordingBootstraps.RecAudit audit = new RecordingBootstraps.RecAudit();
    ext.contributeAudit(audit);
    assertEquals(1, audit.ringBufferSizes.size());
    assertEquals(256, audit.ringBufferSizes.get(0));
    assertEquals(1, audit.loggingCalls);
  }

  @Test
  @DisplayName("contributeCredentials installs a PBKDF2-defaults PasswordHashingService")
  void credentialsDefaults() {
    RecordingBootstraps.RecCredentials cred = new RecordingBootstraps.RecCredentials();
    ext.contributeCredentials(cred);
    assertEquals(1, cred.hashingCalls.size());
  }

  @Test
  @DisplayName("contributeSessions has no defaults — layer 1 doesn't touch sessions")
  void sessionsUntouched() {
    RecordingBootstraps.RecSession session = new RecordingBootstraps.RecSession();
    ext.contributeSessions(session);
    assertEquals(0, session.storeBackedCalls.size());
    assertEquals(0, session.versionStoreCalls.size());
    assertEquals(0, session.resolverCalls.size());
  }

  @Test
  @DisplayName("order() is 0 — must run before any higher-numbered extension")
  void orderIsZero() {
    assertEquals(0, ext.order());
  }
}
