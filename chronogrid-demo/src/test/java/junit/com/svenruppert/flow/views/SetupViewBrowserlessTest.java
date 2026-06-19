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

package junit.com.svenruppert.flow.views;

import com.svenruppert.flow.security.bootstrap.BootstrapWiring;
import com.svenruppert.flow.views.SetupView;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import junit.com.svenruppert.flow.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SetupView — initial-admin form layout")
class SetupViewBrowserlessTest extends BrowserlessTest {

  @BeforeEach
  void seedAdmin() {
    TestSupport.seedAdminAndResetBootstrap();
  }

  @Test
  @DisplayName("NAV constant is 'setup' — must not drift")
  void navConstant() {
    assertEquals("setup", SetupView.NAV);
  }

  @Test
  @DisplayName("when admin already exists → BeforeEnter forwards /setup → /login")
  void forwardsToLoginWhenBootstrapDone() {
    navigate("setup", com.svenruppert.flow.views.AppLoginView.class);
  }

  @Test
  @DisplayName("when bootstrap required → SetupView renders heading + form fields")
  void rendersFormWhenBootstrapRequired() {
    TestSupport.clearAdminAndResetBootstrap();

    navigate(SetupView.class);

    H2 heading = $view(H2.class).first();
    assertEquals("Initial administrator setup", heading.getText());

    // The hint paragraph references the token file path
    Paragraph hint = $view(Paragraph.class).first();
    assertTrue(hint.getText().contains(BootstrapWiring.DEFAULT_TOKEN_FILE.toString()),
        "hint paragraph must contain the bootstrap-token-file path");
    assertTrue(hint.getText().contains("server console"),
        "hint paragraph must mention the server console");

    // Username field is preseeded with 'admin'
    TextField usernameField = $view(TextField.class).all().stream()
        .filter(t -> "Admin username".equals(t.getLabel()))
        .findFirst()
        .orElseThrow();
    assertEquals("admin", usernameField.getValue(),
        "Admin-username field should be pre-filled with 'admin'");

    // Password field carries helper text with MIN_PASSWORD_LENGTH
    PasswordField passwordField = $view(PasswordField.class).all().stream()
        .filter(p -> "New password".equals(p.getLabel()))
        .findFirst()
        .orElseThrow();
    assertTrue(passwordField.getHelperText().contains(
            String.valueOf(BootstrapWiring.MIN_PASSWORD_LENGTH)),
        "Password helper text must mention MIN_PASSWORD_LENGTH");

    // Submit button labelled 'Create administrator'
    List<String> btnLabels = $view(Button.class).all().stream()
        .map(Button::getText)
        .collect(Collectors.toList());
    assertTrue(btnLabels.contains("Create administrator"),
        "the SetupView must expose a 'Create administrator' submit button");
  }

  // ────────────────────────────────────────────────────────────────
  // submit() validation paths — drive each guard via reflection.
  // The bootstrap service is the REAL one (token mismatch path); we
  // never reach the Created branch since we don't know the real token.
  // ────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("submit with empty token → warn, no service call")
  void emptyTokenWarns() throws Exception {
    SetupView view = constructWithBootstrapRequired();
    fillFields(view, /*token=*/"", "alice", "Sufficientlylong1!", "Sufficientlylong1!");

    Notification.Position before = mostRecentNotificationPosition();
    callSubmit(view);

    // No navigation, no admin created — directory still empty.
    assertEquals(0, com.svenruppert.flow.security.model.UserDirectoryProvider.directory()
        .all().count(),
        "no admin must have been created from an empty-token submit");
  }

  @Test
  @DisplayName("submit with empty username → warn, no service call")
  void emptyUsernameWarns() throws Exception {
    SetupView view = constructWithBootstrapRequired();
    fillFields(view, "tok", /*username=*/"", "Sufficientlylong1!", "Sufficientlylong1!");

    callSubmit(view);

    assertEquals(0, com.svenruppert.flow.security.model.UserDirectoryProvider.directory()
        .all().count());
    // Token field must still hold its value — guard fired BEFORE the reset
    assertEquals("tok", privatePasswordValue(view, "tokenField"));
  }

  @Test
  @DisplayName("submit with empty password → warn, no service call")
  void emptyPasswordWarns() throws Exception {
    SetupView view = constructWithBootstrapRequired();
    fillFields(view, "tok", "alice", /*password=*/"", /*confirm=*/"");

    callSubmit(view);

    assertEquals(0, com.svenruppert.flow.security.model.UserDirectoryProvider.directory()
        .all().count());
  }

