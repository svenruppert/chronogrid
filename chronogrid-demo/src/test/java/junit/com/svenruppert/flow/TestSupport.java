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

package junit.com.svenruppert.flow;

import com.svenruppert.flow.security.bootstrap.BootstrapWiring;
import com.svenruppert.flow.security.bootstrap.JSentinelBootstrapInitListener;
import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.model.InMemoryUserDirectoryPersistence;
import com.svenruppert.flow.security.model.PersistentUserDirectory;
import com.svenruppert.flow.security.model.UserDirectoryProvider;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.jsentinel.credential.password.bouncycastle.BouncyCastleHashingServices;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-only utilities that reset framework singletons between
 * Browserless tests. Necessary because the security stack caches
 * references in static fields at first use (Vaadin service init,
 * BootstrapWiring), and a second Browserless test would otherwise
 * inherit polluted state from the first.
 */
public final class TestSupport {

  static {
    // Pin the test JVM to English so Browserless tests can assert on
    // the inline English fallback strings regardless of the developer's
    // machine locale. Production locale resolution still works via the
    // request's Accept-Language header and UI.setLocale.
    java.util.Locale.setDefault(java.util.Locale.ENGLISH);
  }

  private TestSupport() {
  }

  /**
   * Installs an in-memory {@link UserDirectoryProvider} containing one
   * administrator, and resets the {@link BootstrapWiring}/listener
   * singletons so the next Vaadin service init rebuilds the bootstrap
   * pipeline against the fresh directory.
   *
   * <p>Call from {@code @BeforeEach} in any Browserless test that
   * navigates to a non-{@code SetupView} route — without this, the
   * bootstrap forwarder reroutes every navigation to {@code /setup}.
   */
  public static void seedAdminAndResetBootstrap() {
    PersistentUserDirectory seeded = new PersistentUserDirectory(
        new InMemoryUserDirectoryPersistence(),
        BouncyCastleHashingServices.modern());
    seeded.addUser("admin", "abcdef-abcdef-1",
        new AppUser(1000L, "Administrator",
            EnumSet.of(AuthorizationRole.ADMIN, AuthorizationRole.USER)));
    UserDirectoryProvider.setDirectory(seeded);
    resetStaticField(BootstrapWiring.class, "current", null);
    resetAtomicBoolean(JSentinelBootstrapInitListener.class, "DONE");
  }

  /**
   * Installs an empty in-memory {@link UserDirectoryProvider} (no admin)
   * and resets the {@link BootstrapWiring} singleton so the next
   * lookup reports {@code bootstrapRequired=true}. Use this from
   * tests that need to verify the bootstrap / SetupView flow.
   */
  public static void clearAdminAndResetBootstrap() {
    PersistentUserDirectory empty = new PersistentUserDirectory(
        new InMemoryUserDirectoryPersistence(),
        BouncyCastleHashingServices.modern());
    UserDirectoryProvider.setDirectory(empty);
    resetStaticField(BootstrapWiring.class, "current", null);
    resetAtomicBoolean(JSentinelBootstrapInitListener.class, "DONE");
  }

  private static void resetStaticField(Class<?> owner, String name, Object value) {
    try {
      Field f = owner.getDeclaredField(name);
      f.setAccessible(true);
      f.set(null, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Failed to reset " + owner.getSimpleName() + "#" + name, e);
    }
  }

  private static void resetAtomicBoolean(Class<?> owner, String name) {
    try {
      Field f = owner.getDeclaredField(name);
      f.setAccessible(true);
      AtomicBoolean flag = (AtomicBoolean) f.get(null);
      flag.set(false);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Failed to reset " + owner.getSimpleName() + "#" + name, e);
    }
  }
}
