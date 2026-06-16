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

package junit.com.svenruppert.flow.security.permissions;

import com.svenruppert.flow.security.permissions.AppPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("AppPermission — enum-to-PermissionName mapping")
class AppPermissionTest {

  @Test
  @DisplayName("each enum constant maps to its documented permission name")
  void enumNamesMapAsDocumented() {
    assertEquals("app:view", AppPermission.APP_VIEW.permissionName().value());
    assertEquals("audit:read", AppPermission.AUDIT_READ.permissionName().value());
    assertEquals("admin:sessions", AppPermission.ADMIN_SESSIONS.permissionName().value());
    assertEquals("admin:roles", AppPermission.ADMIN_ROLES.permissionName().value());
    assertEquals("calendar:read", AppPermission.CALENDAR_READ.permissionName().value());
  }

  @Test
  @DisplayName("all five enum constants exist — kills enum-truncation mutants")
  void hasExactlyFiveConstants() {
    assertEquals(5, AppPermission.values().length);
  }

  @Test
  @DisplayName("permissionNames are pairwise distinct")
  void permissionNamesAreDistinct() {
    Set<String> values = EnumSet.allOf(AppPermission.class).stream()
        .map(p -> p.permissionName().value())
        .collect(Collectors.toSet());
    assertEquals(5, values.size());
  }

  @Test
  @DisplayName("permissionName() is non-null for every constant")
  void everyPermissionHasNonNullName() {
    for (AppPermission p : AppPermission.values()) {
      assertNotNull(p.permissionName(), "expected non-null permissionName for " + p);
    }
  }
}
