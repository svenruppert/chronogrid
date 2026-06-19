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

import com.svenruppert.flow.security.storage.AppStoragePaths;
import com.svenruppert.jsentinel.persistence.eclipsestore.EclipseStoreJSentinelStorage;

import java.nio.file.Path;

/**
 * Lazy singleton holder for {@link EclipseStoreJSentinelStorage}.
 *
 * <p>The first call to {@link #storage()} opens (or creates) the
 * Eclipse-Store layer at {@code ./data/jsentinel} and registers a
 * shutdown hook so the storage is closed cleanly on JVM exit.
 *
 * <p>Tests can swap the storage via
 * {@link #setStorage(EclipseStoreJSentinelStorage)} before any
 * consumer initialises.
 */
public final class JSentinelStorageProvider {

  /**
   * Default storage directory. Reads {@link AppStoragePaths#PROPERTY}
   * fresh on every reference, so test forks with a different value
   * win without restarting the JVM-wide constants.
   */
  public static final Path DEFAULT_STORAGE_DIR =
      AppStoragePaths.frameworkStorageDir();

  private static volatile EclipseStoreJSentinelStorage current;

  private JSentinelStorageProvider() {
  }

  public static EclipseStoreJSentinelStorage storage() {
    EclipseStoreJSentinelStorage local = current;
    if (local != null) return local;
    synchronized (JSentinelStorageProvider.class) {
      if (current == null) {
        current = EclipseStoreJSentinelStorage.openAt(
            AppStoragePaths.frameworkStorageDir());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          EclipseStoreJSentinelStorage live = current;
          if (live != null) {
            live.close();
          }
        }, "jsentinel-storage-shutdown"));
      }
      return current;
    }
  }

  /** Test seam — install a custom storage instance. */
  public static synchronized void setStorage(EclipseStoreJSentinelStorage replacement) {
    current = replacement;
  }
}
