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

package com.svenruppert.chronogrid.auth;

import java.io.Serializable;
import java.util.Objects;

/**
 * Planning-Feature #9 — long-lived OAuth 2.0 credentials for a
 * Google Calendar server connection.
 *
 * <p><b>Lifetime model.</b> {@code clientId} + {@code clientSecret}
 * come from the Google Cloud Console OAuth-Client configuration —
 * one pair per project, shared by every user of that ChronoGrid
 * deployment. {@code refreshToken} is per-user, obtained once
 * through the Authorization-Code flow in the Wizard, then stored
 * with the server connection so subsequent refreshes happen
 * silently in the background.
 *
 * <p>Access tokens are NOT held here — they are short-lived
 * (~1 hour) and cached transiently inside the
 * {@link GoogleTokenRefresher}'s per-server cache. That keeps this
 * record serializable across session passivation without leaking
 * a token that may be expired by the time the session deserialises.
 *
 * <p><b>Security note.</b> {@code refreshToken} is a long-lived
 * credential equivalent to a password — losing it means a third
 * party can obtain new access tokens for the calendar scope until
 * the user revokes the grant in their Google account settings.
 * Persistence at rest (e.g. via Serialized Sessions) needs
 * encryption; that's a follow-up tracked under Planning #9 Risks.
 */
public record GoogleOAuthCredentials(String clientId,
                                     String clientSecret,
                                     String refreshToken)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  public GoogleOAuthCredentials {
    Objects.requireNonNull(clientId, "clientId must not be null");
    Objects.requireNonNull(clientSecret, "clientSecret must not be null");
    Objects.requireNonNull(refreshToken, "refreshToken must not be null");
    if (clientId.isBlank()) {
      throw new IllegalArgumentException("clientId must not be blank");
    }
    if (clientSecret.isBlank()) {
      throw new IllegalArgumentException("clientSecret must not be blank");
    }
    if (refreshToken.isBlank()) {
      throw new IllegalArgumentException("refreshToken must not be blank");
    }
  }
}
