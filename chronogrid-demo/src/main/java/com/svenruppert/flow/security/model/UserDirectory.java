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

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Application user store. Decoupled from the concrete implementation
 * so a future backend swap (DB, LDAP, IAM) does not touch consumers.
 *
 * <p>{@link com.svenruppert.flow.security.bootstrap.AdministratorAccountStoreImpl}
 * adapts the {@link com.svenruppert.jsentinel.bootstrap.AdministratorAccountStore}
 * SPI required by the first-admin bootstrap pipeline onto this
 * directory — {@link #hasAnyAdministrator()} and
 * {@link #registerWithHashedPassword(String, String, AppUser)} are
 * the two methods the adapter consumes.
 */
public interface UserDirectory {

  Optional<AppUser> findByCredentials(Credentials credentials);

  default boolean checkCredentials(Credentials credentials) {
    return findByCredentials(credentials).isPresent();
  }

  Optional<AppUser> findById(Long id);

  Stream<AppUser> all();

  /**
   * @return {@code true} when at least one user with the
   *         {@link AuthorizationRole#ADMIN} role exists. Used by the
   *         bootstrap pipeline to decide whether the system is
   *         uninitialised.
   */
  boolean hasAnyAdministrator();

  void addUser(String username, String plaintextPassword, AppUser user);

  /**
   * Stores a user with a pre-hashed password — bypasses the directory's
   * own hashing. Used by the bootstrap flow, which receives the
   * already-hashed password from
   * {@link com.svenruppert.jsentinel.bootstrap.InitialAdminBootstrapService}.
   *
   * @throws IllegalStateException if {@code username} already exists
   */
  void registerWithHashedPassword(String username, String passwordHash, AppUser user);

  void deleteUser(Long id);

  void assignRole(Long id, AuthorizationRole role);

  void revokeRole(Long id, AuthorizationRole role);
}
