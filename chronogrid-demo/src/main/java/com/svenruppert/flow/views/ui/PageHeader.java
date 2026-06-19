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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Consistent page heading — H1 title + optional subtitle on the left,
 * optional action slot on the right (typically a primary button or a
 * toolbar).
 *
 * <pre>
 *   PageHeader header = new PageHeader("Active sessions",
 *       "All sessions across every tenant.")
 *           .withActions(new Button("Invalidate all", e -> ...));
 * </pre>
 */
public class PageHeader extends HorizontalLayout {

  private final Div actions = new Div();

  public PageHeader(String title) {
    this(title, null);
  }

  public PageHeader(String title, String subtitle) {
    setWidthFull();
    setSpacing(true);
    setAlignItems(FlexComponent.Alignment.START);
    setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

    Div textBlock = new Div();
    textBlock.getStyle().set("display", "flex");
    textBlock.getStyle().set("flex-direction", "column");
    textBlock.getStyle().set("gap", "var(--lumo-space-xs)");

    H1 h1 = new H1(title);
    h1.getStyle().set("margin", "0");
    h1.getStyle().set("font-size", "1.75rem");
    h1.getStyle().set("font-weight", "700");
    h1.getStyle().set("letter-spacing", "-0.015em");
    textBlock.add(h1);

    if (subtitle != null && !subtitle.isBlank()) {
      Paragraph p = new Paragraph(subtitle);
      p.addClassName(TemplateBrand.CSS_MUTED);
      p.getStyle().set("margin", "0");
      textBlock.add(p);
    }

    add(textBlock, actions);
    expand(textBlock);
  }

  public PageHeader withActions(Component... components) {
    actions.removeAll();
    if (components != null && components.length > 0) {
      HorizontalLayout row = new HorizontalLayout(components);
      row.setSpacing(true);
      row.setAlignItems(FlexComponent.Alignment.CENTER);
      ((HasComponents) actions).add(row);
    }
    return this;
  }
}
