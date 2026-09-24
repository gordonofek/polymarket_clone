package ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.function.BiConsumer;

/**
 * Controller for the login screen (login.fxml).
 * It only checks that a user name and password were typed; what happens after a
 * successful login is decided by whoever calls setOnLogin (Main switches to the main screen).
 */
public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    // called with the user name and password when the user logs in
    private BiConsumer<String, String> onLogin;

    @FXML
    public void initialize() {
        // need to hide the error again as soon as the user starts fixing it
        usernameField.textProperty().addListener((obs, oldText, newText) -> hideError());
        passwordField.textProperty().addListener((obs, oldText, newText) -> hideError());
    }

    public void setOnLogin(BiConsumer<String, String> onLogin) {
        this.onLogin = onLogin;
    }

    // shows a message under the fields (for example a rejection from the server)
    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    // runs when the Log In button is clicked or Enter is pressed
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        // the password is not trimmed, spaces can be part of it
        String password = passwordField.getText();

        if (username.isEmpty()) {
            showError("Please enter a user name.");
            usernameField.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter a password.");
            passwordField.requestFocus();
            return;
        }

        if (onLogin != null) {
            onLogin.accept(username, password);
        }
    }
}