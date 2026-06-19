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

import com.svenruppert.jsentinel.session.JSentinelVersion;
import com.svenruppert.jsentinel.session.JSentinelVersionKey;
import com.svenruppert.jsentinel.session.JSentinelVersionStore;

/**
 * ServiceLoader-friendly adapter that exposes the Eclipse-Store-backed
 * {@code JSentinelVersionStore} via the
 * {@code META-INF/services/com.svenruppert.jsentinel.session.JSentinelVersionStore}
 * entry. Without this adapter, the framework's drift-detection state
 * (the per-subject version counter) lives in
 * {@code InMemoryJSentinelVersionStore} and gets reset on every JVM
 * restart — admins who revoked a role pre-restart see those subjects
 * drift right back into their old session afterwards.
 *
 * <p>This adapter has a public no-arg constructor (required by
 * {@link java.util.ServiceLoader}) and delegates to whatever the
 * {@link JSentinelStorageProvider} currently exposes via
 * {@code securityVersionStore()}. The underlying
 * {@code EclipseStoreJSentinelVersionStore} class is package-private
 * upstream — only the {@code JSentinelVersionStore} interface
 * surface is consumable.
 *
 * <p>Lazy delegate: {@link JSentinelStorageProvider#storage()} is
 * called on first use rather than at constructor time, so a test
 * that installs a stub storage via
 * {@code JSentinelStorageProvider.setStorage(...)} before any drift
 * check still wins.
 */
public final class PersistentJSentinelVersionStoreProvider
    implements JSentinelVersionStore {

  private volatile JSentinelVersionStore delegate;

  public PersistentJSentinelVersionStoreProvider() {
  }

  private JSentinelVersionStore delegate() {
    JSentinelVersionStore local = delegate;
    if (local != null) {
      return local;
    }
    synchronized (this) {
      if (delegate == null) {
        delegate = JSentinelStorageProvider.storage().securityVersionStore();
      }
      return delegate;
    }
  }

  @Override
  public JSentinelVersion current(JSentinelVersionKey key) {
    return delegate().current(key);
  }

  @Override
  public JSentinelVersion increment(JSentinelVersionKey key) {
    return delegate().increment(key);
  }

  @Override
  public void reset(JSentinelVersionKey key) {
    delegate().reset(key);
  }
}
