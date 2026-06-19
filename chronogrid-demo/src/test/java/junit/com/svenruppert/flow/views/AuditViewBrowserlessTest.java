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
import com.svenruppert.flow.views.AuditView;
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

@DisplayName("AuditView — admin grid + refresh toolbar")
class AuditViewBrowserlessTest extends BrowserlessTest {

  @BeforeEach
  void seedAdmin() {
    TestSupport.seedAdminAndResetBootstrap();
    SubjectStores.subjectStore().setCurrentSubject(
        new AppUser(1L, "Admin",
            EnumSet.of(AuthorizationRole.ADMIN, AuthorizationRole.USER)),
        AppUser.class);
  }

  @Test
  @DisplayName("NAV constant is 'audit'")
  void navConstant() {
    assertEquals("audit", AuditView.NAV);
  }

  @Test
  @DisplayName("heading 'Security audit log' is rendered as H1")
  void headingPresent() {
    navigate(AuditView.class);
    H1 heading = $view(H1.class).first();
    assertEquals("Security audit log", heading.getText());
  }

  @Test
  @DisplayName("intro paragraph mentions the ring buffer + audit:read permission")
  void introParagraphPresent() {
    navigate(AuditView.class);
    Paragraph intro = $view(Paragraph.class).first();
    assertTrue(intro.getText().contains("ring buffer"),
        "intro must mention the ring buffer");
    assertTrue(intro.getText().contains("audit:read"),
        "intro must surface the audit:read permission");
  }

  @Test
  @DisplayName("toolbar contains a 'Refresh' button")
  void refreshButtonPresent() {
    navigate(AuditView.class);
    List<String> btnTexts = $view(Button.class).all().stream()
        .map(Button::getText)
        .collect(Collectors.toList());
    assertTrue(btnTexts.contains("Refresh"),
        "AuditView toolbar must include a 'Refresh' button");
  }

  @Test
  @DisplayName("Grid is mounted with non-null column configuration")
  void gridMounted() {
    navigate(AuditView.class);
    Grid<?> grid = $view(Grid.class).first();
    assertNotNull(grid);
    assertEquals(50, grid.getPageSize(),
        "AuditView grid pageSize must be 50 — guards against numeric mutations");
    assertEquals(4, grid.getColumns().size(),
        "exactly four columns: Timestamp, Type, Subject, Detail");
  }
}
