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

package junit.com.svenruppert.chronogrid.auth;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.svenruppert.chronogrid.auth.GoogleOAuthCredentials;
import com.svenruppert.chronogrid.auth.GoogleTokenRefresher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GoogleTokenRefresher — Planning-Feature #9 Schicht 3")
class GoogleTokenRefresherTest {

  private static final GoogleOAuthCredentials CREDS = new GoogleOAuthCredentials(
      "client-id-abc.apps.googleusercontent.com",
      "client-secret-xyz",
      "1//refresh-token-very-long-string");
  private static final String SERVER_ID = "srv-google-1";

  private MockHttpTransport mockTransportReturning(String accessToken,
                                                   long expiresInSeconds,
                                                   AtomicInteger callCounter) {
    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            callCounter.incrementAndGet();
            return new MockLowLevelHttpResponse()
                .setContentType("application/json")
                .setStatusCode(200)
                .setContent("{"
                    + "\"access_token\":\"" + accessToken + "\","
                    + "\"expires_in\":" + expiresInSeconds + ","
                    + "\"token_type\":\"Bearer\""
                    + "}");
          }
        };
      }
    };
  }

  @Test
  @DisplayName("first call hits Google and caches the access token")
  void firstCallRefreshesAndCaches() {
    AtomicInteger calls = new AtomicInteger();
    try (GoogleTokenRefresher refresher = new GoogleTokenRefresher(
        mockTransportReturning("ya29.first", 3600, calls))) {
      String token = refresher.bind(CREDS, SERVER_ID).get();
      assertEquals("ya29.first", token);
      assertEquals(1, calls.get(),
          "exactly one refresh round-trip on first access");
    }
  }

  @Test
  @DisplayName("second call within expiry window is served from cache (no HTTP)")
  void secondCallHitsCache() {
    AtomicInteger calls = new AtomicInteger();
    try (GoogleTokenRefresher refresher = new GoogleTokenRefresher(
        mockTransportReturning("ya29.cached", 3600, calls))) {
      var supplier = refresher.bind(CREDS, SERVER_ID);
      String first = supplier.get();
      String second = supplier.get();
      assertEquals("ya29.cached", first);
      assertEquals(first, second);
      assertEquals(1, calls.get(),
          "second supplier.get() must serve the cached token — "
              + "no second round-trip");
    }
  }

  @Test
  @DisplayName("invalidate(serverId) forces a fresh refresh on the next get()")
  void invalidateForcesRefresh() {
    AtomicInteger calls = new AtomicInteger();
    try (GoogleTokenRefresher refresher = new GoogleTokenRefresher(
        new MockHttpTransport() {
          @Override
          public LowLevelHttpRequest buildRequest(String m, String u) {
            return new MockLowLevelHttpRequest() {
              @Override
              public LowLevelHttpResponse execute() {
                int n = calls.incrementAndGet();
                return new MockLowLevelHttpResponse()
                    .setContentType("application/json")
                    .setStatusCode(200)
                    .setContent("{\"access_token\":\"ya29.v" + n
                        + "\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
              }
            };
          }
        })) {
      var supplier = refresher.bind(CREDS, SERVER_ID);
      assertEquals("ya29.v1", supplier.get());
      refresher.invalidate(SERVER_ID);
      assertEquals("ya29.v2", supplier.get(),
          "after invalidate the next get() must trigger a fresh refresh");
      assertEquals(2, calls.get());
    }
  }

  @Test
  @DisplayName("two servers refresh independently — separate cache entries")
  void perServerCaching() {
    AtomicInteger calls = new AtomicInteger();
    try (GoogleTokenRefresher refresher = new GoogleTokenRefresher(
        new MockHttpTransport() {
          @Override
          public LowLevelHttpRequest buildRequest(String m, String u) {
            return new MockLowLevelHttpRequest() {
              @Override
              public LowLevelHttpResponse execute() {
                int n = calls.incrementAndGet();
                return new MockLowLevelHttpResponse()
                    .setContentType("application/json")
                    .setStatusCode(200)
                    .setContent("{\"access_token\":\"ya29.s" + n
                        + "\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
              }
            };
          }
        })) {
      String a1 = refresher.bind(CREDS, "srv-a").get();
      String b1 = refresher.bind(CREDS, "srv-b").get();
      String a2 = refresher.bind(CREDS, "srv-a").get();
      assertEquals("ya29.s1", a1);
      assertEquals("ya29.s2", b1);
      assertEquals(a1, a2, "second srv-a call must hit cache");
      assertNotEquals(a1, b1, "different servers get independent caches");
      assertEquals(2, calls.get(), "exactly two refreshes (one per server)");
    }
  }

  @Test
  @DisplayName("expired token (expires_in past the safety buffer) triggers refresh on next get()")
  void expiryTriggersRefresh() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    // expires_in = 30 seconds → less than the 60-second safety
    // buffer → cached entry is treated as already expired.
    try (GoogleTokenRefresher refresher = new GoogleTokenRefresher(
        new MockHttpTransport() {
          @Override
          public LowLevelHttpRequest buildRequest(String m, String u) {
            return new MockLowLevelHttpRequest() {
              @Override
              public LowLevelHttpResponse execute() {
                int n = calls.incrementAndGet();
                return new MockLowLevelHttpResponse()
                    .setContentType("application/json")
                    .setStatusCode(200)
                    .setContent("{\"access_token\":\"ya29.short" + n
                        + "\",\"expires_in\":30,\"token_type\":\"Bearer\"}");
              }
            };
          }
        })) {
      var supplier = refresher.bind(CREDS, SERVER_ID);
      String first = supplier.get();
      String second = supplier.get();
      assertEquals("ya29.short1", first);
      assertEquals("ya29.short2", second,
          "short-lived token (less than the 60s safety buffer) must "
              + "refresh on every get() — that's how the buffer works");
      assertTrue(calls.get() >= 2);
    }
  }

  @Test
  @DisplayName("token-endpoint failure surfaces as IllegalStateException with cause")
  void refreshFailureSurfaces() {
    try (GoogleTokenRefresher refresher = new GoogleTokenRefresher(
        new MockHttpTransport() {
          @Override
          public LowLevelHttpRequest buildRequest(String m, String u) {
            return new MockLowLevelHttpRequest() {
              @Override
              public LowLevelHttpResponse execute() {
                return new MockLowLevelHttpResponse()
                    .setContentType("application/json")
                    .setStatusCode(400)
                    .setContent("{\"error\":\"invalid_grant\","
                        + "\"error_description\":\"Token has been "
                        + "expired or revoked.\"}");
              }
            };
          }
        })) {
      Exception thrown = org.junit.jupiter.api.Assertions.assertThrows(
          IllegalStateException.class,
          () -> refresher.bind(CREDS, SERVER_ID).get());
      assertTrue(thrown.getMessage().contains("Google OAuth refresh failed"),
          "failure must mention which path threw — got: "
              + thrown.getMessage());
    }
  }
}
