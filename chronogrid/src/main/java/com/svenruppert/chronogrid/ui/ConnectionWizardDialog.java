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
  // Planning-Feature #9 Schicht 4 — Google OAuth keys.
  private static final String K_GOOGLE_CLIENT_ID = "calendar.wizard.google.clientId";
  private static final String K_GOOGLE_CLIENT_SECRET = "calendar.wizard.google.clientSecret";
  private static final String K_GOOGLE_SIGN_IN = "calendar.wizard.google.signIn";
  private static final String K_GOOGLE_HINT = "calendar.wizard.google.hint";
  private static final String K_GOOGLE_AWAITING = "calendar.wizard.google.awaiting";
  private static final String K_GOOGLE_AUTHORIZED = "calendar.wizard.google.authorized";
  private static final String K_GOOGLE_ERROR = "calendar.wizard.google.error";
  private static final String K_GOOGLE_DEFAULT_CALENDAR = "calendar.wizard.google.defaultCalendar";

  private static final String GOOGLE_AUTH_BASE_URL =
      "https://accounts.google.com/o/oauth2/v2/auth";
  private static final String GOOGLE_CALENDAR_SCOPE =
      "https://www.googleapis.com/auth/calendar";

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
   * Planning-Feature #9 Schicht 4 — Google OAuth credentials filled in
   * by the {@code OAuthCallbackView} once the user has completed the
   * sign-in flow in a separate tab. Non-null means "Step 2 done";
   * the wizard then unlocks the "Next" button.
   */
  private transient com.svenruppert.chronogrid.auth.GoogleOAuthCredentials oauthResult;
  /**
   * Single {@link java.security.SecureRandom} instance shared by
   * every PKCE-verifier generation — instantiating one per call
   * trips SpotBugs' {@code DMI_RANDOM_USED_ONLY_ONCE}.
   */
  private static final java.security.SecureRandom PKCE_RNG =
      new java.security.SecureRandom();
  private final TextField googleClientIdField =
      new TextField("Google OAuth Client ID");
  private final PasswordField googleClientSecretField =
      new PasswordField("Google OAuth Client Secret");
  private final Span googleStatus = new Span();
  private final Button googleSignInButton = new Button();

  /**
   * The captured user input the host needs to upsert a new server
   * connection. Three mutually-exclusive auth shapes:
   *
   * <ul>
   *   <li><strong>Anonymous</strong> — {@code username} blank,
   *       {@code oauth} null.</li>
   *   <li><strong>Basic-auth</strong> — {@code username} +
   *       {@code password} populated, {@code oauth} null.</li>
   *   <li><strong>OAuth</strong> (Planning #9 Schicht 4) —
   *       {@code oauth} non-null with the credentials Google
   *       returned via the callback flow, {@code username} +
   *       {@code password} blank.</li>
   * </ul>
   *
   * <p>{@code selectedCalendars} is defensively copied on
   * construction and exposed read-only.
   */
  public record Result(URI uri, String username, String password,
                       Set<DiscoveredCalendar> selectedCalendars,
                       com.svenruppert.chronogrid.auth.GoogleOAuthCredentials oauth) {
    public Result {
      selectedCalendars = selectedCalendars == null
          ? Set.of()
          : Set.copyOf(selectedCalendars);
    }

    /** Backward-compatible Basic-Auth constructor (no OAuth). */
    public Result(URI uri, String username, String password,
                  Set<DiscoveredCalendar> selectedCalendars) {
      this(uri, username, password, selectedCalendars, null);
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

    // ── Planning-Feature #9 Schicht 4 — Google OAuth fields ──────
    googleClientIdField.setId("calendar-wizard-google-client-id");
    googleClientIdField.setLabel(messages.tr(K_GOOGLE_CLIENT_ID,
        "Google OAuth Client ID"));
    googleClientIdField.setWidthFull();
    String envClientId = System.getenv("CHRONOGRID_GOOGLE_CLIENT_ID");
    if (envClientId != null && !envClientId.isBlank()) {
      googleClientIdField.setValue(envClientId);
    }
    googleClientSecretField.setId("calendar-wizard-google-client-secret");
    googleClientSecretField.setLabel(messages.tr(K_GOOGLE_CLIENT_SECRET,
        "Google OAuth Client Secret"));
    googleClientSecretField.setWidthFull();
    String envClientSecret = System.getenv("CHRONOGRID_GOOGLE_CLIENT_SECRET");
    if (envClientSecret != null && !envClientSecret.isBlank()) {
      googleClientSecretField.setValue(envClientSecret);
    }
    googleStatus.setId("calendar-wizard-google-status");
    googleStatus.addClassName("chronogrid-secondary-text");
    googleSignInButton.setId("calendar-wizard-google-sign-in");
    googleSignInButton.setText(messages.tr(K_GOOGLE_SIGN_IN, "Sign in with Google"));
    googleSignInButton.setIcon(VaadinIcon.SIGN_IN.create());
    googleSignInButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    googleSignInButton.addClickListener(e -> startGoogleSignIn());

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
    if (target == 2) populateStep2();
    if (target == 3) {
      if (!validateStep2()) return;
      // Discovery is async (HTTP) but the dialog stays open and we
      // simply update the status label while picker.setVisible(false).
      if (isGoogle()) {
        // Planning-Feature #9: Google's CalDAV per-calendar discovery
        // is non-trivial; for v1 we present a single placeholder
        // entry so the wizard can finish. The user can then run
        // Re-discover from the Manager-Dialog to enumerate their
        // actual calendars via OAuth.
        useGooglePlaceholderDiscovery();
      } else {
        runDiscovery();
      }
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

  /**
   * Planning-Feature #9 Schicht 4 — swap Step 2's content based on
   * the selected preset. Google needs Client ID / Secret / Sign-in
   * button; everything else needs username / password / test
   * connection.
   */
  private void populateStep2() {
    step2Pane.removeAll();
    if (isGoogle()) {
      Span hint = new Span(messages.tr(K_GOOGLE_HINT,
          "Paste your Google OAuth Client ID + Secret from the Google "
              + "Cloud Console (or set the CHRONOGRID_GOOGLE_CLIENT_ID + "
              + "CHRONOGRID_GOOGLE_CLIENT_SECRET environment variables to "
              + "pre-fill them). Clicking Sign in opens accounts.google.com "
              + "in a new tab; once you grant the Calendar scope, the "
              + "OAuth callback returns here and unlocks Next."));
      hint.addClassName("chronogrid-secondary-text");
      step2Pane.add(googleClientIdField, googleClientSecretField, hint,
          googleSignInButton, googleStatus);
      googleStatus.setText(oauthResult == null
          ? messages.tr(K_GOOGLE_AWAITING, "Awaiting Google sign-in…")
          : messages.tr(K_GOOGLE_AUTHORIZED,
              "✓ Signed in. Click Next to choose calendars."));
    } else {
      step2Pane.add(usernameField, passwordField, providerHint);
      Button testButton = new Button(
          messages.tr(K_TEST_CONNECTION, "Test connection"),
          VaadinIcon.CONNECT.create(),
          e -> runProbe());
      testButton.setId("calendar-wizard-test");
      testButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      step2Pane.add(testButton);
    }
  }

  private boolean isGoogle() {
    return selectedPreset != null && "google".equals(selectedPreset.id());
  }

  /**
   * Planning-Feature #9 Schicht 4 — kicks off the OAuth flow.
   * Generates state + PKCE verifier, registers the flow in the
   * {@link com.svenruppert.chronogrid.auth.OAuthFlowRegistry},
   * opens Google's authorization URL in a new browser tab.
   */
  private void startGoogleSignIn() {
    String clientId = googleClientIdField.getValue();
    String clientSecret = googleClientSecretField.getValue();
    if (clientId == null || clientId.isBlank()
        || clientSecret == null || clientSecret.isBlank()) {
      notify.accept(true, messages.tr(K_GOOGLE_ERROR,
          "Google OAuth needs both Client ID and Client Secret."));
      return;
    }

    com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
    if (ui == null) return;

    // Read window.location.origin from the browser, then build the
    // authorization URL with that as the redirect base.
    ui.getPage().executeJs("return window.location.origin")
        .then(String.class, origin -> launchGoogleAuth(
            origin, clientId, clientSecret, ui));
  }

  private void launchGoogleAuth(String origin, String clientId,
                                String clientSecret,
                                com.vaadin.flow.component.UI originatingUi) {
    String redirectUri = origin + "/oauth/callback/google";
    String state = java.util.UUID.randomUUID().toString();
    String codeVerifier = randomCodeVerifier();
    String codeChallenge = sha256ToBase64Url(codeVerifier);

    com.svenruppert.chronogrid.auth.OAuthFlowRegistry.register(state,
        new com.svenruppert.chronogrid.auth.OAuthFlowRegistry.OAuthFlow(
            codeVerifier, clientId, clientSecret, redirectUri,
            (creds, error) -> originatingUi.access(() -> {
              if (creds != null) {
                this.oauthResult = creds;
                googleStatus.setText(messages.tr(K_GOOGLE_AUTHORIZED,
                    "✓ Signed in. Click Next to choose calendars."));
              } else {
                this.oauthResult = null;
                googleStatus.setText(messages.tr(K_GOOGLE_ERROR,
                    "Sign-in failed: {0}", error == null ? "unknown" : error));
                notify.accept(true, messages.tr(K_GOOGLE_ERROR,
                    "Sign-in failed: {0}", error == null ? "unknown" : error));
              }
            })));

    String authUrl = GOOGLE_AUTH_BASE_URL
        + "?client_id=" + urlEncode(clientId)
        + "&redirect_uri=" + urlEncode(redirectUri)
        + "&response_type=code"
        + "&scope=" + urlEncode(GOOGLE_CALENDAR_SCOPE)
        + "&state=" + urlEncode(state)
        + "&code_challenge=" + urlEncode(codeChallenge)
        + "&code_challenge_method=S256"
        + "&access_type=offline"
        + "&prompt=consent";

    originatingUi.getPage().open(authUrl, "_blank");
  }

  private static String randomCodeVerifier() {
    byte[] bytes = new byte[32];
    PKCE_RNG.nextBytes(bytes);
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String sha256ToBase64Url(String input) {
    try {
      java.security.MessageDigest md =
          java.security.MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (java.security.NoSuchAlgorithmException nsae) {
      // SHA-256 is part of the JDK's minimum requirements; can't actually fire.
      throw new IllegalStateException("SHA-256 not available", nsae);
    }
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Planning-Feature #9 — Step 3 placeholder for Google flows.
   * Google's CalDAV per-calendar discovery is a v2 follow-up; the
   * Wizard can finish with a single placeholder entry that the
   * user will refine via the Manager-Dialog's Re-discover.
   */
  private void useGooglePlaceholderDiscovery() {
    String defaultLabel = messages.tr(K_GOOGLE_DEFAULT_CALENDAR,
        "Primary Google Calendar (discover later from the Manager)");
    URI placeholder = URI.create(
        "https://apidata.googleusercontent.com/caldav/v2/primary/events");
    DiscoveredCalendar placeholderCal =
        new DiscoveredCalendar(placeholder, defaultLabel, "#1F77B4");
    picker.setItems(java.util.List.of(placeholderCal));
    picker.setValue(java.util.Set.of(placeholderCal));
    bulkRow.setVisible(false);
    discoveryStatus.setText(messages.tr(K_DISCOVERY_FOUND_FEW,
        "Found {0} calendar(s) — all pre-selected.", "1"));
    picker.setVisible(true);
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
    if (!validateStep1()) return false;
    if (isGoogle() && oauthResult == null) {
      notify.accept(true, messages.tr(K_GOOGLE_ERROR,
          "Sign-in failed: {0}",
          "Click \"Sign in with Google\" and complete the consent first."));
      return false;
    }
    return true;
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
    // Planning-Feature #9 Schicht 4 — OAuth path: username/password
    // stay empty, oauth carries the credentials. Basic-Auth path
    // unchanged.
    if (isGoogle() && oauthResult != null) {
      onComplete.accept(new Result(parsed, "", "",
          picker.getValue() == null ? Set.of() : picker.getValue(),
          oauthResult));
    } else {
      onComplete.accept(new Result(parsed,
          usernameField.getValue() == null ? "" : usernameField.getValue(),
          passwordField.getValue() == null ? "" : passwordField.getValue(),
          picker.getValue() == null ? Set.of() : picker.getValue()));
    }
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
