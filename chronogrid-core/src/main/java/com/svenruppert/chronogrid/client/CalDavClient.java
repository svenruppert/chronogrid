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

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Minimal CalDAV client. Talks {@code PUT/GET/DELETE/REPORT} to a
 * single calendar collection URI (e.g.
 * {@code http://127.0.0.1:5232/calendars/personal/}) via the JDK
 * {@link HttpClient} and parses {@code REPORT calendar-multiget}
 * responses with a {@link DocumentBuilderFactory} hardened against
 * XXE (no external entities, no DTDs, secure processing on).
 *
 * <p>The 412 {@code Precondition Failed} status is translated into
 * {@link ConcurrentModificationException} so the UI layer can show a
 * conflict notification without inspecting raw HTTP codes.
 */
public final class CalDavClient implements HasLogger {

  private static final String TEXT_CALENDAR_UTF8 = "text/calendar; charset=utf-8";
  private static final String APPLICATION_XML_UTF8 = "application/xml; charset=utf-8";
  private static final String DAV_NS = "DAV:";
  private static final String CALDAV_NS = "urn:ietf:params:xml:ns:caldav";
  public static final String USER_AGENT =
      "flow-template/00.10.00 (+https://8g8.eu/caldav)";
  private static final DateTimeFormatter ICAL_UTC =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private final URI collectionUri;
  private final HttpClient http;
  private final Duration timeout;
  /**
   * Per-request authorization-header source. Returning {@code null}
   * leaves the request anonymous (no {@code Authorization} header).
   * Static credentials (Basic-Auth) bind a constant supplier; rolling
   * credentials (Bearer-Auth with refresh-tokens, per Planning #9)
   * bind a supplier that consults a token cache + refresher.
   *
   * <p>The supplier is invoked on <em>every</em> request — that lets
   * a Bearer-token expiry rollover be transparent to the rest of the
   * client. Implementations that cache should also surface a 401-
   * retry hook (handled by {@link #send} when added in Schicht 3).
   */
  private final java.util.function.Supplier<String> authorizationHeaderSupplier;
  /**
   * Planning-Feature #9 Schicht 7 — token source used for 401-retry.
   * Non-null only on Bearer-Auth clients constructed via
   * {@link #withBearerToken(URI, BearerTokenSource)} (the
   * Supplier-based overload wraps with a no-op invalidate).
   */
  private final BearerTokenSource bearerTokenSource;

  public CalDavClient(URI collectionUri) {
    this(collectionUri, defaultHttpClient(), Duration.ofSeconds(30),
        () -> null, null);
  }

  public CalDavClient(URI collectionUri, String username, String password) {
    this(collectionUri, defaultHttpClient(), Duration.ofSeconds(30),
        staticHeader(buildBasicAuthHeader(username, password)), null);
  }

  public CalDavClient(URI collectionUri, HttpClient http, Duration timeout) {
    this(collectionUri, http, timeout, () -> null, null);
  }

  /**
   * Planning-Feature #9 — Bearer-token variant for Google Calendar
   * (and any future OAuth-secured CalDAV backend). The supplier is
   * consulted on every request: it returns the current
   * {@code Authorization} header value (e.g.
   * {@code "Bearer ya29.A0..."}) or {@code null} to skip the header.
   *
   * <p>The supplier owns its own caching + refresh policy — the
   * client treats it as an opaque source. Token-expiry detection
   * + automatic refresh on 401 is wired in Schicht 3 of Planning #9
   * (the {@code TokenRefresher} service).
   *
   * <p>Convenience: pass a supplier that returns just the access
   * token; the {@code "Bearer "} prefix is added here so callers
   * don't have to repeat it.
   */
  public static CalDavClient withBearerToken(
      URI collectionUri,
      java.util.function.Supplier<String> accessTokenSupplier) {
    return withBearerToken(collectionUri, new BearerTokenSource() {
      @Override
      public String currentToken() {
        return accessTokenSupplier.get();
      }
    });
  }

  /**
   * Planning-Feature #9 Schicht 7 — Bearer-Auth variant with
   * <strong>401-retry</strong>. The {@link BearerTokenSource}
   * provides both the current access token and an
   * {@link BearerTokenSource#onAuthRejected()} hint the client
   * fires when the CalDAV server returns 401.
   *
   * <p>The retry happens once: if the server still returns 401
   * after the rebuild with a freshly-issued token, the response
   * propagates up. Refresh tokens that fail twice in a row are
   * revoked or stale, not a recoverable race — the user has to
   * re-run the wizard.
   */
  public static CalDavClient withBearerToken(URI collectionUri,
                                             BearerTokenSource source) {
    java.util.function.Supplier<String> headerSupplier = () -> {
      String token = source.currentToken();
      return token == null || token.isBlank() ? null : "Bearer " + token;
    };
    return new CalDavClient(collectionUri, defaultHttpClient(),
        Duration.ofSeconds(30), headerSupplier, source);
  }

  private static HttpClient defaultHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  private CalDavClient(URI collectionUri, HttpClient http, Duration timeout,
                       java.util.function.Supplier<String> authorizationHeaderSupplier,
                       BearerTokenSource bearerTokenSource) {
    if (collectionUri == null) {
      throw new IllegalArgumentException("collectionUri must not be null");
    }
    String path = collectionUri.getPath();
    this.collectionUri = path.endsWith("/")
        ? collectionUri
        : collectionUri.resolve(path + "/");
    this.http = http;
    this.timeout = timeout;
    this.authorizationHeaderSupplier = authorizationHeaderSupplier == null
        ? () -> null
        : authorizationHeaderSupplier;
    this.bearerTokenSource = bearerTokenSource;
  }

  private static java.util.function.Supplier<String> staticHeader(String value) {
    return () -> value;
  }

  private static String buildBasicAuthHeader(String username, String password) {
    if (username == null || username.isBlank()) return null;
    String pw = password == null ? "" : password;
    String token = Base64.getEncoder().encodeToString(
        (username + ":" + pw).getBytes(StandardCharsets.UTF_8));
    return "Basic " + token;
  }

  public URI collectionUri() {
    return collectionUri;
  }

  /**
   * Builds the absolute event URI for a given UID, picking the same
   * {@code <uid>.ics} naming the caldav-testbench server uses.
   */
  public URI eventUri(String uid) {
    String safe = URLEncoder.encode(uid, StandardCharsets.UTF_8);
    return collectionUri.resolve(safe + ".ics");
  }

  /** Issues a REPORT calendar-query with a VEVENT time-range filter. */
  public List<RemoteEvent> findInRange(Instant from, Instant to) {
    return reportByComponent("VEVENT", from, to);
  }

  /** Issues a REPORT calendar-query with a VTODO time-range filter. */
  public List<RemoteEvent> findTodosInRange(Instant from, Instant to) {
    return reportByComponent("VTODO", from, to);
  }

  private List<RemoteEvent> reportByComponent(String componentName,
                                              Instant from, Instant to) {
    String body = ""
        + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<c:calendar-query xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">\n"
        + "  <d:prop>\n"
        + "    <d:getetag/>\n"
        + "    <c:calendar-data/>\n"
        + "  </d:prop>\n"
        + "  <c:filter>\n"
        + "    <c:comp-filter name=\"VCALENDAR\">\n"
        + "      <c:comp-filter name=\"" + componentName + "\">\n"
        + "        <c:time-range start=\"" + ICAL_UTC.format(from)
        + "\" end=\"" + ICAL_UTC.format(to) + "\"/>\n"
        + "      </c:comp-filter>\n"
        + "    </c:comp-filter>\n"
        + "  </c:filter>\n"
        + "</c:calendar-query>\n";

    HttpRequest req = baseRequest(collectionUri)
        .header("Depth", "1")
        .header("Content-Type", APPLICATION_XML_UTF8)
        .method("REPORT", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();

    HttpResponse<String> resp = send(req);
    int code = resp.statusCode();
    if (code != HttpStatus.MULTI_STATUS.code()) {
      logger().warn("REPORT {} {} -> HTTP {} (expected 207)",
          componentName, collectionUri, code);
      return List.of();
    }
    List<RemoteEvent> parsed = parseMultistatus(resp.body());
    logger().info("REPORT {} {} -> {} entries", componentName,
        collectionUri, parsed.size());
    return parsed;
  }

  /**
   * PUT a new resource. Server rejects with 412 if the resource
   * already exists ({@code If-None-Match: *}). Returns the new ETag.
   */
  public String putNew(URI eventUri, String iCalBody) {
    HttpRequest req = baseRequest(eventUri)
        .header("Content-Type", TEXT_CALENDAR_UTF8)
        .header("If-None-Match", "*")
        .PUT(HttpRequest.BodyPublishers.ofString(iCalBody, StandardCharsets.UTF_8))
        .build();
    HttpResponse<String> resp = send(req);
    String etag = expectPutSuccess(resp);
    logger().info("PUT(new) {} -> HTTP {} etag={}",
        eventUri, resp.statusCode(), etag);
    return etag;
  }

  /**
   * PUT an existing resource with ETag concurrency check. Returns the
   * new ETag. Throws {@link ConcurrentModificationException} on 412.
   */
  public String putUpdate(URI eventUri, String iCalBody, String expectedEtag) {
    HttpRequest req = baseRequest(eventUri)
        .header("Content-Type", TEXT_CALENDAR_UTF8)
        .header("If-Match", expectedEtag)
        .PUT(HttpRequest.BodyPublishers.ofString(iCalBody, StandardCharsets.UTF_8))
        .build();
    HttpResponse<String> resp = send(req);
    String etag = expectPutSuccess(resp);
    logger().info("PUT(update) {} -> HTTP {} etag {} -> {}",
        eventUri, resp.statusCode(), expectedEtag, etag);
    return etag;
  }

  /**
   * DELETE the resource. {@code expectedEtag} may be {@code null} to
   * skip the {@code If-Match} guard. 404 is silently swallowed (the
   * desired post-state — gone — already holds).
   */
  public void delete(URI eventUri, String expectedEtag) {
    HttpRequest.Builder b = baseRequest(eventUri).DELETE();
    if (expectedEtag != null) {
      b = b.header("If-Match", expectedEtag);
    }
    HttpResponse<String> resp = send(b.build());
    int code = resp.statusCode();
    HttpStatus status = HttpStatus.fromCode(code);
    if (status == HttpStatus.NO_CONTENT
        || status == HttpStatus.OK
        || status == HttpStatus.NOT_FOUND) {
      logger().info("DELETE {} -> HTTP {}", eventUri, code);
      return;
    }
    if (status == HttpStatus.PRECONDITION_FAILED) {
      logger().warn("DELETE {} -> HTTP 412 (stale ETag {})",
          eventUri, expectedEtag);
      throw new ConcurrentModificationException(
          "Stale ETag on DELETE for " + eventUri);
    }
    logger().warn("DELETE {} -> HTTP {} (unexpected)", eventUri, code);
    throw new IllegalStateException(
        "DELETE " + eventUri + " failed with HTTP " + code);
  }

  // ── helpers ────────────────────────────────────────────────────

  private HttpRequest.Builder baseRequest(URI uri) {
    HttpRequest.Builder b = HttpRequest.newBuilder(uri)
        .timeout(timeout)
        .header("User-Agent", USER_AGENT);
    String header = authorizationHeaderSupplier.get();
    if (header != null && !header.isBlank()) {
      b = b.header("Authorization", header);
    }
    return b;
  }

  private HttpResponse<String> send(HttpRequest req) {
    HttpResponse<String> resp = sendOnce(req);
    // Planning-Feature #9 Schicht 7 — Bearer-Auth 401-retry. The
    // cached access token may have been revoked by Google before
    // its declared expiry (e.g. user revoked the grant from
    // myaccount.google.com); a single retry with a freshly-issued
    // token recovers transparently.
    if (resp.statusCode() == 401 && bearerTokenSource != null) {
      bearerTokenSource.onAuthRejected();
      try {
        HttpRequest retry = rebuildWithFreshAuth(req);
        resp = sendOnce(retry);
      } catch (RuntimeException rebuildEx) {
        logger().info("CalDavClient 401-retry rebuild failed: {}",
            rebuildEx.toString());
      }
    }
    return resp;
  }

  private HttpResponse<String> sendOnce(HttpRequest req) {
    try {
      return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (java.io.IOException ioe) {
      throw new IllegalStateException(
          "I/O failure talking to " + req.uri(), ioe);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted talking to " + req.uri(), ie);
    }
  }

  /**
   * Rebuilds a request with a fresh {@code Authorization} header,
   * preserving every other relevant attribute (method, URI, body,
   * timeout, non-restricted headers). Used by the 401-retry path
   * after the {@link BearerTokenSource} has been told its previous
   * token was rejected.
   */
  private HttpRequest rebuildWithFreshAuth(HttpRequest original) {
    HttpRequest.Builder b = HttpRequest.newBuilder(original.uri())
        .method(original.method(),
            original.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
    original.timeout().ifPresent(b::timeout);
    original.headers().map().forEach((name, values) -> {
      if ("Authorization".equalsIgnoreCase(name)) return;
      if (isRestrictedHttpHeader(name)) return;
      for (String v : values) {
        try {
          b.header(name, v);
        } catch (IllegalArgumentException ignored) {
          // some headers stay restricted in custom HTTP-Client
          // implementations — drop silently rather than fail the
          // retry over a non-essential header
        }
      }
    });
    String fresh = authorizationHeaderSupplier.get();
    if (fresh != null && !fresh.isBlank()) {
      b.header("Authorization", fresh);
    }
    return b.build();
  }

  private static boolean isRestrictedHttpHeader(String name) {
    // From java.net.http.HttpRequest.Builder javadoc — these
    // cannot be set by user code on a rebuilt request.
    switch (name.toLowerCase(java.util.Locale.ROOT)) {
      case "connection":
      case "content-length":
      case "expect":
      case "host":
      case "upgrade":
        return true;
      default:
        return false;
    }
  }

  private static String expectPutSuccess(HttpResponse<String> resp) {
    int code = resp.statusCode();
    HttpStatus status = HttpStatus.fromCode(code);
    if (status == HttpStatus.PRECONDITION_FAILED) {
      throw new ConcurrentModificationException(
          "Stale ETag on PUT for " + resp.uri());
    }
    if (status != HttpStatus.CREATED && status != HttpStatus.NO_CONTENT) {
      throw new IllegalStateException(
          "PUT " + resp.uri() + " failed with HTTP " + code);
    }
    return resp.headers().firstValue("ETag")
        .orElseThrow(() -> new IllegalStateException(
            "Server did not return an ETag for PUT " + resp.uri()));
  }

  private List<RemoteEvent> parseMultistatus(String xml) {
    Document doc = parseSecureXml(xml);
    List<RemoteEvent> out = new ArrayList<>();
    NodeList responses = doc.getElementsByTagNameNS(DAV_NS, "response");
    for (int i = 0; i < responses.getLength(); i++) {
      Element response = (Element) responses.item(i);
      String href = childText(response, DAV_NS, "href");
      if (href == null) continue;
      String etag = findPropText(response, DAV_NS, "getetag");
      String ical = findPropText(response, CALDAV_NS, "calendar-data");
      if (etag == null || ical == null) continue;
      URI absolute = collectionUri.resolve(href);
      out.add(new RemoteEvent(absolute, etag, ical));
    }
    return out;
  }

  private static String childText(Element parent, String ns, String localName) {
    NodeList list = parent.getElementsByTagNameNS(ns, localName);
    if (list.getLength() == 0) return null;
    return textContent(list.item(0));
  }

  private static String findPropText(Element response, String ns, String localName) {
    NodeList props = response.getElementsByTagNameNS(ns, localName);
    if (props.getLength() == 0) return null;
    return textContent(props.item(0));
  }

  private static String textContent(Node node) {
    String text = node.getTextContent();
    return text == null ? null : text.trim();
  }

  private static Document parseSecureXml(String xml) {
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
      dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      dbf.setXIncludeAware(false);
      dbf.setExpandEntityReferences(false);
      dbf.setNamespaceAware(true);
      DocumentBuilder db = dbf.newDocumentBuilder();
      return db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    } catch (ParserConfigurationException pce) {
      throw new IllegalStateException("XML parser misconfigured", pce);
    } catch (org.xml.sax.SAXException | java.io.IOException pe) {
      throw new IllegalStateException("Malformed Multistatus XML", pe);
    }
  }

  /** Convenience for callers that have a UID and want to land at the right URI. */
  public RemoteEvent get(String uid) {
    URI uri = eventUri(uid);
    HttpRequest req = baseRequest(uri).GET().build();
    HttpResponse<String> resp = send(req);
    int code = resp.statusCode();
    HttpStatus status = HttpStatus.fromCode(code);
    if (status == HttpStatus.NOT_FOUND) {
      logger().info("GET {} -> HTTP 404 (not found)", uri);
      throw new NoSuchElementException("No event at " + uri);
    }
    if (status != HttpStatus.OK) {
      logger().warn("GET {} -> HTTP {} (unexpected)", uri, code);
      throw new IllegalStateException("GET " + uri + " failed with HTTP " + code);
    }
    String etag = resp.headers().firstValue("ETag")
        .orElseThrow(() -> new IllegalStateException("GET response missing ETag"));
    logger().info("GET {} -> HTTP 200 etag={}", uri, etag);
    return new RemoteEvent(uri, etag, resp.body());
  }
}
