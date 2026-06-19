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

/**
 * Role catalog for the application.
 *
 * <p>The role-to-permission table lives in {@code AppAuthorizationService}.
 * Add a new role here, map it to permissions there, and reference it in
 * {@code @VisibleFor(...)} on views.
 */
public enum AuthorizationRole {
  ADMIN,
  USER
}
