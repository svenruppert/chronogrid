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

import com.svenruppert.flow.i18n.AppI18NProvider;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Restores user-chosen UI preferences (locale, theme) on every
 * freshly-created {@link UI}. The pair {@code (locale, theme)} lives
 * in the {@link VaadinSession} as plain attributes — they survive a
 * page reload because the HTTP session does, but they reset on
 * logout / session invalidation.
 *
 * <p>This listener is the missing piece that makes the
 * {@code LocaleSwitcher} and {@code ThemeSwitcher} stick across the
 * page reload that those switchers trigger. Without it, the new UI
 * would fall back to the request's {@code Accept-Language} header
 * and Lumo's default light theme.
 *
 * <p>Registered via
 * {@code META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener}.
 */
public class SessionPreferencesInitListener implements VaadinServiceInitListener {

  private static final Logger LOG =
      Logger.getLogger(SessionPreferencesInitListener.class.getName());

  /** Session-attribute key for the user-chosen locale. */
  public static final String ATTR_LOCALE = "app.preferredLocale";

  /** Session-attribute key for the user-chosen theme. */
  public static final String ATTR_THEME = "app.preferredTheme";

  /** URL query parameter the LocaleSwitcher uses to force a locale. */
  public static final String PARAM_LANG = "lang";

  @Override
  public void serviceInit(ServiceInitEvent event) {
    LOG.info("[SessionPreferencesInitListener] registered");
    // Diagnostic: which I18NProvider did Vaadin choose at startup?
    // If we see DefaultI18NProvider here, our AppI18NProvider was NOT
    // picked up via META-INF/services and we need manual registration.
    try {
      I18NProvider chosen = event.getSource()
          .getInstantiator().getI18NProvider();
      LOG.info("[SessionPreferencesInitListener] Vaadin chose I18NProvider = "
          + (chosen == null ? "<null>" : chosen.getClass().getName()));
      if (chosen == null
          || !AppI18NProvider.class.isInstance(chosen)) {
        LOG.warning("[SessionPreferencesInitListener] Vaadin is NOT using "
            + "AppI18NProvider — falling back to DefaultI18NProvider's "
            + "JVM-fallback bug. The LocaleSwitcher will appear to do "
            + "nothing because the underlying bundle lookup still maps "
            + "Locale.ENGLISH → JVM-default → German on this machine.");
      }
    } catch (RuntimeException probe) {
      LOG.warning("[SessionPreferencesInitListener] could not introspect "
          + "I18NProvider at serviceInit time: " + probe);
    }

    event.getSource().addUIInitListener(uiInit -> {
      UI ui = uiInit.getUI();
      VaadinSession session = ui.getSession();
      if (session == null) {
        return;
      }
      applyLocale(ui, session);
      applyTheme(ui, session);
    });
  }

  /**
   * Locale-resolution order on every fresh UI:
   * <ol>
   *   <li>{@code ?lang=&lt;code&gt;} query parameter — set by
   *       {@link LocaleSwitcher} when the operator picks a language.
   *       Highest priority: survives session reset and is explicit.</li>
   *   <li>Session attribute {@link #ATTR_LOCALE} — falls in when the
   *       URL has no parameter (subsequent navigation in the session).</li>
   *   <li>Otherwise leave Vaadin's default (request Accept-Language).</li>
   * </ol>
   */
  private static void applyLocale(UI ui, VaadinSession session) {
    Locale fromUrl = readLocaleFromUrl();
    if (fromUrl != null) {
      LOG.info("applyLocale: from URL param → "
          + fromUrl.toLanguageTag());
      session.setLocale(fromUrl);
      session.setAttribute(ATTR_LOCALE, fromUrl);
      ui.setLocale(fromUrl);
      return;
    }
    Object stored = session.getAttribute(ATTR_LOCALE);
    if (stored instanceof Locale locale) {
      LOG.info("applyLocale: from session attr → "
          + locale.toLanguageTag());
      session.setLocale(locale);
      ui.setLocale(locale);
    } else {
      LOG.fine("applyLocale: no override — UI locale stays at "
          + ui.getLocale());
    }
  }

  private static Locale readLocaleFromUrl() {
    VaadinRequest request = VaadinRequest.getCurrent();
    if (request == null) {
      return null;
    }
    String lang = request.getParameter(PARAM_LANG);
    if (lang == null || lang.isBlank()) {
      return null;
    }
    try {
      return Locale.forLanguageTag(lang);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void applyTheme(UI ui, VaadinSession session) {
    Object stored = session.getAttribute(ATTR_THEME);
    if (stored instanceof String theme && !theme.isBlank()) {
      // Re-apply the theme attribute on the document root as soon as
      // the page is up. Light is the default; nothing to do.
      ui.getPage().executeJs(
          "document.documentElement.setAttribute('theme', $0)", theme);
    }
  }

  /** Test seam — lets test code resolve the current service quickly. */
  static VaadinService currentService() {
    return VaadinService.getCurrent();
  }
}
