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

/**
 * Wordmark + icon — the application's primary visual signature.
 *
 * <p>Used inside {@code MainLayout}'s navbar and on the public hero.
 * The icon and label come from {@link TemplateBrand}; rebrand by
 * editing that file, not this component.
 */
public class BrandMark extends Div {

  public BrandMark() {
    addClassName("app-brand-mark");
    add(TemplateBrand.ICON.create());
    Span wordmark = new Span(TemplateBrand.NAME);
    wordmark.addClassName(TemplateBrand.CSS_BRAND_TEXT);
    add(wordmark);
  }
}
