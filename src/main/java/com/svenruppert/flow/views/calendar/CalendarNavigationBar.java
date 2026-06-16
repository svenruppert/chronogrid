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

package com.svenruppert.flow.views.calendar;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.i18n.I18nSupport;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.dom.Element;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Single, coherent navigation strip for the {@code CalendarView}.
 * One nav group on the left, view switcher on the right.
 *
 * <p><strong>Left — navigation:</strong> a five-button segmented
 * group with explicit semantics:
 * <ul>
 *   <li>{@code ⏮ Page back} — FullCalendar's native paging (one
 *       day in Day, one week in Week, one month in Month, N days
 *       in the N-days view).</li>
 *   <li>{@code ◀ Slide back} — always shifts the visible interval
 *       by one day, regardless of view mode (the rolling motion).</li>
 *   <li>{@code Today} — jumps to the current date.</li>
 *   <li>{@code ▶ Slide forward} — symmetric.</li>
 *   <li>{@code ⏭ Page forward} — symmetric.</li>
 * </ul>
 * The DatePicker beside the group jumps to any arbitrary date;
 * its built-in <em>Today</em> popover button is suppressed via the
 * {@code DatePickerI18n.setToday("")} trick so there is exactly one
 * "Today" affordance in the bar.
 *
 * <p><strong>Right — view + N control:</strong> Vaadin
 * {@link Tabs} for Day / Week / N&nbsp;days / Month, plus — in
 * {@code N_DAYS} mode — a single HTML5 range slider (1–21) with a
 * live "N&nbsp;= 7 days" label. The {@code IntegerField} has been
 * removed; the slider is the only N input now.
 */
