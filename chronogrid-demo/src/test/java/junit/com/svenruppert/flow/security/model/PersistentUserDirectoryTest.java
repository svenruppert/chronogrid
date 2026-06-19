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

package junit.com.svenruppert.flow.security.model;

import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.model.Credentials;
import com.svenruppert.flow.security.model.InMemoryUserDirectoryPersistence;
import com.svenruppert.flow.security.model.PersistentUserDirectory;
import com.svenruppert.flow.security.model.StoredUser;
import com.svenruppert.flow.security.model.UserDirectoryPersistence;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.jsentinel.audit.AuditEvent;
import com.svenruppert.jsentinel.audit.AuditQuery;
import com.svenruppert.jsentinel.audit.JSentinelAuditService;
import com.svenruppert.jsentinel.audit.RoleAssigned;
import com.svenruppert.jsentinel.audit.RoleRevoked;
import com.svenruppert.jsentinel.audit.UserCreated;
import com.svenruppert.jsentinel.audit.UserDeleted;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.credential.password.PasswordHashingServices;
import com.svenruppert.jsentinel.credential.password.bouncycastle.BouncyCastleHashingServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link InMemoryUserDirectoryPersistence} seam keeps these tests
 * deterministic and disk-free. Real Argon2id hashing from
 * {@link BouncyCastleHashingServices#modern()} so wire formats match
 * what the production hashing path produces.
 */
@DisplayName("PersistentUserDirectory — CRUD + lifecycle on InMemoryUserDirectoryPersistence")
class PersistentUserDirectoryTest {

  private UserDirectoryPersistence persistence;
  private PersistentUserDirectory directory;
  private RecordingAudit audit;
  private JSentinelAuditService previousAudit;

  @BeforeEach
  void setUp() {
    persistence = new InMemoryUserDirectoryPersistence();
    directory = new PersistentUserDirectory(
        persistence, BouncyCastleHashingServices.modern());
    // Install the recording sink so audit-call assertions can observe
    // UserCreated / UserDeleted / RoleAssigned / RoleRevoked events.
    audit = new RecordingAudit();
    previousAudit = JSentinelServiceResolver.findJSentinelAuditService().orElse(null);
    JSentinelServiceResolver.setJSentinelAuditService(audit);
  }

  @AfterEach
  void tearDown() {
    JSentinelServiceResolver.setJSentinelAuditService(previousAudit);
  }

  // ── addUser + findByCredentials ────────────────────────────────

  @Test
  @DisplayName("addUser persists, findByCredentials returns the user on correct password")
  void addUserThenLogin() {
    AppUser alice = new AppUser(1L, "Alice", EnumSet.of(AuthorizationRole.USER));
    directory.addUser("alice", "correct-horse-battery-staple", alice);

    Optional<AppUser> result = directory.findByCredentials(
        new Credentials("alice", "correct-horse-battery-staple"));

    assertTrue(result.isPresent());
    assertEquals(alice, result.get());
  }

  @Test
  @DisplayName("findByCredentials returns empty on wrong password (no leak via timing-style assert)")
  void wrongPasswordIsRejected() {
    AppUser bob = new AppUser(2L, "Bob", EnumSet.of(AuthorizationRole.USER));
    directory.addUser("bob", "secretsecret", bob);

    Optional<AppUser> result = directory.findByCredentials(
        new Credentials("bob", "guess-attempt"));

    assertFalse(result.isPresent());
  }

  @Test
  @DisplayName("findByCredentials with unknown username is empty")
  void unknownUsernameIsEmpty() {
    assertFalse(directory.findByCredentials(
        new Credentials("ghost", "anything")).isPresent());
  }

  @Test
  @DisplayName("findByCredentials rejects null inputs without throwing")
  void nullCredentialsReturnEmpty() {
    assertFalse(directory.findByCredentials(null).isPresent());
    assertFalse(directory.findByCredentials(
        new Credentials(null, "x")).isPresent());
    assertFalse(directory.findByCredentials(
        new Credentials("x", null)).isPresent());
  }

  // ── persistence round-trip ─────────────────────────────────────

  @Test
  @DisplayName("a second directory pointed at the same persistence sees the same user")
  void secondDirectoryReloadsUsers() {
    AppUser carol = new AppUser(3L, "Carol", EnumSet.of(AuthorizationRole.USER));
    directory.addUser("carol", "trustno1-trustno1", carol);

    PersistentUserDirectory reloaded = new PersistentUserDirectory(
        persistence, BouncyCastleHashingServices.modern());

    Optional<AppUser> found = reloaded.findByCredentials(
        new Credentials("carol", "trustno1-trustno1"));
    assertTrue(found.isPresent());
    assertEquals(carol, found.get());
  }

  @Test
  @DisplayName("hashed password in the persistence is NOT the plaintext")
  void hashIsNotPlaintext() {
    AppUser dave = new AppUser(4L, "Dave", EnumSet.of(AuthorizationRole.USER));
    directory.addUser("dave", "myplaintextpw", dave);

    Map<String, StoredUser> snapshot = persistence.load();
    String stored = snapshot.get("dave").passwordHash();

    assertNotEquals("myplaintextpw", stored);
    assertTrue(stored.startsWith("$"),
        "expected an Argon2id-style envelope, got: " + stored);
  }

  // ── registerWithHashedPassword ─────────────────────────────────

  @Test
  @DisplayName("registerWithHashedPassword stores the hash verbatim (no double-hashing)")
  void registerWithHashedPasswordSkipsHashing() {
    String preHashed = BouncyCastleHashingServices.modern()
        .hash("verbatim-test".toCharArray()).encodedHash();

    AppUser eve = new AppUser(5L, "Eve", EnumSet.of(AuthorizationRole.ADMIN));
    directory.registerWithHashedPassword("eve", preHashed, eve);

    // Round-trip through the persistence layer must keep the hash byte-identical.
    String reloaded = persistence.load().get("eve").passwordHash();
    assertEquals(preHashed, reloaded);

    // The same plaintext that produced the pre-hash must verify.
    Optional<AppUser> result = directory.findByCredentials(
        new Credentials("eve", "verbatim-test"));
    assertTrue(result.isPresent());
    assertEquals(eve, result.get());
  }

  @Test
  @DisplayName("registerWithHashedPassword refuses to overwrite an existing username")
  void registerWithHashedPasswordDuplicateThrows() {
    String anyHash = BouncyCastleHashingServices.modern()
        .hash("x".toCharArray()).encodedHash();

    AppUser frank = new AppUser(6L, "Frank", EnumSet.of(AuthorizationRole.USER));
    directory.registerWithHashedPassword("frank", anyHash, frank);

    AppUser frankAgain = new AppUser(7L, "Frank2", EnumSet.of(AuthorizationRole.ADMIN));
    assertThrows(IllegalStateException.class,
        () -> directory.registerWithHashedPassword("frank", anyHash, frankAgain));
  }

  // ── role mutations ─────────────────────────────────────────────

  @Test
  @DisplayName("assignRole adds the role; revokeRole removes it; hasAnyAdministrator reacts")
  void roleMutationsArePersisted() {
    AppUser greg = new AppUser(10L, "Greg", EnumSet.of(AuthorizationRole.USER));
    directory.addUser("greg", "pw-pw-pw-pw-pw", greg);
    assertFalse(directory.hasAnyAdministrator());

    directory.assignRole(10L, AuthorizationRole.ADMIN);
    assertTrue(directory.hasAnyAdministrator());
    assertTrue(directory.findById(10L).orElseThrow()
        .roles().contains(AuthorizationRole.ADMIN));

    directory.revokeRole(10L, AuthorizationRole.ADMIN);
    assertFalse(directory.hasAnyAdministrator());
    assertFalse(directory.findById(10L).orElseThrow()
        .roles().contains(AuthorizationRole.ADMIN));
  }

  @Test
  @DisplayName("deleteUser removes from both lookup maps and from persistence")
  void deleteUserRemovesEverywhere() {
    AppUser harry = new AppUser(20L, "Harry", EnumSet.of(AuthorizationRole.USER));
    directory.addUser("harry", "passwordpassword", harry);
    assertTrue(directory.findById(20L).isPresent());

    directory.deleteUser(20L);

    assertFalse(directory.findById(20L).isPresent());
    assertFalse(directory.findByCredentials(
        new Credentials("harry", "passwordpassword")).isPresent());
    assertFalse(persistence.load().containsKey("harry"));
  }

  @Test
  @DisplayName("deleteUser of unknown id is a no-op")
  void deleteUnknownIsNoOp() {
    directory.deleteUser(999L);
    assertEquals(0, persistence.load().size());
  }

  // ── stream all() ───────────────────────────────────────────────

  @Test
  @DisplayName("all() yields exactly the users stored")
  void allYieldsStoredUsers() {
    directory.addUser("u1", "alpha-alpha-alpha", new AppUser(
        100L, "U1", EnumSet.of(AuthorizationRole.USER)));
    directory.addUser("u2", "beta-beta-beta-beta", new AppUser(
        101L, "U2", EnumSet.of(AuthorizationRole.USER)));
    directory.addUser("u3", "gamma-gamma-gamma-g", new AppUser(
        102L, "U3", EnumSet.of(AuthorizationRole.ADMIN)));

    assertEquals(3, directory.all().count());
  }

  // ── Audit-call assertions — kill "removed call to audit(...)" mutants ──

  @Test
  @DisplayName("addUser publishes a UserCreated event with the username + role")
  void addUserPublishesUserCreatedEvent() {
    directory.addUser("ada", "abcdef-abcdef-1",
        new AppUser(200L, "Ada", EnumSet.of(AuthorizationRole.ADMIN)));

    assertEquals(1, audit.events.size());
    AuditEvent evt = audit.events.get(0);
    assertTrue(evt instanceof UserCreated,
        "expected UserCreated, got: " + evt.getClass().getSimpleName());
    UserCreated created = (UserCreated) evt;
    assertEquals("ada", created.username());
    assertEquals("ADMIN", created.role());
  }

  @Test
  @DisplayName("deleteUser publishes a UserDeleted event referencing the username")
  void deleteUserPublishesUserDeletedEvent() {
    directory.addUser("ben", "abcdef-abcdef-1",
        new AppUser(201L, "Ben", EnumSet.of(AuthorizationRole.USER)));
    audit.events.clear();

    directory.deleteUser(201L);

    assertEquals(1, audit.events.size());
    assertTrue(audit.events.get(0) instanceof UserDeleted ud
        && "ben".equals(ud.username()));
  }

  @Test
  @DisplayName("assignRole + revokeRole publish RoleAssigned / RoleRevoked")
  void roleMutationsAreAudited() {
    directory.addUser("cy", "abcdef-abcdef-1",
        new AppUser(202L, "Cy", EnumSet.of(AuthorizationRole.USER)));
    audit.events.clear();

    directory.assignRole(202L, AuthorizationRole.ADMIN);
    directory.revokeRole(202L, AuthorizationRole.ADMIN);

    assertEquals(2, audit.events.size());
    assertTrue(audit.events.get(0) instanceof RoleAssigned ra
        && "202".equals(ra.subjectId()) && "ADMIN".equals(ra.role()));
    assertTrue(audit.events.get(1) instanceof RoleRevoked rr
        && "202".equals(rr.subjectId()) && "ADMIN".equals(rr.role()));
  }

  // ── Transparent rehash — kill the needsRehash conditional + persist() call ──

  @Test
  @DisplayName("login against a PBKDF2 hash transparently rehashes to Argon2id and re-persists")
  void loginRehashesLegacyHashToArgon2id() {
    // Hash with the LEGACY profile (PBKDF2 — what jsentinel-vaadin without
    // hardening would have produced). Store it pre-hashed so the directory
    // doesn't re-hash on insert.
    String pbkdf2Hash = PasswordHashingServices.defaults()
        .hash("legacy-12345".toCharArray()).encodedHash();
    AppUser legacy = new AppUser(300L, "Legacy", EnumSet.of(AuthorizationRole.USER));
    directory.registerWithHashedPassword("legacy", pbkdf2Hash, legacy);
    assertTrue(pbkdf2Hash.startsWith("$pbkdf2") || pbkdf2Hash.contains("pbkdf2"),
        "test fixture sanity: expected a PBKDF2 envelope, got: " + pbkdf2Hash);

    // Login: directory verifies via BC-modern, sees ALGORITHM_DEPRECATED, rehashes.
    Optional<AppUser> result = directory.findByCredentials(
        new Credentials("legacy", "legacy-12345"));
    assertTrue(result.isPresent());

    // The stored hash must now be different (rehashed) AND the new hash must
    // verify the original password.
    String afterLogin = persistence.load().get("legacy").passwordHash();
    assertNotEquals(pbkdf2Hash, afterLogin,
        "needsRehash branch should have triggered a fresh hash on disk");
    assertTrue(directory.findByCredentials(
        new Credentials("legacy", "legacy-12345")).isPresent(),
        "the rehashed envelope must still verify the original password");
  }

  // ── Recording audit sink (Skill §2.7 — assert on side effects, not mocks) ──

  private static final class RecordingAudit implements JSentinelAuditService {
    final List<AuditEvent> events = new ArrayList<>();
    @Override public void publish(AuditEvent e) { events.add(e); }
    @Override public List<AuditEvent> query(AuditQuery q) { return List.copyOf(events); }
  }
}
