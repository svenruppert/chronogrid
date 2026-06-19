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
import com.svenruppert.flow.views.AdminRolesView;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import junit.com.svenruppert.flow.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AdminRolesView — user grid + role-mutation toolbar")
class AdminRolesViewBrowserlessTest extends BrowserlessTest {

  @BeforeEach
  void seedAdmin() {
    TestSupport.seedAdminAndResetBootstrap();
    SubjectStores.subjectStore().setCurrentSubject(
        new AppUser(1L, "Admin",
            EnumSet.of(AuthorizationRole.ADMIN, AuthorizationRole.USER)),
        AppUser.class);
  }

  @Test
  @DisplayName("NAV constant is 'admin/roles'")
  void navConstant() {
    assertEquals("admin/roles", AdminRolesView.NAV);
  }

  @Test
  @DisplayName("heading 'Role administration' is rendered as H1")
  void headingPresent() {
    navigate(AdminRolesView.class);
    H1 heading = $view(H1.class).first();
    assertEquals("Role administration", heading.getText());
  }

  @Test
  @DisplayName("intro paragraph mentions the four audit-event types")
  void introMentionsAuditEvents() {
    navigate(AdminRolesView.class);
    Paragraph intro = $view(Paragraph.class).first();
    String text = intro.getText();
    assertTrue(text.contains("RoleAssigned"));
    assertTrue(text.contains("RoleRevoked"));
    assertTrue(text.contains("UserCreated"));
    assertTrue(text.contains("UserDeleted"));
  }

  @Test
  @DisplayName("toolbar contains a primary 'New user' button")
  void newUserButtonPresent() {
    navigate(AdminRolesView.class);
    List<String> btnTexts = $view(Button.class).all().stream()
        .map(Button::getText)
        .collect(Collectors.toList());
    assertTrue(btnTexts.contains("New user"),
        "AdminRolesView toolbar must include a 'New user' button");
  }

  @Test
  @DisplayName("Grid has five columns: Id, Name, Roles, Modify, Delete")
  void gridColumns() {
    navigate(AdminRolesView.class);
    Grid<?> grid = $view(Grid.class).first();
    assertNotNull(grid);
    assertEquals(5, grid.getColumns().size(),
        "exactly five columns are configured");
    assertEquals(50, grid.getPageSize(),
        "pageSize must be 50 — guards against numeric mutations");
  }
}