public final class CalendarNavigationBar
    extends Composite<Div> implements I18nSupport, HasLogger {

  public enum ViewMode { DAY, WEEK, N_DAYS, MONTH }

  public static final int MIN_N_DAYS = 1;
  public static final int MAX_N_DAYS = 21;

  private static final String K_PAGE_PREV = "calendar.nav.page.prev";
  private static final String K_PAGE_NEXT = "calendar.nav.page.next";
  private static final String K_SLIDE_PREV = "calendar.nav.slide.prev";
  private static final String K_SLIDE_NEXT = "calendar.nav.slide.next";
  private static final String K_TODAY = "calendar.nav.today";
  private static final String K_DATE = "calendar.nav.date";
  private static final String K_VIEW_DAY = "calendar.nav.view.day";
  private static final String K_VIEW_WEEK = "calendar.nav.view.week";
  private static final String K_VIEW_NDAYS = "calendar.nav.view.nDays";
  private static final String K_VIEW_MONTH = "calendar.nav.view.month";
  private static final String K_NDAYS_VALUE = "calendar.nav.nDays.value";

  private final DatePicker datePicker;
  private final Tabs viewTabs;
  private final Element nDaysSlider;
  private final Span nDaysValueLabel;
  private final HorizontalLayout nGroup;
  private final Div intervalLabel;
  private final Map<ViewMode, Tab> tabsByMode = new EnumMap<>(ViewMode.class);

  /** Encapsulates the five navigation callbacks. */
  public record NavigationCallbacks(Runnable pageBack,
                                    Runnable slideBack,
                                    Runnable today,
                                    Runnable slideForward,
                                    Runnable pageForward) { }

  public CalendarNavigationBar(LocalDate initialDate,
                               ViewMode initialView,
                               int initialNDays,
                               NavigationCallbacks nav,
                               Consumer<LocalDate> onDateChanged,
                               Consumer<ViewMode> onViewChanged,
                               IntConsumer onNDaysChanged) {
    Div root = getContent();
    styleRoot(root);

    // ── left: 5-button nav group + date picker ───────────────────
    Button pagePrev = iconButton(VaadinIcon.STEP_BACKWARD,
        tr(K_PAGE_PREV, "Previous interval"), nav.pageBack);
    Button slidePrev = iconButton(VaadinIcon.ANGLE_LEFT,
        tr(K_SLIDE_PREV, "Slide back one day"), nav.slideBack);
    Button today = new Button(tr(K_TODAY, "Today"), e -> nav.today.run());
    today.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    Button slideNext = iconButton(VaadinIcon.ANGLE_RIGHT,
        tr(K_SLIDE_NEXT, "Slide forward one day"), nav.slideForward);
    Button pageNext = iconButton(VaadinIcon.STEP_FORWARD,
        tr(K_PAGE_NEXT, "Next interval"), nav.pageForward);

    Div navGroup = new Div(pagePrev, slidePrev, today, slideNext, pageNext);
    navGroup.setId("calendar-nav-group");
    navGroup.addClassName("calendar-nav__group");

    datePicker = new DatePicker(tr(K_DATE, "Go to date"));
    datePicker.setValue(initialDate);
    datePicker.setWeekNumbersVisible(true);
    datePicker.setWidth("190px");
    // Suppress the DatePicker popover's built-in "Today" button so
    // the only Today affordance is the one in the nav group.
    DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
    i18n.setToday("");
    datePicker.setI18n(i18n);
    datePicker.addValueChangeListener(e -> {
      if (e.getValue() != null) {
        logger().info("Nav: date jump -> {}", e.getValue());
        onDateChanged.accept(e.getValue());
      }
    });

    HorizontalLayout left = new HorizontalLayout(navGroup, datePicker);
    left.setAlignItems(FlexComponent.Alignment.END);
    left.setSpacing(true);

    // ── center: interval label ───────────────────────────────────
    intervalLabel = new Div();
    intervalLabel.setText("");
    intervalLabel.addClassName("calendar-nav__interval");

    // ── right: view tabs + (N-days only) slider ──────────────────
    Tab dayTab = new Tab(tr(K_VIEW_DAY, "Day"));
    Tab weekTab = new Tab(tr(K_VIEW_WEEK, "Week"));
    Tab nTab = new Tab(tr(K_VIEW_NDAYS, "N days"));
    Tab monthTab = new Tab(tr(K_VIEW_MONTH, "Month"));
    dayTab.setId("calendar-nav-tab-day");
    weekTab.setId("calendar-nav-tab-week");
    nTab.setId("calendar-nav-tab-ndays");
    monthTab.setId("calendar-nav-tab-month");
    tabsByMode.put(ViewMode.DAY, dayTab);
    tabsByMode.put(ViewMode.WEEK, weekTab);
    tabsByMode.put(ViewMode.N_DAYS, nTab);
    tabsByMode.put(ViewMode.MONTH, monthTab);

    viewTabs = new Tabs(dayTab, weekTab, nTab, monthTab);
    viewTabs.setSelectedTab(tabsByMode.get(initialView));
    viewTabs.addClassName("calendar-nav__tabs");

    // Slider is the ONLY input for N now.
    int initial = clamp(initialNDays);
    nDaysValueLabel = new Span(tr(K_NDAYS_VALUE, "N = {0} days",
        String.valueOf(initial)));
    nDaysValueLabel.addClassName("calendar-nav__ndays-value");

    nDaysSlider = new Element("input");
    nDaysSlider.setAttribute("type", "range");
    nDaysSlider.setAttribute("min", String.valueOf(MIN_N_DAYS));
    nDaysSlider.setAttribute("max", String.valueOf(MAX_N_DAYS));
    nDaysSlider.setAttribute("step", "1");
    nDaysSlider.setProperty("value", String.valueOf(initial));
    nDaysSlider.getClassList().add("calendar-nav__ndays-slider");
    nDaysSlider.addEventListener("input", ev -> {
      tools.jackson.databind.JsonNode node =
          ev.getEventData().get("event.target.value");
      if (node == null) return;
      String v = node.asString();
      if (!v.isBlank()) {
        int next = clamp(Integer.parseInt(v));
        nDaysValueLabel.setText(tr(K_NDAYS_VALUE, "N = {0} days",
            String.valueOf(next)));
        onNDaysChanged.accept(next);
      }
    }).addEventData("event.target.value");

    Div sliderHost = new Div();
    sliderHost.addClassName("calendar-nav__slider-host");
    sliderHost.getElement().appendChild(nDaysSlider);

    nGroup = new HorizontalLayout(nDaysValueLabel, sliderHost);
    nGroup.setAlignItems(FlexComponent.Alignment.CENTER);
    nGroup.setSpacing(true);
    nGroup.setVisible(initialView == ViewMode.N_DAYS);

    viewTabs.addSelectedChangeListener(e -> {
      ViewMode mode = modeFor(e.getSelectedTab());
      nGroup.setVisible(mode == ViewMode.N_DAYS);
      logger().info("Nav: view mode -> {}", mode);
      onViewChanged.accept(mode);
    });

    HorizontalLayout right = new HorizontalLayout(viewTabs, nGroup);
    right.setAlignItems(FlexComponent.Alignment.END);
    right.setSpacing(true);

    // ── assemble ─────────────────────────────────────────────────
    HorizontalLayout bar = new HorizontalLayout(left, intervalLabel, right);
    bar.setWidthFull();
    bar.setAlignItems(FlexComponent.Alignment.END);
    bar.setSpacing(true);
    bar.addClassName("calendar-nav__bar");
    root.add(bar);
  }

  private static int clamp(int v) {
    return Math.max(MIN_N_DAYS, Math.min(MAX_N_DAYS, v));
  }

  private ViewMode modeFor(Tab tab) {
    for (Map.Entry<ViewMode, Tab> e : tabsByMode.entrySet()) {
      if (e.getValue() == tab) return e.getKey();
    }
    return ViewMode.MONTH;
  }

  private static Button iconButton(VaadinIcon icon, String tooltip, Runnable onClick) {
    Button btn = new Button(icon.create(), e -> onClick.run());
    btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON,
        ButtonVariant.LUMO_SMALL);
    btn.getElement().setProperty("title", tooltip);
    return btn;
  }

  private static void styleRoot(Div root) {
    root.setWidthFull();
    // Subtle treatment so the bar reads as a section header inside
    // CalendarView's frame, not a second nested card — see
    // `.calendar-nav` in styles/calendar-view.css.
    root.addClassName("calendar-nav");
  }

  // ── public API ─────────────────────────────────────────────────

  public void setCurrentDate(LocalDate date) {
    datePicker.setValue(date);
  }

  public void setIntervalLabel(String text) {
    intervalLabel.setText(text);
  }

  public ViewMode currentMode() {
    return modeFor(viewTabs.getSelectedTab());
  }
}