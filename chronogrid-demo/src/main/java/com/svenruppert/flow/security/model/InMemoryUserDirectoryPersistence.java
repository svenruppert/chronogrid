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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only / fallback persistence — holds the user map in the JVM
 * heap, no I/O. Inject this into {@link PersistentUserDirectory} for
 * unit tests to avoid touching disk.
 */
public final class InMemoryUserDirectoryPersistence
    implements UserDirectoryPersistence {

  private final Map<String, StoredUser> users = new ConcurrentHashMap<>();

  @Override
  public Map<String, StoredUser> load() {
    return new HashMap<>(users);
  }

  @Override
  public void save(Map<String, StoredUser> snapshot) {
    users.clear();
    users.putAll(snapshot);
  }
}
