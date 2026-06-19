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

import com.vaadin.flow.i18n.I18NProvider;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Custom I18NProvider that fixes the JVM-default-locale fallback
 * trap built into {@link com.vaadin.flow.i18n.DefaultI18NProvider}.
 *
 * <p>Loud, deliberately verbose logging — when the LocaleSwitcher
 * "doesn't seem to work", the question is almost always one of:
 * <ol>
 *   <li>Is Vaadin even loading this class? (static init log)</li>
 *   <li>Is Vaadin constructing it? (constructor log)</li>
 *   <li>Is it being called for getTranslation? (per-call log)</li>
 *   <li>Is it finding the right bundle? (per-call bundle log)</li>
 * </ol>
 * Each of those answers shows up in the Jetty console.
 *
 * <p><strong>The trap we're fixing:</strong> when no translation
 * file matches the requested locale, Java's
 * {@code ResourceBundle.getBundle} falls back to the JVM's default
 * locale before finally landing on the unsuffixed bundle. On a
 * German developer's machine, asking for English →
 * {@code translations_en.properties} absent → fallback to JVM
 * default (German) → {@code translations_de.properties} found →
 * German content returned for an English request.
 *
 * <p><strong>The fix:</strong> use
 * {@link ResourceBundle.Control#getNoFallbackControl}, which strips
 * the JVM-default-locale candidate from the fallback chain. The
 * effective lookup order becomes:
 * <ol>
 *   <li>{@code translations_<lang>_<COUNTRY>.properties}</li>
 *   <li>{@code translations_<lang>.properties}</li>
 *   <li>{@code translations.properties} (ROOT / default)</li>
 * </ol>
 *
 * <p>Registered via
 * {@code META-INF/services/com.vaadin.flow.i18n.I18NProvider}.
 */
public class AppI18NProvider implements I18NProvider {

  private static final Logger LOG =
      Logger.getLogger(AppI18NProvider.class.getName());

  static {
    LOG.info("[AppI18NProvider] class loaded by "
        + AppI18NProvider.class.getClassLoader()
        + "  ← if you see this once at JVM start, the ServiceLoader "
        + "discovery worked.");
  }

  /** Bundle name — dots get translated to slashes on classpath lookup. */
  private static final String BUNDLE = "vaadin-i18n.translations";

  /** Locales the application advertises as supported. */
  private static final List<Locale> SUPPORTED =
      List.of(Locale.ENGLISH, Locale.GERMAN);

  private final AtomicLong invocationCount = new AtomicLong();
  private final long instanceId;
  private static final AtomicLong INSTANCE_COUNTER = new AtomicLong();

  public AppI18NProvider() {
    instanceId = INSTANCE_COUNTER.incrementAndGet();
    LOG.info("[AppI18NProvider#" + instanceId + "] CONSTRUCTED — "
        + "Vaadin discovered the provider via META-INF/services and is "
        + "instantiating it. If you see this AT LEAST ONCE per JVM start, "
        + "the registration works. If you NEVER see this line, then "
        + "META-INF/services/com.vaadin.flow.i18n.I18NProvider is not "
        + "being picked up — Vaadin is still using DefaultI18NProvider.");
  }

  @Override
  public List<Locale> getProvidedLocales() {
    LOG.fine("[AppI18NProvider#" + instanceId
        + "] getProvidedLocales() → " + SUPPORTED);
    return SUPPORTED;
  }

  @Override
  public String getTranslation(String key, Locale locale, Object... params) {
    long n = invocationCount.incrementAndGet();
    if (key == null) {
      LOG.warning("[AppI18NProvider#" + instanceId + " call#" + n
          + "] getTranslation(null, " + locale + ") — key is NULL");
      return "";
    }
    Locale lookup = locale == null ? Locale.ENGLISH : locale;
    try {
      ResourceBundle bundle = ResourceBundle.getBundle(
          BUNDLE, lookup,
          Thread.currentThread().getContextClassLoader(),
          ResourceBundle.Control.getNoFallbackControl(
              ResourceBundle.Control.FORMAT_PROPERTIES));

      if (!bundle.containsKey(key)) {
        // Throttled: don't spam on every missing key — they're often legitimate
        // (Vaadin internally asks for keys like 'vaadin.app.routeNotFound').
        if (n <= 5 || n % 50 == 0) {
          LOG.fine("[AppI18NProvider#" + instanceId + " call#" + n
              + "] MISS  key='" + key + "' locale="
              + lookup.toLanguageTag() + " bundleBaseName="
              + bundle.getBaseBundleName() + " bundleLocale="
              + bundle.getLocale());
        }
        return key;
      }
      String value = bundle.getString(key);
      // Sample every 25th hit + the first 3, to confirm the right
      // bundle is being read without drowning the console.
      if (n <= 3 || n % 25 == 0) {
        LOG.info("[AppI18NProvider#" + instanceId + " call#" + n
            + "] HIT   key='" + key + "' locale="
            + lookup.toLanguageTag() + " bundleLocale="
            + bundle.getLocale() + " value='" + value + "'");
      }
      if (params == null || params.length == 0) {
        return value;
      }
      return MessageFormat.format(value, params);
    } catch (MissingResourceException e) {
      LOG.warning("[AppI18NProvider#" + instanceId + " call#" + n
          + "] NO BUNDLE key='" + key + "' locale="
          + lookup.toLanguageTag() + " — neither translations_"
          + lookup.getLanguage() + ".properties NOR translations.properties "
          + "is on the classpath. Check src/main/resources/vaadin-i18n/");
      return key;
    }
  }
}
