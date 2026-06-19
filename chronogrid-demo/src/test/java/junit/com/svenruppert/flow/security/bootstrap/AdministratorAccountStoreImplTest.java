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

import com.svenruppert.flow.security.bootstrap.AdministratorAccountStoreImpl;
import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.model.Credentials;
import com.svenruppert.flow.security.model.InMemoryUserDirectoryPersistence;
import com.svenruppert.flow.security.model.PersistentUserDirectory;
import com.svenruppert.flow.security.model.UserDirectory;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.jsentinel.bootstrap.NewAdministrator;
import com.svenruppert.jsentinel.credential.password.bouncycastle.BouncyCastleHashingServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AdministratorAccountStoreImpl — adapter from bootstrap SPI to UserDirectory")
class AdministratorAccountStoreImplTest {

  private UserDirectory directory;
  private AdministratorAccountStoreImpl adapter;

  @BeforeEach
  void setUp() {
    directory = new PersistentUserDirectory(
        new InMemoryUserDirectoryPersistence(),
        BouncyCastleHashingServices.modern());
    adapter = new AdministratorAccountStoreImpl(directory);
  }

  // ── hasAnyAdministrator ────────────────────────────────────────

  @Test
  @DisplayName("fresh directory reports no administrator")
  void freshDirectoryHasNoAdmin() {
    assertFalse(adapter.hasAnyAdministrator());
  }

  @Test
  @DisplayName("a USER-only entry does NOT count as administrator")
  void userOnlyDoesNotCountAsAdmin() {
    directory.addUser("plain", "plainplainplain",
        new AppUser(1L, "plain", EnumSet.of(AuthorizationRole.USER)));
    assertFalse(adapter.hasAnyAdministrator());
  }

  @Test
  @DisplayName("createAdministrator flips hasAnyAdministrator to true")
  void createAdminMarksDirectoryAdminful() {
    String hash = BouncyCastleHashingServices.modern()
        .hash("12-char-password".toCharArray()).encodedHash();
    adapter.createAdministrator(new NewAdministrator(
        "admin", "Administrator", "admin@example.com", hash));

    assertTrue(adapter.hasAnyAdministrator());
  }

  // ── createAdministrator side effects ───────────────────────────

  @Test
  @DisplayName("createAdministrator stores ADMIN + USER roles on the new user")
  void createdAdminHasAdminAndUserRoles() {
    String hash = BouncyCastleHashingServices.modern()
        .hash("12-char-password".toCharArray()).encodedHash();
    adapter.createAdministrator(new NewAdministrator(
        "alice", "Alice", null, hash));

    AppUser created = directory.findByCredentials(
        new Credentials("alice", "12-char-password")).orElseThrow();
    assertTrue(created.roles().contains(AuthorizationRole.ADMIN));
    assertTrue(created.roles().contains(AuthorizationRole.USER));
    assertEquals(2, created.roles().size());
  }

  @Test
  @DisplayName("blank displayName falls back to the username")
  void blankDisplayNameFallsBackToUsername() {
    String hash = BouncyCastleHashingServices.modern()
        .hash("12-char-password".toCharArray()).encodedHash();
    adapter.createAdministrator(new NewAdministrator(
        "bob", "  ", null, hash));

    AppUser created = directory.findByCredentials(
        new Credentials("bob", "12-char-password")).orElseThrow();
    assertEquals("bob", created.name());
  }

  @Test
  @DisplayName("null displayName falls back to the username")
  void nullDisplayNameFallsBackToUsername() {
    String hash = BouncyCastleHashingServices.modern()
        .hash("12-char-password".toCharArray()).encodedHash();
    adapter.createAdministrator(new NewAdministrator(
        "carol", null, null, hash));

    AppUser created = directory.findByCredentials(
        new Credentials("carol", "12-char-password")).orElseThrow();
    assertEquals("carol", created.name());
  }

  @Test
  @DisplayName("a non-blank displayName is kept verbatim")
  void nonBlankDisplayNameKept() {
    String hash = BouncyCastleHashingServices.modern()
        .hash("12-char-password".toCharArray()).encodedHash();
    adapter.createAdministrator(new NewAdministrator(
        "dave", "Dave the Admin", "dave@example.com", hash));

    AppUser created = directory.findByCredentials(
        new Credentials("dave", "12-char-password")).orElseThrow();
    assertEquals("Dave the Admin", created.name());
  }

  @Test
  @DisplayName("consecutive createAdministrator calls produce distinct ids")
  void consecutiveIdsDifferent() {
    String hash = BouncyCastleHashingServices.modern()
        .hash("12-char-password".toCharArray()).encodedHash();
    adapter.createAdministrator(new NewAdministrator("u1", null, null, hash));
    adapter.createAdministrator(new NewAdministrator("u2", null, null, hash));

    AppUser a = directory.findByCredentials(
        new Credentials("u1", "12-char-password")).orElseThrow();
    AppUser b = directory.findByCredentials(
        new Credentials("u2", "12-char-password")).orElseThrow();

    assertNotEquals(a.id(), b.id());
  }

  // ── duplicate guard ────────────────────────────────────────────

  @Test
  @DisplayName("second createAdministrator with the same username throws")
  void duplicateUsernameThrows() {
    String hash = BouncyCastleHashingServices.modern()
        .hash("12-char-password".toCharArray()).encodedHash();
    adapter.createAdministrator(new NewAdministrator("admin", null, null, hash));

    assertThrows(IllegalStateException.class, () ->
        adapter.createAdministrator(new NewAdministrator("admin", null, null, hash)));
  }

  // ── null guard ──────────────────────────────────────────────────

  @Test
  @DisplayName("null directory in the constructor is rejected")
  void nullDirectoryRejected() {
    assertThrows(NullPointerException.class, () -> new AdministratorAccountStoreImpl(null));
  }
}
