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

import com.svenruppert.flow.security.model.InMemoryUserDirectoryPersistence;
import com.svenruppert.flow.security.model.PersistentUserDirectory;
import com.svenruppert.flow.security.model.UserDirectory;
import com.svenruppert.flow.security.model.UserDirectoryProvider;
import com.svenruppert.jsentinel.credential.password.bouncycastle.BouncyCastleHashingServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("UserDirectoryProvider — singleton with test-seam swap")
class UserDirectoryProviderTest {

  @AfterEach
  void cleanup() {
    // Do NOT call reset() — reset() opens the Eclipse-Store default which
    // pollutes the working dir. Install a test seam instead.
    UserDirectoryProvider.setDirectory(new PersistentUserDirectory(
        new InMemoryUserDirectoryPersistence(),
        BouncyCastleHashingServices.modern()));
  }

  @Test
  @DisplayName("directory() never returns null after setDirectory")
  void directoryNonNull() {
    UserDirectory replacement = new PersistentUserDirectory(
        new InMemoryUserDirectoryPersistence(),
        BouncyCastleHashingServices.modern());
    UserDirectoryProvider.setDirectory(replacement);
    assertNotNull(UserDirectoryProvider.directory());
  }

  @Test
  @DisplayName("setDirectory installs the supplied instance — same reference")
  void setDirectoryInstallsExactInstance() {
    UserDirectory replacement = new PersistentUserDirectory(
        new InMemoryUserDirectoryPersistence(),
        BouncyCastleHashingServices.modern());
    UserDirectoryProvider.setDirectory(replacement);
    assertSame(replacement, UserDirectoryProvider.directory());
  }

  @Test
  @DisplayName("setDirectory(null) is rejected — kills the requireNonNull mutant")
  void nullReplacementRejected() {
    assertThrows(NullPointerException.class,
        () -> UserDirectoryProvider.setDirectory(null));
  }

  @Test
  @DisplayName("setDirectory can be called twice — second replaces first")
  void secondSetReplacesFirst() {
    UserDirectory first = new PersistentUserDirectory(
        new InMemoryUserDirectoryPersistence(),
        BouncyCastleHashingServices.modern());
    UserDirectory second = new PersistentUserDirectory(
        new InMemoryUserDirectoryPersistence(),
        BouncyCastleHashingServices.modern());

    UserDirectoryProvider.setDirectory(first);
    UserDirectoryProvider.setDirectory(second);

    assertSame(second, UserDirectoryProvider.directory());
  }
}
