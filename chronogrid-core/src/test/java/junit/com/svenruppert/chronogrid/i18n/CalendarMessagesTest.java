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

package junit.com.svenruppert.chronogrid.i18n;

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

import com.svenruppert.chronogrid.i18n.CalendarMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("CalendarMessages — host-i18n seam")
class CalendarMessagesTest {

  @Test
  @DisplayName("fallbackOnly returns the fallback as-is when no args supplied")
  void fallbackOnlyNoArgs() {
    CalendarMessages m = CalendarMessages.fallbackOnly();
    assertEquals("Calendar", m.tr("calendar.heading", "Calendar"));
    assertEquals("", m.tr("anything", ""));
  }

  @Test
  @DisplayName("fallbackOnly runs MessageFormat substitution when args are passed")
  void fallbackOnlyWithArgs() {
    CalendarMessages m = CalendarMessages.fallbackOnly();
    assertEquals("Hidden “Work” from view.",
        m.tr("calendar.notify.subscription.hidden",
            "Hidden “{0}” from view.", "Work"));
  }

  @Test
  @DisplayName("fallbackOnly never returns null")
  void fallbackOnlyNeverNull() {
    CalendarMessages m = CalendarMessages.fallbackOnly();
    assertNotNull(m.tr("any.key", "fb"));
    assertNotNull(m.tr("any.key", "fb", "arg"));
  }

  @Test
  @DisplayName("custom lambda implementation overrides key lookup")
  void customLambda() {
    CalendarMessages m = (key, fb, args) -> "DE:" + key;
    assertEquals("DE:calendar.heading",
        m.tr("calendar.heading", "Calendar"));
  }
}
