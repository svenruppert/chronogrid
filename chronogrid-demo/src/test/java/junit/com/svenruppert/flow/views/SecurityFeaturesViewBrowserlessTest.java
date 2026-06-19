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

import com.svenruppert.flow.views.SecurityFeaturesView;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import junit.com.svenruppert.flow.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SecurityFeaturesView — public capabilities overview")
class SecurityFeaturesViewBrowserlessTest extends BrowserlessTest {

  @BeforeEach
  void seedAdmin() {
    TestSupport.seedAdminAndResetBootstrap();
  }

  @Test
  @DisplayName("NAV constant is 'security'")
  void navConstant() {
    assertEquals("security", SecurityFeaturesView.NAV);
  }

  @Test
  @DisplayName("JSENTINEL_URL points at 8g8.eu/sentinel4j — guards against link rot")
  void jsentinelUrlConstant() {
    assertEquals("https://8g8.eu/sentinel4j", SecurityFeaturesView.JSENTINEL_URL);
  }

  @Test
  @DisplayName("hero H1 reads 'Security, wired in'")
  void heroH1() {
    navigate(SecurityFeaturesView.class);
    H1 h1 = $view(H1.class).first();
    assertEquals("Security, wired in", h1.getText());
  }

  @Test
  @DisplayName("page exposes three H2 section headings — Identity, Access, Audit")
  void threeSectionHeadings() {
    navigate(SecurityFeaturesView.class);
    List<String> h2Texts = $view(H2.class).all().stream()
        .map(H2::getText)
        .collect(Collectors.toList());
    assertTrue(h2Texts.contains("Identity & credentials"),
        "Identity section missing — actual: " + h2Texts);
    assertTrue(h2Texts.contains("Authorization & access"),
        "Authorization section missing");
    assertTrue(h2Texts.contains("Audit & sessions"),
        "Audit section missing");
  }

  @Test
  @DisplayName("page links to the jSentinel project page with target=_blank")
  void jsentinelLinkPresent() {
    navigate(SecurityFeaturesView.class);
    List<Anchor> anchors = $view(Anchor.class).all().stream()
        .filter(a -> SecurityFeaturesView.JSENTINEL_URL.equals(a.getHref()))
        .collect(Collectors.toList());
    assertTrue(anchors.size() >= 2,
        "expected at least 2 jSentinel links (eyebrow + footer), got "
            + anchors.size());
    for (Anchor a : anchors) {
      assertEquals(AnchorTarget.BLANK.getValue(),
          a.getTarget().orElseThrow(),
          "jSentinel links must open in a new tab — caught a same-tab link");
    }
  }
}
