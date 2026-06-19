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
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Feature card — icon-on-top + heading + body copy. Used on the
 * public landing hero to surface what the template ships with
 * (Security / Audit / Mutation-Hardened).
 *
 * <p>Surface = {@code app-card} + {@code app-card-hover}; the icon
 * inherits the brand-primary color from the theme.
 */
public class FeatureCard extends Div {

  public FeatureCard(VaadinIcon icon, String heading, String body) {
    addClassName(TemplateBrand.CSS_CARD);
    addClassName("app-card-hover");
    getStyle().set("display", "flex");
    getStyle().set("flex-direction", "column");
    getStyle().set("gap", "var(--lumo-space-s)");
    getStyle().set("flex", "1 1 240px");
    getStyle().set("min-width", "240px");
    getStyle().set("max-width", "360px");

    Div iconBox = new Div();
    iconBox.addClassName("app-tile-icon");
    Icon icn = icon.create();
    icn.getStyle().set("width", "20px");
    icn.getStyle().set("height", "20px");
    iconBox.add(icn);

    H3 h = new H3(heading);
    h.getStyle().set("margin", "0");
    h.getStyle().set("font-size", "1.125rem");
    h.getStyle().set("font-weight", "600");
    h.getStyle().set("letter-spacing", "-0.01em");

    Paragraph p = new Paragraph(body);
    p.addClassName(TemplateBrand.CSS_MUTED);
    p.getStyle().set("margin", "0");

    add(iconBox, h, p);
  }
}
