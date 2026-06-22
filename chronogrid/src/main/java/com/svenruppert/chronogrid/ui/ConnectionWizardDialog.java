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
import com.svenruppert.chronogrid.client.DiscoveredCalendar;
import com.svenruppert.chronogrid.i18n.CalendarMessages;
import com.svenruppert.chronogrid.service.CalDavProviderPreset;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Planning-Feature #7 Schicht 2 — three-step "Add connection" wizard.
 * Apple-Calendar-style add-account flow: URL → Credentials → pick
 * calendars from discovery.
 *
 * <p><b>Step 1 — Provider + URL.</b> Quick-connect row with a button
 * per {@link CalDavProviderPreset} (clicking one pre-fills the URL +
 * sets the provider-specific hint shown in Step 2), plus a free-form
 * URL field for everything else.
 *
 * <p><b>Step 2 — Credentials.</b> Username + password (HTTP Basic).
 * When the user came from the iCloud preset the iCloud-specific
 * hint ("you must use an app-specific password") replaces the
 * generic one. Test-Connection button runs a quick probe.
 *
 * <p><b>Step 3 — Calendar picking from discovery.</b> Runs
 * {@code CalDavDiscovery} when the step is entered. If ≤5 calendars
 * are returned: all pre-selected, the user just confirms. If >5: a
 * scrollable list, all unselected, with bulk "Select all" /
 * "Select none" buttons so a 30-calendar Nextcloud workspace doesn't
 * flood the grid by default.
 *
 * <p>The dialog is pure UI — it does no HTTP itself. The host
 * (ChronoGrid) injects {@code discoveryFn} (URI/user/pass →
 * calendars), {@code probeFn} (URI/user/pass → success boolean), and
 * {@code onComplete} (final WizardResult → apply to the state
 * store). That keeps the dialog test-friendly and the wizard logic
 * independent of any specific {@code CalendarService} wiring.
 */
