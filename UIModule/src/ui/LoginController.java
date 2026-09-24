package ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.function.Consumer;

/**
 * Controller for the login screen (login.fxml).
 * Checks the user name, sends the login request, and shows any error.
 * Only when the login succeeds does it call the onLoginSuccess callback
 * (Main uses it to switch to the main screen).
 */
public class LoginController {

    @FXML private TextField usernameField;
    @FXML private Label errorLabel;
    @FXML private Button loginBtn;

    // called with the user name once the login succeeded
    private Consumer<String> onLoginSuccess;

    @FXML
    public void initialize() {
        // need to hide the error again as soon as the user starts fixing it
        usernameField.textProperty().addListener((obs, oldText, newText) -> hideError());
    }

    public void setOnLoginSuccess(Consumer<String> onLoginSuccess) {
        this.onLoginSuccess = onLoginSuccess;
    }

    // shows a message under the user name field (for example a rejection from the server)
    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    // while waiting for the server lock the form so the user can't send a second login
    private void setBlock(boolean block) {
        usernameField.setDisable(block);
        loginBtn.setDisable(block);
        loginBtn.setText(block ? "Logging in..." : "Log In");
    }

    // runs when the Log In button is clicked or Enter is pressed
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();

        if (username.isEmpty()) {
            showError("Please enter a user name.");
            usernameField.requestFocus();
            return;
        }

        sendLoginRequest(username);
    }



    /**
     * Sends the login to the server.
     *
     * TODO when we have a server:
     *  build a login request with the user name.
     *      success        -> onLoginSuccess.accept(username)
     *      server refused -> setBlock(false); showError(message from the server)
     *      no connection  -> setBlock(false); showError("Can't reach the server. Is it running?")
     */
    private void sendLoginRequest(String username) {
        setBlock(true);

        // no server yet so every login succeeds

        setBlock(false);
        if (onLoginSuccess != null) {
            onLoginSuccess.accept(username);
        }
    }
}