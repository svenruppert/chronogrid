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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * CalDAV service-location discovery per RFC 4791 §6 / RFC 6764.
 * Drives the three-PROPFIND chain a client uses to figure out which
 * calendars a user has on a server:
 *
 * <ol>
 *   <li>Start URI → {@code <DAV:current-user-principal>}.</li>
 *   <li>Principal URI → {@code <C:calendar-home-set>}.</li>
 *   <li>Calendar-home URI → list of child resources whose
 *       {@code <DAV:resourcetype>} carries {@code <C:calendar/>}.</li>
 * </ol>
 *
 * <p>The Settings dialog calls {@link #discover(URI, String, String)};
 * the resulting list backs the calendar-picker the user chooses from
 * before saving the Settings config.
 *
 * <p>XML parsing uses the same XXE-hardened JAXP setup as
 * {@link CalDavClient}.
 */
public final class CalDavDiscovery implements HasLogger {

  private static final String APPLICATION_XML_UTF8 = "application/xml; charset=utf-8";
  private static final String DAV_NS = "DAV:";
  private static final String CALDAV_NS = "urn:ietf:params:xml:ns:caldav";

  private final HttpClient http;
  private final Duration timeout;

  public CalDavDiscovery() {
    this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build(),
        Duration.ofSeconds(30));
  }

  public CalDavDiscovery(HttpClient http, Duration timeout) {
    this.http = http;
    this.timeout = timeout;
  }

  /**
   * Runs the full discovery chain against {@code startUri} and returns
   * every calendar the credentials see. Order is server-defined.
   */
  public List<DiscoveredCalendar> discover(URI startUri,
                                           String username, String password) {
    if (startUri == null) {
      throw new IllegalArgumentException("startUri must not be null");
    }
    logger().info("Discovery start uri={} user={}",
        startUri, username == null ? "<anonymous>" : username);
    String authHeader = buildBasicAuthHeader(username, password);
    URI principal = findPrincipal(startUri, authHeader);
    logger().info("Discovery step 1: principal={}", principal);
    URI calendarHome = findCalendarHome(principal, authHeader);
    logger().info("Discovery step 2: calendar-home={}", calendarHome);
    List<DiscoveredCalendar> calendars = findCalendars(calendarHome, authHeader);
    logger().info("Discovery step 3: found {} calendar(s) under {}",
        calendars.size(), calendarHome);
    return calendars;
  }

  // ── step 1 — current-user-principal ────────────────────────────

  URI findPrincipal(URI startUri, String authHeader) {
    String body = ""
        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        + "<d:propfind xmlns:d=\"DAV:\">\n"
        + "  <d:prop><d:current-user-principal/></d:prop>\n"
        + "</d:propfind>\n";
    Document doc = propfind(startUri, "0", body, authHeader);
    String href = firstHrefIn(doc, DAV_NS, "current-user-principal");
    if (href == null || href.isBlank()) {
      throw new NoSuchElementException(
          "No <DAV:current-user-principal> in PROPFIND response from " + startUri);
    }
    return startUri.resolve(href);
  }

  // ── step 2 — calendar-home-set ─────────────────────────────────

  URI findCalendarHome(URI principalUri, String authHeader) {
    String body = ""
        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        + "<d:propfind xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">\n"
        + "  <d:prop><c:calendar-home-set/></d:prop>\n"
        + "</d:propfind>\n";
    Document doc = propfind(principalUri, "0", body, authHeader);
    String href = firstHrefIn(doc, CALDAV_NS, "calendar-home-set");
    if (href == null || href.isBlank()) {
      throw new NoSuchElementException(
          "No <C:calendar-home-set> in PROPFIND response from " + principalUri);
    }
    return principalUri.resolve(href);
  }

  // ── step 3 — enumerate calendars ───────────────────────────────

  private static final String APPLE_NS = "http://apple.com/ns/ical/";

  List<DiscoveredCalendar> findCalendars(URI calendarHomeUri, String authHeader) {
    String body = ""
        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        + "<d:propfind xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\"\n"
        + "            xmlns:apple=\"http://apple.com/ns/ical/\">\n"
        + "  <d:prop>\n"
        + "    <d:resourcetype/>\n"
        + "    <d:displayname/>\n"
        + "    <apple:calendar-color/>\n"
        + "  </d:prop>\n"
        + "</d:propfind>\n";
    Document doc = propfind(calendarHomeUri, "1", body, authHeader);
    List<DiscoveredCalendar> out = new ArrayList<>();
    NodeList responses = doc.getElementsByTagNameNS(DAV_NS, "response");
    for (int i = 0; i < responses.getLength(); i++) {
      Element response = (Element) responses.item(i);
      if (!isCalendarResource(response)) continue;
      String href = childText(response, DAV_NS, "href");
      if (href == null || href.isBlank()) continue;
      URI absolute = calendarHomeUri.resolve(href);
      if (absolute.equals(calendarHomeUri)) continue;
      String displayName = firstNonEmpty(
          firstPropText(response, DAV_NS, "displayname"),
          lastPathSegment(absolute));
      String color = normaliseColor(firstPropText(response, APPLE_NS, "calendar-color"));
      out.add(new DiscoveredCalendar(absolute, displayName, color));
    }
    return out;
  }

  /**
   * Strips an optional alpha byte from a CalDAV calendar-color
   * (iCloud emits "#FF0000FF" — RRGGBBAA). Returns {@code #FF0000}
   * for CSS use; or {@code null} if the value is blank / malformed.
   */
  static String normaliseColor(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return null;
    if (trimmed.startsWith("#") && trimmed.length() == 9) {
      return trimmed.substring(0, 7);
    }
    return trimmed;
  }

  // ── HTTP / XML helpers ─────────────────────────────────────────

  private Document propfind(URI uri, String depth, String body, String authHeader) {
    HttpRequest.Builder b = HttpRequest.newBuilder(uri)
        .timeout(timeout)
        .header("User-Agent", CalDavClient.USER_AGENT)
        .header("Depth", depth)
        .header("Content-Type", APPLICATION_XML_UTF8)
        .method("PROPFIND",
            HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (authHeader != null) {
      b = b.header("Authorization", authHeader);
    }
    HttpResponse<String> resp;
    try {
      resp = http.send(b.build(),
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (java.io.IOException ioe) {
      throw new IllegalStateException(
          "I/O failure during PROPFIND to " + uri, ioe);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted during PROPFIND to " + uri, ie);
    }
    int code = resp.statusCode();
    if (code != HttpStatus.MULTI_STATUS.code() && code != HttpStatus.OK.code()) {
      logger().warn("PROPFIND {} (Depth: {}) -> HTTP {}", uri, depth, code);
      throw new IllegalStateException(
          "PROPFIND " + uri + " failed with HTTP " + code);
    }
    return parseSecureXml(resp.body());
  }

  private static boolean isCalendarResource(Element response) {
    NodeList types = response.getElementsByTagNameNS(DAV_NS, "resourcetype");
    for (int i = 0; i < types.getLength(); i++) {
      Element rt = (Element) types.item(i);
      if (rt.getElementsByTagNameNS(CALDAV_NS, "calendar").getLength() > 0) {
        return true;
      }
    }
    return false;
  }

  private static String firstHrefIn(Document doc, String ns, String localName) {
    NodeList containers = doc.getElementsByTagNameNS(ns, localName);
    for (int i = 0; i < containers.getLength(); i++) {
      Element container = (Element) containers.item(i);
      NodeList hrefs = container.getElementsByTagNameNS(DAV_NS, "href");
      if (hrefs.getLength() > 0) {
        return textContent(hrefs.item(0));
      }
    }
    return null;
  }

  private static String firstPropText(Element response, String ns, String localName) {
    NodeList list = response.getElementsByTagNameNS(ns, localName);
    if (list.getLength() == 0) return null;
    return textContent(list.item(0));
  }

  private static String childText(Element parent, String ns, String localName) {
    NodeList list = parent.getElementsByTagNameNS(ns, localName);
    if (list.getLength() == 0) return null;
    return textContent(list.item(0));
  }

  private static String textContent(Node node) {
    String text = node.getTextContent();
    return text == null ? null : text.trim();
  }

  private static String firstNonEmpty(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return b;
  }

  private static String lastPathSegment(URI uri) {
    String path = uri.getPath();
    if (path == null || path.isEmpty()) return uri.toString();
    String stripped = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    int slash = stripped.lastIndexOf('/');
    return slash < 0 ? stripped : stripped.substring(slash + 1);
  }

  private static String buildBasicAuthHeader(String username, String password) {
    if (username == null || username.isBlank()) return null;
    String pw = password == null ? "" : password;
    String token = Base64.getEncoder().encodeToString(
        (username + ":" + pw).getBytes(StandardCharsets.UTF_8));
    return "Basic " + token;
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
      throw new IllegalStateException("Malformed PROPFIND response", pe);
    }
  }
}
