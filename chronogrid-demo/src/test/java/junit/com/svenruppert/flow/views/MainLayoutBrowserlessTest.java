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

import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.flow.views.MainLayout;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import junit.com.svenruppert.flow.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MainLayout — auth-action button + role-gated drawer sections")
class MainLayoutBrowserlessTest extends BrowserlessTest {

  @BeforeEach
  void seedAdminAndClearSubject() {
    TestSupport.seedAdminAndResetBootstrap();
    SubjectStores.subjectStore().deleteCurrentSubject(AppUser.class);
  }

  @Test
  @DisplayName("anonymous → 'Sign in' button + only the Public drawer section")
  void anonymousSeesSignInAndPublicOnly() throws Exception {
    MainLayout layout = new MainLayout();

    assertEquals("Sign in", authButtonText(layout));
    assertEquals(List.of("Public"), drawerSectionLabels(layout),
        "anonymous drawer must contain ONLY the Public section");
  }

  @Test
  @DisplayName("authenticated USER → 'Sign out' button + Public + Application sections")
  void userSeesSignOutPublicApplication() throws Exception {
    AppUser user = new AppUser(11L, "Bob",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    MainLayout layout = new MainLayout();

    assertEquals("Sign out", authButtonText(layout));
    assertEquals(List.of("Public", "Application"), drawerSectionLabels(layout),
        "regular user must NOT see the Administration section");
  }

  @Test
  @DisplayName("authenticated ADMIN → all three drawer sections")
  void adminSeesAllThreeSections() throws Exception {
    AppUser admin = new AppUser(12L, "Alice",
        EnumSet.of(AuthorizationRole.ADMIN, AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(admin, AppUser.class);

    MainLayout layout = new MainLayout();

    assertEquals(List.of("Public", "Application", "Administration"),
        drawerSectionLabels(layout));
    List<String> adminItems = drawerItemsIn(layout, "Administration");
    assertTrue(adminItems.contains("Audit log"));
    assertTrue(adminItems.contains("Active sessions"));
    assertTrue(adminItems.contains("Role administration"));
  }

  @Test
  @DisplayName("beforeEnter rebuilds the auth-action slot — login → logout swap")
  void beforeEnterRebuildsAuthSlot() throws Exception {
    MainLayout layout = new MainLayout();
    assertEquals("Sign in", authButtonText(layout));

    SubjectStores.subjectStore().setCurrentSubject(
        new AppUser(13L, "Eve", EnumSet.of(AuthorizationRole.USER)), AppUser.class);
    layout.beforeEnter(null);

    assertEquals("Sign out", authButtonText(layout),
        "beforeEnter must rebuild the auth slot to reflect the new subject");
  }

  // ── helpers — reflection on the private Div slot fields ────────

  private static String authButtonText(MainLayout layout) throws Exception {
    Div slot = privateDiv(layout, "authActionSlot");
    Button btn = (Button) slot.getChildren().findFirst().orElseThrow();
    return btn.getText();
  }

  private static List<String> drawerSectionLabels(MainLayout layout) throws Exception {
    Div slot = privateDiv(layout, "drawerSlot");
    return allDescendants(slot)
        .filter(SideNav.class::isInstance)
        .map(SideNav.class::cast)
        .map(SideNav::getLabel)
        .collect(Collectors.toList());
  }

  private static List<String> drawerItemsIn(MainLayout layout, String section) throws Exception {
    Div slot = privateDiv(layout, "drawerSlot");
    return allDescendants(slot)
        .filter(SideNav.class::isInstance)
        .map(SideNav.class::cast)
        .filter(s -> section.equals(s.getLabel()))
        .findFirst()
        .map(s -> s.getItems().stream()
            .map(SideNavItem::getLabel)
            .collect(Collectors.toList()))
        .orElse(List.of());
  }

  private static Div privateDiv(MainLayout layout, String name) throws Exception {
    Field f = MainLayout.class.getDeclaredField(name);
    f.setAccessible(true);
    return (Div) f.get(layout);
  }

  private static Stream<Component> allDescendants(Component root) {
    return Stream.concat(
        Stream.of(root),
        root.getChildren().flatMap(MainLayoutBrowserlessTest::allDescendants));
  }
}
