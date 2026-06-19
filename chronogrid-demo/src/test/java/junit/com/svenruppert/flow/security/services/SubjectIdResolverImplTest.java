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
import com.svenruppert.flow.security.services.SubjectIdResolverImpl;
import com.svenruppert.jsentinel.authorization.api.tenant.TenantId;
import com.svenruppert.jsentinel.logout.SubjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SubjectIdResolverImpl — AppUser → SubjectId / TenantId")
class SubjectIdResolverImplTest {

  private final SubjectIdResolverImpl resolver = new SubjectIdResolverImpl();

  @Test
  @DisplayName("resolve uses the user's id as the SubjectId value")
  void resolveStringifiesId() {
    AppUser u = new AppUser(42L, "alice", EnumSet.of(AuthorizationRole.USER));
    assertEquals(SubjectId.of("42"), resolver.resolve(u));
  }

  @Test
  @DisplayName("resolve handles a different id distinctly (kills constant-replace mutants)")
  void resolveDistinguishesIds() {
    AppUser a = new AppUser(1L, "a", EnumSet.of(AuthorizationRole.USER));
    AppUser b = new AppUser(2L, "b", EnumSet.of(AuthorizationRole.USER));
    assertEquals(SubjectId.of("1"), resolver.resolve(a));
    assertEquals(SubjectId.of("2"), resolver.resolve(b));
  }

  @Test
  @DisplayName("tenantFor always returns TenantId.DEFAULT (single-tenant)")
  void tenantIsDefault() {
    AppUser u = new AppUser(99L, "x", EnumSet.of(AuthorizationRole.ADMIN));
    assertEquals(TenantId.DEFAULT, resolver.tenantFor(u));
  }
}
