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
import com.svenruppert.chronogrid.mapping.EntryMapper;
import com.svenruppert.chronogrid.service.CalDavServerConnection;
import com.svenruppert.chronogrid.service.CalendarSubscription;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Element;
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
    extends Composite<Dialog> implements HasLogger {

  private final CalendarMessages messages;

  private static final String ALL_SERVERS_SENTINEL = "__all__";

  private static final String K_DIALOG_TITLE_NEW = "calendar.dialog.title.new";
  private static final String K_DIALOG_TITLE_EDIT = "calendar.dialog.title.edit";
  private static final String K_FIELD_TITLE = "calendar.field.title";
  private static final String K_FIELD_DESCRIPTION = "calendar.field.description";
  private static final String K_FIELD_LOCATION = "calendar.field.location";
  private static final String K_FIELD_URL = "calendar.field.url";
  private static final String K_FIELD_COLOR = "calendar.field.color";
  private static final String K_FIELD_COLOR_USE = "calendar.field.color.use";
  private static final String K_FIELD_COLOR_RESET = "calendar.field.color.reset";
  private static final String K_FIELD_COLOR_HINT = "calendar.field.color.hint";
  private static final String K_FIELD_TAGS = "calendar.field.tags";
  private static final String K_FIELD_TAGS_HINT = "calendar.field.tags.hint";
  private static final String K_FIELD_START = "calendar.field.start";
  private static final String K_FIELD_END = "calendar.field.end";
  private static final String K_FIELD_TIMEZONE = "calendar.field.timezone";
  private static final String K_FIELD_TIMEZONE_HINT = "calendar.field.timezone.hint";
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
  @Deprecated
  public EventEditorDialog(Entry entry, boolean isNew,
                           Consumer<Entry> onSave,
                           BiConsumer<Entry, Runnable> onDeleteRequest) {
    this(CalendarMessages.fallbackOnly(), entry, isNew, List.of(), List.of(), null,
        (e, target) -> onSave.accept(e), onDeleteRequest);
  }

  /** Back-compat from the previous iteration — no server context. */
  @Deprecated
  public EventEditorDialog(Entry entry, boolean isNew,
                           List<CalendarSubscription> subscriptions,
                           URI initialTarget,
                           BiConsumer<Entry, URI> onSave,
                           BiConsumer<Entry, Runnable> onDeleteRequest) {
    this(CalendarMessages.fallbackOnly(), entry, isNew, subscriptions, List.of(),
        initialTarget, onSave, onDeleteRequest);
  }

  /** Back-compat — no CalendarMessages. */
  @Deprecated
  public EventEditorDialog(Entry entry, boolean isNew,
                           List<CalendarSubscription> subscriptions,
                           List<CalDavServerConnection> servers,
                           URI initialTarget,
                           BiConsumer<Entry, URI> onSave,
                           BiConsumer<Entry, Runnable> onDeleteRequest) {
    this(CalendarMessages.fallbackOnly(), entry, isNew, subscriptions, servers,
        initialTarget, onSave, onDeleteRequest);
  }

  public EventEditorDialog(CalendarMessages messages,
                           Entry entry, boolean isNew,
                           List<CalendarSubscription> subscriptions,
                           List<CalDavServerConnection> servers,
                           URI initialTarget,
                           BiConsumer<Entry, URI> onSave,
                           BiConsumer<Entry, Runnable> onDeleteRequest) {
    this.messages = messages;
    Dialog dialog = getContent();
    dialog.setHeaderTitle(isNew
        ? messages.tr(K_DIALOG_TITLE_NEW, "New event")
        : messages.tr(K_DIALOG_TITLE_EDIT, "Edit event"));
    logger().info("EventEditorDialog open: mode={} uid={} subscriptions={} servers={}",
        isNew ? "new" : "edit", entry.getId(),
        subscriptions.size(), servers.size());

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

    TextField title = new TextField(messages.tr(K_FIELD_TITLE, "Title"));
    title.setValue(entry.getTitle() == null ? "" : entry.getTitle());
    title.setWidthFull();

    TextArea description = new TextArea(messages.tr(K_FIELD_DESCRIPTION, "Description"));
    description.setValue(entry.getDescription() == null ? "" : entry.getDescription());
    description.setWidthFull();

    TextField location = new TextField(messages.tr(K_FIELD_LOCATION, "Location"));
    String storedLocation = entry.getCustomProperty(EntryMapper.CUSTOM_LOCATION);
    location.setValue(storedLocation == null ? "" : storedLocation);
    location.setWidthFull();

    TextField url = new TextField(messages.tr(K_FIELD_URL, "URL"));
    String storedUrl = entry.getCustomProperty(EntryMapper.CUSTOM_URL);
    url.setValue(storedUrl == null ? "" : storedUrl);
    url.setWidthFull();

    DateTimePicker start = new DateTimePicker(messages.tr(K_FIELD_START, "Start"));
    start.setValue(entry.getStart());

    DateTimePicker end = new DateTimePicker(messages.tr(K_FIELD_END, "End"));
    end.setValue(entry.getEnd());

    // BUG #14: explicit Timezone selector. Cross-timezone sharing
    // breaks when a Berlin user creates an event with UTC-suffixed
    // DTSTART — receivers in other zones see the wrong wall-clock
    // time. The Select<String> persists the chosen ZoneId onto
    // CUSTOM_TZID; EntryMapper.toICalendarText picks it up and
    // emits the right TZID parameter, falling back to the
    // provider's defaultTimezone() if unset.
    com.vaadin.flow.component.select.Select<String> timezoneSelect =
        new com.vaadin.flow.component.select.Select<>();
    timezoneSelect.setLabel(messages.tr(K_FIELD_TIMEZONE, "Timezone"));
    timezoneSelect.setHelperText(messages.tr(K_FIELD_TIMEZONE_HINT,
        "Wallclock time interpretation. Set to your local timezone for "
            + "events scheduled at a specific local time."));
    timezoneSelect.setWidthFull();
    java.util.List<String> zoneIds = java.time.ZoneId.getAvailableZoneIds().stream()
        .sorted()
        .collect(java.util.stream.Collectors.toList());
    timezoneSelect.setItems(zoneIds);
    String storedTzid = entry.getCustomProperty(EntryMapper.CUSTOM_TZID);
    if (storedTzid != null && !storedTzid.isBlank() && zoneIds.contains(storedTzid)) {
      timezoneSelect.setValue(storedTzid);
    } else {
      // Default to system timezone — same default the provider's
      // defaultTimezone() returns for the Generic case. User sees
      // the value populated so it's clear which TZID will be written.
      timezoneSelect.setValue(java.time.ZoneId.systemDefault().getId());
    }

    // Per-entry colour controls. The user can opt in to overriding
    // the calendar's default colour; the calendar colour stays on the
    // entry's border so the multi-calendar provenance is preserved
    // even when fill colours collide.
    String storedEntryColor = entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR);
    Checkbox colourEnabled =
        new Checkbox(messages.tr(K_FIELD_COLOR_USE, "Use custom colour"));
    colourEnabled.setValue(storedEntryColor != null && !storedEntryColor.isBlank());
    Element colourPicker = new Element("input");
    colourPicker.setAttribute("type", "color");
    colourPicker.setAttribute("title", messages.tr(K_FIELD_COLOR, "Colour"));
    String initialColour = storedEntryColor != null && !storedEntryColor.isBlank()
        ? normaliseColor(storedEntryColor) : "#1f77b4";
    colourPicker.setProperty("value", initialColour);
    colourPicker.getClassList().add("chronogrid-color-picker");

    // BUG #1: the HTML5 <input type="color"> does not auto-sync its
    // `value` back to the server. Without an explicit DOM listener,
    // colourPicker.getProperty("value") on Save would return the
    // initial value the server pushed — the user's actual pick
    // never reaches the server. Mirror the same pattern the N-days
    // slider uses (`addEventData("event.target.value")`) so the
    // current colour is captured into a mutable holder on every
    // 'change' / 'input' event; the Save handler reads from there.
    String[] currentColour = { initialColour };
    java.util.function.Consumer<com.vaadin.flow.dom.DomEvent> grabValue = ev -> {
      tools.jackson.databind.JsonNode node =
          ev.getEventData().get("event.target.value");
      if (node == null) return;
      String v = node.asString();
      if (v != null && !v.isBlank()) currentColour[0] = v;
    };
    colourPicker.addEventListener("change", grabValue::accept)
        .addEventData("event.target.value");
    colourPicker.addEventListener("input", grabValue::accept)
        .addEventData("event.target.value");
    Div pickerHost = new Div();
    pickerHost.getElement().appendChild(colourPicker);
    Span colourHint = new Span(messages.tr(K_FIELD_COLOR_HINT,
        "When off, the event takes its colour from its calendar. "
            + "When on, the chosen colour fills the event; the "
            + "calendar's colour stays on the event's border."));
    colourHint.addClassName("chronogrid-secondary-text");
    HorizontalLayout colourRow = new HorizontalLayout(colourEnabled, pickerHost);
    colourRow.setAlignItems(FlexComponent.Alignment.CENTER);
    Div colourBlock = new Div(colourRow, colourHint);
    colourBlock.getStyle().set("width", "100%");

    // Tags input (Feature #3): free-form comma-separated, normalised
    // on save. The tag universe is harvested across the grid; this
    // field is the entry-point for new tags joining the universe.
    TextField tagsField = new TextField(messages.tr(K_FIELD_TAGS, "Tags"));
    java.util.Set<String> initialTags = EntryMapper.readTags(entry);
    tagsField.setValue(String.join(", ", initialTags));
    tagsField.setWidthFull();
    tagsField.setHelperText(messages.tr(K_FIELD_TAGS_HINT,
        "Comma-separated, e.g. work, client-acme, deep-focus"));

    VerticalLayout form = new VerticalLayout();
    form.setPadding(false);
    form.setSpacing(true);
    if (serverFilter != null) form.add(serverFilter);
    if (calendarSelect != null) form.add(calendarSelect);
    form.add(title, description, location, url, start, end, timezoneSelect,
        colourBlock, tagsField);
    dialog.add(form);

    final Select<CalendarSubscription> calendarSelectFinal = calendarSelect;
    Button save = new Button(messages.tr(K_ACTION_SAVE, "Save"), e -> {
      entry.setTitle(title.getValue());
      entry.setDescription(description.getValue());
      entry.setCustomProperty(EntryMapper.CUSTOM_LOCATION, location.getValue());
      entry.setCustomProperty(EntryMapper.CUSTOM_URL, url.getValue());
      if (Boolean.TRUE.equals(colourEnabled.getValue())) {
        String pickedColor = currentColour[0];
        entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR,
            pickedColor == null || pickedColor.isBlank() ? null : pickedColor);
      } else {
        entry.setCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR, null);
      }
      if (start.getValue() != null) entry.setStart(start.getValue());
      if (end.getValue() != null) entry.setEnd(end.getValue());
      // BUG #14: persist the user-picked timezone so the writer
      // emits the correct TZID parameter. Empty / blank input clears.
      String selectedTz = timezoneSelect.getValue();
      if (selectedTz != null && !selectedTz.isBlank()) {
        entry.setCustomProperty(EntryMapper.CUSTOM_TZID, selectedTz);
      }
      // BUG #11: derive isAllDay from the actual datetime values
      // rather than trusting the entry's pre-edit state. The
      // dialog's DateTimePickers always carry a time component, so
      // if either Start or End has a non-midnight time the user
      // means a timed event. Both midnight collapses to an all-day
      // event (which is also the semantics if the user dragged an
      // all-day cell in the calendar). Catches the Stefan-Entry
      // default-ambiguity that bit BUG #11 plus any edit path
      // where the user switches between all-day and timed.
      java.time.LocalDateTime startVal = start.getValue();
      java.time.LocalDateTime endVal = end.getValue();
      if (startVal != null && endVal != null) {
        boolean bothMidnight = startVal.toLocalTime()
            .equals(java.time.LocalTime.MIDNIGHT)
            && endVal.toLocalTime()
            .equals(java.time.LocalTime.MIDNIGHT);
        entry.setAllDay(bothMidnight);
      }
      EntryMapper.writeTags(entry, splitTags(tagsField.getValue()));
      URI target = calendarSelectFinal != null && calendarSelectFinal.getValue() != null
          ? calendarSelectFinal.getValue().uri()
          : null;
      logger().info("EventEditorDialog Save: uid={} title=\"{}\" target={}",
          entry.getId(), entry.getTitle(), target);
      onSave.accept(entry, target);
      dialog.close();
    });
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    HorizontalLayout actions = new HorizontalLayout(save);
    actions.setAlignItems(FlexComponent.Alignment.CENTER);

    if (!isNew) {
      Button delete = new Button(messages.tr(K_ACTION_DELETE, "Delete"), e -> {
        logger().info("EventEditorDialog Delete requested: uid={} title=\"{}\"",
            entry.getId(), entry.getTitle());
        onDeleteRequest.accept(entry, dialog::close);
      });
      delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
      actions.add(delete);
    }

    Button cancel = new Button(messages.tr(K_ACTION_CANCEL, "Cancel"), e -> dialog.close());
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
    sel.setLabel(messages.tr(K_FIELD_CALENDAR, "Calendar"));
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
    swatch.addClassName("chronogrid-swatch");
    if (sub.color() != null) {
      // Data-driven per-row colour; static rules live in
      // styles/chronogrid.css → .calendar-swatch.
      swatch.getStyle().set("--swatch-color", sub.color());
    }

    Span name = new Span(sub.displayName());

    HorizontalLayout row = new HorizontalLayout(swatch, name);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);

    String serverName = sub.serverId() == null
        ? "" : serverNameById.getOrDefault(sub.serverId(), "");
    if (!serverName.isEmpty()) {
      Span server = new Span("· " + serverName);
      server.addClassName("chronogrid-secondary-text");
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
    labelByValue.put(ALL_SERVERS_SENTINEL, messages.tr(K_SERVER_ALL, "All servers"));
    for (String id : serverIdsList) {
      String name = serverNameById.getOrDefault(id, id);
      labelByValue.put(id, name);
    }
    List<String> values = new ArrayList<>();
    values.add(ALL_SERVERS_SENTINEL);
    values.addAll(serverIdsList);

    Select<String> filter = new Select<>();
    filter.setId("event-editor-server-filter");
    filter.setLabel(messages.tr(K_FIELD_SERVER_FILTER, "Server"));
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

  /**
   * Coerce a stored colour value to the 7-char {@code "#rrggbb"} shape
   * the HTML5 {@code <input type="color">} accepts. CalDAV servers can
   * emit RFC 7986 {@code COLOR} as a CSS3 colour name ({@code "azure"})
   * or as the 9-char {@code "#rrggbbaa"} variant; both need trimming
   * before they're round-trippable through the picker.
   */
  private static String normaliseColor(String raw) {
    if (raw == null || raw.isBlank()) return "#1f77b4";
    String trimmed = raw.trim();
    if (trimmed.startsWith("#") && trimmed.length() == 9) {
      return trimmed.substring(0, 7);
    }
    if (trimmed.startsWith("#") && trimmed.length() == 7) {
      return trimmed;
    }
    return "#1f77b4";
  }

  /**
   * Splits a comma-separated tag input into an ordered set. The
   * underlying {@link EntryMapper#writeTags} normalises (trim +
   * lower-case) and drops blanks, so this method does not have to
   * defensively clean up the values.
   */
  private static java.util.Set<String> splitTags(String raw) {
    if (raw == null || raw.isBlank()) return java.util.Set.of();
    java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
    for (String part : raw.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) out.add(trimmed);
    }
    return out;
  }
}