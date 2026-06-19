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

package com.svenruppert.flow.security.services;

import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.jsentinel.authorization.api.AuthorizationService;
import com.svenruppert.jsentinel.authorization.api.permissions.HasPermissions;
import com.svenruppert.jsentinel.authorization.api.permissions.PermissionName;
import com.svenruppert.jsentinel.authorization.api.permissions.RolePermissionMapping;
import com.svenruppert.jsentinel.authorization.api.permissions.RolePermissionResolver;
import com.svenruppert.jsentinel.authorization.api.permissions.StaticRolePermissionMapping;
import com.svenruppert.jsentinel.authorization.api.roles.HasRoles;
import com.svenruppert.jsentinel.authorization.api.roles.RoleName;
import com.svenruppert.jsentinel.autoservice.api.JSentinelAutoService;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.svenruppert.flow.security.permissions.AppPermission.ADMIN_ROLES;
import static com.svenruppert.flow.security.permissions.AppPermission.ADMIN_SESSIONS;
import static com.svenruppert.flow.security.permissions.AppPermission.APP_VIEW;
import static com.svenruppert.flow.security.permissions.AppPermission.AUDIT_READ;
import static com.svenruppert.flow.security.permissions.AppPermission.CALENDAR_READ;

/**
 * Role → permission table. SPI-registered via
 * {@link JSentinelAutoService @JSentinelAutoService}.
 *
 * <ul>
 *   <li>ADMIN: every permission</li>
 *   <li>USER: just app:view</li>
 * </ul>
 */
@JSentinelAutoService(AuthorizationService.class)
public class AppAuthorizationService
    implements AuthorizationService<AppUser> {

  private static final RolePermissionMapping ROLE_PERMISSIONS = StaticRolePermissionMapping.builder()
      .put(roleName(AuthorizationRole.ADMIN), Set.of(
          APP_VIEW.permissionName(),
          AUDIT_READ.permissionName(),
          ADMIN_SESSIONS.permissionName(),
          ADMIN_ROLES.permissionName(),
          CALENDAR_READ.permissionName()))
      .put(roleName(AuthorizationRole.USER), Set.of(
          APP_VIEW.permissionName(),
          CALENDAR_READ.permissionName()))
      .build();

  @Override
  public HasRoles rolesFor(AppUser subject) {
    Objects.requireNonNull(subject);
    List<RoleName> roles = subject.roles().stream()
        .map(AppAuthorizationService::roleName)
        .toList();
    return () -> roles;
  }

  @Override
  public HasPermissions permissionsFor(AppUser subject) {
    Objects.requireNonNull(subject);
    Set<RoleName> roles = subject.roles().stream()
        .map(AppAuthorizationService::roleName)
        .collect(Collectors.toSet());
    Set<PermissionName> permissions =
        RolePermissionResolver.permissionsForRoles(roles, ROLE_PERMISSIONS);
    return () -> List.copyOf(permissions);
  }

  private static RoleName roleName(AuthorizationRole role) {
    return new RoleName(role.name());
  }
}
