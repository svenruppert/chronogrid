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
import com.svenruppert.flow.security.model.UserDirectoryProvider;
import com.svenruppert.flow.security.storage.AppStoragePaths;
import com.svenruppert.jsentinel.bootstrap.BootstrapConfiguration;
import com.svenruppert.jsentinel.bootstrap.BootstrapMode;
import com.svenruppert.jsentinel.bootstrap.BootstrapStartup;
import com.svenruppert.jsentinel.bootstrap.BootstrapStateService;
import com.svenruppert.jsentinel.bootstrap.BootstrapTokenGenerator;
import com.svenruppert.jsentinel.bootstrap.BootstrapTokenOutput;
import com.svenruppert.jsentinel.bootstrap.BootstrapTokenStore;
import com.svenruppert.jsentinel.bootstrap.FileBootstrapTokenOutput;
import com.svenruppert.jsentinel.bootstrap.FileBootstrapTokenStore;
import com.svenruppert.jsentinel.bootstrap.InitialAdminBootstrapService;
import com.svenruppert.jsentinel.bootstrap.MinimumLengthPasswordPolicy;
import com.svenruppert.jsentinel.credential.password.bouncycastle.BouncyCastleHashingServices;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Wires the first-admin bootstrap flow.
 *
 * <p>Lazy singleton: the first call constructs the chain once and
 * caches it. Layers:
 *
 * <ol>
 *   <li>{@link BootstrapStateService} reads
 *       {@code AdministratorAccountStore.hasAnyAdministrator()} to
 *       decide whether the system is uninitialised.</li>
 *   <li>{@link BootstrapStartup#initializeIfRequired} generates a
 *       one-time token on first start, persists it to
 *       {@code ./data/jsentinel/bootstrap.token} and prints both the
 *       path and the token to stdout.</li>
 *   <li>{@link InitialAdminBootstrapService} validates the token from
 *       the {@code SetupView} form and creates the admin via the
 *       {@link AdministratorAccountStoreImpl} adapter.</li>
 * </ol>
 *
 * <p>Hash generation uses the same {@code BouncyCastleHashingServices.modern()}
 * (Argon2id) that the hardening layer registers on the runtime — so
 * the admin hash created here matches the format the
 * {@code PersistentUserDirectory} verifies against on login.
 *
 * <p>Delete {@code ./data/jsentinel/bootstrap.token} and any admin row
 * in {@code ./data/jsentinel/users.ser} to force a re-bootstrap.
 */
public final class BootstrapWiring implements HasLogger {

  public static final BootstrapMode DEFAULT_MODE = BootstrapMode.PERSISTENT_FILE;
  /**
   * Default token-file location. Reads {@link AppStoragePaths#PROPERTY}
   * so test forks land in {@code target/test-data/…/bootstrap.token}.
   */
  public static final Path DEFAULT_TOKEN_FILE =
      AppStoragePaths.bootstrapTokenFile();
  /** Minimum admin password length — surfaced in the SetupView helper text. */
  public static final int MIN_PASSWORD_LENGTH = 12;

  private static volatile BootstrapWiring current;

  private final BootstrapStateService stateService;
  private final InitialAdminBootstrapService bootstrapService;

  private BootstrapWiring(BootstrapStateService stateService,
                          InitialAdminBootstrapService bootstrapService) {
    this.stateService = stateService;
    this.bootstrapService = bootstrapService;
  }

  public BootstrapStateService stateService() {
    return stateService;
  }

  public InitialAdminBootstrapService bootstrapService() {
    return bootstrapService;
  }

  public static BootstrapWiring instance() {
    BootstrapWiring local = current;
    if (local != null) return local;
    synchronized (BootstrapWiring.class) {
      if (current == null) current = build();
      return current;
    }
  }

  private static BootstrapWiring build() {
    org.slf4j.Logger log = HasLogger.staticLogger();

    BootstrapConfiguration config = new BootstrapConfiguration(
        DEFAULT_MODE, DEFAULT_TOKEN_FILE, BootstrapConfiguration.DEFAULT_VALIDITY);
    log.info("Bootstrap configuration: mode={}, tokenFile={}, tokenValidity={}",
        config.mode(), config.tokenFilePath(), config.tokenValidity());

    AdministratorAccountStoreImpl adminStore =
        new AdministratorAccountStoreImpl(UserDirectoryProvider.directory());
    BootstrapStateService state = new BootstrapStateService(adminStore, config.mode());
    log.info("Bootstrap state on build: bootstrapRequired={}, hasAdministrator={}",
        state.bootstrapRequired(), state.hasAdministrator());

    BootstrapTokenStore tokenStore = new FileBootstrapTokenStore(config.tokenFilePath());
    BootstrapTokenOutput output = new FileBootstrapTokenOutput();
    BootstrapStartup.initializeIfRequired(
        state, tokenStore, new BootstrapTokenGenerator(), output, config);

    InitialAdminBootstrapService service = new InitialAdminBootstrapService(
        state, tokenStore, adminStore,
        BouncyCastleHashingServices.modern(),
        new MinimumLengthPasswordPolicy(MIN_PASSWORD_LENGTH),
        config.tokenValidity(), Clock.systemUTC());
    log.info("Initial-admin bootstrap pipeline wired: hashing=BouncyCastle/modern (Argon2id), "
        + "policy=MinimumLengthPasswordPolicy({} chars), usernamePattern=[A-Za-z0-9._-] (1–64 chars)",
        MIN_PASSWORD_LENGTH);
    return new BootstrapWiring(state, service);
  }
}
