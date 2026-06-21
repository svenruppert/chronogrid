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

package com.svenruppert.chronogrid.ui;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.chronogrid.i18n.CalendarMessages;
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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * Single, coherent navigation strip for the {@code ChronoGrid}.
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
    extends Composite<Div> implements HasLogger {

  private final CalendarMessages messages;

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
  private static final String K_DAY_HINT_NONE = "calendar.nav.dayHint.none";
  private static final String K_DAY_HINT_ONE = "calendar.nav.dayHint.one";
  private static final String K_DAY_HINT_MANY = "calendar.nav.dayHint.many";

  private final DatePicker datePicker;
  private final Tabs viewTabs;
  private final Element nDaysSlider;
  private final Span nDaysValueLabel;
  private final HorizontalLayout nGroup;
  private final Div intervalLabel;
  private final Div dayHintBadge;
  private final Map<ViewMode, Tab> tabsByMode = new EnumMap<>(ViewMode.class);

  /**
   * Resolves the set of calendar colours (CSS-coloured strings — hex,
   * named, anything the consumer's calendars use) for a given day so
   * the popover-adjacent indicator can paint dots in the originating
   * calendars' colours. The function may return an empty set for days
   * without entries; {@code null} is treated as empty.
   */
  private Function<LocalDate, Set<String>> dayColoursProvider =
      d -> java.util.Collections.emptySet();

  /** Encapsulates the five navigation callbacks. */
  public record NavigationCallbacks(Runnable pageBack,
                                    Runnable slideBack,
                                    Runnable today,
                                    Runnable slideForward,
                                    Runnable pageForward) { }

  /** @deprecated kept for tests — prefer the {@link CalendarMessages}-taking variant. */
  @Deprecated
  public CalendarNavigationBar(LocalDate initialDate,
                               ViewMode initialView,
                               int initialNDays,
                               NavigationCallbacks nav,
                               Consumer<LocalDate> onDateChanged,
                               Consumer<ViewMode> onViewChanged,
                               IntConsumer onNDaysChanged) {
    this(CalendarMessages.fallbackOnly(), initialDate, initialView, initialNDays,
        nav, onDateChanged, onViewChanged, onNDaysChanged);
  }

  public CalendarNavigationBar(CalendarMessages messages,
                               LocalDate initialDate,
                               ViewMode initialView,
                               int initialNDays,
                               NavigationCallbacks nav,
                               Consumer<LocalDate> onDateChanged,
                               Consumer<ViewMode> onViewChanged,
                               IntConsumer onNDaysChanged) {
    this.messages = messages;
    Div root = getContent();
    styleRoot(root);

    // ── left: 5-button nav group + date picker ───────────────────
    Button pagePrev = iconButton(VaadinIcon.STEP_BACKWARD,
        messages.tr(K_PAGE_PREV, "Previous interval"), nav.pageBack);
    Button slidePrev = iconButton(VaadinIcon.ANGLE_LEFT,
        messages.tr(K_SLIDE_PREV, "Slide back one day"), nav.slideBack);
    Button today = new Button(messages.tr(K_TODAY, "Today"), e -> nav.today.run());
    today.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    Button slideNext = iconButton(VaadinIcon.ANGLE_RIGHT,
        messages.tr(K_SLIDE_NEXT, "Slide forward one day"), nav.slideForward);
    Button pageNext = iconButton(VaadinIcon.STEP_FORWARD,
        messages.tr(K_PAGE_NEXT, "Next interval"), nav.pageForward);

    Div navGroup = new Div(pagePrev, slidePrev, today, slideNext, pageNext);
    navGroup.setId("chronogrid-nav-group");
    navGroup.addClassName("chronogrid-nav__group");

    datePicker = new DatePicker(messages.tr(K_DATE, "Go to date"));
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
        refreshDayHint(e.getValue());
      }
    });
    // Refresh the day-hint badge whenever the popover opens so the
    // user sees an up-to-date indicator for the currently-focused
    // date without having to commit a pick first.
    datePicker.addOpenedChangeListener(e -> {
      if (e.isOpened()) {
        refreshDayHint(datePicker.getValue());
      }
    });

    // ── Feature #1: appointment indicator next to the DatePicker ──
    // FullCalendar's popover does not expose a stable day-renderer
    // hook in Vaadin 25, so the per-day dots that the original
    // sketch envisaged inside the popover cannot ship freeze-safe.
    // Instead, an adjacent badge updates on date-pick + popover-open
    // and shows the count of events on the focused day plus a
    // coloured dot for each contributing calendar — same information
    // signal, reachable via stable Vaadin API.
    dayHintBadge = new Div();
    dayHintBadge.addClassName("chronogrid-nav__day-hint");
    refreshDayHint(initialDate);

    HorizontalLayout left = new HorizontalLayout(navGroup, datePicker, dayHintBadge);
    left.setAlignItems(FlexComponent.Alignment.END);
    left.setSpacing(true);

    // ── center: interval label ───────────────────────────────────
    intervalLabel = new Div();
    intervalLabel.setText("");
    intervalLabel.addClassName("chronogrid-nav__interval");

    // ── right: view tabs + (N-days only) slider ──────────────────
    Tab dayTab = new Tab(messages.tr(K_VIEW_DAY, "Day"));
    Tab weekTab = new Tab(messages.tr(K_VIEW_WEEK, "Week"));
    Tab nTab = new Tab(messages.tr(K_VIEW_NDAYS, "N days"));
    Tab monthTab = new Tab(messages.tr(K_VIEW_MONTH, "Month"));
    dayTab.setId("chronogrid-nav-tab-day");
    weekTab.setId("chronogrid-nav-tab-week");
    nTab.setId("chronogrid-nav-tab-ndays");
    monthTab.setId("chronogrid-nav-tab-month");
    tabsByMode.put(ViewMode.DAY, dayTab);
    tabsByMode.put(ViewMode.WEEK, weekTab);
    tabsByMode.put(ViewMode.N_DAYS, nTab);
    tabsByMode.put(ViewMode.MONTH, monthTab);

    viewTabs = new Tabs(dayTab, weekTab, nTab, monthTab);
    viewTabs.setSelectedTab(tabsByMode.get(initialView));
    viewTabs.addClassName("chronogrid-nav__tabs");

    // Slider is the ONLY input for N now.
    int initial = clamp(initialNDays);
    nDaysValueLabel = new Span(messages.tr(K_NDAYS_VALUE, "N = {0} days",
        String.valueOf(initial)));
    nDaysValueLabel.addClassName("chronogrid-nav__ndays-value");

    nDaysSlider = new Element("input");
    nDaysSlider.setAttribute("type", "range");
    nDaysSlider.setAttribute("min", String.valueOf(MIN_N_DAYS));
    nDaysSlider.setAttribute("max", String.valueOf(MAX_N_DAYS));
    nDaysSlider.setAttribute("step", "1");
    nDaysSlider.setProperty("value", String.valueOf(initial));
    nDaysSlider.getClassList().add("chronogrid-nav__ndays-slider");
    nDaysSlider.addEventListener("input", ev -> {
      tools.jackson.databind.JsonNode node =
          ev.getEventData().get("event.target.value");
      if (node == null) return;
      String v = node.asString();
      if (!v.isBlank()) {
        int next = clamp(Integer.parseInt(v));
        nDaysValueLabel.setText(messages.tr(K_NDAYS_VALUE, "N = {0} days",
            String.valueOf(next)));
        onNDaysChanged.accept(next);
      }
    }).addEventData("event.target.value");

    Div sliderHost = new Div();
    sliderHost.addClassName("chronogrid-nav__slider-host");
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
    bar.addClassName("chronogrid-nav__bar");
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
    // ChronoGrid's frame, not a second nested card — see
    // `.chronogrid-nav` in styles/chronogrid.css.
    root.addClassName("chronogrid-nav");
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

  /**
   * Wires an aggregator that resolves the set of calendar colours
   * present on any given day. Called on every value-change and on
   * every popover-open of the DatePicker; the badge next to the
   * picker repaints accordingly. Pass an empty-returning function to
   * effectively hide the badge.
   */
  public void setDayColoursProvider(Function<LocalDate, Set<String>> provider) {
    this.dayColoursProvider = provider != null
        ? provider
        : d -> java.util.Collections.emptySet();
    refreshDayHint(datePicker.getValue());
  }

  /** Visible for the browserless tests — keeps the contract explicit. */
  public String dayHintTextForTesting() {
    return dayHintBadge.getText();
  }

  private void refreshDayHint(LocalDate date) {
    dayHintBadge.removeAll();
    if (date == null) {
      dayHintBadge.setVisible(false);
      return;
    }
    Set<String> colours = dayColoursProvider.apply(date);
    if (colours == null) colours = java.util.Collections.emptySet();
    // Dedupe + preserve insertion order so the same calendar palette
    // always renders the dots in the same order across rebuilds.
    Set<String> ordered = new LinkedHashSet<>(colours);
    int n = ordered.size();
    Span text;
    if (n == 0) {
      text = new Span(messages.tr(K_DAY_HINT_NONE, "No events"));
    } else if (n == 1) {
      text = new Span(messages.tr(K_DAY_HINT_ONE, "1 calendar"));
    } else {
      text = new Span(messages.tr(K_DAY_HINT_MANY,
          "{0} calendars", String.valueOf(n)));
    }
    text.addClassName("chronogrid-nav__day-hint-text");
    dayHintBadge.add(text);
    int painted = 0;
    for (String colour : ordered) {
      if (painted >= 3) break;   // cap visual width — extra runs via "+N"
      if (colour == null || colour.isBlank()) continue;
      Span dot = new Span();
      dot.addClassName("chronogrid-nav__day-hint-dot");
      dot.getStyle().set("background-color", colour);
      dayHintBadge.add(dot);
      painted++;
    }
    if (n > 3) {
      Span overflow = new Span("+" + (n - 3));
      overflow.addClassName("chronogrid-nav__day-hint-overflow");
      dayHintBadge.add(overflow);
    }
    dayHintBadge.setVisible(true);
  }
}