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

import com.svenruppert.chronogrid.service.CalDavConnectionConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CalDavConnectionConfig — normalisation + hasAuth")
class CalDavConnectionConfigTest {

  private static final URI URI_LOCAL =
      URI.create("http://127.0.0.1:5232/calendars/personal/");

  @Test
  @DisplayName("anonymous(...) yields a config without credentials")
  void anonymousFactory() {
    CalDavConnectionConfig c = CalDavConnectionConfig.anonymous(URI_LOCAL);
    assertFalse(c.hasAuth());
    assertNull(c.username());
    assertNull(c.password());
  }

  @Test
  @DisplayName("null URI throws")
  void rejectsNullUri() {
    assertThrows(NullPointerException.class,
        () -> new CalDavConnectionConfig(null, "u", "p"));
  }

  @Test
  @DisplayName("hasAuth() is true only when username is non-blank")
  void hasAuthSemantics() {
    assertTrue(new CalDavConnectionConfig(URI_LOCAL, "alice", "x").hasAuth());
    assertFalse(new CalDavConnectionConfig(URI_LOCAL, "", "x").hasAuth());
    assertFalse(new CalDavConnectionConfig(URI_LOCAL, "  ", "x").hasAuth());
    assertFalse(new CalDavConnectionConfig(URI_LOCAL, null, "x").hasAuth());
  }

  @Test
  @DisplayName("normalised() trims username and nulls blank credentials")
  void normalisedCleansBlanks() {
    CalDavConnectionConfig n =
        new CalDavConnectionConfig(URI_LOCAL, "  bob  ", "secret").normalised();
    assertEquals("bob", n.username());
    assertEquals("secret", n.password());

    CalDavConnectionConfig empty =
        new CalDavConnectionConfig(URI_LOCAL, "", "  ").normalised();
    assertNull(empty.username());
    assertNull(empty.password());
  }

  @Test
  @DisplayName("normalised() keeps the URI as-is")
  void normalisedPreservesUri() {
    CalDavConnectionConfig n =
        new CalDavConnectionConfig(URI_LOCAL, null, null).normalised();
    assertEquals(URI_LOCAL, n.collectionUri());
  }
}
