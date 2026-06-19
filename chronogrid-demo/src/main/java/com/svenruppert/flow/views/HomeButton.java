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

package com.svenruppert.flow.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.Route;

import java.util.Optional;

/**
 * Conditionally renders a "Home" navigation button for a view.
 *
 * <p>A view registered inside {@link MainLayout} already has the
 * drawer / navbar pointing back to {@link PublicHomeView} — adding
 * another Home button would be redundant. A view registered as a
 * standalone {@code @Route} without a layout (i.e. {@code layout =
 * UI.class}, Vaadin's default) has no such fallback, so this helper
 * hands it one.
 *
 * <h2>Usage</h2>
 * <pre>
 *   HorizontalLayout toolbar = new HorizontalLayout();
 *   HomeButton.forStandalone(getClass()).ifPresent(toolbar::add);
 * </pre>
 *
 * <p>For views without a toolbar (e.g. subclasses of the framework's
 * {@code SessionManagementView}), drop the button into the root
 * layout instead:
 * <pre>
 *   HomeButton.forStandalone(getClass())
 *       .ifPresent(btn -&gt; getContent().addComponentAsFirst(btn));
 * </pre>
 */
public final class HomeButton {

  private HomeButton() {
  }

  /**
   * Returns a Home button when {@code viewClass} is registered as a
   * standalone {@code @Route} (no parent layout). Otherwise returns
   * {@link Optional#empty()}.
   */
  public static Optional<Button> forStandalone(Class<?> viewClass) {
    Route route = viewClass.getAnnotation(Route.class);
    if (route == null || !UI.class.equals(route.layout())) {
      return Optional.empty();
    }
    Button btn = new Button(
        com.svenruppert.flow.i18n.I18n.tr("ui.home.button", "Home"),
        VaadinIcon.HOME.create(),
        e -> UI.getCurrent().navigate(PublicHomeView.class));
    btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    return Optional.of(btn);
  }
}
