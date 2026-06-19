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

import com.svenruppert.flow.views.AuditView;
import com.svenruppert.flow.views.HomeButton;
import com.svenruppert.flow.views.MainLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HomeButton.forStandalone — render only for layout=UI.class routes")
class HomeButtonTest {

  // ── Embedded views (AuditView lives under MainLayout) ──────────

  @Test
  @DisplayName("AuditView is embedded in MainLayout → no Home button")
  void embeddedAuditViewGetsNoButton() {
    Optional<Button> result = HomeButton.forStandalone(AuditView.class);
    assertFalse(result.isPresent(),
        "AuditView declares layout=MainLayout.class — HomeButton must be empty");
  }

  // ── Standalone fixtures (layout defaults to UI.class) ──────────

  @Test
  @DisplayName("standalone @Route view → Home button is present")
  void standaloneViewGetsButton() {
    Optional<Button> result = HomeButton.forStandalone(StandaloneFixture.class);
    assertTrue(result.isPresent(),
        "StandaloneFixture has no explicit layout (defaults to UI.class)");
    assertTrue(result.get().getText().toLowerCase().contains("home"),
        "button label should say 'Home', got: " + result.get().getText());
  }

  // ── No-@Route class → no Home button (kills the !=UI-only mutant) ─

  @Test
  @DisplayName("a class without @Route at all → empty")
  void classWithoutRouteAnnotationIsEmpty() {
    Optional<Button> result = HomeButton.forStandalone(NoRouteFixture.class);
    assertFalse(result.isPresent());
  }

  @Test
  @DisplayName("MainLayout (no @Route at all) → empty")
  void mainLayoutHasNoRouteAndIsEmpty() {
    Optional<Button> result = HomeButton.forStandalone(MainLayout.class);
    assertFalse(result.isPresent());
  }

  // ── Fixtures (test-only routes; not registered at runtime) ─────

  @Route("standalone-fixture")
  public static class StandaloneFixture extends VerticalLayout {
  }

  /** No annotation at all — exercises the {@code route == null} branch. */
  public static class NoRouteFixture {
    @SuppressWarnings("unused")
    public void setUi(UI ui) {
    }
  }
}
