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

package com.svenruppert.flow.views;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.i18n.I18nSupport;
import com.svenruppert.flow.security.bootstrap.BootstrapWiring;
import com.svenruppert.flow.security.services.PasswordPreflight;
import com.svenruppert.flow.views.ui.BrandMark;
import com.svenruppert.flow.views.ui.TemplateBrand;
import com.svenruppert.jsentinel.bootstrap.CreateInitialAdminCommand;
import com.svenruppert.jsentinel.bootstrap.InitialAdminCreationResult;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;

/**
 * One-time setup form. Reachable on first start (before any admin
 * exists); on subsequent visits {@link #beforeEnter} forwards to
 * {@link AppLoginView}.
 *
 * <p>The bootstrap token is printed on the server console and
 * written to {@code ./data/jsentinel/bootstrap.token} by
 * {@link BootstrapWiring} at JVM start. The operator pastes it here
 * together with a chosen admin username + password.
 *
 * <p>The view is intentionally NOT embedded in {@code MainLayout}:
 * the layout's drawer assumes a working user / session model, which
 * doesn't yet exist before the first admin is created. Standalone
 * {@code @Route} keeps the setup surface minimal.
 */
@Route(SetupView.NAV)
public class SetupView extends Composite<Div>
    implements HasLogger, BeforeEnterObserver, I18nSupport {

  public static final String NAV = "setup";

  // i18n keys
  private static final String K_HEADING = "setup.heading";
  private static final String K_HINT = "setup.hint";
  private static final String K_F_TOKEN = "setup.field.token";
  private static final String K_F_USER = "setup.field.username";
  private static final String K_F_USER_HELPER = "setup.field.username.helper";
  private static final String K_F_PWD = "setup.field.password";
  private static final String K_F_PWD_HELPER = "setup.field.password.helper";
  private static final String K_F_CONFIRM = "setup.field.confirm";
  private static final String K_F_DN = "setup.field.displayName";
  private static final String K_F_EMAIL = "setup.field.email";
  private static final String K_SUBMIT = "setup.action.submit";
  private static final String K_E_REQ = "setup.error.required";
  private static final String K_E_MISMATCH = "setup.error.mismatch";
  private static final String K_E_TOOSHORT = "setup.error.tooShort";
  private static final String K_E_BLOCKED = "setup.error.blocklisted";
  private static final String K_E_TOKEN = "setup.error.invalidToken";
  private static final String K_SUCCESS = "setup.success";
  private static final String K_ALREADY = "setup.alreadyInitialized";
  private static final String K_E_USERNAME = "setup.error.invalidUsername";
  private static final String K_E_POLICY = "setup.error.policyViolation";
  private static final String K_E_INTERNAL = "setup.error.internal";

  private final PasswordField tokenField = new PasswordField();
  private final TextField usernameField = new TextField();
  private final PasswordField passwordField = new PasswordField();
  private final PasswordField confirmField = new PasswordField();
  private final TextField displayNameField = new TextField();
  private final TextField emailField = new TextField();

  public SetupView() {
    tokenField.setLabel(tr(K_F_TOKEN, "Bootstrap token"));
    usernameField.setLabel(tr(K_F_USER, "Admin username"));
    passwordField.setLabel(tr(K_F_PWD, "New password"));
    confirmField.setLabel(tr(K_F_CONFIRM, "Repeat password"));
    displayNameField.setLabel(tr(K_F_DN, "Display name (optional)"));
    emailField.setLabel(tr(K_F_EMAIL, "Email (optional)"));

    H2 heading = new H2(tr(K_HEADING, "Initial administrator setup"));
    Paragraph hint = new Paragraph(tr(K_HINT,
        "The bootstrap token was printed on the server console at startup "
            + "and stored at {0}. It authorises the one-time creation of the "
            + "first administrator. Paste it together with a chosen username "
            + "and password.",
        BootstrapWiring.DEFAULT_TOKEN_FILE));

    tokenField.setWidthFull();
    usernameField.setWidthFull();
    usernameField.setValue("admin");
    usernameField.setHelperText(tr(K_F_USER_HELPER,
        "1–64 chars of [A-Za-z0-9._-]"));
    passwordField.setWidthFull();
    passwordField.setHelperText(tr(K_F_PWD_HELPER,
        "Minimum {0} characters.", BootstrapWiring.MIN_PASSWORD_LENGTH));
    confirmField.setWidthFull();
    displayNameField.setWidthFull();
    emailField.setWidthFull();

    Button submit = new Button(tr(K_SUBMIT, "Create administrator"),
        e -> submit());
    submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);

    VerticalLayout form = new VerticalLayout(
        new BrandMark(),
        heading, hint,
        tokenField, usernameField,
        passwordField, confirmField,
        displayNameField, emailField,
        submit);
    form.setMaxWidth("520px");
    form.setSpacing(true);
    form.setAlignItems(FlexComponent.Alignment.STRETCH);

    Div hero = new Div(form);
    hero.addClassName(TemplateBrand.CSS_HERO_SURFACE);
    hero.getStyle().set("max-width", "640px");
    hero.getStyle().set("margin", "var(--lumo-space-xl) auto");

    Div root = getContent();
    root.getStyle().set("display", "flex");
    root.getStyle().set("justify-content", "center");
    root.getStyle().set("padding", "var(--lumo-space-m)");
    root.add(hero);
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    if (!BootstrapWiring.instance().stateService().bootstrapRequired()) {
      event.forwardTo(AppLoginView.class);
    }
  }

  private void submit() {
    String token = tokenField.getValue();
    String username = usernameField.getValue();
    String password = passwordField.getValue();
    String confirm = confirmField.getValue();

    logger().info("Setup attempt: username='{}', tokenLen={}, passwordLen={}, "
            + "displayNamePresent={}, emailPresent={}",
        username,
        token == null ? 0 : token.length(),
        password == null ? 0 : password.length(),
        blankToNull(displayNameField.getValue()) != null,
        blankToNull(emailField.getValue()) != null);

    // ── Client-side pre-flight ─────────────────────────────────────
    if (token == null || token.isBlank()
        || username == null || username.isBlank()
        || password == null || password.isEmpty()) {
      logger().warn("Setup rejected: required field empty");
      warn(tr(K_E_REQ, "Token, username and password are required."));
      return;
    }
    if (!password.equals(confirm)) {
      logger().warn("Setup rejected: password and confirmation mismatch");
      warn(tr(K_E_MISMATCH, "Password and confirmation do not match."));
      return;
    }
    if (password.length() < BootstrapWiring.MIN_PASSWORD_LENGTH) {
      logger().warn("Setup rejected: password length={} below minimum={}",
          password.length(), BootstrapWiring.MIN_PASSWORD_LENGTH);
      warn(tr(K_E_TOOSHORT,
          "Password must be at least {0} characters long.",
          BootstrapWiring.MIN_PASSWORD_LENGTH));
      return;
    }
    if (!PasswordPreflight.isAcceptable(password)) {
      logger().warn("Setup rejected: password flagged by local blocklist (length={})",
          password.length());
      warn(tr(K_E_BLOCKED,
          "Password rejected — it appears on a known-bad list. Pick something different."));
      return;
    }

    // ── Server-side bootstrap call ─────────────────────────────────
    logger().info("Calling InitialAdminBootstrapService.createInitialAdmin for username='{}'", username);
    InitialAdminCreationResult result = BootstrapWiring.instance().bootstrapService()
        .createInitialAdmin(new CreateInitialAdminCommand(
            token, username, password.toCharArray(),
            blankToNull(displayNameField.getValue()),
            blankToNull(emailField.getValue())));
    tokenField.clear();
    passwordField.clear();
    confirmField.clear();

    // ── Outcome handling ───────────────────────────────────────────
    switch (result) {
      case InitialAdminCreationResult.Created created -> {
        logger().info("Setup succeeded: administrator '{}' created. Bootstrap token invalidated; "
            + "subsequent /setup visits will redirect to /login.", created.username());
        success(tr(K_SUCCESS,
            "Administrator ''{0}'' created. You can now sign in.",
            created.username()));
        UI.getCurrent().navigate(AppLoginView.class);
      }
      case InitialAdminCreationResult.AlreadyInitialized ignored -> {
        logger().info("Setup ignored: an administrator already exists. Redirecting to /login.");
        warn(tr(K_ALREADY, "System already initialised — redirecting to sign in."));
        UI.getCurrent().navigate(AppLoginView.class);
      }
      case InitialAdminCreationResult.InvalidBootstrapToken ignored -> {
        logger().warn("Setup rejected: bootstrap token did not match the value in {} "
                + "(may be wrong, expired, or already consumed).",
            BootstrapWiring.DEFAULT_TOKEN_FILE);
        warn(tr(K_E_TOKEN,
            "Bootstrap token rejected. Verify it matches the value in {0}.",
            BootstrapWiring.DEFAULT_TOKEN_FILE));
      }
      case InitialAdminCreationResult.PasswordPolicyViolation policy -> {
        logger().warn("Setup rejected by server-side policy: {}",
            policy.reason() == null ? "<no reason supplied>" : policy.reason());
        warn(policy.reason() == null
            ? tr(K_E_POLICY, "Password rejected by policy.")
            : policy.reason());
      }
      case InitialAdminCreationResult.InvalidUsername invalid -> {
        logger().warn("Setup rejected: invalid username '{}' — {}",
            username, invalid.reason() == null ? "<no reason supplied>" : invalid.reason());
        warn(invalid.reason() == null
            ? tr(K_E_USERNAME, "Invalid username.")
            : invalid.reason());
      }
      case InitialAdminCreationResult.InternalError error -> {
        logger().warn("Setup failed with internal error: {}", error.reason());
        warn(tr(K_E_INTERNAL,
            "Internal error during setup: {0}. See server log.",
            error.reason() == null ? "<no reason supplied>" : error.reason()));
      }
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static void success(String message) {
    Notification n = Notification.show(message, 4000, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  private static void warn(String message) {
    Notification n = Notification.show(message, 8000, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_WARNING);
  }
}