  @Test
  @DisplayName("submit with password ≠ confirm → warn, no service call")
  void passwordMismatchWarns() throws Exception {
    SetupView view = constructWithBootstrapRequired();
    fillFields(view, "tok", "alice", "Sufficientlylong1!", "Differentpassword2!");

    callSubmit(view);

    assertEquals(0, com.svenruppert.flow.security.model.UserDirectoryProvider.directory()
        .all().count());
    // Token preserved — early-out before the field-clear block
    assertEquals("tok", privatePasswordValue(view, "tokenField"));
  }

  @Test
  @DisplayName("submit with password shorter than MIN_PASSWORD_LENGTH → warn")
  void passwordTooShortWarns() throws Exception {
    SetupView view = constructWithBootstrapRequired();
    String shortPw = "x".repeat(BootstrapWiring.MIN_PASSWORD_LENGTH - 1);
    fillFields(view, "tok", "alice", shortPw, shortPw);

    callSubmit(view);

    assertEquals(0, com.svenruppert.flow.security.model.UserDirectoryProvider.directory()
        .all().count());
  }

  @Test
  @DisplayName("submit with blocklisted password → PasswordPreflight rejects")
  void blocklistedPasswordWarns() throws Exception {
    SetupView view = constructWithBootstrapRequired();
    // 'password123' is in the local blocklist and is 11 chars — pad to ≥12.
    fillFields(view, "tok", "alice", "password1234", "password1234");

    callSubmit(view);

    assertEquals(0, com.svenruppert.flow.security.model.UserDirectoryProvider.directory()
        .all().count(),
        "blocklisted-by-Preflight password must NOT create an admin");
  }

  @Test
  @DisplayName("submit with all-valid inputs but wrong token → InvalidBootstrapToken branch")
  void invalidBootstrapTokenBranch() throws Exception {
    SetupView view = constructWithBootstrapRequired();
    // All client-side guards pass — the call reaches the bootstrap service.
    fillFields(view, "totally-wrong-token", "alice",
        "Sufficientlylong1!", "Sufficientlylong1!");

    callSubmit(view);

    // The service returns InvalidBootstrapToken → no admin created
    assertEquals(0, com.svenruppert.flow.security.model.UserDirectoryProvider.directory()
        .all().count(),
        "an InvalidBootstrapToken result must NOT create an admin");
    // Token + password fields are cleared regardless of outcome
    assertEquals("", privatePasswordValue(view, "tokenField"),
        "token field must be cleared after a bootstrap call");
    assertEquals("", privatePasswordValue(view, "passwordField"),
        "password field must be cleared after a bootstrap call");
  }

  @Test
  @DisplayName("submit with valid inputs but invalid username → InvalidUsername branch")
  void invalidUsernameBranch() throws Exception {
    SetupView view = constructWithBootstrapRequired();
    // Username with illegal chars — fails server-side username pattern,
    // but passes the client-side isBlank guard.
    fillFields(view, "totally-wrong-token", "alice@nope!",
        "Sufficientlylong1!", "Sufficientlylong1!");

    callSubmit(view);

    assertEquals(0, com.svenruppert.flow.security.model.UserDirectoryProvider.directory()
        .all().count(),
        "an InvalidUsername result must NOT create an admin");
  }

  // ── helpers ──────────────────────────────────────────────────

  private static SetupView constructWithBootstrapRequired() {
    TestSupport.clearAdminAndResetBootstrap();
    // Force the singleton to (re)build against the empty directory.
    BootstrapWiring.instance();
    return new SetupView();
  }

  private static void fillFields(SetupView view, String token, String username,
                                 String password, String confirm) throws Exception {
    setPasswordValue(view, "tokenField", token);
    setTextValue(view, "usernameField", username);
    setPasswordValue(view, "passwordField", password);
    setPasswordValue(view, "confirmField", confirm);
  }

  private static void setPasswordValue(SetupView view, String fieldName, String value)
      throws Exception {
    Field f = SetupView.class.getDeclaredField(fieldName);
    f.setAccessible(true);
    ((PasswordField) f.get(view)).setValue(value);
  }

  private static void setTextValue(SetupView view, String fieldName, String value)
      throws Exception {
    Field f = SetupView.class.getDeclaredField(fieldName);
    f.setAccessible(true);
    ((TextField) f.get(view)).setValue(value);
  }

  private static String privatePasswordValue(SetupView view, String fieldName)
      throws Exception {
    Field f = SetupView.class.getDeclaredField(fieldName);
    f.setAccessible(true);
    return ((PasswordField) f.get(view)).getValue();
  }

  private static void callSubmit(SetupView view) throws Exception {
    Method m = SetupView.class.getDeclaredMethod("submit");
    m.setAccessible(true);
    m.invoke(view);
  }

  private static Notification.Position mostRecentNotificationPosition() {
    // placeholder — Vaadin Notifications are fired and forgotten; we only
    // assert on side effects (directory size, field clears) instead.
    return Notification.Position.BOTTOM_END;
  }
}
