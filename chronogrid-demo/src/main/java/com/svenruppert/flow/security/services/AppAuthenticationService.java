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

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.model.Credentials;
import com.svenruppert.flow.security.model.UserDirectoryProvider;
import com.svenruppert.jsentinel.audit.JSentinelAuditService;
import com.svenruppert.jsentinel.audit.LoginSucceeded;
import com.svenruppert.jsentinel.authentication.AuthenticationService;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.autoservice.api.JSentinelAutoService;
import com.svenruppert.jsentinel.bruteforce.LoginAttemptContext;
import com.svenruppert.jsentinel.bruteforce.LoginAttemptDecision;
import com.svenruppert.jsentinel.bruteforce.LoginAttemptPolicy;
import com.vaadin.flow.server.VaadinRequest;

import java.time.Clock;
import java.time.Instant;

/**
 * SPI-registered via {@link JSentinelAutoService @JSentinelAutoService} —
 * the annotation processor produces the matching
 * {@code META-INF/services/com.svenruppert.jsentinel.authentication.AuthenticationService}
 * entry at compile time.
 *
 * <p>Consults the active {@link LoginAttemptPolicy} for throttling
 * before delegating to the user directory; records success / failure
 * back into the policy so brute-force protection works.
 */
@JSentinelAutoService(AuthenticationService.class)
public class AppAuthenticationService
    implements AuthenticationService<Credentials, AppUser>, HasLogger {

  @Override
  public boolean checkCredentials(Credentials credentials) {
    if (credentials == null) {
      return false;
    }

    LoginAttemptPolicy policy = JSentinelServiceResolver.loginAttemptPolicy();
    LoginAttemptContext attempt = LoginAttemptContext.now(
        credentials.username(), currentClientAddress(), null);

    LoginAttemptDecision decision = policy.beforeAttempt(attempt);
    if (decision instanceof LoginAttemptDecision.LockedOut lockout) {
      logger().warn("Login throttled for username={} (remaining={}s, failedAttempts={})",
          credentials.username(), lockout.remaining().toSeconds(), lockout.failedAttempts());
      return false;
    }

    boolean ok = UserDirectoryProvider.directory().checkCredentials(credentials);
    if (ok) {
      policy.recordSuccess(attempt);
      auditLoginSucceeded(credentials.username(), attempt.clientAddress());
    } else {
      policy.recordFailure(attempt);
    }
    return ok;
  }

  @Override
  public AppUser loadSubject(Credentials credentials) {
    return UserDirectoryProvider.directory().findByCredentials(credentials).orElse(null);
  }

  @Override
  public Class<AppUser> subjectType() {
    return AppUser.class;
  }

  private static void auditLoginSucceeded(String username, String clientAddress) {
    JSentinelAuditService sink = JSentinelServiceResolver.securityAuditService();
    try {
      sink.publish(new LoginSucceeded(
          Instant.now(Clock.systemUTC()), username, clientAddress, null));
    } catch (RuntimeException ignored) {
      // never block a successful login because the audit sink failed
    }
  }

  private static String currentClientAddress() {
    try {
      VaadinRequest request = VaadinRequest.getCurrent();
      return request == null ? null : request.getRemoteAddr();
    } catch (RuntimeException ignored) {
      return null;
    }
  }
}
