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

package junit.com.svenruppert.flow.security.model;

import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.model.EclipseStoreUserDirectoryPersistence;
import com.svenruppert.flow.security.model.StoredUser;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EclipseStoreUserDirectoryPersistence — round-trip + close lifecycle")
class EclipseStoreUserDirectoryPersistenceTest {

  @Test
  @DisplayName("fresh storage returns an empty map on load")
  void freshStorageIsEmpty(@TempDir Path tempDir) {
    EclipseStoreUserDirectoryPersistence p =
        new EclipseStoreUserDirectoryPersistence(tempDir.resolve("users"));
    try {
      Map<String, StoredUser> loaded = p.load();
      assertNotNull(loaded);
      assertTrue(loaded.isEmpty());
    } finally {
      p.close();
    }
  }

  @Test
  @DisplayName("save then close + reopen + load yields the same entries")
  void roundTripSurvivesClose(@TempDir Path tempDir) {
    Path dir = tempDir.resolve("users-roundtrip");

    AppUser alice = new AppUser(1L, "Alice", EnumSet.of(AuthorizationRole.ADMIN));
    AppUser bob = new AppUser(2L, "Bob", EnumSet.of(AuthorizationRole.USER));
    Map<String, StoredUser> snapshot = new HashMap<>();
    snapshot.put("alice", new StoredUser(alice, "$argon2id$fake$alice"));
    snapshot.put("bob", new StoredUser(bob, "$argon2id$fake$bob"));

    EclipseStoreUserDirectoryPersistence p1 = new EclipseStoreUserDirectoryPersistence(dir);
    try {
      p1.save(snapshot);
    } finally {
      p1.close();
    }

    // Re-open the same directory in a fresh instance — must see the same data.
    EclipseStoreUserDirectoryPersistence p2 = new EclipseStoreUserDirectoryPersistence(dir);
    try {
      Map<String, StoredUser> reloaded = p2.load();
      assertEquals(2, reloaded.size());
      assertEquals("$argon2id$fake$alice", reloaded.get("alice").passwordHash());
      assertEquals("$argon2id$fake$bob", reloaded.get("bob").passwordHash());
      assertEquals(alice, reloaded.get("alice").user());
      assertEquals(bob, reloaded.get("bob").user());
    } finally {
      p2.close();
    }
  }

  @Test
  @DisplayName("save replaces previous content wholesale (delete by omission)")
  void saveIsReplaceNotMerge(@TempDir Path tempDir) {
    Path dir = tempDir.resolve("users-replace");
    EclipseStoreUserDirectoryPersistence p = new EclipseStoreUserDirectoryPersistence(dir);
    try {
      Map<String, StoredUser> first = new HashMap<>();
      first.put("alice",
          new StoredUser(new AppUser(1L, "Alice", EnumSet.of(AuthorizationRole.USER)),
              "$alice"));
      first.put("bob",
          new StoredUser(new AppUser(2L, "Bob", EnumSet.of(AuthorizationRole.USER)),
              "$bob"));
      p.save(first);
      assertEquals(2, p.load().size());

      Map<String, StoredUser> second = new HashMap<>();
      second.put("carol",
          new StoredUser(new AppUser(3L, "Carol", EnumSet.of(AuthorizationRole.ADMIN)),
              "$carol"));
      p.save(second);

      Map<String, StoredUser> reloaded = p.load();
      assertEquals(1, reloaded.size());
      assertEquals("$carol", reloaded.get("carol").passwordHash());
    } finally {
      p.close();
    }
  }

  @Test
  @DisplayName("close is idempotent — calling it twice does not throw")
  void closeIsIdempotent(@TempDir Path tempDir) {
    EclipseStoreUserDirectoryPersistence p =
        new EclipseStoreUserDirectoryPersistence(tempDir.resolve("users-idempotent"));
    // Open via load → ensure manager is started before we close.
    p.load();
    p.close();
    p.close();
  }

  @Test
  @DisplayName("load returns a defensive copy — caller mutations do not bleed into storage")
  void loadReturnsCopy(@TempDir Path tempDir) {
    EclipseStoreUserDirectoryPersistence p =
        new EclipseStoreUserDirectoryPersistence(tempDir.resolve("users-defensive"));
    try {
      Map<String, StoredUser> snapshot = new HashMap<>();
      snapshot.put("alice",
          new StoredUser(new AppUser(1L, "Alice", EnumSet.of(AuthorizationRole.USER)),
              "$h"));
      p.save(snapshot);

      Map<String, StoredUser> loaded = p.load();
      loaded.clear();

      // A re-load must still see the saved entry.
      assertEquals(1, p.load().size());
    } finally {
      p.close();
    }
  }
}
