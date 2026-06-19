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

package com.svenruppert.flow.i18n;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.server.VaadinService;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Static i18n facade. Single entry point for translating strings in
 * non-Component contexts (factories, dialog bodies built before
 * being attached, utility classes). Returns the inline fallback when
 * Vaadin is not initialised, the key is unknown, or the lookup
 * crashes — never null, never the raw key.
 *
 * <p>From inside a view, prefer the {@link I18nSupport} mixin —
 * it routes through Vaadin's component-scoped {@code getTranslation},
 * which respects the per-UI locale.
 */
public final class I18n {

  private I18n() {
  }

  /**
   * Translates {@code key} using the current locale, falling back to
   * {@code fallback} (which may itself contain {@code {0}}…{@code {n}}
   * placeholders) when the lookup misses. Applies
   * {@link MessageFormat} to whichever string is returned, so the
   * placeholder syntax is consistent across hit and miss.
   *
   * @param key      dotted-namespace key, e.g. {@code "users.action.create"}
   * @param fallback ground-truth English text shown when the key is
   *                 unknown or the i18n provider is unavailable.
   *                 Must not be {@code null}.
   * @param params   optional positional arguments for MessageFormat
   */
  public static String tr(String key, String fallback, Object... params) {
    String pattern = lookup(key)
        .orElse(fallback != null ? fallback : key);
    if (params == null || params.length == 0) {
      return pattern;
    }
    return MessageFormat.format(pattern, params);
  }

  private static Optional<String> lookup(String key) {
    try {
      I18NProvider provider = VaadinService.getCurrent()
          .getInstantiator().getI18NProvider();
      String t = provider.getTranslation(key, currentLocale());
      // DefaultI18NProvider returns the key itself on miss — treat
      // that as "not translated" so the inline fallback wins.
      if (t == null || t.isBlank() || t.equals(key)) {
        return Optional.empty();
      }
      return Optional.of(t);
    } catch (Exception ignored) {
      // No VaadinService, no provider, or any other early-init
      // condition — fall through to the inline fallback.
      return Optional.empty();
    }
  }

  private static Locale currentLocale() {
    UI ui = UI.getCurrent();
    if (ui != null && ui.getLocale() != null) {
      return ui.getLocale();
    }
    var req = VaadinService.getCurrentRequest();
    if (req != null && req.getLocale() != null) {
      return req.getLocale();
    }
    return Locale.getDefault();
  }
}
