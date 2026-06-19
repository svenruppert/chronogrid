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

import com.svenruppert.flow.security.bootstrap.BootstrapBuilder;
import com.svenruppert.flow.security.bootstrap.BootstrapExtension;
import com.svenruppert.jsentinel.dx.bootstrap.AuditBootstrap;
import com.svenruppert.jsentinel.dx.bootstrap.CredentialBootstrap;
import com.svenruppert.jsentinel.dx.bootstrap.SessionBootstrap;
import com.svenruppert.jsentinel.dx.vaadin.bootstrap.VaadinJSentinelBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("BootstrapBuilder — ServiceLoader fan-out into a single audit/sessions/credentials chain")
class BootstrapBuilderTest {

  @Test
  @DisplayName("apply() returns the same builder it was given (chainable)")
  void applyReturnsSameBuilder() {
    RecordingBootstrapChain chain = new RecordingBootstrapChain();
    VaadinJSentinelBootstrap given = chain.proxy();

    VaadinJSentinelBootstrap result = BootstrapBuilder.apply(given);

    assertSame(given, result,
        "BootstrapBuilder.apply() must return the supplied builder unchanged");
  }

  @Test
  @DisplayName("apply() calls audit / sessions / credentials exactly once each")
  void applyCallsAllThreeSubBuilders() {
    RecordingBootstrapChain chain = new RecordingBootstrapChain();
    BootstrapBuilder.apply(chain.proxy());

    assertEquals(1, chain.auditConsumers.size(), "audit(Consumer) called once");
    assertEquals(1, chain.sessionConsumers.size(), "sessions(Consumer) called once");
    assertEquals(1, chain.credentialConsumers.size(), "credentials(Consumer) called once");
  }

  @Test
  @DisplayName("each consumer dispatches to every registered extension's contribute* hook")
  void consumersFanOutToEveryExtension() {
    RecordingBootstrapChain chain = new RecordingBootstrapChain();
    BootstrapBuilder.apply(chain.proxy());

    // Drive each captured consumer with a recording sub-builder. Whatever
    // BootstrapBuilder did internally is replayed here, against a known
    // recipient — that's how we observe "did it fan out to every extension?"
    RecordingBootstraps.RecAudit audit = new RecordingBootstraps.RecAudit();
    chain.auditConsumers.get(0).accept(audit);

    RecordingBootstraps.RecSession session = new RecordingBootstraps.RecSession();
    chain.sessionConsumers.get(0).accept(session);

    RecordingBootstraps.RecCredentials cred = new RecordingBootstraps.RecCredentials();
    chain.credentialConsumers.get(0).accept(cred);

    // We expect at least default + persistence + hardening registered
    // (the project ships all three META-INF/services entries).
    // Layer 1 (default): ringBuffer(256) + logging() → audit
    // Layer 2 (persistence): storeBacked + logging() → audit
    // Layer 3 (hardening): no audit contribution
    // So at minimum: one ringBuffer call from default, one storeBacked
    // from persistence, two logging() calls total.
    assertEquals(1, audit.ringBufferSizes.size(),
        "default extension must contribute ringBuffer(256)");
    assertEquals(256, audit.ringBufferSizes.get(0).intValue());
    assertEquals(1, audit.storeBackedCalls.size(),
        "persistence extension must contribute storeBacked");
    assertEquals(2, audit.loggingCalls,
        "both default and persistence call logging() — order matters");

    // Sessions: persistence binds the store, hardening binds version + resolver
    assertEquals(1, session.storeBackedCalls.size());
    assertEquals(1, session.versionStoreCalls.size());
    assertEquals(1, session.resolverCalls.size());

    // Credentials: default installs hashing(default), hardening replaces with modern
    assertEquals(2, cred.hashingCalls.size(),
        "both default and hardening call hashing(...) — replace semantics");
  }

