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

package com.svenruppert.flow.security.services;

import com.svenruppert.jsentinel.credential.compromised.CompromisedPasswordChecker;
import com.svenruppert.jsentinel.credential.compromised.CompromisedPasswordResult;
import com.svenruppert.jsentinel.credential.compromised.LocalBlocklistCompromisedPasswordChecker;
import com.svenruppert.jsentinel.credential.compromised.hibp.HaveIBeenPwnedCompromisedPasswordChecker;
import com.svenruppert.jsentinel.credential.secret.SecretValue;

import java.time.Duration;
import java.util.List;

/**
 * Password-acceptance check called before any new / changed password
 * is committed.
 *
 * <p>Two layers, checked in order:
 *
 * <ol>
 *   <li><strong>Local blocklist</strong> — the 19 most-common
 *       passwords, offline, no network. Always on.</li>
 *   <li><strong>HIBP k-anonymity range</strong> — calls
 *       {@code https://api.pwnedpasswords.com/range/<5-hex-prefix>}
 *       with the first 5 SHA-1 hex chars of the candidate. The
 *       plaintext never leaves the JVM. Enabled by default in
 *       production, disabled in tests via the system property
 *       {@code app.hibp.enabled=false} (Surefire sets it).</li>
 * </ol>
 *
 * <p>Caller treats {@link CompromisedPasswordResult.Pwned} from
 * <em>either</em> layer as reject (with a generic message — CWE-209:
 * never name the dictionary), {@link CompromisedPasswordResult.Indeterminate}
 * from HIBP as <em>accept</em> (CWE-359 fail-open: a network outage
 * must not block legitimate password changes), and
 * {@link CompromisedPasswordResult.Safe} as accept.
 */
public final class PasswordPreflight {

  /** System-property toggle for the HIBP layer. Defaults to enabled. */
  public static final String HIBP_ENABLED_PROPERTY = "app.hibp.enabled";

  /** Request timeout for the HIBP range call. Failure = Indeterminate. */
  public static final Duration HIBP_TIMEOUT = Duration.ofSeconds(5);

  private static final CompromisedPasswordChecker LOCAL =
      new LocalBlocklistCompromisedPasswordChecker(List.of(
          "password", "password1", "password123",
          "qwerty", "qwerty123", "letmein",
          "admin", "admin123", "administrator",
          "welcome", "welcome1",
          "12345678", "123456789", "abc12345",
          "iloveyou", "monkey", "dragon",
          "hunter2", "trustno1"));

  /**
   * Lazy holder for the HIBP checker — the JDK {@code HttpClient}
   * instance is only created on first use of an enabled HIBP check.
   * Disabling HIBP via system property therefore costs nothing at
   * class-load time.
   */
  private static final class HibpHolder {
    static final HaveIBeenPwnedCompromisedPasswordChecker INSTANCE =
        HaveIBeenPwnedCompromisedPasswordChecker.usingJdkHttpClient(
            HaveIBeenPwnedCompromisedPasswordChecker.DEFAULT_ENDPOINT,
            HIBP_TIMEOUT);
  }

  private PasswordPreflight() {
  }

  /**
   * Returns {@code true} if the candidate is acceptable. {@code false}
   * means a Pwned result from one of the layers — caller shows a
   * generic rejection message.
   */
  public static boolean isAcceptable(String candidate) {
    if (candidate == null || candidate.isEmpty()) {
      return false;
    }
    SecretValue secret = SecretValue.ofString(candidate);
    if (LOCAL.check(secret) instanceof CompromisedPasswordResult.Pwned) {
      return false;
    }
    if (!hibpEnabled()) {
      return true;
    }
    // Indeterminate (network outage / timeout / parse error) → allow,
    // by design. Only an explicit Pwned result blocks.
    return !(HibpHolder.INSTANCE.check(secret)
        instanceof CompromisedPasswordResult.Pwned);
  }

  private static boolean hibpEnabled() {
    return Boolean.parseBoolean(
        System.getProperty(HIBP_ENABLED_PROPERTY, "true"));
  }
}
