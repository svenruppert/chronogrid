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

import com.svenruppert.chronogrid.service.CalDavServerConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CalDavServerConnection — record + create() helper")
class CalDavServerConnectionTest {

  private static final URI ICLOUD = URI.create("https://caldav.icloud.com/");

  @Test
  @DisplayName("create() generates a fresh UUID per call")
  void freshIdEveryCall() {
    CalDavServerConnection a =
        CalDavServerConnection.create("iCloud", ICLOUD, "u@x", "pw");
    CalDavServerConnection b =
        CalDavServerConnection.create("iCloud", ICLOUD, "u@x", "pw");
    assertNotNull(a.id());
    assertNotEquals(a.id(), b.id(),
        "each create() call must produce a fresh id");
  }

  @Test
  @DisplayName("create() with blank displayName falls back to the host")
  void blankDisplayNameDefaultsToHost() {
    CalDavServerConnection s =
        CalDavServerConnection.create("", ICLOUD, null, null);
    assertEquals("caldav.icloud.com", s.displayName());
  }

  @Test
  @DisplayName("create() with a real displayName keeps it")
  void realDisplayNameKept() {
    CalDavServerConnection s =
        CalDavServerConnection.create("My iCloud", ICLOUD, null, null);
    assertEquals("My iCloud", s.displayName());
  }

  @Test
  @DisplayName("hasAuth() is true only for non-blank username")
  void hasAuthSemantics() {
    assertTrue(CalDavServerConnection.create("x", ICLOUD, "u", "p").hasAuth());
    assertFalse(CalDavServerConnection.create("x", ICLOUD, "", "p").hasAuth());
    assertFalse(CalDavServerConnection.create("x", ICLOUD, "   ", "p").hasAuth());
    assertFalse(CalDavServerConnection.create("x", ICLOUD, null, "p").hasAuth());
  }

  @Test
  @DisplayName("null id / null baseUri rejected on direct ctor")
  void rejectsNullsOnCtor() {
    assertThrows(NullPointerException.class,
        () -> new CalDavServerConnection(null, "x", ICLOUD, "u", "p"));
    assertThrows(NullPointerException.class,
        () -> new CalDavServerConnection("id", "x", null, "u", "p"));
  }
}
