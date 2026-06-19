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
import com.svenruppert.flow.views.ui.TemplateBrand;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

/**
 * About page — the most decorative public surface. Hero ribbon, stat
 * row, profile card with avatar and topic badges, project description
 * card, and a footer of outbound links.
 *
 * <p>Reachable without an account — listed in the drawer's Public
 * section. Every visible string runs through {@link I18nSupport}.
 */
@Route(value = AboutView.PATH, layout = MainLayout.class)
@CssImport("./styles/about-view.css")
public class AboutView extends Composite<VerticalLayout>
    implements I18nSupport {

  public static final String PATH = "about";

  public static final String WEBSITE = "https://www.svenruppert.com";
  public static final String GITHUB = "https://github.com/svenruppert";
  public static final String LINKEDIN = "https://www.linkedin.com/in/sven-ruppert";

  // i18n keys
  private static final String K_EYEBROW = "about.hero.eyebrow";
  private static final String K_TITLE = "about.hero.title";
  private static final String K_LEDE = "about.hero.lede";
  private static final String K_STAT_YEARS_VALUE = "about.stat.years.value";
  private static final String K_STAT_YEARS_LABEL = "about.stat.years.label";
  private static final String K_STAT_TALKS_VALUE = "about.stat.talks.value";
  private static final String K_STAT_TALKS_LABEL = "about.stat.talks.label";
  private static final String K_STAT_ARTICLES_VALUE = "about.stat.articles.value";
  private static final String K_STAT_ARTICLES_LABEL = "about.stat.articles.label";
  private static final String K_PROFILE_NAME = "about.profile.name";
  private static final String K_PROFILE_ROLE = "about.profile.role";
  private static final String K_PROFILE_BIO = "about.profile.bio";
  private static final String K_LINK_WEBSITE = "about.link.website";
  private static final String K_LINK_GITHUB = "about.link.github";
  private static final String K_LINK_LINKEDIN = "about.link.linkedin";
  private static final String K_PROJECT_TITLE = "about.project.title";
  private static final String K_PROJECT_BODY = "about.project.body";
  private static final String K_FEAT_RESPONSIVE = "about.feature.responsive";
  private static final String K_FEAT_LUMO = "about.feature.lumo";
  private static final String K_FEAT_TYPOGRAPHY = "about.feature.typography";
  private static final String K_FEAT_ACCESSIBLE = "about.feature.accessible";
  private static final String K_FOOTER = "about.footer";

  public AboutView() {
    VerticalLayout root = getContent();
    root.setSpacing(false);
    root.setPadding(false);
    root.addClassName("about-root");

    root.add(
        buildHero(),
        buildStatRow(),
        buildGrid(),
        buildFooter());
  }

  // ── Hero ───────────────────────────────────────────────────────

  private Div buildHero() {
    Div hero = new Div();
    hero.addClassName(TemplateBrand.CSS_HERO_SURFACE);
    // Ensure the hero surface itself spans the full width of its parent.
    hero.getStyle().set("width", "100%");
    hero.getStyle().set("box-sizing", "border-box");

    Span eyebrow = new Span(VaadinIcon.INFO_CIRCLE.create(),
        new Span(tr(K_EYEBROW, "About the template")));
    eyebrow.addClassName("app-hero-eyebrow");

    H1 title = new H1(tr(K_TITLE, "Crafted with Vaadin Flow"));
    title.addClassName("app-hero-title");
    // Hard-override the 22ch max-width inherited from .app-hero-title —
    // on the About hero the title is supposed to read ceremonial,
    // spanning the entire hero width.
    title.getStyle().set("max-width", "none");
    title.getStyle().set("width", "100%");
    title.getStyle().set("text-align", "center");

    Paragraph lede = new Paragraph(tr(K_LEDE,
        "A polished starter for product-grade Vaadin Flow applications — "
            + "with the security, persistence and design system already "
            + "wired in. Built by Sven Ruppert, refined over many years "
            + "of Java craftsmanship."));
    lede.addClassName("app-hero-lede");
    lede.getStyle().set("text-align", "center");
    lede.getStyle().set("margin-left", "auto");
    lede.getStyle().set("margin-right", "auto");

    Div inner = new Div(eyebrow, title, lede);
    // Centered column layout — inline styles guarantee precedence over
    // the `.app-hero-surface > div`'s default flex-start alignment.
    inner.getStyle().set("display", "flex");
    inner.getStyle().set("flex-direction", "column");
    inner.getStyle().set("align-items", "center");
    inner.getStyle().set("text-align", "center");
    inner.getStyle().set("position", "relative");
    inner.getStyle().set("z-index", "1");
    inner.getStyle().set("width", "100%");
    hero.add(inner);
    return hero;
  }

  // ── Stat row ───────────────────────────────────────────────────

  private Div buildStatRow() {
    Div row = new Div();
    row.addClassName("about-stat-row");
    row.add(
        stat(tr(K_STAT_YEARS_VALUE, "20+"),
            tr(K_STAT_YEARS_LABEL, "Years in Java")),
        stat(tr(K_STAT_TALKS_VALUE, "120+"),
            tr(K_STAT_TALKS_LABEL, "Conference talks")),
        stat(tr(K_STAT_ARTICLES_VALUE, "100+"),
            tr(K_STAT_ARTICLES_LABEL, "Articles & books")));
    return row;
  }

  private Div stat(String value, String label) {
    Div s = new Div();
    s.addClassName("about-stat");
    Paragraph v = new Paragraph(value);
    v.addClassName("about-stat-value");
    Span l = new Span(label);
    l.addClassName("about-stat-label");
    s.add(v, l);
    return s;
  }

  // ── Cards grid ─────────────────────────────────────────────────

  private Div buildGrid() {
    Div grid = new Div(buildProfileCard(), buildProjectCard());
    grid.addClassName("about-grid");
    return grid;
  }

  private Div buildProfileCard() {
    Div card = new Div();
    card.addClassName(TemplateBrand.CSS_CARD);
    card.getStyle().set("display", "flex");
    card.getStyle().set("flex-direction", "column");
    card.getStyle().set("gap", "var(--lumo-space-m)");

    Div profile = new Div();
    profile.addClassName("about-profile");

    Div avatar = new Div();
    avatar.addClassName("about-avatar-frame");
    Image portrait = new Image("images/portrait-sven.jpg",
        tr(K_PROFILE_NAME, "Sven Ruppert"));
    portrait.getStyle().set("object-fit", "cover");
    avatar.add(portrait);

    Div names = new Div();
    names.getStyle().set("display", "flex");
    names.getStyle().set("flex-direction", "column");
    names.getStyle().set("gap", "2px");
    Paragraph n = new Paragraph(tr(K_PROFILE_NAME, "Sven Ruppert"));
    n.addClassName("about-profile-name");
    Paragraph r = new Paragraph(tr(K_PROFILE_ROLE,
        "Developer Advocate • Java • Secure Coding"));
    r.addClassName("about-profile-role");
    names.add(n, r);

    profile.add(avatar, names);

    Paragraph bio = new Paragraph(tr(K_PROFILE_BIO,
        "Sven Ruppert has been involved in software development for "
            + "more than 20 years. He speaks internationally at "
            + "conferences and has authored numerous technical articles "
            + "and books on secure coding, modern Java and DevSecOps."));
    bio.addClassName(TemplateBrand.CSS_MUTED);
    bio.getStyle().set("margin", "0");

    Div topics = new Div();
    topics.addClassName("about-topics");
    String[] topicList = {"Vaadin Flow", "Java 8-25", "Security",
        "RAG/AI", "EclipseStore", "DevSecOps"};
    for (String t : topicList) {
      Span chip = new Span(t);
      chip.addClassName("about-topic");
      topics.add(chip);
    }

    Div links = new Div(
        link(WEBSITE, tr(K_LINK_WEBSITE, "Website"),
            new com.vaadin.flow.component.icon.Icon(VaadinIcon.GLOBE_WIRE)),
        link(GITHUB, tr(K_LINK_GITHUB, "GitHub"),
            img("icons/github-mark.svg", "GitHub")),
        link(LINKEDIN, tr(K_LINK_LINKEDIN, "LinkedIn"),
            img("icons/linkedin-mark.png", "LinkedIn")));
    links.addClassName("about-links");

    card.add(profile, bio, topics, links);
    return card;
  }

  private Div buildProjectCard() {
    Div card = new Div();
    card.addClassName(TemplateBrand.CSS_CARD);
    card.getStyle().set("display", "flex");
    card.getStyle().set("flex-direction", "column");
    card.getStyle().set("gap", "var(--lumo-space-m)");

    H3 h = new H3(tr(K_PROJECT_TITLE, "About this project"));
    h.getStyle().set("margin", "0");
    h.getStyle().set("font-size", "1.25rem");
    h.getStyle().set("font-weight", "700");
    h.getStyle().set("letter-spacing", "-0.015em");

    Paragraph p = new Paragraph(tr(K_PROJECT_BODY,
        "This application demonstrates clean UI composition with Flow "
            + "components, a soft visual hierarchy, accessible defaults "
            + "and a mutation-tested security core — ready to fork for "
            + "your next internal product."));
    p.addClassName(TemplateBrand.CSS_MUTED);
    p.getStyle().set("margin", "0");

    UnorderedList list = new UnorderedList();
    list.addClassName("about-feature-list");
    list.add(
        featureItem(tr(K_FEAT_RESPONSIVE, "Responsive layout & cards")),
        featureItem(tr(K_FEAT_LUMO, "Lumo-themed badges & icons")),
        featureItem(tr(K_FEAT_TYPOGRAPHY, "Clean typography & spacing")),
        featureItem(tr(K_FEAT_ACCESSIBLE,
            "Accessible links and readable contrast")));

    card.add(h, p, list);
    return card;
  }

  private ListItem featureItem(String text) {
    ListItem li = new ListItem();
    com.vaadin.flow.component.icon.Icon check =
        VaadinIcon.CHECK_CIRCLE.create();
    Span s = new Span(text);
    Div row = new Div(check, s);
    row.getStyle().set("display", "inline-flex");
    row.getStyle().set("align-items", "center");
    row.getStyle().set("gap", "var(--lumo-space-s)");
    li.add(row);
    return li;
  }

  private Anchor link(String href, String label,
                      com.vaadin.flow.component.Component icon) {
    Anchor a = new Anchor(href, "");
    a.addClassName("about-link");
    a.setTarget(AnchorTarget.BLANK);
    a.getElement().setAttribute("aria-label", label);
    a.add(icon, new Span(label));
    return a;
  }

  private Image img(String src, String alt) {
    Image image = new Image(src, alt);
    image.addClassName("about-link__imgicon");
    return image;
  }

  // ── Footer ─────────────────────────────────────────────────────

  private Div buildFooter() {
    Div footer = new Div();
    footer.addClassName("about-footer");
    footer.add(VaadinIcon.HEART.create(),
        new Span(tr(K_FOOTER,
            "Built with Vaadin Flow & Lumo — fork it, ship it.")));
    footer.setSizeUndefined();
    Div wrap = new Div(footer);
    wrap.getStyle().set("display", "flex");
    wrap.getStyle().set("justify-content", "center");
    wrap.getStyle().set("width", "100%");
    return wrap;
  }
}
