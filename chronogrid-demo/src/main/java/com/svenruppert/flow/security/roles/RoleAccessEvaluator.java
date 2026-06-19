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

package com.svenruppert.flow.security.roles;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.views.AppLoginView;
import com.svenruppert.flow.views.PublicHomeView;
import com.svenruppert.jsentinel.authorization.api.AccessEvaluator;
import com.svenruppert.jsentinel.authorization.api.AuthorizationService;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.svenruppert.jsentinel.authorization.api.roles.RoleName;
import com.svenruppert.jsentinel.authorization.navigation.AccessContext;
import com.svenruppert.jsentinel.authorization.navigation.AccessDecision;

import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

/**
 * Evaluates {@link VisibleFor @VisibleFor} restrictions on route views.
 *
 * <ul>
 *   <li>No subject in the SubjectStore → reroute to login.</li>
 *   <li>Subject lacks every required role → reroute to MainView.</li>
 *   <li>Subject has at least one required role → granted.</li>
 * </ul>
 */
public class RoleAccessEvaluator
    implements AccessEvaluator<VisibleFor>, HasLogger {

  @Override
  public AccessDecision evaluate(AccessContext context, VisibleFor annotation) {
    Set<RoleName> requiredRoles = stream(annotation.value())
        .map(Enum::name)
        .map(RoleName::new)
        .collect(Collectors.toSet());

    if (requiredRoles.isEmpty()) {
      return AccessDecision.granted();
    }

    var currentSubject = SubjectStores.subjectStore().currentSubject(AppUser.class);
    if (currentSubject.isEmpty()) {
      return AccessDecision.denied(AppLoginView.NAV, false);
    }

    AuthorizationService<AppUser> authorizationService =
        JSentinelServiceResolver.authorizationService();
    boolean hasRole = authorizationService.rolesFor(currentSubject.get())
        .roleNames()
        .stream()
        .anyMatch(requiredRoles::contains);

    if (hasRole) {
      return AccessDecision.granted();
    }
    return AccessDecision.denied(PublicHomeView.NAV, true);
  }
}
