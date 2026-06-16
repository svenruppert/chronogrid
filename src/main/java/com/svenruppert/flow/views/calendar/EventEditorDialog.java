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

import com.svenruppert.flow.calendar.mapping.EntryMapper;
import com.svenruppert.flow.calendar.service.CalDavServerConnection;
import com.svenruppert.flow.calendar.service.CalendarSubscription;
import com.svenruppert.flow.i18n.I18nSupport;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import org.vaadin.stefan.fullcalendar.Entry;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Edit-or-new event dialog. Carries Title / Description / Location /
 * URL / Start / End fields plus Save, Cancel, and (for existing
 * events) Delete buttons. For new events the dialog adds a
 * <strong>Server</strong> filter and a <strong>Calendar</strong>
 * picker — the calendar list renders each option as a coloured
 * swatch + calendar name + server name so the user can tell which
 * "Personal" they're picking when several servers expose the same
 * label.
 */
public final class EventEditorDialog
    extends Composite<Dialog> implements I18nSupport {

  private static final String ALL_SERVERS_SENTINEL = "__all__";

  private static final String K_DIALOG_TITLE_NEW = "calendar.dialog.title.new";
  private static final String K_DIALOG_TITLE_EDIT = "calendar.dialog.title.edit";
  private static final String K_FIELD_TITLE = "calendar.field.title";
  private static final String K_FIELD_DESCRIPTION = "calendar.field.description";
  private static final String K_FIELD_LOCATION = "calendar.field.location";
  private static final String K_FIELD_URL = "calendar.field.url";
  private static final String K_FIELD_START = "calendar.field.start";
  private static final String K_FIELD_END = "calendar.field.end";
  private static final String K_FIELD_CALENDAR = "calendar.field.calendar";
  private static final String K_FIELD_SERVER_FILTER = "calendar.field.serverFilter";
  private static final String K_SERVER_ALL = "calendar.field.serverFilter.all";
  private static final String K_ACTION_SAVE = "calendar.action.save";
  private static final String K_ACTION_DELETE = "calendar.action.delete";
  private static final String K_ACTION_CANCEL = "calendar.action.cancel";

  /**
   * Back-compat — no subscriptions, no calendar selector. Save
   * callback receives the entry unchanged.
   */
  public EventEditorDialog(Entry entry, boolean isNew,
                           Consumer<Entry> onSave,
                           BiConsumer<Entry, Runnable> onDeleteRequest) {
    this(entry, isNew, List.of(), List.of(), null,
        (e, target) -> onSave.accept(e), onDeleteRequest);
  }

  /** Back-compat from the previous iteration — no server context. */
  public EventEditorDialog(Entry entry, boolean isNew,
                           List<CalendarSubscription> subscriptions,
                           URI initialTarget,
                           BiConsumer<Entry, URI> onSave,
                           BiConsumer<Entry, Runnable> onDeleteRequest) {
    this(entry, isNew, subscriptions, List.of(), initialTarget,
        onSave, onDeleteRequest);
  }

  public EventEditorDialog(Entry entry, boolean isNew,
                           List<CalendarSubscription> subscriptions,
                           List<CalDavServerConnection> servers,
                           URI initialTarget,
                           BiConsumer<Entry, URI> onSave,
                           BiConsumer<Entry, Runnable> onDeleteRequest) {
    Dialog dialog = getContent();
    dialog.setHeaderTitle(isNew
        ? tr(K_DIALOG_TITLE_NEW, "New event")
        : tr(K_DIALOG_TITLE_EDIT, "Edit event"));

    Map<String, String> serverNameById = new HashMap<>();
    for (CalDavServerConnection s : servers) {
      serverNameById.put(s.id(), s.displayName());
    }

    Select<String> serverFilter = null;
    Select<CalendarSubscription> calendarSelect = null;
    if (isNew && !subscriptions.isEmpty()) {
      calendarSelect = buildCalendarSelect(subscriptions, initialTarget,
          serverNameById);

      Set<String> serverIds = new LinkedHashSet<>();
      for (CalendarSubscription s : subscriptions) {
        if (s.serverId() != null) serverIds.add(s.serverId());
      }
      if (serverIds.size() >= 2) {
        serverFilter = buildServerFilter(serverIds, serverNameById,
            subscriptions, calendarSelect);
      }
    }

    TextField title = new TextField(tr(K_FIELD_TITLE, "Title"));
    title.setValue(entry.getTitle() == null ? "" : entry.getTitle());
    title.setWidthFull();

    TextArea description = new TextArea(tr(K_FIELD_DESCRIPTION, "Description"));
    description.setValue(entry.getDescription() == null ? "" : entry.getDescription());
    description.setWidthFull();

    TextField location = new TextField(tr(K_FIELD_LOCATION, "Location"));
    String storedLocation = entry.getCustomProperty(EntryMapper.CUSTOM_LOCATION);
    location.setValue(storedLocation == null ? "" : storedLocation);
    location.setWidthFull();

    TextField url = new TextField(tr(K_FIELD_URL, "URL"));
    String storedUrl = entry.getCustomProperty(EntryMapper.CUSTOM_URL);
    url.setValue(storedUrl == null ? "" : storedUrl);
    url.setWidthFull();

    DateTimePicker start = new DateTimePicker(tr(K_FIELD_START, "Start"));
    start.setValue(entry.getStart());

    DateTimePicker end = new DateTimePicker(tr(K_FIELD_END, "End"));
    end.setValue(entry.getEnd());

    VerticalLayout form = new VerticalLayout();
    form.setPadding(false);
    form.setSpacing(true);
    if (serverFilter != null) form.add(serverFilter);
    if (calendarSelect != null) form.add(calendarSelect);
    form.add(title, description, location, url, start, end);
    dialog.add(form);

    final Select<CalendarSubscription> calendarSelectFinal = calendarSelect;
    Button save = new Button(tr(K_ACTION_SAVE, "Save"), e -> {
      entry.setTitle(title.getValue());
      entry.setDescription(description.getValue());
      entry.setCustomProperty(EntryMapper.CUSTOM_LOCATION, location.getValue());
      entry.setCustomProperty(EntryMapper.CUSTOM_URL, url.getValue());
      if (start.getValue() != null) entry.setStart(start.getValue());
      if (end.getValue() != null) entry.setEnd(end.getValue());
      URI target = calendarSelectFinal != null && calendarSelectFinal.getValue() != null
          ? calendarSelectFinal.getValue().uri()
          : null;
      onSave.accept(entry, target);
      dialog.close();
    });
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    HorizontalLayout actions = new HorizontalLayout(save);
    actions.setAlignItems(FlexComponent.Alignment.CENTER);

    if (!isNew) {
      Button delete = new Button(tr(K_ACTION_DELETE, "Delete"),
          e -> onDeleteRequest.accept(entry, dialog::close));
      delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
      actions.add(delete);
    }

    Button cancel = new Button(tr(K_ACTION_CANCEL, "Cancel"), e -> dialog.close());
    cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    actions.add(cancel);

    dialog.getFooter().add(actions);
  }

  // ── Calendar Select with rich items ────────────────────────────

  private Select<CalendarSubscription> buildCalendarSelect(
      List<CalendarSubscription> subscriptions,
      URI initialTarget,
      Map<String, String> serverNameById) {
    Select<CalendarSubscription> sel = new Select<>();
    sel.setId("event-editor-calendar");
    sel.setLabel(tr(K_FIELD_CALENDAR, "Calendar"));
    sel.setItems(subscriptions);
    sel.setRenderer(new ComponentRenderer<>(sub ->
        renderCalendarItem(sub, serverNameById)));
    sel.setItemLabelGenerator(CalendarSubscription::displayName);
    sel.setWidthFull();
    CalendarSubscription initial = subscriptions.stream()
        .filter(s -> s.uri().equals(initialTarget))
        .findFirst()
        .orElse(subscriptions.get(0));
    sel.setValue(initial);
    return sel;
  }

  private Component renderCalendarItem(CalendarSubscription sub,
                                       Map<String, String> serverNameById) {
    Div swatch = new Div();
    swatch.setWidth("14px");
    swatch.setHeight("14px");
    swatch.getStyle()
        .set("background", sub.color() == null
            ? "var(--lumo-contrast-30pct)" : sub.color())
        .set("border-radius", "3px")
        .set("border", "1px solid var(--lumo-contrast-20pct)")
        .set("flex-shrink", "0");

    Span name = new Span(sub.displayName());

    HorizontalLayout row = new HorizontalLayout(swatch, name);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);

    String serverName = sub.serverId() == null
        ? "" : serverNameById.getOrDefault(sub.serverId(), "");
    if (!serverName.isEmpty()) {
      Span server = new Span("· " + serverName);
      server.getStyle()
          .set("color", "var(--lumo-secondary-text-color)")
          .set("font-size", "var(--lumo-font-size-s)");
      row.add(server);
    }
    return row;
  }

  // ── Server filter ──────────────────────────────────────────────

  private Select<String> buildServerFilter(
      Set<String> serverIds,
      Map<String, String> serverNameById,
      List<CalendarSubscription> allSubs,
      Select<CalendarSubscription> calendarSelect) {
    List<String> serverIdsList = new ArrayList<>(serverIds);
    Map<String, String> labelByValue = new HashMap<>();
    labelByValue.put(ALL_SERVERS_SENTINEL, tr(K_SERVER_ALL, "All servers"));
    for (String id : serverIdsList) {
      String name = serverNameById.getOrDefault(id, id);
      labelByValue.put(id, name);
    }
    List<String> values = new ArrayList<>();
    values.add(ALL_SERVERS_SENTINEL);
    values.addAll(serverIdsList);

    Select<String> filter = new Select<>();
    filter.setId("event-editor-server-filter");
    filter.setLabel(tr(K_FIELD_SERVER_FILTER, "Server"));
    filter.setItems(values);
    filter.setItemLabelGenerator(labelByValue::get);
    filter.setValue(ALL_SERVERS_SENTINEL);
    filter.setWidthFull();
    filter.addValueChangeListener(e -> {
      String picked = e.getValue();
      List<CalendarSubscription> filtered = ALL_SERVERS_SENTINEL.equals(picked)
          ? allSubs
          : allSubs.stream()
              .filter(s -> picked.equals(s.serverId()))
              .toList();
      calendarSelect.setItems(filtered);
      if (!filtered.isEmpty()) {
        calendarSelect.setValue(filtered.getFirst());
      }
    });
    return filter;
  }

  public void open() {
    getContent().open();
  }

  public Dialog dialog() {
    return getContent();
  }
}