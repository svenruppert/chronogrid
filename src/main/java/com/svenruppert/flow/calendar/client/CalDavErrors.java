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

package com.svenruppert.flow.calendar.client;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.NoSuchElementException;

/**
 * Maps a {@link Throwable} coming out of {@link CalDavClient} or
 * {@link CalDavDiscovery} to one of a small set of stable error
 * categories. The UI looks up i18n strings keyed by the category and
 * substitutes the raw exception detail into it — so users see
 * "Wrong username or password" instead of "I/O failure talking to
 * https://…: HTTP 401".
 *
 * <p>Kept exception-shaped on purpose: Phase 5 will swap in a sealed
 * {@code CalDavError} type from {@code functional-reactive}; until
 * then this classifier translates whatever bubbles up.
 */
public final class CalDavErrors {

  public enum Kind {
    UNAUTHORIZED,   // HTTP 401 / 403
    NOT_FOUND,      // HTTP 404
    CONFLICT,       // HTTP 412
    TIMEOUT,        // HttpTimeoutException
    NETWORK,        // ConnectException / UnknownHostException / generic IO
    SERVER,         // HTTP 5xx
    MALFORMED,      // unparseable response
    GENERIC         // anything else
  }

  private CalDavErrors() {
  }

  public static Kind classify(Throwable t) {
    if (t == null) return Kind.GENERIC;

    if (t instanceof java.util.ConcurrentModificationException) return Kind.CONFLICT;
    if (t instanceof NoSuchElementException) return Kind.NOT_FOUND;

    Throwable cause = t;
    while (cause != null) {
      if (cause instanceof HttpTimeoutException) return Kind.TIMEOUT;
      if (cause instanceof ConnectException) return Kind.NETWORK;
      if (cause instanceof UnknownHostException) return Kind.NETWORK;
      cause = cause.getCause();
    }

    String msg = t.getMessage();
    if (msg != null) {
      if (msg.contains("HTTP 401") || msg.contains("HTTP 403")) return Kind.UNAUTHORIZED;
      if (msg.contains("HTTP 404")) return Kind.NOT_FOUND;
      if (msg.contains("HTTP 412")) return Kind.CONFLICT;
      if (msg.contains("HTTP 5") && msg.matches(".*HTTP 5\\d\\d.*")) return Kind.SERVER;
      if (msg.contains("Malformed") || msg.contains("misconfigured")) return Kind.MALFORMED;
    }
    return Kind.GENERIC;
  }

  /**
   * Short message for the UI / tooltip. NOT i18n-aware — the caller
   * routes the {@link Kind} through {@code I18n.tr(...)}.
   */
  public static String detail(Throwable t) {
    if (t == null) return "";
    String msg = t.getMessage();
    if (msg == null || msg.isBlank()) return t.getClass().getSimpleName();
    return msg.length() > 200 ? msg.substring(0, 197) + "…" : msg;
  }
}
