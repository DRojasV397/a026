package com.app.ui.home;

import com.app.core.session.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class HomeController {

    @FXML
    private Label welcomeLabel;

    @FXML
    public void initialize() {
        // luego pondremos sidebar + dashboard
        System.out.println("Usuario en sesión: " + UserSession.getUser());
    }
}