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

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Planning-Feature #9 Schicht 3 — caches Google OAuth access tokens
 * per-server and refreshes them transparently against
 * {@code oauth2.googleapis.com/token} when they expire.
 *
 * <p>The {@link CalDavClient#withBearerToken} Bearer-Auth path
 * consumes a {@link Supplier} that this refresher hands out via
 * {@link #bind(GoogleOAuthCredentials, String)}. Every CalDAV
 * request flows through the supplier:
 *
 * <ol>
 *   <li>Cache hit + still fresh (more than 60 s left on the
 *       access token) → return the cached value, no HTTP I/O.</li>
 *   <li>Cache miss or near-expiry → acquire the per-server lock,
 *       double-check the cache (another thread may have just
 *       refreshed), then call the SDK's
 *       {@link GoogleRefreshTokenRequest} to swap the refresh
 *       token for a new access token + expiry, cache the new
 *       entry, return it.</li>
 * </ol>
 *
 * <p>Per-server locking — implemented via a parallel
 * {@code ConcurrentHashMap<serverId, Object>} of mutex objects —
 * prevents two concurrent CalDAV requests to the same server from
 * spawning two refresh round-trips at once. Different servers
 * refresh independently in parallel; that's the whole point of
 * BACKLOG #9's parallel fan-out staying intact.
 *
 * <p>The 60 s safety buffer is the conventional choice: a request
 * that started with a soon-to-expire token would otherwise race
 * the wire round-trip and arrive at Google after expiry, causing
 * a 401. Refreshing one buffer-window early eliminates that race
 * for any sane network latency.
 *
 * <p><b>Lifetime.</b> One refresher instance per UI session is the
 * intended model; the host (typically {@code ChronoGrid}) holds
 * it as a field and calls {@link #close()} on session-destroy to
 * drop cached access tokens. The shared {@link NetHttpTransport}
 * is closed too, releasing its connection pool.
 */
public final class GoogleTokenRefresher implements AutoCloseable {

  /**
   * Safety buffer before token expiry: refresh ahead of the wire
   * round-trip so a request started "just before" the access token
   * dies still carries a fresh one when it arrives.
   */
  public static final int EXPIRY_SAFETY_BUFFER_SECONDS = 60;

  /** Fallback TTL if Google's response omits {@code expires_in}. */
  static final long FALLBACK_TTL_SECONDS = 3599L;

  private final HttpTransport transport;
  private final JsonFactory jsonFactory;
  private final ConcurrentHashMap<String, CachedToken> cache;
  private final ConcurrentHashMap<String, Object> locks;
  private final boolean ownsTransport;

  public GoogleTokenRefresher() {
    this(new NetHttpTransport(), true);
  }

  /**
   * Test seam: inject a mock transport (e.g.
   * {@code MockHttpTransport} from
   * {@code com.google.api-client:google-http-client-test}) for
   * deterministic refresh-flow tests.
   */
  public GoogleTokenRefresher(HttpTransport transport) {
    this(transport, false);
  }

  private GoogleTokenRefresher(HttpTransport transport, boolean ownsTransport) {
    this.transport = transport;
    this.jsonFactory = GsonFactory.getDefaultInstance();
    this.cache = new ConcurrentHashMap<>();
    this.locks = new ConcurrentHashMap<>();
    this.ownsTransport = ownsTransport;
  }

  /**
   * Binds the given credentials + server identity to a
   * {@link Supplier} that delivers a current access token on every
   * call. The supplier is suitable to pass to
   * {@link CalDavClient#withBearerToken(java.net.URI, Supplier)}.
   *
   * <p>The returned supplier closes over the credentials reference
   * — if the caller persists a new {@code refreshToken} for the
   * same server (e.g. re-running the Wizard), they must call
   * {@link #bind} again with the fresh credentials and rebuild the
   * affected {@code CalDavClient}.
   */
  public Supplier<String> bind(GoogleOAuthCredentials credentials,
                               String serverId) {
    if (credentials == null) {
      throw new IllegalArgumentException("credentials must not be null");
    }
    if (serverId == null || serverId.isBlank()) {
      throw new IllegalArgumentException("serverId must not be blank");
    }
    return () -> getValidAccessToken(credentials, serverId);
  }

  /**
   * Planning-Feature #9 Schicht 7 — Bearer-Auth source with
   * 401-retry support. {@link CalDavClient#withBearerToken(java.net.URI,
   * com.svenruppert.chronogrid.client.BearerTokenSource)} consumes
   * this directly. Same per-server cache + refresh path as
   * {@link #bind(GoogleOAuthCredentials, String)}; difference is
   * that the {@code onAuthRejected} hook invalidates the cached
   * access token so the next {@code currentToken} call refreshes.
   */
  public com.svenruppert.chronogrid.client.BearerTokenSource bindAsSource(
      GoogleOAuthCredentials credentials, String serverId) {
    if (credentials == null) {
      throw new IllegalArgumentException("credentials must not be null");
    }
    if (serverId == null || serverId.isBlank()) {
      throw new IllegalArgumentException("serverId must not be blank");
    }
    return new com.svenruppert.chronogrid.client.BearerTokenSource() {
      @Override
      public String currentToken() {
        return getValidAccessToken(credentials, serverId);
      }
      @Override
      public void onAuthRejected() {
        invalidate(serverId);
      }
    };
  }

  String getValidAccessToken(GoogleOAuthCredentials credentials,
                             String serverId) {
    CachedToken cached = cache.get(serverId);
    if (isStillFresh(cached)) return cached.accessToken();

    Object lock = locks.computeIfAbsent(serverId, k -> new Object());
    synchronized (lock) {
      // Double-checked locking: a concurrent caller may have just
      // refreshed while we waited.
      CachedToken second = cache.get(serverId);
      if (isStillFresh(second)) return second.accessToken();

      CachedToken fresh = doRefresh(credentials);
      cache.put(serverId, fresh);
      return fresh.accessToken();
    }
  }

  private static boolean isStillFresh(CachedToken token) {
    if (token == null) return false;
    return token.expiry()
        .isAfter(Instant.now().plusSeconds(EXPIRY_SAFETY_BUFFER_SECONDS));
  }

  private CachedToken doRefresh(GoogleOAuthCredentials credentials) {
    try {
      GoogleRefreshTokenRequest request = new GoogleRefreshTokenRequest(
          transport, jsonFactory,
          credentials.refreshToken(),
          credentials.clientId(),
          credentials.clientSecret());
      TokenResponse response = request.execute();
      Long expiresIn = response.getExpiresInSeconds();
      long ttl = expiresIn != null && expiresIn > 0
          ? expiresIn : FALLBACK_TTL_SECONDS;
      return new CachedToken(
          response.getAccessToken(),
          Instant.now().plusSeconds(ttl));
    } catch (IOException ioe) {
      throw new IllegalStateException(
          "Google OAuth refresh failed for client " + credentials.clientId(),
          ioe);
    }
  }

  /**
   * Forces the next {@link #bind} call for this server to refresh
   * even if the cached token is still fresh. Useful when a CalDAV
   * request comes back with 401 — the cached token may have been
   * revoked early by Google, so dropping it before the retry is
   * the right move.
   */
  public void invalidate(String serverId) {
    cache.remove(serverId);
  }

  /** Test seam: read the cached token without triggering a refresh. */
  CachedToken peek(String serverId) {
    return cache.get(serverId);
  }

  @Override
  public void close() {
    cache.clear();
    locks.clear();
    if (ownsTransport) {
      try {
        transport.shutdown();
      } catch (IOException ignored) {
        // shutdown best-effort — connection pool eviction
      }
    }
  }

  /**
   * Cached access-token tuple. Package-private so the test seam
   * {@link #peek} can expose it without leaking the type publicly.
   */
  record CachedToken(String accessToken, Instant expiry) { }
}
