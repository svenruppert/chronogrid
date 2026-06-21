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

import com.svenruppert.chronogrid.mapping.EntryMapper;
import com.svenruppert.chronogrid.service.CalendarService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.vaadin.stefan.fullcalendar.Entry;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Contract for {@link CalendarService#applyColours(Entry, String)} —
 * the helper that drives the per-event fill / calendar-border split.
 *
 * <p>Two paths cover the whole behaviour:
 *
 * <ul>
 *   <li>Entry without an own colour: both fill and border become the
 *       calendar colour (uniform pre-v01.00.00 look).</li>
 *   <li>Entry with a user-set {@code CUSTOM_ENTRY_COLOR}: fill takes
 *       that colour, border keeps the calendar colour so the
 *       multi-calendar provenance is preserved.</li>
 * </ul>
 *
 * <p>{@link CalendarService#CUSTOM_CALENDAR_COLOR} is stamped on the
 * entry in both cases so downstream CSS hooks can read it
 * independently of FullCalendar's own background/border slots.
 */
@DisplayName("CalendarService.applyColours — per-event fill + calendar-border split")
class CalendarServiceColoursTest {

  private static final String CALENDAR_COLOR = "#2CA02C";
  private static final String ENTRY_COLOR = "#FFAA00";

  @Test
  @DisplayName("entry without own colour → fill + border = calendar colour (uniform)")
  void uniformWhenNoOwnColour() {
    Entry entry = new Entry();

    CalendarService.applyColours(entry, CALENDAR_COLOR);

    assertEquals(CALENDAR_COLOR,
        entry.getCustomProperty(CalendarService.CUSTOM_CALENDAR_COLOR),
        "CUSTOM_CALENDAR_COLOR must always carry the owning calendar's colour");
    assertEquals(CALENDAR_COLOR, entry.getColor(),
        "Without an own colour, fill + border collapse to the calendar colour");
  }

  @Test
  @DisplayName("entry with CUSTOM_ENTRY_COLOR → fill = own, border = calendar")
  void splitWhenOwnColourSet() {
    Entry entry = new Entry();
    entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, ENTRY_COLOR);

    CalendarService.applyColours(entry, CALENDAR_COLOR);

    assertEquals(CALENDAR_COLOR,
        entry.getCustomProperty(CalendarService.CUSTOM_CALENDAR_COLOR),
        "CUSTOM_CALENDAR_COLOR must always carry the owning calendar's colour");
    assertEquals(ENTRY_COLOR, entry.getBackgroundColor(),
        "Fill must take the user-set CUSTOM_ENTRY_COLOR");
    assertEquals(CALENDAR_COLOR, entry.getBorderColor(),
        "Border must keep the calendar colour so the multi-calendar provenance is preserved");
  }

  @Test
  @DisplayName("blank CUSTOM_ENTRY_COLOR is treated as not-set (uniform)")
  void blankEntryColourIgnored() {
    Entry entry = new Entry();
    entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, "   ");

    CalendarService.applyColours(entry, CALENDAR_COLOR);

    assertEquals(CALENDAR_COLOR, entry.getColor(),
        "Whitespace-only CUSTOM_ENTRY_COLOR must not flip the entry into split mode");
  }

  // ── BUG #1: applySubscriptionOverride must preserve the
  //    per-event fill / calendar-border split. The previous
  //    ChronoGrid.applySubscriptionColor used to call
  //    entry.setColor(subColour) directly, which collapsed fill
  //    and border to the subscription colour and silently killed
  //    any user-picked per-event colour.

  private static final String SUB_COLOR = "#8C564B";

  @Test
  @DisplayName("applySubscriptionOverride splits fill + border when entry has own colour")
  void subscriptionOverridePreservesPerEventFill() {
    URI collectionUri = URI.create("https://caldav.example/u/sven/work/");
    Entry entry = new Entry();
    entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, ENTRY_COLOR);
    EntryMapper.writeHref(entry, collectionUri.resolve("meeting.ics"));

    Map<URI, String> subColours = new LinkedHashMap<>();
    subColours.put(collectionUri, SUB_COLOR);

    CalendarService.applySubscriptionOverride(entry, subColours);

    assertEquals(ENTRY_COLOR, entry.getBackgroundColor(),
        "Fill must keep the user-picked CUSTOM_ENTRY_COLOR — BUG #1");
    assertEquals(SUB_COLOR, entry.getBorderColor(),
        "Border must take the user-picked subscription colour");
    assertEquals(SUB_COLOR,
        entry.getCustomProperty(CalendarService.CUSTOM_CALENDAR_COLOR),
        "CUSTOM_CALENDAR_COLOR must reflect the subscription override "
            + "so #5 popover dots and #4 strong-border CSS read the right value");
  }

  @Test
  @DisplayName("applySubscriptionOverride collapses to subscription colour when no own colour")
  void subscriptionOverrideUniformWithoutOwnColour() {
    URI collectionUri = URI.create("https://caldav.example/u/sven/family/");
    Entry entry = new Entry();
    EntryMapper.writeHref(entry, collectionUri.resolve("trip.ics"));

    Map<URI, String> subColours = new LinkedHashMap<>();
    subColours.put(collectionUri, SUB_COLOR);

    CalendarService.applySubscriptionOverride(entry, subColours);

    assertEquals(SUB_COLOR, entry.getColor(),
        "Without an own colour, fill + border collapse to the subscription colour");
    assertEquals(SUB_COLOR,
        entry.getCustomProperty(CalendarService.CUSTOM_CALENDAR_COLOR));
  }

  @Test
  @DisplayName("applySubscriptionOverride is a no-op when the entry has no matching href")
  void subscriptionOverrideNoMatch() {
    URI knownUri = URI.create("https://caldav.example/u/sven/work/");
    URI otherUri = URI.create("https://other.example/u/sven/work/");
    Entry entry = new Entry();
    entry.setColor("#444444");
    EntryMapper.writeHref(entry, otherUri.resolve("meeting.ics"));

    Map<URI, String> subColours = new LinkedHashMap<>();
    subColours.put(knownUri, SUB_COLOR);

    CalendarService.applySubscriptionOverride(entry, subColours);

    assertNotEquals(SUB_COLOR, entry.getColor(),
        "Non-matching subscription must not touch the entry's colour");
  }

  @Test
  @DisplayName("applySubscriptionOverride is a no-op for an empty subscription map")
  void subscriptionOverrideEmptyMap() {
    Entry entry = new Entry();
    entry.setColor(CALENDAR_COLOR);

    CalendarService.applySubscriptionOverride(entry, Map.of());

    assertEquals(CALENDAR_COLOR, entry.getColor(),
        "Empty subscription map must not touch the entry");
  }
}
