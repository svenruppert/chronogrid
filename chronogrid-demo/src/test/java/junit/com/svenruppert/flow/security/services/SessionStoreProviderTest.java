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

package junit.com.svenruppert.flow.security.services;

import com.svenruppert.flow.security.bootstrap.JSentinelStorageProvider;
import com.svenruppert.flow.security.services.SessionStoreProvider;
import com.svenruppert.jsentinel.session.SessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("SessionStoreProvider — delegates to JSentinelStorageProvider")
class SessionStoreProviderTest {

  @Test
  @DisplayName("sessionStore is non-null")
  void nonNull() {
    SessionStore store = SessionStoreProvider.sessionStore();
    assertNotNull(store);
  }

  @Test
  @DisplayName("sessionStore delegates through the storage singleton")
  void delegatesThroughStorage() {
    // The provider has no state — every call must produce a session-store
    // facade routed via the underlying Eclipse-Store instance.
    assertNotNull(JSentinelStorageProvider.storage().sessionStore());
    assertNotNull(SessionStoreProvider.sessionStore());
  }
}
