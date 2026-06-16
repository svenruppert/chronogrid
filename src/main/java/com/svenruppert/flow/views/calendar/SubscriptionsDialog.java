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
import com.svenruppert.flow.calendar.service.CalDavServerConnection;
import com.svenruppert.flow.calendar.service.CalendarSubscription;
import com.svenruppert.flow.i18n.I18nSupport;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Element;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Manages the user's current set of CalDAV subscriptions:
 *
 * <ul>
 *   <li>Per-row {@code Visible} checkbox toggles the calendar in /
 *       out of the merged view without re-fetching.</li>
 *   <li>Per-row {@code Disconnect} button drops the subscription
 *       from the session entirely — next REPORT round skips it.</li>
 * </ul>
 *
 * <p>The parent {@code CalendarView} owns the canonical list; the
 * dialog calls {@code onToggleVisible(uri, visible)} and
 * {@code onRemove(uri)} on user interaction. The View persists, then
 * triggers a calendar refresh.
 */
public final class SubscriptionsDialog
    extends Composite<Dialog> implements I18nSupport, HasLogger {

  private static final String K_TITLE = "calendar.subscriptions.title";
  private static final String K_EMPTY = "calendar.subscriptions.empty";
  private static final String K_COL_COLOR = "calendar.subscriptions.col.color";
  private static final String K_COL_NAME = "calendar.subscriptions.col.name";
  private static final String K_COL_SERVER = "calendar.subscriptions.col.server";
  private static final String K_COL_VISIBLE = "calendar.subscriptions.col.visible";
  private static final String K_COL_ACTION = "calendar.subscriptions.col.action";
  private static final String K_ACTION_DISCONNECT = "calendar.subscriptions.disconnect";
  private static final String K_ACTION_CLOSE = "calendar.action.cancel";

  /** Back-compat — no server context, no colour callback. */
  public SubscriptionsDialog(List<CalendarSubscription> subscriptions,
                             BiConsumer<URI, Boolean> onToggleVisible,
                             Consumer<URI> onRemove) {
    this(subscriptions, List.of(), onToggleVisible, (uri, color) -> { /* noop */ }, onRemove);
  }

  /** Back-compat — server context but no colour callback. */
  public SubscriptionsDialog(List<CalendarSubscription> subscriptions,
                             List<CalDavServerConnection> servers,
                             BiConsumer<URI, Boolean> onToggleVisible,
                             Consumer<URI> onRemove) {
    this(subscriptions, servers, onToggleVisible,
        (uri, color) -> { /* noop */ }, onRemove);
  }

  public SubscriptionsDialog(List<CalendarSubscription> subscriptions,
                             List<CalDavServerConnection> servers,
                             BiConsumer<URI, Boolean> onToggleVisible,
                             BiConsumer<URI, String> onColorChanged,
                             Consumer<URI> onRemove) {
    Dialog dialog = getContent();
    dialog.setHeaderTitle(tr(K_TITLE, "Subscribed calendars"));
    dialog.setWidth("720px");
    logger().info("SubscriptionsDialog open: {} subscription(s), {} server(s)",
        subscriptions.size(), servers.size());

    if (subscriptions.isEmpty()) {
      Span empty = new Span(tr(K_EMPTY,
          "You are not subscribed to any CalDAV calendar yet. "
              + "Use Settings → Discover calendars to add one."));
      empty.addClassName("calendar-secondary-text");
      dialog.add(empty);
    } else {
      dialog.add(buildGrid(subscriptions, servers,
          onToggleVisible, onColorChanged, onRemove));
    }

    Button close = new Button(tr(K_ACTION_CLOSE, "Cancel"),
        e -> dialog.close());
    close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    dialog.getFooter().add(close);
  }

  private VerticalLayout buildGrid(List<CalendarSubscription> subs,
                                   List<CalDavServerConnection> servers,
                                   BiConsumer<URI, Boolean> onToggleVisible,
                                   BiConsumer<URI, String> onColorChanged,
                                   Consumer<URI> onRemove) {
    Map<String, String> serverNameById = new HashMap<>();
    for (CalDavServerConnection s : servers) {
      serverNameById.put(s.id(), s.displayName());
    }
    Grid<CalendarSubscription> grid = new Grid<>(CalendarSubscription.class, false);
    grid.setItems(subs);
    grid.setAllRowsVisible(true);

    grid.addComponentColumn(s -> {
      Div host = new Div();
      Element picker = new Element("input");
      picker.setAttribute("type", "color");
      picker.setAttribute("title", s.displayName());
      String initial = normaliseColor(s.color());
      picker.setProperty("value", initial);
      picker.getClassList().add("calendar-color-picker");
      picker.addEventListener("change", ev -> {
        var node = ev.getEventData().get("event.target.value");
        if (node != null) {
          String picked = node.asString();
          logger().info("SubscriptionsDialog colour change: {} -> {}",
              s.uri(), picked);
          onColorChanged.accept(s.uri(), picked);
        }
      }).addEventData("event.target.value");
      host.getElement().appendChild(picker);
      return host;
    }).setHeader(tr(K_COL_COLOR, "")).setAutoWidth(true).setFlexGrow(0);

    grid.addColumn(CalendarSubscription::displayName)
        .setHeader(tr(K_COL_NAME, "Calendar"))
        .setAutoWidth(true)
        .setFlexGrow(1);

    grid.addColumn(s -> {
      if (s.serverId() == null) return "";
      String name = serverNameById.get(s.serverId());
      return name == null ? "" : name;
    }).setHeader(tr(K_COL_SERVER, "Server"))
        .setAutoWidth(true).setFlexGrow(0);

    grid.addComponentColumn(s -> {
      Checkbox visible = new Checkbox(s.visible());
      visible.setId("sub-visible-" + s.uri().hashCode());
      visible.addValueChangeListener(e -> {
        logger().info("SubscriptionsDialog visibility toggle: {} -> {}",
            s.uri(), e.getValue());
        onToggleVisible.accept(s.uri(), e.getValue());
      });
      return visible;
    }).setHeader(tr(K_COL_VISIBLE, "Visible")).setAutoWidth(true).setFlexGrow(0);

    grid.addComponentColumn(s -> {
      Button disconnect = new Button(VaadinIcon.CLOSE.create(), e -> {
        logger().info("SubscriptionsDialog disconnect: {}", s.uri());
        onRemove.accept(s.uri());
      });
      disconnect.setId("sub-disconnect-" + s.uri().hashCode());
      disconnect.getElement().setProperty("title",
          tr(K_ACTION_DISCONNECT, "Disconnect"));
      disconnect.addThemeVariants(
          ButtonVariant.LUMO_ERROR,
          ButtonVariant.LUMO_TERTIARY,
          ButtonVariant.LUMO_ICON);
      return disconnect;
    }).setHeader(tr(K_COL_ACTION, "")).setAutoWidth(true).setFlexGrow(0);

    VerticalLayout layout = new VerticalLayout(grid);
    layout.setPadding(false);
    layout.setSpacing(false);
    return layout;
  }

  public void open() {
    getContent().open();
  }

  /**
   * The HTML5 {@code <input type="color">} value must be a 7-char
   * "#rrggbb" string. iCloud and friends sometimes ship "#rrggbbaa"
   * or null; this helper trims the alpha and picks a deterministic
   * fallback so the picker always renders something sensible.
   */
  private static String normaliseColor(String raw) {
    if (raw == null || raw.isBlank()) return "#888888";
    String trimmed = raw.trim();
    if (trimmed.startsWith("#") && trimmed.length() == 9) {
      return trimmed.substring(0, 7);
    }
    if (trimmed.startsWith("#") && trimmed.length() == 7) {
      return trimmed;
    }
    return "#888888";
  }
}