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
import java.util.Map;
import java.util.function.Consumer;
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

  private final DatePicker datePicker;
  private final Tabs viewTabs;
  private final Element nDaysSlider;
  private final Span nDaysValueLabel;
  private final HorizontalLayout nGroup;
  private final Div intervalLabel;
  private final Map<ViewMode, Tab> tabsByMode = new EnumMap<>(ViewMode.class);

  /**
   * Resolves, for a given inclusive date range, a per-day set of
   * calendar-source colours. The popover walker uses the returned
   * map to paint up to three coloured dots per day cell in the
   * {@code <vaadin-date-picker>} popover — same colours the
   * underlying calendars use, so the user can see at a glance
   * which calendars have events on which day. {@code null} or
   * absent days are treated as "no events".
   *
   * <p>A range-shaped query (vs. per-day) is the single hot-path
   * call we make for the popover: one CalDAV {@code findInRange}
   * for the whole visible window (month ± 1), grouped on the
   * Java side. Avoids the popover-fans-out-as-many-RPCs-as-days
   * trap.
   */
  private java.util.function.BiFunction<LocalDate, LocalDate,
      java.util.Map<LocalDate, java.util.Set<String>>> dayDotsProvider =
      (from, to) -> java.util.Collections.emptyMap();

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
        pushDayDots(e.getValue());
      }
    });
    // ── Feature #5: per-day dots inside the popover ─────────────
    // On every popover-open, aggregate the visible month-range from
    // the user's cached entries and push a Map<LocalDate,Set<String>>
    // (date → calendar-source colours) down to the picker element.
    // The JS-side walker installs a MutationObserver on the overlay
    // and stamps `data-cg-events` + a `--cg-day-colors` custom
    // property on every matching day cell — CSS paints the dots
    // from the gradient.
    datePicker.addOpenedChangeListener(e -> {
      if (e.isOpened()) {
        pushDayDots(datePicker.getValue());
      }
    });

    HorizontalLayout left = new HorizontalLayout(navGroup, datePicker);
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
   * Wires a range aggregator that resolves, for an inclusive date
   * range, a per-day set of calendar-source colours. Called on
   * every {@code opened-changed}/value-change of the DatePicker;
   * the resulting map is serialised to JSON and pushed to the
   * picker element where the in-popover JS walker paints dots on
   * each matching day cell.
   *
   * <p>Pass an empty-returning function to effectively disable the
   * indicator.
   */
  public void setDayDotsProvider(
      java.util.function.BiFunction<LocalDate, LocalDate,
          java.util.Map<LocalDate, java.util.Set<String>>> provider) {
    this.dayDotsProvider = provider != null ? provider
        : (from, to) -> java.util.Collections.emptyMap();
  }

  /**
   * Computes the visible-month-±1 window around {@code anchor}
   * and pushes the colour map down to the picker element. Idempotent
   * for the same window — the JS walker reads {@code data-cg-dots}
   * on the picker element on every paint, so re-pushing the same
   * value is cheap.
   */
  private void pushDayDots(LocalDate anchor) {
    LocalDate centre = anchor != null ? anchor : LocalDate.now();
    LocalDate from = centre.withDayOfMonth(1).minusMonths(1);
    LocalDate to = centre.withDayOfMonth(1).plusMonths(2).minusDays(1);
    java.util.Map<LocalDate, java.util.Set<String>> coloursByDay;
    try {
      coloursByDay = dayDotsProvider.apply(from, to);
      if (coloursByDay == null) coloursByDay = java.util.Collections.emptyMap();
    } catch (RuntimeException ex) {
      logger().info("dayDotsProvider({}..{}) failed: {}", from, to, ex.toString());
      coloursByDay = java.util.Collections.emptyMap();
    }
    String json = serialiseDayDots(coloursByDay);
    Element el = datePicker.getElement();
    el.setProperty("__cgDayDotsPayload", json);
    // Mirror the payload as an attribute so the DevTools Elements
    // panel surfaces it without having to break into the JS heap —
    // diagnostic only, the walker reads the property.
    // Diagnostic: surface the count as an attribute so DevTools'
    // Elements panel shows the data-shape without breaking into JS.
    el.setAttribute("data-cg-dots-days", String.valueOf(coloursByDay.size()));
    el.executeJs(POPOVER_WALKER_JS);
  }

  /**
   * Serialises a {@code Map<LocalDate, Set<String>>} into the JSON
   * shape the in-popover walker expects:
   * {@code {"2026-06-15":["#1f77b4","#ff7f0e"], ...}}.
   *
   * <p>Hand-rolled to avoid pulling Jackson into the navigation-bar
   * for this single one-shot push — the values are CSS colour
   * strings (hex / named) so escaping rules are trivial.
   */
  private static String serialiseDayDots(
      java.util.Map<LocalDate, java.util.Set<String>> map) {
    StringBuilder b = new StringBuilder(map.size() * 32 + 2);
    b.append('{');
    boolean first = true;
    for (java.util.Map.Entry<LocalDate, java.util.Set<String>> e : map.entrySet()) {
      java.util.Set<String> colours = e.getValue();
      if (colours == null || colours.isEmpty()) continue;
      if (!first) b.append(',');
      first = false;
      b.append('"').append(e.getKey()).append("\":[");
      int painted = 0;
      for (String c : colours) {
        if (c == null || c.isBlank()) continue;
        if (painted > 0) b.append(',');
        b.append('"').append(escapeJsonString(c)).append('"');
        painted++;
        if (painted >= 3) break;
      }
      b.append(']');
    }
    b.append('}');
    return b.toString();
  }

  private static String escapeJsonString(String s) {
    StringBuilder b = new StringBuilder(s.length() + 2);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '"' || c == '\\') b.append('\\');
      b.append(c);
    }
    return b.toString();
  }

  /**
   * In-popover walker. Runs against {@code this} = the
   * {@code <vaadin-date-picker>} element on every push.
   *
   * <p>Architecture (validated against Vaadin 25.1.1):
   * <ul>
   *   <li>Vaadin teleports the popover to {@code document.body},
   *       so {@code <vaadin-month-calendar>} instances are
   *       reachable via a document-wide {@code querySelectorAll}
   *       once the overlay has mounted. A recursive shadow-tree
   *       walk from {@code document.body} catches any case where
   *       the overlay nests them under custom scroller wrappers.</li>
   *   <li>The first popover-open often races the overlay mount —
   *       {@code paintWithRetry} polls every 60 ms (up to 10×)
   *       until at least one cell is found, then stops.</li>
   *   <li>Day cells are {@code [part~="date"]} {@code <td>}
   *       elements inside {@code <table id="monthGrid">
   *       <tbody id="days-container">} (confirmed via DevTools
   *       inspection on Vaadin 25.1.1). Fallback selectors
   *       {@code #days-container td} and {@code #monthGrid tbody
   *       td} guard against minor-version changes.</li>
   *   <li>Visual rendering: a {@code <style data-cg="1">} element
   *       is injected once into each {@code <vaadin-month-calendar>}'s
   *       shadow root. The stylesheet lives in the same shadow
   *       scope as the cells, so its selectors match without
   *       {@code ::part()} gymnastics. The bar is a
   *       {@code ::after} pseudo — a brand-new box that Vaadin's
   *       own td-background rules can't fight on specificity.</li>
   *   <li>Per cell we set only {@code data-cg-events="N"} as an
   *       attribute and a custom property {@code --cg-day-bg}
   *       (either a single colour or a 2-/3-stop {@code linear-
   *       gradient}). The injected stylesheet reads
   *       {@code --cg-day-bg} from the cell and paints the bar.</li>
   *   <li>MutationObservers are installed recursively on every
   *       reachable shadow root (a single observer doesn't cross
   *       boundaries) so user navigation inside the popover —
   *       month scrolling, year picking — triggers re-paints
   *       automatically.</li>
   * </ul>
   *
   * <p>Diagnostics: every key step logs at {@code console.debug}
   * level under the {@code [chronogrid:#5]} prefix; enable the
   * "Verbose" log filter in DevTools to see them. The picker
   * element also carries {@code data-cg-dots-days="N"} so the
   * data shape is visible in the Elements panel without breaking
   * into JS.
   */
  private static final String POPOVER_WALKER_JS =
      "const picker=this;\n"
    + "const TAG='[chronogrid:#5]';\n"
    + "const raw=picker.__cgDayDotsPayload||'{}';\n"
    + "let map={};\n"
    + "try{map=JSON.parse(raw);}catch(e){console.warn(TAG,'JSON parse failed',e);map={};}\n"
    + "console.debug(TAG,'push',Object.keys(map).length,'days');\n"
    // pad helper
    + "function pad(n){return String(n).padStart(2,'0');}\n"
    + "function isoFor(d){return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate());}\n"
    // ── extract date from a cell, multiple paths
    + "function dateOf(cell){\n"
    + "  if(cell.date instanceof Date) return cell.date;\n"
    + "  if(cell._date instanceof Date) return cell._date;\n"
    // host-month fallback: the <vaadin-month-calendar> exposes `.month`
    + "  const root=cell.getRootNode();\n"
    + "  const host=root && root.host;\n"
    + "  if(host && host.month instanceof Date){\n"
    + "    const dayNum=parseInt((cell.textContent||'').trim(),10);\n"
    + "    if(!isNaN(dayNum) && dayNum>=1 && dayNum<=31){\n"
    + "      return new Date(host.month.getFullYear(),host.month.getMonth(),dayNum);\n"
    + "    }\n"
    + "  }\n"
    + "  return null;\n"
    + "}\n"
    // ── stylesheet injected into every <vaadin-month-calendar>
    //     shadow root. Lives in the same shadow scope as the day
    //     cells, so its selectors match cleanly without any
    //     ::part() / specificity gymnastics. The ::after pseudo is
    //     a brand-new box that Vaadin's own td styles cannot fight.
    + "const CG_STYLE_TEXT=\n"
    + "  '[data-cg-events]{position:relative}'+\n"
    + "  '[data-cg-events]::after{'+\n"
    + "    'content:\"\";'+\n"
    + "    'position:absolute;left:50%;bottom:2px;'+\n"
    + "    'transform:translateX(-50%);'+\n"
    + "    'width:60%;max-width:24px;height:4px;'+\n"
    + "    'border-radius:2px;'+\n"
    + "    'background:var(--cg-day-bg,currentColor);'+\n"
    + "    'pointer-events:none;'+\n"
    + "  '}';\n"
    + "function ensureStyle(mc){\n"
    + "  const r=mc.shadowRoot;\n"
    + "  if(!r||r.querySelector('style[data-cg]'))return;\n"
    + "  const s=document.createElement('style');\n"
    + "  s.setAttribute('data-cg','1');\n"
    + "  s.textContent=CG_STYLE_TEXT;\n"
    + "  r.appendChild(s);\n"
    + "}\n"
    // ── paint a single cell with up to 3 colours
    + "function paint(cell){\n"
    + "  const d=dateOf(cell);\n"
    + "  if(!d){return;}\n"
    + "  const iso=isoFor(d);\n"
    + "  const cs=map[iso];\n"
    + "  if(cs && cs.length){\n"
    + "    let bg;\n"
    + "    if(cs.length===1){bg=cs[0];}\n"
    + "    else if(cs.length===2){bg='linear-gradient(to right,'+cs[0]+' 0% 50%,'+cs[1]+' 50% 100%)';}\n"
    + "    else{bg='linear-gradient(to right,'+cs[0]+' 0% 33.333%,'+cs[1]+' 33.333% 66.666%,'+cs[2]+' 66.666% 100%)';}\n"
    + "    cell.style.setProperty('--cg-day-bg',bg);\n"
    + "    cell.setAttribute('data-cg-events',String(Math.min(3,cs.length)));\n"
    + "  } else if(cell.hasAttribute('data-cg-events')){\n"
    + "    cell.style.removeProperty('--cg-day-bg');\n"
    + "    cell.removeAttribute('data-cg-events');\n"
    + "  }\n"
    + "}\n"
    // ── recursively collect every <vaadin-month-calendar> across
    //     all shadow roots reachable from root. Critical: also
    //     recurse into light-DOM children of elements with shadow
    //     roots (slotted content) — a shallow shadowRoot+children
    //     walker would miss vaadin-month-calendar instances mounted
    //     as light-DOM under a scroller wrapper.
    + "function collectMonths(root,acc){\n"
    + "  acc=acc||[];\n"
    + "  if(!root) return acc;\n"
    + "  const shadowRoot=root.shadowRoot;\n"
    + "  const lightRoot=root;\n"
    + "  [shadowRoot,lightRoot.shadowRoot?null:lightRoot].forEach(r=>{\n"
    + "    if(!r||!r.querySelectorAll) return;\n"
    + "    r.querySelectorAll('vaadin-month-calendar').forEach(m=>{if(acc.indexOf(m)<0)acc.push(m);});\n"
    + "  });\n"
    + "  if(shadowRoot && shadowRoot.querySelectorAll){\n"
    + "    shadowRoot.querySelectorAll('vaadin-month-calendar').forEach(m=>{if(acc.indexOf(m)<0)acc.push(m);});\n"
    + "    shadowRoot.querySelectorAll('*').forEach(el=>collectMonths(el,acc));\n"
    + "  }\n"
    + "  if(lightRoot.children){\n"
    + "    Array.from(lightRoot.children).forEach(el=>collectMonths(el,acc));\n"
    + "  }\n"
    + "  return acc;\n"
    + "}\n"
    // ── tolerant cell collector — Vaadin 25.1.1's month-calendar
    //     uses a <table id='monthGrid'><tbody id='days-container'>
    //     structure (confirmed via DevTools inspection). Cells may
    //     or may not carry part='date' depending on minor version;
    //     fall back to plain td under #days-container.
    + "function collectCells(mc){\n"
    + "  const mr=mc.shadowRoot||mc;\n"
    + "  if(!mr||!mr.querySelectorAll) return [];\n"
    + "  let cells=mr.querySelectorAll('[part~=\"date\"]');\n"
    + "  if(cells.length>0) return Array.from(cells);\n"
    + "  cells=mr.querySelectorAll('#days-container td');\n"
    + "  if(cells.length>0) return Array.from(cells);\n"
    + "  cells=mr.querySelectorAll('#monthGrid tbody td');\n"
    + "  return Array.from(cells);\n"
    + "}\n"
    // Document-wide search bypasses any overlay-content scoping
    // issues — vaadin overlays can portal anywhere.
    // BUG #3 re-entrancy guard. The MutationObserver below listens
    // to attribute changes from Vaadin (cell.part, cell.aria-label
    // change on month scroll). When the user clicks a day cell
    // Vaadin's selection logic ALSO changes attributes on the cell
    // — that triggers our observer → paintAll runs → paintAll's
    // own setAttribute calls would trigger the observer again →
    // infinite loop → frozen browser. The window-level flag short-
    // circuits the recursive call so we never re-enter while a
    // paint is already in flight.
    + "function paintAll(){\n"
    + "  if(window.__cgPaintBusy) return {cals:0,cells:0,painted:0};\n"
    + "  window.__cgPaintBusy=true;\n"
    + "  try {\n"
    + "    const docMonths=Array.from(document.querySelectorAll('vaadin-month-calendar'));\n"
    + "    const recursed=collectMonths(document.body);\n"
    + "    const seen=new Set(docMonths);\n"
    + "    recursed.forEach(m=>seen.add(m));\n"
    + "    const cals=Array.from(seen);\n"
    + "    let cellsTotal=0,cellsPainted=0;\n"
    + "    cals.forEach(mc=>{\n"
    + "      ensureStyle(mc);\n"
    + "      const cells=collectCells(mc);\n"
    + "      cellsTotal+=cells.length;\n"
    + "      cells.forEach(c=>{paint(c); if(c.hasAttribute('data-cg-events')) cellsPainted++;});\n"
    + "    });\n"
    + "    console.debug(TAG,'paintAll: calendars=',cals.length,'cells=',cellsTotal,'painted=',cellsPainted);\n"
    + "    return {cals:cals.length,cells:cellsTotal,painted:cellsPainted};\n"
    + "  } finally {\n"
    + "    window.__cgPaintBusy=false;\n"
    + "  }\n"
    + "}\n"
    // Polling retry: first popover-open often hits before the
    // overlay finishes mounting. Up to 10 retries × 60 ms gives the
    // month grids ~600 ms to materialise; stop early on success.
    + "function paintWithRetry(retries){\n"
    + "  const r=paintAll();\n"
    + "  if(r.cells>0||retries<=0) return;\n"
    + "  setTimeout(()=>paintWithRetry(retries-1),60);\n"
    + "}\n"
    + "function attach(content){\n"
    + "  if(!content){console.warn(TAG,'no overlay content'); return;}\n"
    + "  paintWithRetry(10);\n"
    // ── recursively install observers on every reachable shadow
    //     root so user-navigation re-paints work (MutationObserver
    //     does NOT cross shadow boundaries — one per root needed).
    + "  function installObs(node,depth){\n"
    + "    if(!node||depth>6) return;\n"
    + "    if(node.shadowRoot){\n"
    + "      if(!node.__cgObs){\n"
    + "        const o=new MutationObserver(()=>paintAll());\n"
    // BUG #3: scope the attribute listener to what Vaadin actually
    // mutates when cells move between months ('part', 'aria-label')
    // — NOT to every attribute change. Without this filter, the
    // observer also picked up our own 'data-cg-events' writes and
    // looped infinitely on day-cell selection. childList +
    // characterData stay open so cell-text-content changes during
    // month scrolling still trigger a repaint.
    + "        o.observe(node.shadowRoot,{\n"
    + "            subtree:true,childList:true,characterData:true,\n"
    + "            attributes:true,attributeFilter:['part','aria-label']\n"
    + "        });\n"
    + "        node.__cgObs=o;\n"
    + "      }\n"
    + "      node.shadowRoot.querySelectorAll('*').forEach(el=>installObs(el,depth+1));\n"
    + "    }\n"
    + "    if(node.children) Array.from(node.children).forEach(el=>installObs(el,depth+1));\n"
    + "  }\n"
    + "  installObs(content,0);\n"
    + "  console.debug(TAG,'MutationObservers installed');\n"
    + "}\n"
    + "function findOverlayContent(){\n"
    // try the documented accessor first
    + "  if(picker._overlayContent) return picker._overlayContent;\n"
    // try via the overlay slot (vaadin 25 sometimes lazy-mounts)
    + "  if(picker.$ && picker.$.overlay){\n"
    + "    const oc=picker.$.overlay.querySelector('vaadin-date-picker-overlay-content');\n"
    + "    if(oc) return oc;\n"
    + "  }\n"
    // last resort: document-wide query (popover is portal'd to body)
    + "  return document.querySelector('vaadin-date-picker-overlay-content');\n"
    + "}\n"
    + "function waitForOverlay(retries){\n"
    + "  const oc=findOverlayContent();\n"
    + "  if(oc){attach(oc);return;}\n"
    + "  if(retries<=0){console.warn(TAG,'gave up waiting for overlay');return;}\n"
    + "  setTimeout(()=>waitForOverlay(retries-1),40);\n"
    + "}\n"
    + "waitForOverlay(40);\n";
}