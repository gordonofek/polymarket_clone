package ui;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import java.util.function.Consumer;

import ui.network.ClientUtil;

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
     * Sends the login to the server in the background.
     *      success        -> onLoginSuccess.accept(name as the server stores it)
     *      server refused -> showError(message from the server), e.g. the name is already logged in
     *      no connection  -> showError("Can't reach the server...")
     */
    private void sendLoginRequest(String username) {
        setBlock(true);

        Task<String> loginTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                // names are case-insensitive, so the server answers with the spelling it has stored
                return new ClientUtil().login(username);
            }
        };

        // מתבצע אוטומטית על ה-Thread של ממשק המשתמש (UI) במקרה של הצלחה
        loginTask.setOnSucceeded(event ->
        {
            setBlock(false);
            if (onLoginSuccess != null) {
                onLoginSuccess.accept(loginTask.getValue());
            }
        });

        // מתבצע אוטומטית על ה-Thread של ממשק המשתמש במקרה של כישלון (חריגה ב-call)
        loginTask.setOnFailed(event ->
        {
            setBlock(false);
            Throwable exception = loginTask.getException();

            if (exception instanceof java.net.ConnectException) {
                showError("Can't reach the server. Is Tomcat running?");
            } else {
                showError(exception.getMessage());
            }
        });

        // הרצת המשימה
        Thread thread = new Thread(loginTask);
        thread.setDaemon(true);
        thread.start();
    }
}
