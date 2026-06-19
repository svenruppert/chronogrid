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

import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.model.UserDirectory;
import com.svenruppert.flow.security.model.UserDirectoryProvider;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.flow.security.services.PasswordPreflight;
import com.svenruppert.flow.security.services.VersionBumper;
import com.svenruppert.flow.i18n.I18nSupport;
import com.svenruppert.flow.views.ui.EmptyState;
import com.svenruppert.flow.views.ui.FilterBar;
import com.svenruppert.flow.views.ui.PageHeader;
import com.svenruppert.jsentinel.authorization.annotations.RequiresPermission;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

import java.util.Arrays;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * User + role admin. Restricted by
 * {@code @RequiresPermission("admin:roles")}.
 */
@Route(value = AdminRolesView.NAV, layout = MainLayout.class)
@RequiresPermission("admin:roles")
public class AdminRolesView extends Composite<VerticalLayout>
    implements I18nSupport {

  public static final String NAV = "admin/roles";

  // i18n keys
  private static final String K_HEADING = "roles.heading";
  private static final String K_SUBTITLE = "roles.subtitle";
  private static final String K_NEW_USER = "roles.action.newUser";
  private static final String K_COL_ID = "roles.column.id";
  private static final String K_COL_NAME = "roles.column.name";
  private static final String K_COL_ROLES = "roles.column.roles";
  private static final String K_COL_MODIFY = "roles.column.modify";
  private static final String K_COL_DELETE = "roles.column.delete";
  private static final String K_FLT_ID = "roles.filter.id";
  private static final String K_FLT_ID_PH = "roles.filter.id.placeholder";
  private static final String K_FLT_NAME = "roles.filter.name";
  private static final String K_FLT_ROLES = "roles.filter.roles";
  private static final String K_FLT_ROLES_PH = "roles.filter.roles.placeholder";
  private static final String K_ED_ASSIGN = "roles.editor.assign";
  private static final String K_ED_REVOKE = "roles.editor.revoke";
  private static final String K_ED_PICK = "roles.editor.pickRole";
  private static final String K_ED_ALREADY = "roles.editor.alreadyAssigned";
  private static final String K_ED_NOTASSIGNED = "roles.editor.notAssigned";
  private static final String K_ED_GRANTED = "roles.editor.granted";
  private static final String K_ED_REVOKED = "roles.editor.revoked";
  private static final String K_DEL_HEADER = "roles.delete.confirm.header";
  private static final String K_DEL_TEXT = "roles.delete.confirm.text";
  private static final String K_DEL_BTN = "roles.delete.confirm.button";
  private static final String K_DEL_SUCCESS = "roles.delete.success";
  private static final String K_CR_TITLE = "roles.create.title";
  private static final String K_CR_F_USER = "roles.create.field.username";
  private static final String K_CR_F_PWD = "roles.create.field.password";
  private static final String K_CR_F_DN = "roles.create.field.displayName";
  private static final String K_CR_F_DN_PH = "roles.create.field.displayName.placeholder";
  private static final String K_CR_F_ROLE = "roles.create.field.role";
  private static final String K_CR_A_CREATE = "roles.create.action.create";
  private static final String K_CR_A_CANCEL = "roles.create.action.cancel";
  private static final String K_CR_E_REQ = "roles.create.error.required";
  private static final String K_CR_E_WEAK = "roles.create.error.weakPassword";
  private static final String K_CR_SUCCESS = "roles.create.success";
  private static final String K_CR_E_FAIL = "roles.create.error.failed";
  private static final String K_EMPTY_TITLE = "roles.empty.title";
  private static final String K_EMPTY_BODY = "roles.empty.body";
  private static final String K_UNIT_USERS = "users";

  private final Grid<AppUser> grid = new Grid<>(AppUser.class, false);
  private final FilterBar filterBar = new FilterBar();

  // One filter per column — combined with AND.
  private final TextField idFilter;
  private final TextField nameFilter;
  private final MultiSelectComboBox<AuthorizationRole> rolesFilter;

  private final EmptyState emptyState;

  {
    idFilter   = filterBar.addText(tr(K_FLT_ID, "Id"),
        tr(K_FLT_ID_PH, "exact or substring"));
    nameFilter = filterBar.addText(tr(K_FLT_NAME, "Name"), "Contains…");
    rolesFilter = filterBar.addMultiSelect(tr(K_FLT_ROLES, "Roles"),
        Arrays.asList(AuthorizationRole.values()),
        tr(K_FLT_ROLES_PH, "Any role"));
    emptyState = new EmptyState(VaadinIcon.USERS,
        tr(K_EMPTY_TITLE, "No users match"),
        tr(K_EMPTY_BODY,
            "Try clearing the filters above, or create the first user "
                + "with the 'New user' button."));
  }

  public AdminRolesView() {
    VerticalLayout root = getContent();
    root.setSizeFull();
    root.setSpacing(false);
    root.getStyle().set("gap", "var(--lumo-space-l)");

    Button newUser = new Button(tr(K_NEW_USER, "New user"),
        VaadinIcon.PLUS_CIRCLE.create(), e -> openCreateUserDialog());
    newUser.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    PageHeader header = new PageHeader(
        tr(K_HEADING, "Role administration"),
        tr(K_SUBTITLE,
            "Admin-only. Mutations emit RoleAssigned / RoleRevoked / "
                + "UserCreated / UserDeleted audit events, visible in "
                + "the /audit grid."))
        .withActions(newUser);
    HomeButton.forStandalone(getClass()).ifPresent(header::add);
    root.add(header);

    rolesFilter.setItemLabelGenerator(Enum::name);
    idFilter.addValueChangeListener(e -> refresh());
    nameFilter.addValueChangeListener(e -> refresh());
    rolesFilter.addValueChangeListener(e -> refresh());
    filterBar.onClear(this::refresh);
    root.add(filterBar);

    grid.setSizeFull();
    grid.setPageSize(50);
    grid.addColumn(AppUser::id).setHeader(tr(K_COL_ID, "Id"))
        .setWidth("4em").setFlexGrow(0);
    grid.addColumn(AppUser::name).setHeader(tr(K_COL_NAME, "Name"))
        .setWidth("14em").setFlexGrow(0);
    grid.addColumn(u -> u.roles().stream().map(Enum::name).sorted().toList().toString())
        .setHeader(tr(K_COL_ROLES, "Roles")).setFlexGrow(1);
    grid.addComponentColumn(this::buildRoleEditor)
        .setHeader(tr(K_COL_MODIFY, "Modify")).setWidth("28em").setFlexGrow(0);
    grid.addComponentColumn(this::buildDeleteButton)
        .setHeader(tr(K_COL_DELETE, "Delete")).setWidth("7em").setFlexGrow(0);

    root.add(grid);
    root.add(emptyState);
    root.setFlexGrow(1, grid);
    refresh();
  }

  private HorizontalLayout buildRoleEditor(AppUser user) {
    ComboBox<AuthorizationRole> select = new ComboBox<>();
    select.setItems(AuthorizationRole.values());
    select.setItemLabelGenerator(Enum::name);
    select.setPlaceholder("role");

    Button assign = new Button(tr(K_ED_ASSIGN, "Assign"),
        VaadinIcon.PLUS.create(), e -> {
      AuthorizationRole role = select.getValue();
      if (role == null) {
        warn(tr(K_ED_PICK, "Pick a role first."));
        return;
      }
      if (user.roles().contains(role)) {
        warn(tr(K_ED_ALREADY, "Already assigned."));
        return;
      }
      UserDirectoryProvider.directory().assignRole(user.id(), role);
      VersionBumper.bump(user);
      success(tr(K_ED_GRANTED, "Granted {0} to {1}.", role.name(), user.name()));
      refresh();
    });
    assign.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

    Button revoke = new Button(tr(K_ED_REVOKE, "Revoke"),
        VaadinIcon.MINUS.create(), e -> {
      AuthorizationRole role = select.getValue();
      if (role == null) {
        warn(tr(K_ED_PICK, "Pick a role first."));
        return;
      }
      if (!user.roles().contains(role)) {
        warn(tr(K_ED_NOTASSIGNED, "Not assigned."));
        return;
      }
      UserDirectoryProvider.directory().revokeRole(user.id(), role);
      VersionBumper.bump(user);
      success(tr(K_ED_REVOKED, "Revoked {0} from {1}.", role.name(), user.name()));
      refresh();
    });
    revoke.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

    HorizontalLayout row = new HorizontalLayout(select, assign, revoke);
    row.setSpacing(true);
    return row;
  }

  private Button buildDeleteButton(AppUser user) {
    Button delete = new Button(VaadinIcon.TRASH.create(), e -> confirmDelete(user));
    delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY,
        ButtonVariant.LUMO_SMALL);
    return delete;
  }

  private void confirmDelete(AppUser user) {
    ConfirmDialog dialog = new ConfirmDialog();
    dialog.setHeader(tr(K_DEL_HEADER, "Delete user"));
    dialog.setText(tr(K_DEL_TEXT,
        "Permanently remove ''{0}'' (id={1})?", user.name(), user.id()));
    dialog.setCancelable(true);
    dialog.setConfirmText(tr(K_DEL_BTN, "Delete"));
    dialog.setConfirmButtonTheme("error primary");
    dialog.addConfirmListener(e -> {
      UserDirectoryProvider.directory().deleteUser(user.id());
      VersionBumper.bump(user);
      success(tr(K_DEL_SUCCESS, "Deleted user {0}.", user.name()));
      refresh();
    });
    dialog.open();
  }

  private void openCreateUserDialog() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(tr(K_CR_TITLE, "Create user"));

    TextField username = new TextField(tr(K_CR_F_USER, "Username"));
    username.setRequiredIndicatorVisible(true);
    PasswordField password = new PasswordField(tr(K_CR_F_PWD, "Password"));
    password.setRequiredIndicatorVisible(true);
    TextField displayName = new TextField(tr(K_CR_F_DN, "Display name"));
    displayName.setPlaceholder(tr(K_CR_F_DN_PH, "(defaults to username)"));
    ComboBox<AuthorizationRole> role = new ComboBox<>(tr(K_CR_F_ROLE, "Initial role"));
    role.setItems(AuthorizationRole.values());
    role.setItemLabelGenerator(Enum::name);
    role.setRequiredIndicatorVisible(true);
    role.setValue(AuthorizationRole.USER);

    FormLayout form = new FormLayout(username, password, displayName, role);
    dialog.add(new H3(tr(K_CR_TITLE, "Create user")), form);

    Button save = new Button(tr(K_CR_A_CREATE, "Create"),
        VaadinIcon.CHECK.create(), e -> {
      String u = username.getValue() == null ? "" : username.getValue().trim();
      String p = password.getValue() == null ? "" : password.getValue();
      AuthorizationRole r = role.getValue();
      if (u.isEmpty() || p.isEmpty() || r == null) {
        warn(tr(K_CR_E_REQ,
            "Username, password and initial role are required."));
        return;
      }
      if (!PasswordPreflight.isAcceptable(p)) {
        warn(tr(K_CR_E_WEAK, "Password rejected — pick a stronger one."));
        return;
      }
      String display = displayName.getValue() == null || displayName.getValue().isBlank()
          ? u : displayName.getValue();
      AppUser created = new AppUser(nextId(), display,
          EnumSet.of(AuthorizationRole.USER, r));
      try {
        UserDirectoryProvider.directory().addUser(u, p, created);
        success(tr(K_CR_SUCCESS, "Created user {0}.", u));
        dialog.close();
        refresh();
      } catch (RuntimeException failure) {
        warn(tr(K_CR_E_FAIL,
            "Could not create user: {0}", failure.getMessage()));
      }
    });
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancel = new Button(tr(K_CR_A_CANCEL, "Cancel"), e -> dialog.close());
    cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    dialog.getFooter().add(cancel, save);
    dialog.open();
  }

  private static Long nextId() {
    return UserDirectoryProvider.directory().all()
        .mapToLong(AppUser::id)
        .max()
        .orElse(0L) + 1L;
  }

  private void refresh() {
    UserDirectory directory = UserDirectoryProvider.directory();
    String idNeedle = textValue(idFilter);
    String nameNeedle = textValue(nameFilter);
    java.util.Set<AuthorizationRole> wantedRoles = rolesFilter.getValue();

    List<AppUser> users = directory.all()
        .sorted(Comparator.comparing(AppUser::id))
        .filter(u -> idNeedle.isEmpty()
            || String.valueOf(u.id()).contains(idNeedle))
        .filter(u -> nameNeedle.isEmpty()
            || u.name().toLowerCase().contains(nameNeedle))
        .filter(u -> wantedRoles.isEmpty()
            || u.roles().stream().anyMatch(wantedRoles::contains))
        .toList();
    grid.setItems(users);
    filterBar.setCount(users.size(), K_UNIT_USERS);
    boolean empty = users.isEmpty();
    grid.setVisible(!empty);
    emptyState.setVisible(empty);
  }

  private static String textValue(TextField field) {
    String v = field.getValue();
    return v == null ? "" : v.trim().toLowerCase();
  }

  private static void success(String message) {
    Notification n = Notification.show(message, 2500, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  private static void warn(String message) {
    Notification n = Notification.show(message, 3000, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_WARNING);
  }
}
