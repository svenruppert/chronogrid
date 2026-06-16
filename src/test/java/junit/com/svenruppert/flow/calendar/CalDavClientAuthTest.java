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

package junit.com.svenruppert.flow.calendar;

import com.sun.net.httpserver.HttpServer;
import com.svenruppert.flow.calendar.client.CalDavClient;
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
}
