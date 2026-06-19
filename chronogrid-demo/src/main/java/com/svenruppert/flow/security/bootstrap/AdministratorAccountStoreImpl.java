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

package com.svenruppert.flow.security.bootstrap;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.model.UserDirectory;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.jsentinel.bootstrap.AdministratorAccountStore;
import com.svenruppert.jsentinel.bootstrap.NewAdministrator;

import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapter from {@link AdministratorAccountStore} (the bootstrap-flow
 * SPI) to the application's {@link UserDirectory}. Consumed by
 * {@code InitialAdminBootstrapService} during the {@code SetupView}-driven
 * first-admin flow.
 *
 * <p>{@link NewAdministrator#passwordHash()} is already hashed by the
 * bootstrap service — this adapter calls
 * {@link UserDirectory#registerWithHashedPassword(String, String, AppUser)}
 * to bypass re-hashing.
 */
public final class AdministratorAccountStoreImpl
    implements AdministratorAccountStore, HasLogger {

  private final UserDirectory directory;
  private final AtomicLong idSequence = new AtomicLong(1000);

  public AdministratorAccountStoreImpl(UserDirectory directory) {
    this.directory = Objects.requireNonNull(directory, "directory");
  }

  @Override
  public boolean hasAnyAdministrator() {
    return directory.hasAnyAdministrator();
  }

  @Override
  public void createAdministrator(NewAdministrator newAdministrator) {
    String displayName = newAdministrator.displayName() == null
        || newAdministrator.displayName().isBlank()
        ? newAdministrator.username()
        : newAdministrator.displayName();
    AppUser user = new AppUser(
        idSequence.getAndIncrement(),
        displayName,
        EnumSet.of(AuthorizationRole.ADMIN, AuthorizationRole.USER));
    logger().info("Persisting initial administrator: username='{}', id={}, displayName='{}', roles={}",
        newAdministrator.username(), user.id(), displayName, user.roles());
    try {
      directory.registerWithHashedPassword(
          newAdministrator.username(),
          newAdministrator.passwordHash(),
          user);
      logger().info("Initial administrator '{}' (id={}) committed to {}",
          newAdministrator.username(), user.id(), directory.getClass().getSimpleName());
    } catch (RuntimeException failure) {
      // InitialAdminBootstrapService swallows this exception and surfaces a
      // generic "could not persist administrator" — log the real cause first.
      logger().error("Failed to persist initial administrator '{}' (id={})",
          newAdministrator.username(), user.id(), failure);
      throw failure;
    }
  }
}
