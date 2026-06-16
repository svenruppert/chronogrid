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

package junit.com.svenruppert.flow;

import com.svenruppert.flow.CalDavDemo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("CalDavDemo — wireDefaultsIfMissing")
class CalDavDemoTest {

  private static final String PROP = "app.caldav.baseUri";
  private String original;

  @BeforeEach
  void capture() {
    original = System.getProperty(PROP);
    System.clearProperty(PROP);
  }

  @AfterEach
  void restore() {
    if (original == null) {
      System.clearProperty(PROP);
    } else {
      System.setProperty(PROP, original);
    }
  }

  @Test
  @DisplayName("unset → installs the testbench default URL")
  void installsDefault() {
    String effective = CalDavDemo.wireDefaultsIfMissing();
    assertEquals(CalDavDemo.DEFAULT_CALDAV_BASE_URI, effective);
    assertEquals(CalDavDemo.DEFAULT_CALDAV_BASE_URI, System.getProperty(PROP));
  }

  @Test
  @DisplayName("blank → installs the testbench default URL")
  void blankIsTreatedAsMissing() {
    System.setProperty(PROP, "   ");
    String effective = CalDavDemo.wireDefaultsIfMissing();
    assertEquals(CalDavDemo.DEFAULT_CALDAV_BASE_URI, effective);
    assertEquals(CalDavDemo.DEFAULT_CALDAV_BASE_URI, System.getProperty(PROP));
  }

  @Test
  @DisplayName("explicit value wins over the default")
  void explicitWins() {
    String custom = "http://internal.example/dav/u1/cal/";
    System.setProperty(PROP, custom);
    String effective = CalDavDemo.wireDefaultsIfMissing();
    assertEquals(custom, effective);
    assertEquals(custom, System.getProperty(PROP));
  }
}
