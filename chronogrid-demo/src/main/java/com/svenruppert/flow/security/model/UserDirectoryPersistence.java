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

import java.util.Map;

/**
 * Storage abstraction for the application's {@link UserDirectory}.
 *
 * <p>{@link PersistentUserDirectory} keeps its in-memory state, but
 * delegates load and persist to an implementation of this interface:
 *
 * <ul>
 *   <li>{@link InMemoryUserDirectoryPersistence} — test seam, lives
 *       in the JVM heap, no I/O.</li>
 *   <li>{@code EclipseStoreUserDirectoryPersistence} — production
 *       default, holds a separate {@code EmbeddedStorageManager}
 *       under {@code ./data/app/users/}. Consistent with the rest
 *       of the persistence layer.</li>
 *   <li>Future {@code JdbcUserDirectoryPersistence} /
 *       {@code JsonUserDirectoryPersistence} — drop-in alternatives
 *       without touching {@link PersistentUserDirectory}.</li>
 * </ul>
 *
 * <p>Implementations are responsible for atomicity (no half-written
 * state) and durability (a successfully returned {@link #save(Map)}
 * survives a JVM crash). Concurrency is the caller's problem —
 * {@link PersistentUserDirectory} serialises mutations via
 * {@code synchronized}.
 */
public interface UserDirectoryPersistence {

  /**
   * Reads the full user map from the backing store. Returns an
   * empty map on first start (when no data has been persisted yet).
   */
  Map<String, StoredUser> load();

  /**
   * Persists the full user map. Implementations may replace the
   * stored state wholesale or apply an incremental diff — the
   * contract only requires that a subsequent {@link #load()}
   * returns the supplied map.
   */
  void save(Map<String, StoredUser> users);

  /**
   * Releases any underlying resources (file handles, embedded
   * storage managers, JDBC connections). Idempotent. Called from
   * a JVM shutdown hook when the implementation owns external
   * resources; in-memory implementations may no-op.
   */
  default void close() {
  }
}
