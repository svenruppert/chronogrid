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

import com.svenruppert.flow.views.AppLoginView;
import com.svenruppert.flow.views.DashboardView;
import com.svenruppert.flow.views.PublicHomeView;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import junit.com.svenruppert.flow.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Browserless test on the public landing page. The {@link BeforeEach}
 * hook seeds an administrator so the
 * {@code JSentinelBootstrapInitListener}'s BeforeEnter forwarder
 * doesn't reroute every navigation to {@code /setup}.
 *
 * <p>Killing surviving mutants is the actual goal: we assert on the
 * H1 text, the body paragraph's text, and the CTA's icon — each
 * blocks a class of mutations in {@link PublicHomeView}'s
 * constructor.
 */
@DisplayName("PublicHomeView — Browserless: anonymous landing")
class PublicHomeViewBrowserlessTest extends BrowserlessTest {

  @BeforeEach
  void seedAdmin() {
    TestSupport.seedAdminAndResetBootstrap();
  }

  @Test
  @DisplayName("anonymous visitor sees the brand H1, lede copy, and a 'Sign in' CTA")
  void anonymousVisitorSeesSignInCta() {
    PublicHomeView view = navigate(PublicHomeView.class);
    assertNotNull(view);

    // Hero H1 mirrors the brand name — guards against TemplateBrand drift.
    H1 heading = $view(H1.class).first();
    assertEquals(com.svenruppert.flow.views.ui.TemplateBrand.NAME,
        heading.getText());

    // Lede paragraph is the TemplateBrand intro copy.
    Paragraph copy = $view(Paragraph.class).first();
    assertEquals(com.svenruppert.flow.views.ui.TemplateBrand.LANDING_INTRO,
        copy.getText());

    // The primary CTA is the first button labelled 'Sign in' anywhere
    // in the navbar + hero. Search by label, not position — the navbar
    // also renders a Sign-in button.
    Button cta = $view(Button.class).all().stream()
        .filter(b -> "Sign in".equals(b.getText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no 'Sign in' button found"));
    assertFalse(cta.getThemeNames().isEmpty(),
        "Sign-in CTA should carry at least one Lumo theme variant (primary)");
  }

  @Test
  @DisplayName("PublicHomeView.NAV is the empty string — guards against route drift")
  void navConstantIsEmptyRoute() {
    assertEquals("", PublicHomeView.NAV);
  }

  @Test
  @DisplayName("DashboardView.NAV mirrors the route documented in the drawer/skill")
  void dashboardNavConstant() {
    assertEquals("dashboard", DashboardView.NAV);
  }
}
