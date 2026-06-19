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

package junit.com.svenruppert.flow.security.bootstrap;

import com.svenruppert.flow.security.bootstrap.PersistentJSentinelVersionStoreProvider;
import com.svenruppert.jsentinel.authorization.api.tenant.TenantId;
import com.svenruppert.jsentinel.logout.SubjectId;
import com.svenruppert.jsentinel.session.JSentinelVersion;
import com.svenruppert.jsentinel.session.JSentinelVersionKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("PersistentJSentinelVersionStoreProvider — delegates to Eclipse-Store-backed store")
class PersistentJSentinelVersionStoreProviderTest {

  private JSentinelVersionKey freshKey(String prefix) {
    return new JSentinelVersionKey(
        TenantId.DEFAULT,
        SubjectId.of(prefix + "-" + UUID.randomUUID()));
  }

  @Test
  @DisplayName("current(key) returns a non-null version from the delegate")
  void currentReturnsNonNull() {
    PersistentJSentinelVersionStoreProvider provider =
        new PersistentJSentinelVersionStoreProvider();
    JSentinelVersion v = provider.current(freshKey("current"));
    assertNotNull(v, "delegate must produce a JSentinelVersion for an unseen key");
  }

  @Test
  @DisplayName("increment(key) raises the version above its previous value")
  void incrementRaisesVersion() {
    PersistentJSentinelVersionStoreProvider provider =
        new PersistentJSentinelVersionStoreProvider();
    JSentinelVersionKey key = freshKey("increment");
    JSentinelVersion before = provider.current(key);
    JSentinelVersion after = provider.increment(key);
    assertNotEquals(before.value(), after.value(),
        "increment must change the version value");
    assertEquals(before.value() + 1, after.value(),
        "increment must add exactly 1 to the previous version");
  }

  @Test
  @DisplayName("reset(key) restores the version to the initial state")
  void resetRestoresInitial() {
    PersistentJSentinelVersionStoreProvider provider =
        new PersistentJSentinelVersionStoreProvider();
    JSentinelVersionKey key = freshKey("reset");
    JSentinelVersion initial = provider.current(key);
    provider.increment(key);
    provider.increment(key);
    provider.reset(key);
    assertEquals(initial.value(), provider.current(key).value(),
        "reset must revert the version to the unseen-key value");
  }

  @Test
  @DisplayName("two providers share the same underlying delegate state")
  void providersShareDelegate() {
    JSentinelVersionKey key = freshKey("shared");
    PersistentJSentinelVersionStoreProvider a =
        new PersistentJSentinelVersionStoreProvider();
    PersistentJSentinelVersionStoreProvider b =
        new PersistentJSentinelVersionStoreProvider();
    JSentinelVersion afterA = a.increment(key);
    JSentinelVersion seenByB = b.current(key);
    assertEquals(afterA.value(), seenByB.value(),
        "both providers must resolve to the same Eclipse-Store-backed delegate");
  }
}
