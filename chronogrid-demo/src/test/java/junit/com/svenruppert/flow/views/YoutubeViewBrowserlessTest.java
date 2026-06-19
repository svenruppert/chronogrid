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
import com.svenruppert.flow.views.YoutubeView;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.html.IFrame;
import junit.com.svenruppert.flow.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("YoutubeView — IFrame embed config")
class YoutubeViewBrowserlessTest extends BrowserlessTest {

  @BeforeEach
  void seedUser() {
    TestSupport.seedAdminAndResetBootstrap();
    SubjectStores.subjectStore().setCurrentSubject(
        new AppUser(44L, "ViewerUser", EnumSet.of(AuthorizationRole.USER)),
        AppUser.class);
  }

  @Test
  @DisplayName("PATH constant is 'youtube'")
  void pathConstant() {
    assertEquals("youtube", YoutubeView.PATH);
  }

  @Test
  @DisplayName("renders a single IFrame pointing at the embedded video URL")
  void iframeWiredCorrectly() {
    navigate(YoutubeView.class);

    IFrame iframe = $view(IFrame.class).first();
    assertNotNull(iframe);
    assertEquals("https://www.youtube.com/embed/CxCMIc5Bx18", iframe.getSrc(),
        "IFrame src must point at the embed URL — guards against link rot mutations");
  }

  @Test
  @DisplayName("IFrame is responsive (100% size in 16:9 card) and fullscreen-capable")
  void iframeSizeAndAttributes() {
    navigate(YoutubeView.class);

    IFrame iframe = $view(IFrame.class).first();
    // Player fills its 16:9 card surface — sizing is via CSS aspect-ratio.
    assertEquals("100%", iframe.getWidth());
    assertEquals("100%", iframe.getHeight());
    assertTrue("true".equalsIgnoreCase(iframe.getElement().getAttribute("allowfullscreen"))
            || "".equals(iframe.getElement().getAttribute("allowfullscreen")),
        "allowfullscreen attribute must be set");
    assertEquals("0", iframe.getElement().getAttribute("frameborder"));
  }
}
