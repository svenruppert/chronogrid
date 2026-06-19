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

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.HasValueAndElement;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;

import java.util.ArrayList;
import java.util.List;

/**
 * Column-aligned filter panel for entity grids.
 *
 * <p>Each filter is a labeled control that maps 1:1 to a grid column.
 * Multiple filters combine with AND semantics — the caller asks each
 * filter for its current value and refilters the grid on every change.
 *
 * <pre>
 *   TextField subject = bar.addText("Subject", "Search by subject…");
 *   MultiSelectComboBox&lt;String&gt; types =
 *       bar.addMultiSelect("Type", KNOWN_TYPES, "Any type");
 *   DatePicker since = bar.addDate("Since");
 *
 *   subject.addValueChangeListener(e -&gt; refresh());
 *   types.addValueChangeListener(e -&gt; refresh());
 *   since.addValueChangeListener(e -&gt; refresh());
 *
 *   bar.onClear(() -&gt; refresh());
 *   bar.setCount(items.size(), "users");
 * </pre>
 *
 * <p>The visual surface (brand-50 card with soft border and rounded
 * corners) makes the filter strip visually distinct from the grid
 * below and consistent across every admin view.
 */
public class FilterBar extends Div {

  private final Div filtersWrap = new Div();
  private final Button clear = new Button(
      com.svenruppert.flow.i18n.I18n.tr("ui.filter.clear", "Clear filters"));
  private final Span count = new Span();
  private final List<HasValue<?, ?>> registeredFilters = new ArrayList<>();
  private Runnable onClear = noop();

  public FilterBar() {
    // Always span the full width of the surrounding layout so the
    // filter strip lines up edge-to-edge with the grid below it.
    setWidthFull();
    getStyle().set("display", "flex");
    getStyle().set("flex-direction", "column");
    getStyle().set("box-sizing", "border-box");
    getStyle().set("gap", "var(--lumo-space-s)");
    getStyle().set("padding", "var(--lumo-space-m)");
    getStyle().set("background", "var(--app-brand-50)");
    getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
    getStyle().set("border-radius", "var(--app-radius-md)");

    filtersWrap.getStyle().set("display", "flex");
    filtersWrap.getStyle().set("flex-wrap", "wrap");
    filtersWrap.getStyle().set("gap",
        "var(--lumo-space-m) var(--lumo-space-l)");
    filtersWrap.getStyle().set("align-items", "flex-end");

    clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY,
        ButtonVariant.LUMO_SMALL);
    clear.setIcon(VaadinIcon.CLOSE_SMALL.create());
    clear.addClickListener(e -> {
      for (HasValue<?, ?> f : registeredFilters) {
        f.clear();
      }
      onClear.run();
    });

    count.getElement().getThemeList().add("badge contrast pill");
    count.setText("0");

    Div footer = new Div(clear, count);
    footer.getStyle().set("display", "flex");
    footer.getStyle().set("align-items", "center");
    footer.getStyle().set("justify-content", "space-between");
    footer.getStyle().set("padding-top", "var(--lumo-space-xs)");
    footer.getStyle().set("border-top",
        "1px dashed var(--lumo-contrast-10pct)");

    add(filtersWrap, footer);
  }

  // ── Filter builders — one per common control type ──────────────

  /** Add a labeled text-search filter. Lazy-debounced, with clear button. */
  public TextField addText(String label, String placeholder) {
    TextField field = new TextField();
    field.setPlaceholder(placeholder);
    field.setClearButtonVisible(true);
    field.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
    field.setValueChangeTimeout(200);
    field.addThemeVariants(TextFieldVariant.LUMO_SMALL);
    field.setWidth("220px");
    field.setPrefixComponent(VaadinIcon.SEARCH.create());
    registerLabeled(label, field);
    return field;
  }

  /** Add a labeled single-select dropdown over an enum or list. */
  public <T> ComboBox<T> addSingleSelect(String label, T[] items, String placeholder) {
    ComboBox<T> box = new ComboBox<>();
    box.setItems(items);
    box.setPlaceholder(placeholder);
    box.setClearButtonVisible(true);
    box.setWidth("200px");
    box.addThemeName("small");
    registerLabeled(label, box);
    return box;
  }

  /** Add a labeled multi-select that ANDs an "in-set" filter on a column. */
  public <T> MultiSelectComboBox<T> addMultiSelect(String label, List<T> items,
                                                   String placeholder) {
    MultiSelectComboBox<T> box = new MultiSelectComboBox<>();
    box.setItems(items);
    box.setPlaceholder(placeholder);
    box.setWidth("260px");
    box.addThemeName("small");
    registerLabeled(label, box);
    return box;
  }

  /** Add a labeled date filter — typically used as ≥ (since) or ≤ (until). */
  public DatePicker addDate(String label, String placeholder) {
    DatePicker date = new DatePicker();
    date.setPlaceholder(placeholder);
    date.setClearButtonVisible(true);
    date.setWidth("180px");
    date.addThemeName("small");
    registerLabeled(label, date);
    return date;
  }

  /** Callback invoked AFTER all filters were cleared by "Clear filters". */
  public FilterBar onClear(Runnable handler) {
    this.onClear = handler == null ? noop() : handler;
    return this;
  }

  /** Update the right-hand result-count badge — e.g. {@code (42, "users")}. */
  public void setCount(int n, String unit) {
    count.setText(n + " " + unit);
  }

  // ── Internals ──────────────────────────────────────────────────

  /**
   * Wraps a Vaadin form control in a labeled "slot" — small uppercase
   * caption above the control. Keeps every filter on the bar visually
   * consistent regardless of whether it's a TextField, ComboBox, etc.
   */
  private void registerLabeled(String label, Component component) {
    Span caption = new Span(label);
    caption.getStyle().set("font-size", "var(--lumo-font-size-xs)");
    caption.getStyle().set("font-weight", "600");
    caption.getStyle().set("text-transform", "uppercase");
    caption.getStyle().set("letter-spacing", "0.04em");
    caption.getStyle().set("color", "var(--lumo-secondary-text-color)");

    Div slot = new Div(caption, component);
    slot.getStyle().set("display", "flex");
    slot.getStyle().set("flex-direction", "column");
    slot.getStyle().set("gap", "var(--lumo-space-xs)");

    if (component instanceof HasSize hs) {
      // already sized inline above
    }
    if (component instanceof HasValueAndElement<?, ?> hv) {
      registeredFilters.add(hv);
    } else if (component instanceof AbstractField<?, ?> af) {
      registeredFilters.add(af);
    }
    filtersWrap.add(slot);
  }

  private static Runnable noop() {
    return () -> { };
  }
}
