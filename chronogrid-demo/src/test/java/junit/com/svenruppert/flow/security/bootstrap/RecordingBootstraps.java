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

import com.svenruppert.jsentinel.audit.AuditEventStore;
import com.svenruppert.jsentinel.audit.JSentinelAuditService;
import com.svenruppert.jsentinel.authentication.PasswordHasher;
import com.svenruppert.jsentinel.authorization.api.SubjectIdResolver;
import com.svenruppert.jsentinel.credential.password.PasswordHashingService;
import com.svenruppert.jsentinel.credential.password.pepper.PepperService;
import com.svenruppert.jsentinel.credential.store.CredentialStore;
import com.svenruppert.jsentinel.dx.bootstrap.AuditBootstrap;
import com.svenruppert.jsentinel.dx.bootstrap.CredentialBootstrap;
import com.svenruppert.jsentinel.dx.bootstrap.SessionBootstrap;
import com.svenruppert.jsentinel.session.JSentinelVersionStore;
import com.svenruppert.jsentinel.session.SessionPolicy;
import com.svenruppert.jsentinel.session.SessionStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only recording stubs for the three fluent sub-builders the
 * {@code BootstrapExtension} contract operates on. Each call records
 * its argument and returns {@code this} so chained calls work.
 */
final class RecordingBootstraps {

  private RecordingBootstraps() {
  }

  static final class RecAudit implements AuditBootstrap {
    final List<JSentinelAuditService> serviceCalls = new ArrayList<>();
    final List<AuditEventStore> storeBackedCalls = new ArrayList<>();
    int loggingCalls;
    final List<Integer> ringBufferSizes = new ArrayList<>();
    final List<Boolean> credentialEventsCalls = new ArrayList<>();

    @Override public AuditBootstrap securityAuditService(JSentinelAuditService s) {
      serviceCalls.add(s); return this;
    }
    @Override public AuditBootstrap storeBacked(AuditEventStore store) {
      storeBackedCalls.add(store); return this;
    }
    @Override public AuditBootstrap logging() {
      loggingCalls++; return this;
    }
    @Override public AuditBootstrap ringBuffer(int size) {
      ringBufferSizes.add(size); return this;
    }
    @Override public AuditBootstrap credentialEvents(boolean v) {
      credentialEventsCalls.add(v); return this;
    }
  }

  static final class RecSession implements SessionBootstrap {
    final List<SessionStore> storeBackedCalls = new ArrayList<>();
    final List<JSentinelVersionStore> versionStoreCalls = new ArrayList<>();
    final List<SubjectIdResolver<?>> resolverCalls = new ArrayList<>();
    final List<Duration> timeoutCalls = new ArrayList<>();
    final List<Duration> lifetimeCalls = new ArrayList<>();
    final List<SessionPolicy<?>> policyCalls = new ArrayList<>();

    @Override public SessionBootstrap storeBacked(SessionStore s) {
      storeBackedCalls.add(s); return this;
    }
    @Override public SessionBootstrap securityVersion(JSentinelVersionStore s) {
      versionStoreCalls.add(s); return this;
    }
    @Override public SessionBootstrap subjectIdResolver(SubjectIdResolver<?> r) {
      resolverCalls.add(r); return this;
    }
    @Override public SessionBootstrap timeout(Duration d) {
      timeoutCalls.add(d); return this;
    }
    @Override public SessionBootstrap absoluteLifetime(Duration d) {
      lifetimeCalls.add(d); return this;
    }
    @Override public SessionBootstrap policy(SessionPolicy<?> p) {
      policyCalls.add(p); return this;
    }
  }

  static final class RecCredentials implements CredentialBootstrap {
    final List<PasswordHashingService> hashingCalls = new ArrayList<>();
    final List<PasswordHasher> hasherCalls = new ArrayList<>();
    final List<PepperService> pepperCalls = new ArrayList<>();
    final List<CredentialStore> storeCalls = new ArrayList<>();
    int pbkdf2Calls;
    int modernCalls;

    @Override public CredentialBootstrap passwordHasher(PasswordHasher h) {
      hasherCalls.add(h); return this;
    }
    @Override public CredentialBootstrap hashing(PasswordHashingService s) {
      hashingCalls.add(s); return this;
    }
    @Override public CredentialBootstrap pbkdf2Defaults() {
      pbkdf2Calls++; return this;
    }
    @Override public CredentialBootstrap modern() {
      modernCalls++; return this;
    }
    @Override public CredentialBootstrap pepper(PepperService p) {
      pepperCalls.add(p); return this;
    }
    @Override public CredentialBootstrap credentialStore(CredentialStore s) {
      storeCalls.add(s); return this;
    }
    @Override public CredentialBootstrap passwordChange(
        com.svenruppert.jsentinel.credential.change.PasswordChangeService s) {
      return this;
    }
    @Override public CredentialBootstrap passwordReset(
        com.svenruppert.jsentinel.credential.reset.PasswordResetService s) {
      return this;
    }
  }
}
