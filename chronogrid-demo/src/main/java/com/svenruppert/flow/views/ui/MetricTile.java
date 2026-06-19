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

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Dashboard metric tile — icon, big number, label, optional helper text.
 *
 * <pre>
 *   MetricTile sessions = new MetricTile(
 *       VaadinIcon.USERS, "Active sessions", "42",
 *       "+6 in the last hour");
 * </pre>
 *
 * <p>Layout: {@code app-card} + {@code app-card-hover} +
 * {@code app-tile}. Stack three or four side by side in a
 * {@link com.vaadin.flow.component.orderedlayout.FlexLayout} for the
 * dashboard hero row.
 */
public class MetricTile extends Div {

  public MetricTile(VaadinIcon icon, String label, String value) {
    this(icon, label, value, null);
  }

  public MetricTile(VaadinIcon icon, String label, String value, String hint) {
    addClassName(TemplateBrand.CSS_CARD);
    addClassName("app-card-hover");
    addClassName("app-tile");

    HorizontalLayout iconRow = new HorizontalLayout();
    iconRow.setAlignItems(FlexComponent.Alignment.CENTER);
    iconRow.setSpacing(true);
    iconRow.setPadding(false);

    Div iconBox = new Div();
    iconBox.addClassName("app-tile-icon");
    Icon iconElement = icon.create();
    iconElement.getStyle().set("width", "20px");
    iconElement.getStyle().set("height", "20px");
    iconBox.add(iconElement);

    Span labelSpan = new Span(label);
    labelSpan.addClassName("app-tile-label");
    iconRow.add(iconBox, labelSpan);

    Div valueSpan = new Div();
    valueSpan.addClassName("app-tile-value");
    valueSpan.setText(value);

    add(iconRow, valueSpan);

    if (hint != null && !hint.isBlank()) {
      Span hintSpan = new Span(hint);
      hintSpan.addClassName(TemplateBrand.CSS_MUTED);
      add(hintSpan);
    }
  }
}
