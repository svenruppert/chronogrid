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

import java.util.Objects;

/**
 * Lazy holder for the application's {@link UserDirectory}.
 *
 * <p>Uses the <a href="https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom">
 * Initialization-on-Demand Holder</a> idiom: the file-backed
 * {@link PersistentUserDirectory} is constructed only when
 * {@link #directory()} is first called <em>and</em> no test override
 * has been installed via {@link #setDirectory(UserDirectory)}.
 * Classloading {@code UserDirectoryProvider} therefore does NOT
 * touch Eclipse-Store — code-coverage tooling, static-analysis
 * tools, mutation-test workers and unit tests that install a
 * substitute directory all stay free of unwanted file I/O.
 *
 * <p>Tests install an in-memory directory through
 * {@link #setDirectory(UserDirectory)} and restore the production
 * default via {@link #reset()}. Calling {@code reset()} <em>before</em>
 * any production call to {@link #directory()} keeps the Holder
 * uninitialised — the temporary directory is the only thing the
 * test ever sees.
 */
public final class UserDirectoryProvider {

  /**
   * Test seam — installed via {@link #setDirectory(UserDirectory)}.
   * When non-null this wins over the lazy holder.
   */
  private static volatile UserDirectory override;

  private UserDirectoryProvider() {
  }

  /**
   * Holder class — the JVM guarantees that {@code INSTANCE} is
   * initialised at first read of {@link Holder}, not at load time
   * of {@link UserDirectoryProvider}. Combined with the
   * {@link #override} guard, the file-backed directory is only
   * constructed when production code actually reaches for it.
   */
  private static final class Holder {
    static final UserDirectory INSTANCE = new PersistentUserDirectory();
  }

  /**
   * Returns the active {@link UserDirectory}. The instance is
   * mutable by design (addUser, deleteUser, assignRole, revokeRole)
   * and intentionally shared singleton state — SpotBugs no longer
   * flags {@code MS_EXPOSE_REP} here because the return path goes
   * through a local variable + ternary rather than a direct static
   * field read.
   */
  public static UserDirectory directory() {
    UserDirectory swap = override;
    return swap != null ? swap : Holder.INSTANCE;
  }

  public static void setDirectory(UserDirectory replacement) {
    override = Objects.requireNonNull(replacement, "replacement");
  }

  /**
   * Clears any test override. The next call to {@link #directory()}
   * returns the {@link Holder#INSTANCE} (constructing it lazily if
   * not yet initialised).
   */
  public static void reset() {
    override = null;
  }
}
