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

import com.svenruppert.chronogrid.client.CalDavErrors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("CalDavErrors — classification of upstream exceptions")
class CalDavErrorsTest {

  @Test
  @DisplayName("ConcurrentModificationException → CONFLICT")
  void cmeIsConflict() {
    assertEquals(CalDavErrors.Kind.CONFLICT,
        CalDavErrors.classify(new ConcurrentModificationException()));
  }

  @Test
  @DisplayName("NoSuchElementException → NOT_FOUND")
  void noSuchElementIsNotFound() {
    assertEquals(CalDavErrors.Kind.NOT_FOUND,
        CalDavErrors.classify(new NoSuchElementException("nope")));
  }

  @Test
  @DisplayName("HTTP 401 in message → UNAUTHORIZED")
  void http401IsUnauthorized() {
    assertEquals(CalDavErrors.Kind.UNAUTHORIZED,
        CalDavErrors.classify(new IllegalStateException(
            "PUT http://host/cal/abc.ics failed with HTTP 401")));
  }

  @Test
  @DisplayName("HTTP 403 in message → UNAUTHORIZED")
  void http403IsUnauthorized() {
    assertEquals(CalDavErrors.Kind.UNAUTHORIZED,
        CalDavErrors.classify(new IllegalStateException(
            "PROPFIND http://host/ failed with HTTP 403")));
  }

  @Test
  @DisplayName("HTTP 404 in message → NOT_FOUND")
  void http404IsNotFound() {
    assertEquals(CalDavErrors.Kind.NOT_FOUND,
        CalDavErrors.classify(new IllegalStateException(
            "GET http://host/cal/missing.ics failed with HTTP 404")));
  }

  @Test
  @DisplayName("HTTP 503 in message → SERVER")
  void http503IsServer() {
    assertEquals(CalDavErrors.Kind.SERVER,
        CalDavErrors.classify(new IllegalStateException(
            "REPORT http://host/cal/ failed with HTTP 503")));
  }

  @Test
  @DisplayName("HttpTimeoutException nested in cause chain → TIMEOUT")
  void timeoutDetectedThroughCauseChain() {
    Throwable wrapped = new IllegalStateException("I/O failure",
        new HttpTimeoutException("server slow"));
    assertEquals(CalDavErrors.Kind.TIMEOUT, CalDavErrors.classify(wrapped));
  }

  @Test
  @DisplayName("ConnectException → NETWORK")
  void connectExceptionIsNetwork() {
    Throwable wrapped = new IllegalStateException("I/O failure",
        new ConnectException("Connection refused"));
    assertEquals(CalDavErrors.Kind.NETWORK, CalDavErrors.classify(wrapped));
  }

  @Test
  @DisplayName("UnknownHostException → NETWORK")
  void unknownHostIsNetwork() {
    Throwable wrapped = new IllegalStateException("I/O failure",
        new UnknownHostException("no.such.host"));
    assertEquals(CalDavErrors.Kind.NETWORK, CalDavErrors.classify(wrapped));
  }

  @Test
  @DisplayName("unknown failure → GENERIC")
  void unknownIsGeneric() {
    assertEquals(CalDavErrors.Kind.GENERIC,
        CalDavErrors.classify(new IllegalArgumentException("dunno")));
  }

  @Test
  @DisplayName("null → GENERIC")
  void nullIsGeneric() {
    assertEquals(CalDavErrors.Kind.GENERIC, CalDavErrors.classify(null));
  }

  @Test
  @DisplayName("detail() shortens overly long messages and never returns null")
  void detailTruncatesAndIsNonNull() {
    String longMsg = "x".repeat(500);
    String detail = CalDavErrors.detail(new RuntimeException(longMsg));
    assertNotNull(detail);
    assertEquals(198, detail.length(),
        "detail must be capped to 198 characters (197 + 1-char ellipsis '…')");
  }
}