  @Test
  @DisplayName("extensions are invoked in ascending order() — default (0) then persistence (10) then hardening (20)")
  void extensionsAppliedInOrder() {
    RecordingBootstrapChain chain = new RecordingBootstrapChain();
    BootstrapBuilder.apply(chain.proxy());

    // Verify by ordering: default's hashing call must precede hardening's
    // because hardening's modern profile is meant to OVERRIDE.
    OrderRecordingCredentialBootstrap probe = new OrderRecordingCredentialBootstrap();
    chain.credentialConsumers.get(0).accept(probe);

    assertEquals(2, probe.hashingProfileOrder.size(),
        "expected two hashing(...) calls (default + hardening)");
    String first = String.valueOf(probe.hashingProfileOrder.get(0));
    String second = String.valueOf(probe.hashingProfileOrder.get(1));
    assertNotNull(first);
    assertNotNull(second);
    // default is PBKDF2; hardening is argon2id — the SECOND envelope must be argon2id.
    String secondEnvelope = probe.hashingProfileOrder.get(1).hash("seed-12345".toCharArray()).encodedHash();
    assert secondEnvelope.contains("argon2id")
        : "hardening (order=20) must run AFTER default (order=0), but second hashing call wasn't argon2id: " + secondEnvelope;
  }

  // ─────────────────────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────────────────────

  /**
   * Captures every {@code audit(Consumer)} / {@code sessions(Consumer)} /
   * {@code credentials(Consumer)} call on a synthetic {@link VaadinJSentinelBootstrap}.
   * All other methods on the interface are made to return the proxy itself
   * so the fluent chain doesn't NPE.
   */
  private static final class RecordingBootstrapChain {
    final List<Consumer<AuditBootstrap>> auditConsumers = new ArrayList<>();
    final List<Consumer<SessionBootstrap>> sessionConsumers = new ArrayList<>();
    final List<Consumer<CredentialBootstrap>> credentialConsumers = new ArrayList<>();

    @SuppressWarnings("unchecked")
    VaadinJSentinelBootstrap proxy() {
      return (VaadinJSentinelBootstrap) Proxy.newProxyInstance(
          VaadinJSentinelBootstrap.class.getClassLoader(),
          new Class<?>[]{VaadinJSentinelBootstrap.class},
          (p, method, args) -> dispatch(p, method, args));
    }

    @SuppressWarnings("unchecked")
    private Object dispatch(Object proxy, Method method, Object[] args) {
      String name = method.getName();
      switch (name) {
        case "audit":
          auditConsumers.add((Consumer<AuditBootstrap>) args[0]);
          return proxy;
        case "sessions":
          sessionConsumers.add((Consumer<SessionBootstrap>) args[0]);
          return proxy;
        case "credentials":
          credentialConsumers.add((Consumer<CredentialBootstrap>) args[0]);
          return proxy;
        default:
          // every other CommonJSentinelBootstrap method returns the builder
          if (method.getReturnType().isAssignableFrom(VaadinJSentinelBootstrap.class)) {
            return proxy;
          }
          return null;
      }
    }
  }

  /**
   * CredentialBootstrap that records ONLY the hashing-service calls in
   * arrival order. Used to assert the runtime order matches the configured
   * {@link BootstrapExtension#order()} priorities.
   */
  private static final class OrderRecordingCredentialBootstrap implements CredentialBootstrap {
    final List<com.svenruppert.jsentinel.credential.password.PasswordHashingService> hashingProfileOrder = new ArrayList<>();

    @Override public CredentialBootstrap passwordHasher(com.svenruppert.jsentinel.authentication.PasswordHasher h) {
      return this;
    }
    @Override public CredentialBootstrap hashing(com.svenruppert.jsentinel.credential.password.PasswordHashingService s) {
      hashingProfileOrder.add(s); return this;
    }
    @Override public CredentialBootstrap pbkdf2Defaults() { return this; }
    @Override public CredentialBootstrap modern() { return this; }
    @Override public CredentialBootstrap pepper(com.svenruppert.jsentinel.credential.password.pepper.PepperService p) {
      return this;
    }
    @Override public CredentialBootstrap credentialStore(com.svenruppert.jsentinel.credential.store.CredentialStore s) {
      return this;
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
