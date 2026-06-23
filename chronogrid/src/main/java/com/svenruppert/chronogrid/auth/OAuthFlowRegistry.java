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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Planning-Feature #9 Schicht 4 — in-process bookkeeping for
 * in-flight OAuth authorization-code flows.
 *
 * <p>The browser-based OAuth dance is intrinsically two-step: the
 * wizard opens Google's consent page in a new tab, then the user
 * grants the scope and Google redirects to our
 * {@code /oauth/callback/google} route. The state parameter is the
 * only thread linking the two — every {@code register} call
 * publishes the (state → pending-flow) tuple here, and the
 * callback view looks it up by the {@code state} parameter that
 * Google echoes back.
 *
 * <p>State + code-verifier are short-lived secrets:
 *
 * <ul>
 *   <li><strong>state</strong> — random opaque UUID. Google
 *       echoes it back verbatim, the wizard checks they match
 *       (CSRF defence: a spoofed callback URL fired by a third
 *       party can't guess our state).</li>
 *   <li><strong>code-verifier</strong> — PKCE secret. The wizard
 *       SHA-256s it into a {@code code_challenge} that goes to
 *       Google with the initial request; on token exchange we
 *       send the original verifier and Google checks the hash.
 *       Defence against interception of the authorization code on
 *       the redirect URL.</li>
 * </ul>
 *
 * <p>The {@link OAuthFlow} record carries the verifier + a
 * {@link BiConsumer} callback the registry invokes when the flow
 * resolves. The callback receives either
 * ({@code GoogleOAuthCredentials}, {@code null}) for success or
 * ({@code null}, error message) for failure — the wizard threads
 * both into its UI without round-tripping back through the
 * registry.
 *
 * <p>This registry is process-wide static. A multi-tenant
 * deployment would need to scope it per-session, but the blog-
 * demo target is single-tenant. The {@code state} UUID's
 * uniqueness alone prevents cross-session bleed-through (two
 * concurrent wizards from different sessions get distinct UUIDs).
 */
public final class OAuthFlowRegistry {

  private static final Map<String, OAuthFlow> FLOWS = new ConcurrentHashMap<>();

  private OAuthFlowRegistry() { }

  /**
   * Publishes a pending OAuth flow. The {@code state} value is
   * what we put on the authorization-URL we send the user to;
   * Google will echo it on the callback redirect.
   */
  public static void register(String state, OAuthFlow flow) {
    if (state == null || state.isBlank()) {
      throw new IllegalArgumentException("state must not be blank");
    }
    if (flow == null) {
      throw new IllegalArgumentException("flow must not be null");
    }
    FLOWS.put(state, flow);
  }

  /**
   * Removes and returns the pending flow keyed by the given state.
   * Used by {@code OAuthCallbackView} exactly once per inbound
   * callback. Returns {@code null} if no flow matches — that's how
   * the callback view distinguishes a legitimate request from a
   * stale or spoofed one.
   */
  public static OAuthFlow consume(String state) {
    if (state == null) return null;
    return FLOWS.remove(state);
  }

  /**
   * Test seam — drops everything. Call from {@code @BeforeEach} to
   * keep tests independent.
   */
  public static void reset() {
    FLOWS.clear();
  }

  /**
   * Test seam — checks whether a flow is currently registered
   * without consuming it.
   */
  public static boolean isRegistered(String state) {
    return state != null && FLOWS.containsKey(state);
  }

  /**
   * A pending OAuth flow waiting for the Google redirect.
   *
   * @param codeVerifier  PKCE secret, sent on token exchange so
   *                      Google can verify the matching code_challenge
   *                      we provided initially
   * @param clientId      OAuth client ID (from the Wizard's user
   *                      input — pre-filled from env vars when
   *                      available)
   * @param clientSecret  OAuth client secret (paired with clientId)
   * @param redirectUri   the registered callback URL — Google
   *                      requires us to send this on the token-
   *                      exchange request too, and it must match
   *                      what we sent initially
   * @param onComplete    invoked by the callback view when the
   *                      flow resolves. First argument: the
   *                      obtained {@link GoogleOAuthCredentials}
   *                      on success, {@code null} on failure.
   *                      Second: human-readable error message on
   *                      failure, {@code null} on success.
   */
  public record OAuthFlow(
      String codeVerifier,
      String clientId,
      String clientSecret,
      String redirectUri,
      BiConsumer<GoogleOAuthCredentials, String> onComplete) {

    public OAuthFlow {
      if (codeVerifier == null || codeVerifier.isBlank()) {
        throw new IllegalArgumentException("codeVerifier must not be blank");
      }
      if (clientId == null || clientId.isBlank()) {
        throw new IllegalArgumentException("clientId must not be blank");
      }
      if (clientSecret == null || clientSecret.isBlank()) {
        throw new IllegalArgumentException("clientSecret must not be blank");
      }
      if (redirectUri == null || redirectUri.isBlank()) {
        throw new IllegalArgumentException("redirectUri must not be blank");
      }
      if (onComplete == null) {
        throw new IllegalArgumentException("onComplete must not be null");
      }
    }
  }
}
