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

import com.vaadin.flow.component.Component;

import java.text.MessageFormat;

/**
 * Mixin for any class extending {@link Component}. Adds the same
 * key + fallback convention as
 * {@link I18n#tr(String, String, Object...)} but routes through
 * Vaadin's component-scoped {@code getTranslation}, which respects
 * the component's locale (set per-UI).
 *
 * <p>Implement on every view that has more than ~3 translated
 * strings. Declare the keys as {@code private static final String K_*}
 * constants at the top of the class so a typo is a compile error,
 * not a silent miss.
 */
public interface I18nSupport {

  /** Translate without a fallback — only safe when the key is known to exist. */
  default String tr(String key) {
    return ((Component) this).getTranslation(key);
  }

  /** Translate with an inline ground-truth fallback. */
  default String tr(String key, String fallback) {
    String translated = ((Component) this).getTranslation(key);
    if (translated == null || translated.isBlank() || translated.equals(key)) {
      return fallback;
    }
    return translated;
  }

  /** Translate with placeholders {@code {0}}…{@code {n}}. */
  default String tr(String key, String fallback, Object... params) {
    String translated = ((Component) this).getTranslation(key, params);
    if (translated == null || translated.isBlank() || translated.equals(key)) {
      return MessageFormat.format(fallback, params);
    }
    return translated;
  }
}
