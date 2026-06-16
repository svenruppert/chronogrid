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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Error type carried by every {@code Result<T, CalDavError>} returned
 * from {@code CalendarService}. {@link #cause()} is the original
 * exception bubbled up from {@code CalDavClient} / {@code
 * CalDavDiscovery}; {@link #detail()} is its short message capped to
 * 200 characters. {@link #kind()} is the bucket the UI maps to a
 * friendly i18n string via {@link CalDavErrors}.
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP",
    justification = "CalDavError is a value carrier; exposing the originating "
        + "Throwable via cause() is intentional so callers can log it with "
        + "stack trace.")
public record CalDavError(CalDavErrors.Kind kind,
                          String detail,
                          Throwable cause) {

  public static CalDavError of(Throwable t) {
    return new CalDavError(
        CalDavErrors.classify(t),
        CalDavErrors.detail(t),
        t);
  }
}
