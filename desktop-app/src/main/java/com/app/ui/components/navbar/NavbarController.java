package com.app.ui.components.navbar;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class NavbarController {

    @FXML
    private Label screenTitle;

    @FXML
    private Label screenSubtitle;

    @FXML
    private Label userNameLabel;

    @FXML
    private Label userRoleLabel;

    public void setTitle(String title, String subtitle) {
        screenTitle.setText(title);
        screenSubtitle.setText(subtitle);
    }

    public void setUser(String user) {
        userNameLabel.setText(user);
    }

    public void setUserRole(String role) {
        userRoleLabel.setText(role);
    }

}
