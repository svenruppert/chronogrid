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

package com.svenruppert.flow.views.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.VaadinSession;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Pill-shaped segmented control for switching the UI locale at runtime.
 *
 * <p>Each supported locale becomes a small button inside a rounded
 * container; the active locale is highlighted with the brand primary
 * variant, the others stay tertiary. Clicking a button calls
 * {@code UI.setLocale(...)} and reloads the page so every translated
 * label rerenders.
 *
 * <p>Add or remove locales by editing {@link #SUPPORTED}. Every entry
 * must have a matching {@code translations_<code>.properties} file in
 * {@code src/main/resources/vaadin-i18n/}; the default file
 * {@code translations.properties} ships the English ground-truth.
 */
public class LocaleSwitcher extends Div {

  private static final Logger LOG =
      Logger.getLogger(LocaleSwitcher.class.getName());

  /** Locales the switcher exposes. Keep in sync with the resource files. */
  public static final List<Locale> SUPPORTED =
      List.of(Locale.ENGLISH, Locale.GERMAN);

  public LocaleSwitcher() {
    addClassName("app-locale-switcher");
    rebuild();
  }

  private void rebuild() {
    removeAll();
    Locale current = currentLocale();
    for (Locale locale : SUPPORTED) {
      Button btn = new Button(locale.getLanguage().toUpperCase(Locale.ROOT));
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL);
      if (isSameLanguage(locale, current)) {
        btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
      } else {
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      }
      btn.getElement().setAttribute("aria-label",
          "Switch language to " + locale.getDisplayLanguage(Locale.ENGLISH));
      btn.getElement().setAttribute("title",
          locale.getDisplayLanguage(locale));
      btn.addClickListener(e -> switchTo(locale));
      add(btn);
    }
  }

  private static Locale currentLocale() {
    UI ui = UI.getCurrent();
    if (ui != null && ui.getLocale() != null) {
      return ui.getLocale();
    }
    VaadinSession session = VaadinSession.getCurrent();
    if (session != null && session.getLocale() != null) {
      return session.getLocale();
    }
    return Locale.ENGLISH;
  }

  private static boolean isSameLanguage(Locale a, Locale b) {
    return a != null && b != null
        && a.getLanguage().equals(b.getLanguage());
  }

  /**
   * Switches the session locale and forces a hard re-navigation so
   * every translatable component rerenders.
   *
   * <p>Diagnostics:
   * <ul>
   *   <li>Server-side log line proves the click handler reached the
   *       server — visible in the Jetty console as
   *       {@code LocaleSwitcher: switching to <code>}.</li>
   *   <li>Toast notification ("Sprache wechseln…") proves the JS-side
   *       round-trip reached the browser before the redirect.</li>
   * </ul>
   *
   * <p>Redirect uses the simplest possible JS — one assignment to
   * {@code window.location.href}. No nested URL parsing, no IIFE.
   * The reload picks up {@code ?lang=<code>} which the
   * {@link SessionPreferencesInitListener} reads to set the new UI's
   * locale before the first render.
   */
  private static void switchTo(Locale locale) {
    UI ui = UI.getCurrent();
    if (ui == null) {
      LOG.warning("LocaleSwitcher.switchTo: UI.getCurrent() is null");
      return;
    }
    LOG.info("LocaleSwitcher: switching to "
        + locale.toLanguageTag());

    VaadinSession session = ui.getSession();
    if (session != null) {
      session.setLocale(locale);
      session.setAttribute(SessionPreferencesInitListener.ATTR_LOCALE, locale);
    }
    ui.setLocale(locale);

    // Visible confirmation that the click reached the server.
    Notification n = Notification.show(
        "🌐 " + locale.getDisplayLanguage(locale),
        700, Notification.Position.TOP_END);
    n.addThemeVariants(NotificationVariant.LUMO_PRIMARY);

    // Simplest reload: ?lang=<code> on the current path. The browser
    // does a full GET to that URL — InitListener picks up the param
    // on the new request, applies the locale before any view renders.
    ui.getPage().executeJs(
        "window.location.href = window.location.pathname + '?lang=' + $0",
        locale.getLanguage());
  }
}
