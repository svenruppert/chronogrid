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

package com.svenruppert.flow.calendar.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.net.URI;

/**
 * Static holder so a single {@link CalendarService} instance can be
 * shared between the {@code CalendarView} and any background hook.
 * Tests redirect the view at the caldav-testbench port by calling
 * {@link #setService(CalendarService)} from {@code @BeforeAll}.
 *
 * <p>Lazily initialises from the system property
 * {@code app.caldav.baseUri} on first read (default
 * {@code http://127.0.0.1:5232/calendars/personal/} — the Radicale
 * convention; override for any real backend).
 */
public final class CalendarServiceProvider {

  private static final String SYSPROP = "app.caldav.baseUri";
  private static final String DEFAULT_URI =
      "http://127.0.0.1:5232/calendars/personal/";

  private static volatile CalendarService instance;

  private CalendarServiceProvider() {
  }

  public static CalendarService service() {
    CalendarService local = instance;
    if (local == null) {
      synchronized (CalendarServiceProvider.class) {
        if (instance == null) {
          instance = new CalendarService(URI.create(
              System.getProperty(SYSPROP, DEFAULT_URI)));
        }
        local = instance;
      }
    }
    return local;
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_STATIC_REP2",
      justification = "This provider is the project's deliberate process-wide DI "
          + "seam — sharing a single CalendarService reference across views and "
          + "tests is its entire purpose.")
  public static void setService(CalendarService service) {
    instance = service;
  }

  public static void reset() {
    instance = null;
  }
}
