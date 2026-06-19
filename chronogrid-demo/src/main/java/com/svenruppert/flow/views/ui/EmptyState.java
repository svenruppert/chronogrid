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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Reusable empty-state surface. Wire it under any grid / list / view
 * that may legitimately show "nothing here" — anonymous dashboard,
 * empty audit log, empty session inventory.
 *
 * <pre>
 *   EmptyState e = new EmptyState(VaadinIcon.RECORDS,
 *       "No audit events yet",
 *       "Events appear here as soon as someone signs in or "
 *           + "an admin grants or revokes a role.");
 *   e.withAction(new Button("Refresh", ev -> grid.refresh()));
 * </pre>
 */
public class EmptyState extends Div {

  public EmptyState(VaadinIcon icon, String heading, String body) {
    addClassName("app-empty");
    add(icon.create());
    H3 h = new H3(heading);
    h.getStyle().set("margin", "0");
    h.getStyle().set("font-size", "1.125rem");
    add(h);
    if (body != null && !body.isBlank()) {
      Paragraph p = new Paragraph(body);
      p.addClassName(TemplateBrand.CSS_MUTED);
      p.getStyle().set("margin", "0");
      p.getStyle().set("max-width", "44ch");
      add(p);
    }
  }

  public EmptyState withAction(Component action) {
    add(action);
    return this;
  }
}
