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
import com.svenruppert.chronogrid.service.CalDavServerConnection;
import com.svenruppert.chronogrid.service.CalendarSubscription;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.TabsVariant;
import com.vaadin.flow.dom.Element;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Planning-Feature #7 Schicht 3 — Apple-Calendar-style Connection
 * Manager. A single Master-Detail dialog replaces the three legacy
 * dialogs (Settings, Connections, Subscriptions).
 *
 * <p><b>Layout.</b> Left pane: vertical {@link Tabs} with one entry
 * per configured CalDAV server connection. Right pane: the selected
 * server's subscription list, each row with a colour dot, name,
 * visibility checkbox, inline colour picker, and remove-this-
 * subscription button. Footer carries an "Add server" primary
 * button (opens the {@link ConnectionWizardDialog}), a Re-discover
 * button (re-runs CalDAV discovery against the selected server's
 * URL+creds and adds any new calendars), and a Remove-server
 * destructive button (atomic — pulls server + all its subscriptions
 * + per-entry colour fallbacks out of the state store).
 *
 * <p>The dialog reads the live server + subscription lists through
 * the injected {@link Supplier}s on every interaction, so any
 * mutation from another path (e.g. the wizard adding a new server)
 * is reflected on the next refresh without an explicit push from
 * the host.
 */
