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

import com.svenruppert.flow.security.bootstrap.JSentinelStorageProvider;
import com.svenruppert.jsentinel.persistence.eclipsestore.EclipseStoreJSentinelStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("JSentinelStorageProvider — lazy singleton + test seam")
class JSentinelStorageProviderTest {

  private EclipseStoreJSentinelStorage saved;

  @BeforeEach
  void capturePrevious() {
    saved = JSentinelStorageProvider.storage();
  }

  @AfterEach
  void restorePrevious() {
    JSentinelStorageProvider.setStorage(saved);
  }

  @Test
  @DisplayName("storage() returns a non-null singleton")
  void storageNonNull() {
    EclipseStoreJSentinelStorage storage = JSentinelStorageProvider.storage();
    assertNotNull(storage);
    assertNotNull(storage.auditEventStore());
    assertNotNull(storage.sessionStore());
  }

  @Test
  @DisplayName("storage() returns the same instance across calls")
  void storageIsCached() {
    EclipseStoreJSentinelStorage a = JSentinelStorageProvider.storage();
    EclipseStoreJSentinelStorage b = JSentinelStorageProvider.storage();
    assertSame(a, b, "subsequent calls must return the cached instance");
  }

  @Test
  @DisplayName("setStorage(replacement) overrides the singleton")
  void setStorageReplaces(@TempDir Path tempDir) {
    EclipseStoreJSentinelStorage replacement =
        EclipseStoreJSentinelStorage.openAt(tempDir.resolve("override"));
    try {
      JSentinelStorageProvider.setStorage(replacement);
      assertSame(replacement, JSentinelStorageProvider.storage(),
          "after setStorage, storage() must return the replacement instance");
    } finally {
      replacement.close();
    }
  }
}
