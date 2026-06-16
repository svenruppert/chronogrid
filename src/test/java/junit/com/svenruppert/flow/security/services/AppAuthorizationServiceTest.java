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
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.flow.security.services.AppAuthorizationService;
import com.svenruppert.jsentinel.authorization.api.permissions.PermissionName;
import com.svenruppert.jsentinel.authorization.api.roles.RoleName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AppAuthorizationService — role → permission mapping")
class AppAuthorizationServiceTest {

  private final AppAuthorizationService service = new AppAuthorizationService();

  // ── permissionsFor ─────────────────────────────────────────────

  @Test
  @DisplayName("ADMIN+USER subject gets the full five-permission set")
  void adminGetsAllFivePermissions() {
    AppUser admin = new AppUser(1L, "admin",
        EnumSet.of(AuthorizationRole.ADMIN, AuthorizationRole.USER));

    Set<String> perms = service.permissionsFor(admin).permissionNames().stream()
        .map(PermissionName::value)
        .collect(Collectors.toSet());

    assertEquals(
        Set.of("app:view", "audit:read", "admin:sessions", "admin:roles", "calendar:read"),
        perms);
  }

  @Test
  @DisplayName("USER-only subject gets app:view + calendar:read")
  void userGetsAppViewAndCalendarRead() {
    AppUser user = new AppUser(2L, "user", EnumSet.of(AuthorizationRole.USER));

    Set<String> perms = service.permissionsFor(user).permissionNames().stream()
        .map(PermissionName::value)
        .collect(Collectors.toSet());

    assertEquals(Set.of("app:view", "calendar:read"), perms);
  }

  @Test
  @DisplayName("ADMIN-only (no USER) still gets the full five — ADMIN's set is complete on its own")
  void adminAloneGetsFullFive() {
    AppUser admin = new AppUser(3L, "admin-only",
        EnumSet.of(AuthorizationRole.ADMIN));

    Set<String> perms = service.permissionsFor(admin).permissionNames().stream()
        .map(PermissionName::value)
        .collect(Collectors.toSet());

    assertEquals(
        Set.of("app:view", "audit:read", "admin:sessions", "admin:roles", "calendar:read"),
        perms);
  }

  @Test
  @DisplayName("subject without roles gets empty permissions")
  void rolelessSubjectGetsNothing() {
    AppUser nobody = new AppUser(4L, "nobody",
        EnumSet.noneOf(AuthorizationRole.class));

    assertTrue(service.permissionsFor(nobody).permissionNames().isEmpty());
  }

  // ── rolesFor ────────────────────────────────────────────────────

  @Test
  @DisplayName("rolesFor mirrors the subject's role enum names exactly")
  void rolesForReturnsRoleNames() {
    AppUser admin = new AppUser(5L, "admin",
        EnumSet.of(AuthorizationRole.ADMIN, AuthorizationRole.USER));

    Set<String> names = admin.roles().stream()
        .map(Enum::name)
        .collect(Collectors.toSet());

    Set<String> reported = service.rolesFor(admin).roleNames().stream()
        .map(RoleName::value)
        .collect(Collectors.toSet());

    assertEquals(names, reported);
    assertEquals(Set.of("ADMIN", "USER"), reported);
  }

  @Test
  @DisplayName("rolesFor of a roleless subject is empty")
  void rolesForRolelessSubjectIsEmpty() {
    AppUser nobody = new AppUser(6L, "nobody",
        EnumSet.noneOf(AuthorizationRole.class));

    assertTrue(service.rolesFor(nobody).roleNames().isEmpty());
  }

  // ── null defence ───────────────────────────────────────────────

  @Test
  @DisplayName("null subject is rejected on both lookups")
  void nullSubjectThrows() {
    assertThrows(NullPointerException.class, () -> service.rolesFor(null));
    assertThrows(NullPointerException.class, () -> service.permissionsFor(null));
  }
}
