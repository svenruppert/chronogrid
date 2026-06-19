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
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.server.VaadinSession;

import java.util.List;

/**
 * Pill-shaped segmented control for switching the page-wide theme at
 * runtime. Three options:
 *
 * <ul>
 *   <li>{@code light} — Lumo defaults, brand violet.</li>
 *   <li>{@code dark}  — Lumo dark mode, brand violet stays bright.</li>
 *   <li>{@code jsentinel} — custom cyan/blue palette evoking the
 *       upstream jSentinel security project.</li>
 * </ul>
 *
 * <p>Unlike the locale, theme changes don't require a reload — the
 * tokens are pure CSS variables, so swapping the {@code theme}
 * attribute on {@code document.documentElement} is enough. The
 * choice is persisted in {@link VaadinSession} so it survives the
 * next page load (typically triggered by the locale switcher).
 */
public class ThemeSwitcher extends Div {

  public static final String LIGHT = "light";
  public static final String DARK = "dark";
  public static final String JSENTINEL = "jsentinel";

  /** Theme metadata — keyed by the attribute value, ordered. */
  private static final List<Choice> CHOICES = List.of(
      new Choice(LIGHT,    VaadinIcon.SUN_O,      "Light"),
      new Choice(DARK,     VaadinIcon.MOON_O,     "Dark"),
      new Choice(JSENTINEL, VaadinIcon.SHIELD,    "jSentinel"));

  public ThemeSwitcher() {
    addClassName("app-theme-switcher");
    rebuild();
  }

  private void rebuild() {
    removeAll();
    String current = currentTheme();
    for (Choice c : CHOICES) {
      Button btn = new Button(c.icon.create());
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL,
          ButtonVariant.LUMO_ICON);
      if (c.value.equals(current)) {
        btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
      } else {
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      }
      btn.getElement().setAttribute("aria-label", c.label + " theme");
      btn.getElement().setAttribute("title", c.label);
      btn.addClickListener(e -> apply(c.value));
      add(btn);
    }
  }

  /** The currently active theme — light when nothing was chosen yet. */
  public static String currentTheme() {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) {
      return LIGHT;
    }
    Object stored = session.getAttribute(
        SessionPreferencesInitListener.ATTR_THEME);
    if (stored instanceof String s && !s.isBlank()) {
      return s;
    }
    return LIGHT;
  }

  private void apply(String theme) {
    UI ui = UI.getCurrent();
    if (ui == null) {
      return;
    }
    VaadinSession session = ui.getSession();
    if (session != null) {
      session.setAttribute(
          SessionPreferencesInitListener.ATTR_THEME,
          LIGHT.equals(theme) ? null : theme);
    }
    // Light theme = no attribute; the CSS defaults kick in.
    if (LIGHT.equals(theme)) {
      ui.getPage().executeJs(
          "document.documentElement.removeAttribute('theme')");
    } else {
      ui.getPage().executeJs(
          "document.documentElement.setAttribute('theme', $0)", theme);
    }
    rebuild();
  }

  private record Choice(String value, VaadinIcon icon, String label) {
  }
}
