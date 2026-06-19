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
import com.svenruppert.flow.views.main.PushDemoView;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import junit.com.svenruppert.flow.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("PushDemoView — button + initial status paragraph")
class PushDemoViewBrowserlessTest extends BrowserlessTest {

  @BeforeEach
  void seedUser() {
    TestSupport.seedAdminAndResetBootstrap();
    SubjectStores.subjectStore().setCurrentSubject(
        new AppUser(55L, "PushUser", EnumSet.of(AuthorizationRole.USER)),
        AppUser.class);
  }

  @Test
  @DisplayName("PATH constant is 'pushDemo'")
  void pathConstant() {
    assertEquals("pushDemo", PushDemoView.PATH);
  }

  @Test
  @DisplayName("initial status paragraph shows the pre-push placeholder")
  void initialStatusText() {
    navigate(PushDemoView.class);

    Paragraph status = $view(Paragraph.class).first();
    assertNotNull(status);
    // Tests pin Locale.ENGLISH via TestSupport — assert on the
    // English fallback, not the German source.
    assertEquals("No push yet", status.getText(),
        "initial status text must match — guards against literal mutations");
  }

  @Test
  @DisplayName("'Start push' button is rendered")
  void startButtonRendered() {
    navigate(PushDemoView.class);

    boolean buttonPresent = $view(Button.class).all().stream()
        .anyMatch(b -> "Start push".equals(b.getText()));
    assertEquals(true, buttonPresent,
        "PushDemoView must expose a 'Start push' button");
  }
}
