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
import com.svenruppert.jsentinel.dx.bootstrap.SessionBootstrap;
import com.svenruppert.jsentinel.persistence.eclipsestore.EclipseStoreJSentinelStorage;

/**
 * Layer-2 persistence extension. Contributes the Eclipse-Store-backed
 * {@code audit} and {@code sessions} stores to the layer-1 bootstrap
 * chain.
 *
 * <p>Registered via
 * {@code META-INF/services/com.svenruppert.flow.security.bootstrap.BootstrapExtension}.
 * {@link #order()} returns 10 — runs after layer-1 defaults
 * ({@code order=0}, registers {@code ringBuffer(256).logging()}) and
 * before layer-3 hardening ({@code order=20}, hashing + drift
 * detection). The Eclipse-Store {@code storeBacked(...)} bindings
 * override the layer-1 ring buffer on the same sub-builder.
 *
 * <p>The static initialiser eagerly opens the storage backend and
 * triggers {@link BootstrapWiring#instance()} so the bootstrap
 * token lands on stdout / the token file before the first request
 * arrives.
 */
public final class PersistenceBootstrapExtension implements BootstrapExtension {

  private static final EclipseStoreJSentinelStorage STORAGE;

  static {
    STORAGE = JSentinelStorageProvider.storage();
    BootstrapWiring.instance();
  }

  @Override
  public void contributeAudit(AuditBootstrap a) {
    a.storeBacked(STORAGE.auditEventStore()).logging();
  }

  @Override
  public void contributeSessions(SessionBootstrap s) {
    s.storeBacked(STORAGE.sessionStore());
  }

  @Override
  public int order() {
    return 10;
  }
}
