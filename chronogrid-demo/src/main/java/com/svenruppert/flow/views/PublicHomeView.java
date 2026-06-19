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

package com.svenruppert.flow.views;

import com.svenruppert.flow.i18n.I18nSupport;
import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.views.ui.FeatureCard;
import com.svenruppert.flow.views.ui.TemplateBrand;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

/**
 * Public landing page. The first impression of the template.
 *
 * <p>Hero with eyebrow + headline + lede + dual CTA, followed by a
 * three-card feature grid that advertises what the template ships
 * with out of the box.
 *
 * <p>Strings + icons come from {@link TemplateBrand} — rebrand the
 * fork by editing that single class.
 */
@Route(value = PublicHomeView.NAV, layout = MainLayout.class)
public class PublicHomeView extends Composite<VerticalLayout>
    implements I18nSupport {

  public static final String NAV = "";

  // i18n keys
  private static final String K_EYEBROW = "home.hero.eyebrow";
  private static final String K_CTA_DASHBOARD = "home.hero.cta.openDashboard";
  private static final String K_CTA_SIGNIN = "home.hero.cta.signIn";
  private static final String K_CTA_ABOUT = "home.hero.cta.about";
  private static final String K_FEAT_SEC_TITLE = "home.feature.security.title";
  private static final String K_FEAT_SEC_BODY = "home.feature.security.body";
  private static final String K_FEAT_AUDIT_TITLE = "home.feature.audit.title";
  private static final String K_FEAT_AUDIT_BODY = "home.feature.audit.body";
  private static final String K_FEAT_MUT_TITLE = "home.feature.mutation.title";
  private static final String K_FEAT_MUT_BODY = "home.feature.mutation.body";

  public PublicHomeView() {
    VerticalLayout content = getContent();
    content.setSpacing(false);
    content.setPadding(false);
    content.getStyle().set("gap", "var(--lumo-space-xl)");
    content.setAlignItems(FlexComponent.Alignment.STRETCH);

    content.add(buildHero());
    content.add(buildFeatureGrid());
  }

  // ── Hero ───────────────────────────────────────────────────────

  private Div buildHero() {
    Div hero = new Div();
    hero.addClassName(TemplateBrand.CSS_HERO_SURFACE);

    Span eyebrow = new Span(VaadinIcon.FLASH.create(),
        new Span(tr(K_EYEBROW, "Vaadin Flow • Java 25 • jSentinel")));
    eyebrow.addClassName("app-hero-eyebrow");

    H1 title = new H1(TemplateBrand.NAME);
    title.addClassName("app-hero-title");

    Paragraph lede = new Paragraph(TemplateBrand.LANDING_INTRO);
    lede.addClassName("app-hero-lede");

    HorizontalLayout ctaRow = new HorizontalLayout();
    ctaRow.setSpacing(true);
    ctaRow.setAlignItems(FlexComponent.Alignment.CENTER);
    ctaRow.getStyle().set("margin-top", "var(--lumo-space-l)");
    ctaRow.add(buildPrimaryCta(), buildSecondaryCta());

    Div inner = new Div(eyebrow, title, lede, ctaRow);
    inner.getStyle().set("display", "flex");
    inner.getStyle().set("flex-direction", "column");
    inner.getStyle().set("align-items", "flex-start");
    inner.getStyle().set("position", "relative");
    inner.getStyle().set("z-index", "1");
    hero.add(inner);
    return hero;
  }

  private Button buildPrimaryCta() {
    boolean loggedIn = SubjectStores.subjectStore()
        .currentSubject(AppUser.class).isPresent();
    if (loggedIn) {
      Button toDashboard = new Button(tr(K_CTA_DASHBOARD, "Open dashboard"),
          VaadinIcon.ARROW_RIGHT.create(),
          e -> UI.getCurrent().navigate(DashboardView.class));
      toDashboard.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
      toDashboard.setIconAfterText(true);
      return toDashboard;
    }
    Button signIn = new Button(tr(K_CTA_SIGNIN, "Sign in"),
        VaadinIcon.ARROW_RIGHT.create(),
        e -> UI.getCurrent().navigate(AppLoginView.class));
    signIn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
    signIn.setIconAfterText(true);
    return signIn;
  }

  private Button buildSecondaryCta() {
    Button about = new Button(tr(K_CTA_ABOUT, "About this template"),
        e -> UI.getCurrent().navigate(AboutView.class));
    about.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_LARGE);
    return about;
  }

  // ── Feature grid ───────────────────────────────────────────────

  private FlexLayout buildFeatureGrid() {
    FlexLayout grid = new FlexLayout(
        new FeatureCard(VaadinIcon.SHIELD,
            tr(K_FEAT_SEC_TITLE, "Security wired"),
            tr(K_FEAT_SEC_BODY,
                "jSentinel-backed authentication, role + permission "
                    + "model and drift detection. First-admin bootstrap "
                    + "via one-time token.")),
        new FeatureCard(VaadinIcon.RECORDS,
            tr(K_FEAT_AUDIT_TITLE, "Audit & sessions"),
            tr(K_FEAT_AUDIT_BODY,
                "Persistent audit log on Eclipse-Store, live admin grids "
                    + "for sessions and roles, revoke-on-click ready.")),
        new FeatureCard(VaadinIcon.BUG,
            tr(K_FEAT_MUT_TITLE, "Mutation hardened"),
            tr(K_FEAT_MUT_BODY,
                "PIT-based mutation tests, Browserless harness, per-package "
                    + "coverage floors enforced in CI.")));
    grid.setFlexWrap(FlexLayout.FlexWrap.WRAP);
    grid.getStyle().set("gap", "var(--lumo-space-l)");
    grid.getStyle().set("padding", "0 var(--lumo-space-s)");
    return grid;
  }
}
