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

import com.svenruppert.jsentinel.dx.bootstrap.AuditBootstrap;
import com.svenruppert.jsentinel.dx.bootstrap.CredentialBootstrap;
import com.svenruppert.jsentinel.dx.bootstrap.SessionBootstrap;

/**
 * Project-local SPI for additive bootstrap configuration.
 *
 * <p>Layer 1 (this project's baseline) defines the contract. Higher
 * layers ({@code jsentinel-vaadin-persistence},
 * {@code jsentinel-vaadin-hardening}, future {@code -mfa} /
 * {@code -multi-tenant} etc.) each ship one implementation registered
 * via {@code META-INF/services/} + {@link #SERVICE_NAME}.
 *
 * <p>{@link BootstrapBuilder#apply(com.svenruppert.jsentinel.dx.vaadin.bootstrap.VaadinJSentinelBootstrap)}
 * loads every registered implementation through
 * {@link java.util.ServiceLoader}, sorts by {@link #order()}, and
 * invokes the three {@code contribute*} hooks inside a single
 * {@code .audit(...)} / {@code .sessions(...)} / {@code .credentials(...)}
 * call on the fluent {@code VaadinSecurity.bootstrap()} chain — so
 * multiple layers can configure the same sub-aspect without
 * overwriting each other.
 *
 * <p>All three hooks have an empty default — a layer overrides only
 * the sub-aspects it cares about.
 */
public interface BootstrapExtension {

  /** SPI service-file name. */
  String SERVICE_NAME = "com.svenruppert.flow.security.bootstrap.BootstrapExtension";

  /** Adds audit-related configuration. */
  default void contributeAudit(AuditBootstrap a) {
  }

  /** Adds session-related configuration. */
  default void contributeSessions(SessionBootstrap s) {
  }

  /** Adds credential / hashing configuration. */
  default void contributeCredentials(CredentialBootstrap c) {
  }

  /**
   * Order in which extensions are invoked. Lower runs first; ties are
   * broken in service-loader discovery order (effectively undefined).
   * Layer 1 defaults: 0. Layer 2 (persistence): 10. Layer 3
   * (hardening): 20. Leaves room for finer interleaving.
   */
  default int order() {
    return 0;
  }
}
