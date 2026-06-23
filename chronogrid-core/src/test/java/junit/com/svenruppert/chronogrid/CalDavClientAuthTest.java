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

package junit.com.svenruppert.chronogrid;

/*-
 * #%L
 * Calendar — CalDAV headless
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2013 - 2026 Sven Ruppert
 * %%
 * Licensed under the EUPL, Version 1.1 or – as soon they will be
 * approved by the European Commission - subsequent versions of the
 * EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 * #L%
 */

import com.sun.net.httpserver.HttpServer;
import com.svenruppert.chronogrid.client.CalDavClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("CalDavClient — Basic-Auth Authorization header")
class CalDavClientAuthTest {

  private static HttpServer recorder;
  private static URI baseUri;
  private static final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
  private static final AtomicReference<String> lastUserAgentHeader = new AtomicReference<>();

  @BeforeAll
  static void startRecorder() throws IOException {
    recorder = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    recorder.createContext("/", exchange -> {
      lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
      lastUserAgentHeader.set(exchange.getRequestHeaders().getFirst("User-Agent"));
      String body = "<?xml version=\"1.0\"?><d:multistatus xmlns:d=\"DAV:\"/>";
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/xml");
      exchange.sendResponseHeaders(207, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    recorder.start();
    baseUri = URI.create(
        "http://127.0.0.1:" + recorder.getAddress().getPort() + "/cal/");
  }

  @AfterAll
  static void stopRecorder() {
    recorder.stop(0);
  }

  @Test
  @DisplayName("anonymous constructor sends no Authorization header")
  void anonymousHasNoAuthorizationHeader() {
    lastAuthHeader.set(null);
    new CalDavClient(baseUri).findInRange(
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-02T00:00:00Z"));
    assertNull(lastAuthHeader.get(),
        "anonymous CalDavClient must not send Authorization");
  }

  @Test
  @DisplayName("credentialed constructor sends a Basic <base64(user:pass)> header")
  void credentialsAreSentAsBasicAuth() {
    lastAuthHeader.set(null);
    new CalDavClient(baseUri, "alice", "s3cr3t").findInRange(
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-02T00:00:00Z"));
    String header = lastAuthHeader.get();
    assertNotNull(header, "Authorization must be set");
    String expected = "Basic " + Base64.getEncoder().encodeToString(
        "alice:s3cr3t".getBytes(StandardCharsets.UTF_8));
    assertEquals(expected, header);
  }

  @Test
  @DisplayName("every request carries the project User-Agent header")
  void userAgentHeaderIsSent() {
    lastUserAgentHeader.set(null);
    new CalDavClient(baseUri).findInRange(
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-02T00:00:00Z"));
    assertEquals(CalDavClient.USER_AGENT, lastUserAgentHeader.get(),
        "CalDavClient must identify itself with its branded User-Agent");
  }

  @Test
  @DisplayName("blank username falls back to no auth header")
  void blankUsernameDisablesAuth() {
    lastAuthHeader.set(null);
    new CalDavClient(baseUri, "  ", "ignored").findInRange(
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-02T00:00:00Z"));
    assertNull(lastAuthHeader.get());
  }

  @Test
  @DisplayName("Planning-Feature #9: withBearerToken sends a Bearer <accessToken> header")
  void bearerTokenIsSentAsBearerAuth() {
    lastAuthHeader.set(null);
    java.util.function.Supplier<String> supplier = () -> "ya29.A0AfH6SMBExampleToken";
    CalDavClient.withBearerToken(baseUri, supplier)
        .findInRange(
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T00:00:00Z"));
    assertEquals("Bearer ya29.A0AfH6SMBExampleToken", lastAuthHeader.get(),
        "Bearer-token CalDavClient must prefix the token with \"Bearer \"");
  }

  @Test
  @DisplayName("Planning-Feature #9: withBearerToken consults the supplier on every request (rolling tokens)")
  void bearerSupplierIsConsultedPerRequest() {
    java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
    java.util.function.Supplier<String> supplier =
        () -> "token-" + counter.incrementAndGet();
    CalDavClient client = CalDavClient.withBearerToken(baseUri, supplier);
    lastAuthHeader.set(null);
    client.findInRange(Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-02T00:00:00Z"));
    String first = lastAuthHeader.get();
    lastAuthHeader.set(null);
    client.findInRange(Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-02T00:00:00Z"));
    String second = lastAuthHeader.get();
    assertEquals("Bearer token-1", first,
        "first request must consult supplier once");
    assertEquals("Bearer token-2", second,
        "second request must consult supplier again — that's how "
            + "token refresh becomes transparent to the rest of the client");
  }

  @Test
  @DisplayName("Planning-Feature #9 Schicht 7: 401 → onAuthRejected + retry once with fresh token")
  void bearer401TriggersInvalidateAndRetry() throws IOException {
    // Stand up a single-shot 401-then-200 server so the test can
    // observe both the invalidate hook firing AND the retry going
    // out with a fresh token.
    com.sun.net.httpserver.HttpServer flakyServer =
        com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    java.util.concurrent.atomic.AtomicInteger requestCount =
        new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicReference<String> firstAuth = new java.util.concurrent.atomic.AtomicReference<>();
    java.util.concurrent.atomic.AtomicReference<String> secondAuth = new java.util.concurrent.atomic.AtomicReference<>();
    flakyServer.createContext("/", exchange -> {
      int n = requestCount.incrementAndGet();
      String auth = exchange.getRequestHeaders().getFirst("Authorization");
      if (n == 1) firstAuth.set(auth);
      else secondAuth.set(auth);
      if (n == 1) {
        exchange.sendResponseHeaders(401, -1);
        exchange.close();
      } else {
        String body = "<?xml version=\"1.0\"?><d:multistatus xmlns:d=\"DAV:\"/>";
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/xml");
        exchange.sendResponseHeaders(207, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      }
    });
    flakyServer.start();
    URI flakyBase = URI.create("http://127.0.0.1:"
        + flakyServer.getAddress().getPort() + "/cal/");

    try {
      java.util.concurrent.atomic.AtomicInteger tokenCounter = new java.util.concurrent.atomic.AtomicInteger();
      java.util.concurrent.atomic.AtomicInteger invalidateCount = new java.util.concurrent.atomic.AtomicInteger();
      com.svenruppert.chronogrid.client.BearerTokenSource source =
          new com.svenruppert.chronogrid.client.BearerTokenSource() {
            @Override
            public String currentToken() {
              return "rolling-token-" + tokenCounter.incrementAndGet();
            }
            @Override
            public void onAuthRejected() {
              invalidateCount.incrementAndGet();
            }
          };

      com.svenruppert.chronogrid.client.CalDavClient.withBearerToken(flakyBase, source)
          .findInRange(
              Instant.parse("2026-01-01T00:00:00Z"),
              Instant.parse("2026-01-02T00:00:00Z"));

      assertEquals(2, requestCount.get(),
          "401 must trigger exactly one retry (total 2 requests)");
      assertEquals(1, invalidateCount.get(),
          "onAuthRejected must fire exactly once before the retry");
      assertEquals("Bearer rolling-token-1", firstAuth.get(),
          "first request carries the initial token");
      assertEquals("Bearer rolling-token-2", secondAuth.get(),
          "retry carries a freshly-issued token — that's how a "
              + "revoked-mid-session refresh recovers without a user reauth");
    } finally {
      flakyServer.stop(0);
    }
  }

  @Test
  @DisplayName("Planning-Feature #9: withBearerToken treats null token as anonymous (no header)")
  void bearerNullTokenIsAnonymous() {
    lastAuthHeader.set(null);
    java.util.function.Supplier<String> nullSupplier = () -> null;
    CalDavClient.withBearerToken(baseUri, nullSupplier)
        .findInRange(
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T00:00:00Z"));
    assertNull(lastAuthHeader.get(),
        "null token must skip the Authorization header — leaves the "
            + "request anonymous instead of sending \"Bearer null\"");
  }
}
