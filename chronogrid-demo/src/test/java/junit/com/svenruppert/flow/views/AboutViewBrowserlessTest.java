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
import com.svenruppert.flow.views.AboutView;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
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

@DisplayName("AboutView — hero, profile, project + outbound links")
class AboutViewBrowserlessTest extends BrowserlessTest {

  @BeforeEach
  void seedAdminAndAuth() {
    TestSupport.seedAdminAndResetBootstrap();
    SubjectStores.subjectStore().setCurrentSubject(
        new AppUser(33L, "Tester", EnumSet.of(AuthorizationRole.USER, AuthorizationRole.ADMIN)),
        AppUser.class);
  }

  @Test
  @DisplayName("PATH constant is 'about'")
  void pathConstant() {
    assertEquals("about", AboutView.PATH);
  }

  @Test
  @DisplayName("hero renders the brand H1 + 'About the template' eyebrow")
  void heroPresent() {
    navigate(AboutView.class);

    H1 heading = $view(H1.class).first();
    assertEquals("Crafted with Vaadin Flow", heading.getText());

    boolean eyebrowPresent = $view(Span.class).all().stream()
        .anyMatch(s -> "About the template".equals(s.getText()));
    assertTrue(eyebrowPresent,
        "Hero must include an 'About the template' eyebrow");
  }

  @Test
  @DisplayName("outbound links: Website / GitHub / LinkedIn with target=_blank")
  void outboundLinksPresent() {
    navigate(AboutView.class);

    List<Anchor> anchors = $view(Anchor.class).all();
    List<String> hrefs = anchors.stream().map(Anchor::getHref).collect(Collectors.toList());

    assertTrue(hrefs.contains("https://www.svenruppert.com"),
        "Website link missing");
    assertTrue(hrefs.contains("https://github.com/svenruppert"),
        "GitHub link missing");
    assertTrue(hrefs.contains("https://www.linkedin.com/in/sven-ruppert"),
        "LinkedIn link missing");

    long blankTargets = anchors.stream()
        .filter(a -> a.getTarget().map("_blank"::equals).orElse(false))
        .count();
    assertTrue(blankTargets >= 3,
        "every outbound profile link must open in a new tab (target=_blank)");
  }

  @Test
  @DisplayName("expertise badges include the curated topic list")
  void expertiseBadgesPresent() {
    navigate(AboutView.class);

    List<String> spanTexts = $view(Span.class).all().stream()
        .map(Span::getText)
        .collect(Collectors.toList());

    List<String> expected = List.of(
        "Vaadin Flow", "Java 8-25", "Security", "RAG/AI", "EclipseStore", "DevSecOps");
    for (String badge : expected) {
      assertTrue(spanTexts.contains(badge),
          "expected expertise badge missing: " + badge);
    }
  }
}
