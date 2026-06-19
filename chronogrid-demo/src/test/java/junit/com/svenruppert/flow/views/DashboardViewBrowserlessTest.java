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
import com.svenruppert.flow.views.AppLoginView;
import com.svenruppert.flow.views.DashboardView;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import junit.com.svenruppert.flow.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DashboardView — greeting + role badges + hint")
class DashboardViewBrowserlessTest extends BrowserlessTest {

  @BeforeEach
  void seedAdmin() {
    TestSupport.seedAdminAndResetBootstrap();
  }

  @Test
  @DisplayName("NAV constant is 'dashboard'")
  void navConstant() {
    assertEquals("dashboard", DashboardView.NAV);
  }

  @Test
  @DisplayName("anonymous visit to /dashboard reroutes to /login (RoleAccessEvaluator denies)")
  void anonymousReroutedToLogin() {
    SubjectStores.subjectStore().deleteCurrentSubject(AppUser.class);
    navigate("dashboard", AppLoginView.class);
  }

  @Test
  @DisplayName("authenticated admin sees their name in the heading + ADMIN+USER badges")
  void adminGetsNameAndBadges() {
    AppUser admin = new AppUser(7L, "Alice Admin",
        EnumSet.of(AuthorizationRole.ADMIN, AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(admin, AppUser.class);

    navigate(DashboardView.class);

    H1 heading = $view(H1.class).first();
    assertEquals("Welcome, Alice Admin", heading.getText());

    List<String> badgeTexts = $view(Span.class).all().stream()
        .map(Span::getText)
        .collect(Collectors.toList());
    assertTrue(badgeTexts.contains("ADMIN"));
    assertTrue(badgeTexts.contains("USER"));
  }

  @Test
  @DisplayName("regular user sees only the USER badge")
  void regularUserGetsOneBadge() {
    AppUser user = new AppUser(8L, "Bob User",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(DashboardView.class);

    List<String> badgeTexts = $view(Span.class).all().stream()
        .map(Span::getText)
        .filter(t -> "ADMIN".equals(t) || "USER".equals(t))
        .collect(Collectors.toList());
    assertEquals(List.of("USER"), badgeTexts);
  }

  @Test
  @DisplayName("hint paragraph references drawer-based navigation gating")
  void hintParagraphPresent() {
    AppUser user = new AppUser(9L, "Carol",
        EnumSet.of(AuthorizationRole.USER));
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);

    navigate(DashboardView.class);

    Paragraph hint = $view(Paragraph.class).first();
    assertTrue(hint.getText().contains("Drawer entries appear based on the permissions"),
        "DashboardView hint must explain drawer-permission gating");
  }
}