public final class ConnectionWizardDialog
    extends Composite<Dialog> implements HasLogger {

  private static final long serialVersionUID = 1L;

  public static final int HYBRID_DEFAULT_VISIBILITY_THRESHOLD = 5;

  // ── i18n keys ─────────────────────────────────────────────────────
  private static final String K_TITLE = "calendar.wizard.title";
  private static final String K_STEP1 = "calendar.wizard.step1";
  private static final String K_STEP2 = "calendar.wizard.step2";
  private static final String K_STEP3 = "calendar.wizard.step3";
  private static final String K_PRESETS_LABEL = "calendar.wizard.presetsLabel";
  private static final String K_URI = "calendar.wizard.uri";
  private static final String K_USER = "calendar.wizard.user";
  private static final String K_PASS = "calendar.wizard.pass";
  private static final String K_TEST_CONNECTION = "calendar.wizard.testConnection";
  private static final String K_TEST_OK = "calendar.wizard.testOk";
  private static final String K_TEST_FAIL = "calendar.wizard.testFail";
  private static final String K_HINT_GENERIC = "calendar.wizard.hint.generic";
  private static final String K_BACK = "calendar.wizard.back";
  private static final String K_NEXT = "calendar.wizard.next";
  private static final String K_FINISH = "calendar.wizard.finish";
  private static final String K_CANCEL = "calendar.wizard.cancel";
  private static final String K_DISCOVERY_BUSY = "calendar.wizard.discovery.busy";
  private static final String K_DISCOVERY_EMPTY = "calendar.wizard.discovery.empty";
  private static final String K_DISCOVERY_FOUND_FEW =
      "calendar.wizard.discovery.foundFew";
  private static final String K_DISCOVERY_FOUND_MANY =
      "calendar.wizard.discovery.foundMany";
  private static final String K_BULK_ON = "calendar.wizard.bulk.on";
  private static final String K_BULK_OFF = "calendar.wizard.bulk.off";
  private static final String K_PROVIDER_PREFIX = "calendar.settings.provider.";

  // ── injected callbacks ────────────────────────────────────────────
  private final CalendarMessages messages;
  private final BiFunction<URI, String[], List<DiscoveredCalendar>> discoveryFn;
  private final Function<String[], Boolean> probeFn;
  private final Consumer<Result> onComplete;
  private final BiConsumer<Boolean, String> notify;

  // ── form fields ───────────────────────────────────────────────────
  private final TextField uriField;
  private final TextField usernameField;
  private final PasswordField passwordField;
  private final CheckboxGroup<DiscoveredCalendar> picker;
  private final Span providerHint;
  private final Span step1Header;
  private final Span step2Header;
  private final Span step3Header;
  private final Span discoveryStatus;
  private final HorizontalLayout bulkRow;
  private final Button backButton;
  private final Button nextButton;
  private final VerticalLayout step1Pane;
  private final VerticalLayout step2Pane;
  private final VerticalLayout step3Pane;

  // ── transient state ───────────────────────────────────────────────
  private int currentStep = 1;
  // Preset reference is transient — Vaadin's Composite is Serializable
  // via the session, and CalDavProviderPreset is not (icon enum carries
  // unsupported state). The preset is only read inside the open dialog
  // for the hint swap; never needs to survive a session-passivation.
  private transient CalDavProviderPreset selectedPreset;

  /**
   * The captured user input the host needs to upsert a new server
   * connection. {@code username} / {@code password} may be empty
   * strings (anonymous Basic-auth) but are never null.
   * {@code selectedCalendars} is defensively copied on construction
   * and exposed read-only.
   */
  public record Result(URI uri, String username, String password,
                       Set<DiscoveredCalendar> selectedCalendars) {
    public Result {
      selectedCalendars = selectedCalendars == null
          ? Set.of()
          : Set.copyOf(selectedCalendars);
    }
  }

  /**
   * @param messages         host-i18n adapter
   * @param discoveryFn      (URI, [user, pass]) → list of discovered
   *                         calendars; throws RuntimeException on
   *                         transport / auth errors. The 2-element
   *                         credential array keeps the signature
   *                         single-arg for {@code BiFunction}.
   * @param probeFn          ([uri, user, pass]) → true if the
   *                         connection works; the wizard reports
   *                         success/failure via {@code notify}. Same
   *                         array shape as discoveryFn.
   * @param onComplete       called when the user confirms Step 3
   * @param notify           (isError, message) — host-side toast hook
   */
  public ConnectionWizardDialog(
      CalendarMessages messages,
      BiFunction<URI, String[], List<DiscoveredCalendar>> discoveryFn,
      Function<String[], Boolean> probeFn,
      Consumer<Result> onComplete,
      BiConsumer<Boolean, String> notify) {
    this.messages = messages;
    this.discoveryFn = discoveryFn;
    this.probeFn = probeFn;
    this.onComplete = onComplete;
    this.notify = notify;

    Dialog dialog = getContent();
    dialog.setId("calendar-wizard");
    dialog.setHeaderTitle(messages.tr(K_TITLE, "Add a CalDAV connection"));
    dialog.setWidth("540px");

    // ── step indicator ────────────────────────────────────────────
    step1Header = stepBadge("1");
    step2Header = stepBadge("2");
    step3Header = stepBadge("3");
    HorizontalLayout steps = new HorizontalLayout(
        labelledBadge(step1Header, messages.tr(K_STEP1, "Provider + URL")),
        labelledBadge(step2Header, messages.tr(K_STEP2, "Credentials")),
        labelledBadge(step3Header, messages.tr(K_STEP3, "Calendars")));
    steps.setAlignItems(FlexComponent.Alignment.CENTER);
    steps.setSpacing(true);
    steps.getStyle().set("padding-bottom", "var(--lumo-space-s)");

    // ── step 1 — provider + URL ───────────────────────────────────
    uriField = new TextField(messages.tr(K_URI, "Collection URI"));
    uriField.setId("calendar-wizard-uri");
    uriField.setWidthFull();
    uriField.setPlaceholder("https://caldav.example.com/calendars/personal/");
    HorizontalLayout presets = buildPresetRow();
    step1Pane = new VerticalLayout(presets, uriField);
    step1Pane.setPadding(false);
    step1Pane.setSpacing(true);

    // ── step 2 — credentials ──────────────────────────────────────
    usernameField = new TextField(messages.tr(K_USER, "Username"));
    usernameField.setId("calendar-wizard-user");
    usernameField.setWidthFull();
    passwordField = new PasswordField(messages.tr(K_PASS, "Password"));
    passwordField.setId("calendar-wizard-pass");
    passwordField.setWidthFull();
    providerHint = new Span(messages.tr(K_HINT_GENERIC,
        "Credentials are sent as HTTP Basic auth on every request."));
    providerHint.setId("calendar-wizard-hint");
    providerHint.addClassName("chronogrid-secondary-text");
    Button testButton = new Button(
        messages.tr(K_TEST_CONNECTION, "Test connection"),
        VaadinIcon.CONNECT.create(),
        e -> runProbe());
    testButton.setId("calendar-wizard-test");
    testButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    step2Pane = new VerticalLayout(usernameField, passwordField,
        providerHint, testButton);
    step2Pane.setPadding(false);
    step2Pane.setSpacing(true);
    step2Pane.setVisible(false);

    // ── step 3 — calendars ────────────────────────────────────────
    discoveryStatus = new Span("");
    discoveryStatus.setId("calendar-wizard-discovery-status");
    discoveryStatus.addClassName("chronogrid-secondary-text");
    picker = new CheckboxGroup<>();
    picker.setId("calendar-wizard-picker");
    picker.setItemLabelGenerator(DiscoveredCalendar::displayName);
    picker.setWidthFull();
    picker.setVisible(false);

    Button bulkOn = new Button(messages.tr(K_BULK_ON, "Select all"),
        e -> {
          if (picker.getListDataView() != null) {
            picker.setValue(
                picker.getListDataView().getItems().collect(
                    java.util.stream.Collectors.toSet()));
          }
        });
    bulkOn.setId("calendar-wizard-bulk-on");
    bulkOn.addThemeVariants(ButtonVariant.LUMO_TERTIARY,
        ButtonVariant.LUMO_SMALL);
    Button bulkOff = new Button(messages.tr(K_BULK_OFF, "Select none"),
        e -> picker.setValue(Set.of()));
    bulkOff.setId("calendar-wizard-bulk-off");
    bulkOff.addThemeVariants(ButtonVariant.LUMO_TERTIARY,
        ButtonVariant.LUMO_SMALL);
    bulkRow = new HorizontalLayout(bulkOn, bulkOff);
    bulkRow.setSpacing(true);
    bulkRow.setPadding(false);
    bulkRow.setVisible(false);

    step3Pane = new VerticalLayout(discoveryStatus, bulkRow, picker);
    step3Pane.setPadding(false);
    step3Pane.setSpacing(true);
    step3Pane.setVisible(false);

    // ── footer (back / next / cancel) ─────────────────────────────
    backButton = new Button(messages.tr(K_BACK, "Back"), e -> moveToStep(currentStep - 1));
    backButton.setId("calendar-wizard-back");
    backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    backButton.setVisible(false);

    nextButton = new Button(messages.tr(K_NEXT, "Next"), e -> moveToStep(currentStep + 1));
    nextButton.setId("calendar-wizard-next");
    nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancel = new Button(messages.tr(K_CANCEL, "Cancel"),
        e -> getContent().close());
    cancel.setId("calendar-wizard-cancel");
    cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    HorizontalLayout footer = new HorizontalLayout(backButton, cancel, nextButton);
    footer.setAlignItems(FlexComponent.Alignment.CENTER);
    dialog.getFooter().add(footer);

    // ── assemble ──────────────────────────────────────────────────
    VerticalLayout body = new VerticalLayout(steps, step1Pane, step2Pane, step3Pane);
    body.setPadding(false);
    body.setSpacing(true);
    dialog.add(body);

    refreshStepBadges();
  }

  // ── public API ────────────────────────────────────────────────────

  public void open() {
    getContent().open();
  }

  /** Test seam: read what step the wizard is currently on (1, 2, 3). */
  public int currentStep() {
    return currentStep;
  }

  // ── presets ───────────────────────────────────────────────────────

  private HorizontalLayout buildPresetRow() {
    Span label = new Span(messages.tr(K_PRESETS_LABEL, "Quick connect:"));
    label.addClassName("chronogrid-secondary-text");
    HorizontalLayout row = new HorizontalLayout(label);
    row.setAlignItems(FlexComponent.Alignment.CENTER);
    row.setSpacing(true);
    for (CalDavProviderPreset preset : CalDavProviderPreset.DEFAULTS) {
      Button btn = new Button(messages.tr(
          K_PROVIDER_PREFIX + preset.id() + ".label", preset.label()),
          preset.icon().create(),
          e -> {
            selectedPreset = preset;
            uriField.setValue(preset.entryUri());
            providerHint.setText(messages.tr(
                K_PROVIDER_PREFIX + preset.id() + ".hint", preset.hint()));
          });
      btn.setId("calendar-wizard-preset-" + preset.id());
      btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      row.add(btn);
    }
    return row;
  }

  // ── step navigation ───────────────────────────────────────────────

  private void moveToStep(int target) {
    if (target < 1) target = 1;
    if (target > 3) {
      finish();
      return;
    }
    if (target == 2 && !validateStep1()) return;
    if (target == 3) {
      if (!validateStep2()) return;
      // Discovery is async (HTTP) but the dialog stays open and we
      // simply update the status label while picker.setVisible(false).
      runDiscovery();
    }
    currentStep = target;
    step1Pane.setVisible(currentStep == 1);
    step2Pane.setVisible(currentStep == 2);
    step3Pane.setVisible(currentStep == 3);
    backButton.setVisible(currentStep > 1);
    nextButton.setText(currentStep == 3
        ? messages.tr(K_FINISH, "Add connection")
        : messages.tr(K_NEXT, "Next"));
    refreshStepBadges();
  }

  private boolean validateStep1() {
    String raw = uriField.getValue();
    if (raw == null || raw.isBlank()) {
      uriField.setInvalid(true);
      uriField.setErrorMessage("URI required");
      return false;
    }
    try {
      URI.create(raw.trim());
      uriField.setInvalid(false);
      return true;
    } catch (IllegalArgumentException ex) {
      uriField.setInvalid(true);
      uriField.setErrorMessage("Invalid URI");
      return false;
    }
  }

  private boolean validateStep2() {
    // Credentials may be blank (anonymous); only validate the URI
    // again in case the user edited it in step 1 with an empty
    // value but somehow got through.
    return validateStep1();
  }

  private void refreshStepBadges() {
    badge(step1Header, currentStep == 1, currentStep > 1);
    badge(step2Header, currentStep == 2, currentStep > 2);
    badge(step3Header, currentStep == 3, false);
  }

  // ── discovery + probe + finish ────────────────────────────────────

  private void runDiscovery() {
    discoveryStatus.setText(messages.tr(K_DISCOVERY_BUSY, "Discovering…"));
    picker.setVisible(false);
    bulkRow.setVisible(false);
    URI startUri;
    try {
      startUri = URI.create(uriField.getValue().trim());
    } catch (IllegalArgumentException ex) {
      discoveryStatus.setText(messages.tr(K_DISCOVERY_EMPTY,
          "No calendars found at this URL."));
      return;
    }
    List<DiscoveredCalendar> found;
    try {
      found = discoveryFn.apply(startUri,
          new String[] { usernameField.getValue(), passwordField.getValue() });
    } catch (RuntimeException ex) {
      logger().info("Wizard discovery against {} failed: {}", startUri, ex.toString());
      discoveryStatus.setText(messages.tr(K_DISCOVERY_EMPTY,
          "No calendars found at this URL."));
      notify.accept(true, ex.toString());
      return;
    }
    if (found == null || found.isEmpty()) {
      discoveryStatus.setText(messages.tr(K_DISCOVERY_EMPTY,
          "No calendars found at this URL."));
      return;
    }
    picker.setItems(found);
    // Hybrid: ≤5 → pre-select all, no bulk row. >5 → pre-select
    // none, show bulk row so the user can flip "all on" in one click.
    if (found.size() <= HYBRID_DEFAULT_VISIBILITY_THRESHOLD) {
      picker.setValue(java.util.Set.copyOf(found));
      bulkRow.setVisible(false);
      discoveryStatus.setText(messages.tr(K_DISCOVERY_FOUND_FEW,
          "Found {0} calendar(s) — all pre-selected.",
          String.valueOf(found.size())));
    } else {
      picker.setValue(Set.of());
      bulkRow.setVisible(true);
      discoveryStatus.setText(messages.tr(K_DISCOVERY_FOUND_MANY,
          "Found {0} calendars — pick the ones you want to see.",
          String.valueOf(found.size())));
    }
    picker.setVisible(true);
  }

  private void runProbe() {
    String raw = uriField.getValue();
    if (raw == null || raw.isBlank()) return;
    boolean ok;
    try {
      ok = probeFn.apply(new String[] {
          raw, usernameField.getValue(), passwordField.getValue()
      });
    } catch (RuntimeException ex) {
      notify.accept(true, messages.tr(K_TEST_FAIL,
          "Connection failed: {0}", ex.toString()));
      return;
    }
    if (ok) {
      notify.accept(false, messages.tr(K_TEST_OK, "Connection OK."));
    } else {
      notify.accept(true, messages.tr(K_TEST_FAIL,
          "Connection failed: {0}", "see toast / server log"));
    }
  }

  private void finish() {
    URI parsed;
    try {
      parsed = URI.create(uriField.getValue().trim());
    } catch (IllegalArgumentException ex) {
      uriField.setInvalid(true);
      return;
    }
    onComplete.accept(new Result(parsed,
        usernameField.getValue() == null ? "" : usernameField.getValue(),
        passwordField.getValue() == null ? "" : passwordField.getValue(),
        picker.getValue() == null ? Set.of() : picker.getValue()));
    getContent().close();
  }

  // ── small visual helpers ──────────────────────────────────────────

  private static Span stepBadge(String text) {
    Span s = new Span(text);
    s.getElement().setAttribute("theme", "badge contrast");
    s.getStyle().set("width", "1.5rem").set("text-align", "center");
    return s;
  }

  private static Div labelledBadge(Span badge, String label) {
    Span name = new Span(label);
    name.getStyle().set("font-weight", "500");
    Div wrap = new Div(badge, name);
    wrap.getStyle()
        .set("display", "inline-flex")
        .set("align-items", "center")
        .set("gap", "var(--lumo-space-xs)");
    return wrap;
  }

  private static void badge(Span badge, boolean current, boolean done) {
    String theme = current ? "badge primary"
        : done ? "badge success" : "badge contrast";
    badge.getElement().setAttribute("theme", theme);
  }
}