public final class ConnectionManagerDialog
    extends Composite<Dialog> implements HasLogger {

  private static final long serialVersionUID = 1L;

  // ── i18n keys ─────────────────────────────────────────────────────
  private static final String K_TITLE = "calendar.manager.title";
  private static final String K_EMPTY = "calendar.manager.empty";
  private static final String K_ACTION_ADD = "calendar.manager.action.add";
  private static final String K_ACTION_REDISCOVER = "calendar.manager.action.rediscover";
  private static final String K_ACTION_REMOVE_SERVER = "calendar.manager.action.removeServer";
  private static final String K_ACTION_CLOSE = "calendar.manager.action.close";
  private static final String K_SERVER_HEADER = "calendar.manager.serverHeader";
  private static final String K_NO_SUBS = "calendar.manager.noSubs";
  private static final String K_REMOVE_SUB_TITLE = "calendar.manager.removeSub.title";

  // ── injected state ────────────────────────────────────────────────
  private final CalendarMessages messages;
  private final Supplier<List<CalDavServerConnection>> serversSupplier;
  private final Supplier<List<CalendarSubscription>> subsSupplier;
  private final Runnable onAddServer;
  private final Consumer<String> onRediscoverServer;
  private final Consumer<String> onRemoveServer;
  private final BiConsumer<URI, Boolean> onToggleVisibility;
  private final BiConsumer<URI, String> onChangeColour;
  private final Consumer<URI> onRemoveSubscription;

  // ── widgets ───────────────────────────────────────────────────────
  private final Tabs serverTabs;
  private final Map<Tab, String> serverIdByTab = new LinkedHashMap<>();
  private final VerticalLayout detailPane;
  private final Span emptyHint;
  private final HorizontalLayout serverActions;
  private final Button rediscoverBtn;
  private final Button removeServerBtn;
  // The wizard add-button lives in the dialog's footer slot via
  // Dialog#getFooter(); a direct field reference keeps it test-
  // visible without traversing the overlay.

  /**
   * @param messages           host-i18n adapter
   * @param serversSupplier    live read of the configured servers
   * @param subsSupplier       live read of the active subscriptions
   * @param onAddServer        opens {@link ConnectionWizardDialog}
   * @param onRediscoverServer (serverId) → re-discovery + merge
   * @param onRemoveServer     (serverId) → atomic server + subs +
   *                           colour-fallback removal
   * @param onToggleVisibility (uri, visible) → visibility persist
   * @param onChangeColour     (uri, "#rrggbb") → colour persist
   * @param onRemoveSubscription (uri) → single-sub removal
   */
  public ConnectionManagerDialog(
      CalendarMessages messages,
      Supplier<List<CalDavServerConnection>> serversSupplier,
      Supplier<List<CalendarSubscription>> subsSupplier,
      Runnable onAddServer,
      Consumer<String> onRediscoverServer,
      Consumer<String> onRemoveServer,
      BiConsumer<URI, Boolean> onToggleVisibility,
      BiConsumer<URI, String> onChangeColour,
      Consumer<URI> onRemoveSubscription) {
    this.messages = messages;
    this.serversSupplier = serversSupplier;
    this.subsSupplier = subsSupplier;
    this.onAddServer = onAddServer;
    this.onRediscoverServer = onRediscoverServer;
    this.onRemoveServer = onRemoveServer;
    this.onToggleVisibility = onToggleVisibility;
    this.onChangeColour = onChangeColour;
    this.onRemoveSubscription = onRemoveSubscription;

    Dialog dialog = getContent();
    dialog.setId("calendar-manager");
    dialog.setHeaderTitle(messages.tr(K_TITLE, "Connection Manager"));
    dialog.setWidth("860px");
    dialog.setHeight("560px");

    // ── left: server tabs (vertical) ──────────────────────────────
    serverTabs = new Tabs();
    serverTabs.setId("calendar-manager-server-tabs");
    serverTabs.setOrientation(Tabs.Orientation.VERTICAL);
    serverTabs.addThemeVariants(TabsVariant.LUMO_MINIMAL);
    serverTabs.getStyle()
        .set("min-width", "200px")
        .set("border-right", "1px solid var(--lumo-contrast-10pct)");
    serverTabs.addSelectedChangeListener(e -> refreshDetailPane());

    // ── right: detail pane (subs of selected server) ─────────────
    detailPane = new VerticalLayout();
    detailPane.setId("calendar-manager-detail");
    detailPane.setPadding(false);
    detailPane.setSpacing(false);
    detailPane.setSizeFull();
    detailPane.getStyle().set("padding-left", "var(--lumo-space-m)");

    emptyHint = new Span(messages.tr(K_EMPTY,
        "No CalDAV servers configured yet. Click \"+ Add server\" to set one up."));
    emptyHint.setId("calendar-manager-empty");
    emptyHint.addClassName("chronogrid-secondary-text");

    rediscoverBtn = new Button(
        messages.tr(K_ACTION_REDISCOVER, "Re-discover"),
        VaadinIcon.REFRESH.create(),
        e -> {
          String id = currentServerId();
          if (id != null) onRediscoverServer.accept(id);
          refresh();
        });
    rediscoverBtn.setId("calendar-manager-rediscover");
    rediscoverBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    removeServerBtn = new Button(
        messages.tr(K_ACTION_REMOVE_SERVER, "Remove server"),
        VaadinIcon.TRASH.create(),
        e -> {
          String id = currentServerId();
          if (id != null) {
            onRemoveServer.accept(id);
            refresh();
          }
        });
    removeServerBtn.setId("calendar-manager-remove-server");
    removeServerBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY,
        ButtonVariant.LUMO_ERROR);

    serverActions = new HorizontalLayout(rediscoverBtn, removeServerBtn);
    serverActions.setSpacing(true);
    serverActions.setPadding(false);
    serverActions.setAlignItems(FlexComponent.Alignment.CENTER);
    serverActions.getStyle().set("padding-top", "var(--lumo-space-m)");

    HorizontalLayout body = new HorizontalLayout(serverTabs, detailPane);
    body.setSizeFull();
    body.setSpacing(false);
    body.setPadding(false);
    dialog.add(body);

    // ── footer ────────────────────────────────────────────────────
    Button addServer = new Button(
        messages.tr(K_ACTION_ADD, "+ Add server"),
        VaadinIcon.PLUS.create(),
        e -> {
          dialog.close();
          if (onAddServer != null) onAddServer.run();
        });
    addServer.setId("calendar-manager-add");
    addServer.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button close = new Button(
        messages.tr(K_ACTION_CLOSE, "Close"),
        e -> dialog.close());
    close.setId("calendar-manager-close");
    close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    dialog.getFooter().add(close, addServer);

    refresh();
  }

  // ── public API ────────────────────────────────────────────────────

  public void open() {
    refresh();
    getContent().open();
  }

  /** Test seam: total tab count = configured server count. */
  public int serverTabCount() {
    return serverIdByTab.size();
  }

  // ── tab + detail wiring ───────────────────────────────────────────

  private void refresh() {
    serverTabs.removeAll();
    serverIdByTab.clear();
    List<CalDavServerConnection> servers = serversSupplier.get();
    for (CalDavServerConnection server : servers) {
      Tab tab = new Tab(serverTabLabel(server));
      tab.setId("calendar-manager-server-tab-" + server.id());
      serverIdByTab.put(tab, server.id());
      serverTabs.add(tab);
    }
    if (!servers.isEmpty()) {
      serverTabs.setSelectedIndex(0);
    }
    refreshDetailPane();
  }

  private void refreshDetailPane() {
    detailPane.removeAll();
    String currentId = currentServerId();
    if (currentId == null) {
      detailPane.add(emptyHint);
      return;
    }
    CalDavServerConnection server = serversSupplier.get().stream()
        .filter(s -> s.id().equals(currentId)).findFirst().orElse(null);
    if (server == null) {
      detailPane.add(emptyHint);
      return;
    }

    Span header = new Span(messages.tr(K_SERVER_HEADER,
        "{0} — {1}", server.displayName(), server.baseUri().toString()));
    header.getStyle().set("font-weight", "600");
    detailPane.add(header);

    List<CalendarSubscription> subs = subsSupplier.get().stream()
        .filter(s -> currentId.equals(s.serverId()))
        .toList();
    if (subs.isEmpty()) {
      Span none = new Span(messages.tr(K_NO_SUBS,
          "This server has no active subscriptions."));
      none.addClassName("chronogrid-secondary-text");
      detailPane.add(none);
    } else {
      for (CalendarSubscription sub : subs) {
        detailPane.add(buildSubRow(sub));
      }
    }
    detailPane.add(serverActions);
  }

  private com.vaadin.flow.component.Component buildSubRow(CalendarSubscription sub) {
    Div dot = new Div();
    dot.getStyle()
        .set("width", "10px")
        .set("height", "10px")
        .set("border-radius", "50%")
        .set("background-color", sub.color() != null ? sub.color() : "transparent")
        .set("flex-shrink", "0");

    Span name = new Span(sub.displayName());
    name.getStyle().set("flex", "1");

    Checkbox visible = new Checkbox(sub.visible());
    visible.setId("manager-sub-visible-" + Integer.toHexString(sub.uri().hashCode()));
    visible.addValueChangeListener(ev -> {
      onToggleVisibility.accept(sub.uri(), ev.getValue());
      refreshDetailPane();
    });

    // Reuse the HTML5 <input type=color> pattern that lives in
    // SubscriptionsDialog#buildSubRow — inline picker for the
    // per-calendar override colour.
    Element picker = new Element("input");
    picker.setAttribute("type", "color");
    picker.setAttribute("value", normaliseHex(sub.color()));
    picker.getStyle().set("width", "32px").set("height", "26px")
        .set("border", "1px solid var(--lumo-contrast-20pct)")
        .set("border-radius", "var(--lumo-border-radius-s)")
        .set("background", "transparent")
        .set("padding", "1px");
    picker.addEventListener("change", ev -> {
      tools.jackson.databind.JsonNode node =
          ev.getEventData().get("event.target.value");
      if (node != null) {
        String picked = node.asString();
        if (picked != null && !picked.isBlank()) {
          onChangeColour.accept(sub.uri(), picked);
          refreshDetailPane();
        }
      }
    }).addEventData("event.target.value");
    Div pickerHost = new Div();
    pickerHost.getElement().appendChild(picker);

    Button removeSub = new Button(VaadinIcon.CLOSE.create(), e -> {
      onRemoveSubscription.accept(sub.uri());
      refresh();
    });
    removeSub.setId("manager-sub-remove-" + Integer.toHexString(sub.uri().hashCode()));
    removeSub.getElement().setProperty("title",
        messages.tr(K_REMOVE_SUB_TITLE, "Disconnect this calendar"));
    removeSub.addThemeVariants(
        ButtonVariant.LUMO_ERROR,
        ButtonVariant.LUMO_TERTIARY,
        ButtonVariant.LUMO_ICON,
        ButtonVariant.LUMO_SMALL);

    HorizontalLayout row = new HorizontalLayout(dot, name, visible,
        pickerHost, removeSub);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setWidthFull();
    row.setSpacing(true);
    row.setPadding(false);
    row.getStyle().set("padding", "var(--lumo-space-xs) 0");
    return row;
  }

  // ── helpers ───────────────────────────────────────────────────────

  private String currentServerId() {
    Tab selected = serverTabs.getSelectedTab();
    if (selected == null) return null;
    return serverIdByTab.get(selected);
  }

  private static String serverTabLabel(CalDavServerConnection server) {
    String name = server.displayName();
    return (name == null || name.isBlank())
        ? server.baseUri().getHost()
        : name;
  }

  /**
   * HTML5 colour input requires a 7-char "#rrggbb". Coerce 4-char
   * iCloud-shorthand colours or null to a sane default so the
   * picker always renders something.
   */
  private static String normaliseHex(String raw) {
    if (raw == null) return "#888888";
    String trimmed = raw.trim();
    if (trimmed.length() == 7 && trimmed.charAt(0) == '#') return trimmed;
    if (trimmed.length() == 9 && trimmed.charAt(0) == '#') return trimmed.substring(0, 7);
    return "#888888";
  }
}
