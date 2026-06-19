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

package com.svenruppert.flow.security.model;

import com.svenruppert.flow.security.roles.AuthorizationRole;

import java.util.Set;

/**
 * Authenticated subject of the application. Carries the id, a display
 * name, and the set of roles the {@link com.svenruppert.jsentinel.authorization.api.AuthorizationService}
 * resolves permissions from.
 *
 * <p>Persisted via {@link UserDirectoryPersistence} — no
 * {@link java.io.Serializable} marker is required because the default
 * Eclipse-Store-backed implementation uses its own type-mapping
 * pipeline. A record-header change still warrants planning, but
 * Eclipse-Store generates a legacy-type mapping rather than crashing
 * with {@code InvalidClassException}.
 */
public record AppUser(Long id, String name, Set<AuthorizationRole> roles) {

  public AppUser {
    roles = Set.copyOf(roles);
  }
}
