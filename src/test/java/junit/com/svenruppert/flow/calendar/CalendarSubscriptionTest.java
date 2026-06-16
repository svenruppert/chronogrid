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

import com.svenruppert.flow.calendar.service.CalendarSubscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CalendarSubscription — record + withVisible")
class CalendarSubscriptionTest {

  private static final URI HOME =
      URI.create("https://caldav.icloud.com/123/calendars/home/");

  @Test
  @DisplayName("constructor preserves all four fields")
  void carriesAllFields() {
    CalendarSubscription sub =
        new CalendarSubscription(HOME, "Home", "#FF0000", true);
    assertEquals(HOME, sub.uri());
    assertEquals("Home", sub.displayName());
    assertEquals("#FF0000", sub.color());
    assertTrue(sub.visible());
  }

  @Test
  @DisplayName("null URI is rejected")
  void rejectsNullUri() {
    assertThrows(NullPointerException.class,
        () -> new CalendarSubscription(null, "x", "#000000", true));
  }

  @Test
  @DisplayName("withVisible flips only the visibility flag, returns a new instance")
  void withVisibleFlipsOnlyVisibleFlag() {
    CalendarSubscription on =
        new CalendarSubscription(HOME, "Home", "#FF0000", true);
    CalendarSubscription off = on.withVisible(false);

    assertNotSame(on, off, "withVisible must yield a new record");
    assertFalse(off.visible(), "off.visible() must be false");
    assertEquals(on.uri(), off.uri());
    assertEquals(on.displayName(), off.displayName());
    assertEquals(on.color(), off.color());
  }

  @Test
  @DisplayName("a record without color is allowed (null colour)")
  void nullColorAllowed() {
    CalendarSubscription sub =
        new CalendarSubscription(HOME, "Home", null, true);
    assertEquals(null, sub.color());
  }
}
