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

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.svenruppert.chronogrid.client.CalDavDiscovery;
import com.svenruppert.chronogrid.client.DiscoveredCalendar;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the three-step PROPFIND chain
 * ({@code current-user-principal} → {@code calendar-home-set} →
 * calendar list) against a scripted JDK {@link HttpServer} that
 * impersonates iCloud's response shape.
 */
@DisplayName("CalDavDiscovery — three-step PROPFIND chain (iCloud shape)")
class CalDavDiscoveryTest {

  private static HttpServer server;
  private static URI baseUri;
  private static final Map<String, HttpHandler> handlers = new HashMap<>();
  private static final List<String> requestedPaths = new ArrayList<>();
  private static final AtomicReference<String> lastAuth = new AtomicReference<>();

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      requestedPaths.add(exchange.getRequestURI().getPath());
      lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
      HttpHandler delegate = handlers.get(exchange.getRequestURI().getPath());
      if (delegate != null) {
        delegate.handle(exchange);
      } else {
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
      }
    });
    server.start();
    int port = server.getAddress().getPort();
    baseUri = URI.create("http://127.0.0.1:" + port + "/");
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  @BeforeEach
  void resetRecording() {
    handlers.clear();
    requestedPaths.clear();
    lastAuth.set(null);
  }

  @Test
  @DisplayName("full chain → two calendars, paths resolved relative to step responses")
  void fullChainReturnsCalendars() {
    handlers.put("/", multistatus(
        "<d:response>"
            + "<d:href>/</d:href>"
            + "<d:propstat><d:prop><d:current-user-principal>"
            + "<d:href>/123456789/principal/</d:href>"
            + "</d:current-user-principal></d:prop>"
            + "<d:status>HTTP/1.1 200 OK</d:status></d:propstat>"
            + "</d:response>"));
    handlers.put("/123456789/principal/", multistatus(
        "<d:response>"
            + "<d:href>/123456789/principal/</d:href>"
            + "<d:propstat><d:prop><c:calendar-home-set>"
            + "<d:href>/123456789/calendars/</d:href>"
            + "</c:calendar-home-set></d:prop>"
            + "<d:status>HTTP/1.1 200 OK</d:status></d:propstat>"
            + "</d:response>"));
    handlers.put("/123456789/calendars/", multistatus(
        "<d:response>"
            + "<d:href>/123456789/calendars/</d:href>"
            + "<d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype>"
            + "<d:displayname>Calendars</d:displayname></d:prop>"
            + "<d:status>HTTP/1.1 200 OK</d:status></d:propstat>"
            + "</d:response>"
            + "<d:response>"
            + "<d:href>/123456789/calendars/home/</d:href>"
            + "<d:propstat><d:prop><d:resourcetype><d:collection/><c:calendar/></d:resourcetype>"
            + "<d:displayname>Home</d:displayname></d:prop>"
            + "<d:status>HTTP/1.1 200 OK</d:status></d:propstat>"
            + "</d:response>"
            + "<d:response>"
            + "<d:href>/123456789/calendars/work/</d:href>"
            + "<d:propstat><d:prop><d:resourcetype><d:collection/><c:calendar/></d:resourcetype>"
            + "<d:displayname>Work</d:displayname></d:prop>"
            + "<d:status>HTTP/1.1 200 OK</d:status></d:propstat>"
            + "</d:response>"));

    List<DiscoveredCalendar> found =
        new CalDavDiscovery().discover(baseUri, "alice@example.com", "abcd-efgh-ijkl-mnop");

    assertEquals(2, found.size(), "the calendar-home itself must be filtered out");
    assertEquals("Home", found.get(0).displayName());
    assertEquals("Work", found.get(1).displayName());
    assertTrue(found.get(0).href().toString().endsWith("/123456789/calendars/home/"));
    assertTrue(found.get(1).href().toString().endsWith("/123456789/calendars/work/"));
  }

  @Test
  @DisplayName("Authorization: Basic header is sent on every PROPFIND")
  void authorizationHeaderIsForwarded() {
    handlers.put("/", multistatus(
        "<d:response>"
            + "<d:href>/</d:href>"
            + "<d:propstat><d:prop><d:current-user-principal>"
            + "<d:href>/u/principal/</d:href>"
            + "</d:current-user-principal></d:prop></d:propstat>"
            + "</d:response>"));
    handlers.put("/u/principal/", multistatus(
        "<d:response>"
            + "<d:href>/u/principal/</d:href>"
            + "<d:propstat><d:prop><c:calendar-home-set>"
            + "<d:href>/u/calendars/</d:href>"
            + "</c:calendar-home-set></d:prop></d:propstat>"
            + "</d:response>"));
    handlers.put("/u/calendars/", multistatus(""));

    new CalDavDiscovery().discover(baseUri, "bob", "s3cr3t");

    assertNotNull(lastAuth.get(), "Authorization must be set on the calendar-home call");
    assertTrue(lastAuth.get().startsWith("Basic "),
        "expected Basic … header, got: " + lastAuth.get());
  }

  @Test
  @DisplayName("missing <current-user-principal> → NoSuchElementException")
  void missingPrincipalIsRaised() {
    handlers.put("/", multistatus(
        "<d:response>"
            + "<d:href>/</d:href>"
            + "<d:propstat><d:status>HTTP/1.1 404 Not Found</d:status>"
            + "</d:propstat>"
            + "</d:response>"));

    assertThrows(java.util.NoSuchElementException.class,
        () -> new CalDavDiscovery().discover(baseUri, "x", "y"));
  }

  @Test
  @DisplayName("5xx from PROPFIND → IllegalStateException")
  void serverErrorIsRaised() {
    handlers.put("/", exchange -> {
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
    });
    assertThrows(IllegalStateException.class,
        () -> new CalDavDiscovery().discover(baseUri, "x", "y"));
  }

  // ── helpers ────────────────────────────────────────────────────

  private static HttpHandler multistatus(String responsesXml) {
    return exchange -> {
      String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
          + "<d:multistatus xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">"
          + responsesXml
          + "</d:multistatus>";
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=utf-8");
      exchange.sendResponseHeaders(207, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    };
  }
}
