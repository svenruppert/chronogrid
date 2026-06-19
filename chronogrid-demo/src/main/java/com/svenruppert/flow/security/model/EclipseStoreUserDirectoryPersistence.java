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

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.security.storage.AppStoragePaths;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production-default {@link UserDirectoryPersistence}. Holds the user
 * map in a <strong>separate</strong> Eclipse-Store instance from the
 * one the jSentinel persistence module owns. Two reasons for the
 * separation:
 *
 * <ul>
 *   <li>{@code EclipseStoreJSentinelStorage} is single-rooted with
 *       its own framework-owned root type — there is no app-extensible
 *       slot to hang a user map onto.</li>
 *   <li>Two independent storages can be migrated, backed up and
 *       reset independently. A corrupt app-user store does not take
 *       down the audit log.</li>
 * </ul>
 *
 * <p>Default storage directory: {@code ./data/app/users}. Storage is
 * opened lazily on first {@link #load()} and closed via a JVM
 * shutdown hook plus an explicit {@link #close()} call.
 *
 * <p>Eclipse-Store uses its own type-mapping, not Java serialisation
 * — {@link AppUser} does <strong>not</strong> need
 * {@code implements Serializable}, and adding a field to the record
 * does not corrupt the on-disk format (Eclipse-Store generates a
 * legacy-type mapping automatically).
 */
public final class EclipseStoreUserDirectoryPersistence
    implements UserDirectoryPersistence, HasLogger {

  /**
   * Default user-directory storage. Reads {@link AppStoragePaths#PROPERTY}
   * so test forks can redirect this to {@code target/test-data} without
   * touching the repo-rooted {@code ./data/} tree.
   */
  public static final Path DEFAULT_STORAGE_DIR =
      AppStoragePaths.userDirectoryDir();

  private final Path storageDir;
  private final AtomicBoolean closed = new AtomicBoolean();
  private volatile EmbeddedStorageManager manager;
  private volatile AppUsersRoot root;

  public EclipseStoreUserDirectoryPersistence() {
    this(DEFAULT_STORAGE_DIR);
  }

  public EclipseStoreUserDirectoryPersistence(Path storageDir) {
    this.storageDir = Objects.requireNonNull(storageDir, "storageDir");
  }

  @Override
  public synchronized Map<String, StoredUser> load() {
    ensureOpen();
    return new HashMap<>(root.users);
  }

  @Override
  public synchronized void save(Map<String, StoredUser> snapshot) {
    ensureOpen();
    root.users.clear();
    root.users.putAll(snapshot);
    manager.store(root.users);
    logger().debug("EclipseStoreUserDirectoryPersistence: persisted {} users to {}",
        root.users.size(), storageDir);
  }

  @Override
  public synchronized void close() {
    if (!closed.compareAndSet(false, true)) return;
    if (manager != null) {
      try {
        manager.shutdown();
        logger().info("EclipseStoreUserDirectoryPersistence: storage at {} closed", storageDir);
      } catch (RuntimeException e) {
        logger().warn("EclipseStoreUserDirectoryPersistence: shutdown failed for {}", storageDir, e);
      }
    }
  }

  private void ensureOpen() {
    if (manager != null) return;
    AppUsersRoot initial = new AppUsersRoot();
    manager = EmbeddedStorage.start(initial, storageDir);
    Object loaded = manager.root();
    if (loaded instanceof AppUsersRoot existing) {
      root = existing;
      logger().info("EclipseStoreUserDirectoryPersistence: opened {} ({} users present)",
          storageDir, root.users.size());
    } else if (loaded == null) {
      root = initial;
      manager.setRoot(root);
      manager.storeRoot();
      logger().info("EclipseStoreUserDirectoryPersistence: bootstrapped fresh storage at {}", storageDir);
    } else {
      manager.shutdown();
      throw new IllegalStateException(
          "Unexpected root type in " + storageDir + ": " + loaded.getClass().getName()
              + " — expected " + AppUsersRoot.class.getName());
    }
    Runtime.getRuntime().addShutdownHook(new Thread(this::close,
        "app-users-storage-shutdown"));
  }

  /** Eclipse-Store root container. Public type so EclipseStore can map it. */
  public static final class AppUsersRoot {
    public final Map<String, StoredUser> users = new ConcurrentHashMap<>();
  }
}
