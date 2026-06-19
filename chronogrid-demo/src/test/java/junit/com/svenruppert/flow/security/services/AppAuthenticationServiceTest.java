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

import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.model.Credentials;
import com.svenruppert.flow.security.model.InMemoryUserDirectoryPersistence;
import com.svenruppert.flow.security.model.PersistentUserDirectory;
import com.svenruppert.flow.security.model.UserDirectoryProvider;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.flow.security.services.AppAuthenticationService;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.bruteforce.LoginAttemptContext;
import com.svenruppert.jsentinel.bruteforce.LoginAttemptDecision;
import com.svenruppert.jsentinel.bruteforce.LoginAttemptPolicy;
import com.svenruppert.jsentinel.credential.password.bouncycastle.BouncyCastleHashingServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AppAuthenticationService — brute-force-protected login pipeline")
class AppAuthenticationServiceTest {

  private AppAuthenticationService service;
  private RecordingPolicy policy;
  private LoginAttemptPolicy previousPolicy;

  @BeforeEach
  void setUp() {
    PersistentUserDirectory seeded = new PersistentUserDirectory(
        new InMemoryUserDirectoryPersistence(),
        BouncyCastleHashingServices.modern());
    seeded.addUser("alice", "abcdef-abcdef-1",
        new AppUser(10L, "Alice", EnumSet.of(AuthorizationRole.USER)));
    UserDirectoryProvider.setDirectory(seeded);

    policy = new RecordingPolicy();
    previousPolicy = JSentinelServiceResolver.findLoginAttemptPolicy().orElse(null);
    JSentinelServiceResolver.setLoginAttemptPolicy(policy);

    service = new AppAuthenticationService();
  }

  @AfterEach
  void tearDown() {
    JSentinelServiceResolver.setLoginAttemptPolicy(previousPolicy);
  }

  // ── subjectType ────────────────────────────────────────────────

  @Test
  @DisplayName("subjectType is AppUser.class")
  void subjectTypeIsAppUser() {
    assertSame(AppUser.class, service.subjectType());
  }

  // ── loadSubject ────────────────────────────────────────────────

  @Test
  @DisplayName("loadSubject returns the matching user for valid credentials")
  void loadSubjectFindsUser() {
    AppUser u = service.loadSubject(new Credentials("alice", "abcdef-abcdef-1"));
    assertEquals("Alice", u.name());
  }

  @Test
  @DisplayName("loadSubject returns null for an unknown username (legacy contract)")
  void loadSubjectNullForUnknown() {
    AppUser u = service.loadSubject(new Credentials("ghost", "nopassword"));
    org.junit.jupiter.api.Assertions.assertNull(u);
  }

  // ── checkCredentials — happy path ──────────────────────────────

  @Test
  @DisplayName("valid credentials → true; policy.recordSuccess called exactly once")
  void validCredentialsRecordSuccess() {
    boolean ok = service.checkCredentials(new Credentials("alice", "abcdef-abcdef-1"));
    assertTrue(ok);
    assertEquals(1, policy.successes.size());
    assertEquals(0, policy.failures.size());
  }

  // ── checkCredentials — sad paths ───────────────────────────────

  @Test
  @DisplayName("wrong password → false; recordFailure called, not recordSuccess")
  void wrongPasswordRecordsFailure() {
    boolean ok = service.checkCredentials(new Credentials("alice", "guess-guess-guess"));
    assertFalse(ok);
    assertEquals(0, policy.successes.size());
    assertEquals(1, policy.failures.size());
  }

  @Test
  @DisplayName("unknown username → false; recordFailure called")
  void unknownUsernameRecordsFailure() {
    boolean ok = service.checkCredentials(new Credentials("ghost", "anything-anything"));
    assertFalse(ok);
    assertEquals(1, policy.failures.size());
  }

  @Test
  @DisplayName("null credentials → false without consulting policy at all")
  void nullCredentialsShortCircuit() {
    boolean ok = service.checkCredentials(null);
    assertFalse(ok);
    assertEquals(0, policy.beforeCalls.size());
    assertEquals(0, policy.successes.size());
    assertEquals(0, policy.failures.size());
  }

  // ── checkCredentials — throttle path ───────────────────────────

  @Test
  @DisplayName("policy returns LockedOut → checkCredentials short-circuits with false; no record")
  void lockedOutShortCircuits() {
    policy.nextDecision = LoginAttemptDecision.lockedOut(Duration.ofSeconds(30), 5);

    boolean ok = service.checkCredentials(new Credentials("alice", "abcdef-abcdef-1"));

    assertFalse(ok);
    assertEquals(1, policy.beforeCalls.size());
    assertEquals(0, policy.successes.size(),
        "LockedOut path must NOT record the call as a success");
    assertEquals(0, policy.failures.size(),
        "LockedOut path must NOT record the call as a failure");
  }

  @Test
  @DisplayName("policy.beforeAttempt receives the username from the credentials")
  void beforeAttemptCarriesUsername() {
    service.checkCredentials(new Credentials("alice", "abcdef-abcdef-1"));
    assertEquals("alice", policy.beforeCalls.get(0).username());
  }

  // ── Recording LoginAttemptPolicy ───────────────────────────────

  private static final class RecordingPolicy implements LoginAttemptPolicy {
    final List<LoginAttemptContext> beforeCalls = new ArrayList<>();
    final List<LoginAttemptContext> successes = new ArrayList<>();
    final List<LoginAttemptContext> failures = new ArrayList<>();
    LoginAttemptDecision nextDecision = LoginAttemptDecision.allowed();

    @Override public LoginAttemptDecision beforeAttempt(LoginAttemptContext ctx) {
      beforeCalls.add(ctx);
      return nextDecision;
    }
    @Override public void recordSuccess(LoginAttemptContext ctx) { successes.add(ctx); }
    @Override public void recordFailure(LoginAttemptContext ctx) { failures.add(ctx); }
  }
}
