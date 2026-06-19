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

package com.svenruppert.chronogrid.i18n;

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

import java.io.Serializable;
import java.text.MessageFormat;

/**
 * Thin localisation seam for the calendar component family. Lets the
 * view + sub-components emit user-facing text without binding to a
 * specific host i18n stack — a host can pass in an adapter that
 * routes lookups to its own {@code ResourceBundle}, JSON catalog,
 * remote translation service, or whatever else; tests can plug
 * {@link #fallbackOnly()} and just use the English fallbacks
 * baked into call sites.
 *
 * <p>Single-method functional interface so call sites can pass a
 * method reference (e.g. {@code I18nSupport::tr}) directly.
 */
@FunctionalInterface
public interface CalendarMessages extends Serializable {

  /**
   * Look up a localised string for the given key, substituting any
   * positional arguments. Implementations must accept (and ideally
   * use) the fallback when the key is not in the underlying catalog.
   *
   * @param key      i18n key (e.g. {@code "calendar.heading"})
   * @param fallback English text to use when the key is missing
   * @param args     positional arguments, MessageFormat style
   * @return the resolved text — never {@code null}
   */
  String tr(String key, String fallback, Object... args);

  /**
   * No-i18n implementation: ignores the key, always uses the fallback,
   * and runs {@link MessageFormat#format(String, Object...)} only when
   * there are positional arguments. Convenient for unit tests that
   * don't want to spin up a real i18n provider.
   */
  static CalendarMessages fallbackOnly() {
    return (key, fallback, args) -> args == null || args.length == 0
        ? fallback
        : MessageFormat.format(fallback, args);
  }
}
