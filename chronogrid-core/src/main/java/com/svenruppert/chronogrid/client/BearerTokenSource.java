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

package com.svenruppert.chronogrid.client;

/**
 * Planning-Feature #9 Schicht 7 — bidirectional token-source contract
 * for Bearer-Auth {@link CalDavClient} variants.
 *
 * <p>Two-way is the key difference from a plain {@link
 * java.util.function.Supplier Supplier&lt;String&gt;}:
 *
 * <ul>
 *   <li>{@link #currentToken()} is called on every request to fetch
 *       the {@code Authorization: Bearer …} value — same as a
 *       supplier.</li>
 *   <li>{@link #onAuthRejected()} is the upstream signal we send
 *       <em>back</em> to the source when the server rejects the
 *       token (HTTP 401). Implementations use it to drop the
 *       cached access token so the next
 *       {@link #currentToken()} call triggers a fresh refresh
 *       round-trip. A no-op default keeps the contract opt-in
 *       for the static-Basic-Auth-supplier wrapper.</li>
 * </ul>
 *
 * <p>The {@link CalDavClient} consumes this interface for the
 * 401-retry path: on a Bearer-Auth request that comes back 401,
 * the client calls {@code onAuthRejected()} and retries the
 * request exactly once with the freshly-issued token. A second
 * 401 is propagated as an exception — refresh-tokens that fail
 * twice in a row are revoked or stale, not a recoverable race.
 */
public interface BearerTokenSource {

  /**
   * Returns the current access token (without the
   * {@code "Bearer "} prefix — {@link CalDavClient} adds it). May
   * return {@code null} or empty to signal "no token available";
   * in that case the request goes out anonymous and almost
   * certainly fails — but the source can recover state on the
   * next call.
   */
  String currentToken();

  /**
   * Hint that the most-recent token returned by
   * {@link #currentToken()} was rejected by the server with 401.
   * Implementations should drop any cached access token so the
   * next {@link #currentToken()} call refreshes against the
   * authorisation server.
   *
   * <p>Default: no-op. Suitable for static Basic-Auth wrappers
   * where there is nothing to invalidate.
   */
  default void onAuthRejected() {
    // no-op by default
  }
}
