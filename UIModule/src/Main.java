import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ui.LoginController;
import ui.MainController;

import java.io.IOException;

public class Main extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Guess Market");

        // the login screen is the only thing shown until the user logs in
        showLoginScreen();
        primaryStage.show();
    }

    private void showLoginScreen() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("ui/fxml/login.fxml"));
        Parent root = loader.load();

        LoginController loginController = loader.getController();
        // the login controller talks to the server; we only hear about successful logins
        loginController.setOnLoginSuccess(username -> {
            // passing the active username to the AppState
            try {
                showMainScreen(username);
            } catch (IOException e) {
                e.printStackTrace();
                loginController.showError("Could not open the main screen: " + e.getMessage());
            }
        });

        primaryStage.setMinWidth(900.);
        primaryStage.setMinHeight(600.);
        setScene(root);
    }

    private void showMainScreen(String username) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("ui/fxml/main.fxml"));
        Parent root = loader.load();

        // the logged in user stays the active user for the whole session
        MainController mainController = loader.getController();
        mainController.setLoggedInUser(username);
        primaryStage.setTitle("Guess Market - " + username);

        setScene(root);
        primaryStage.setMinWidth(900.0);
        primaryStage.setMinHeight(600.0);
    }

    // puts the screen in the window
    private void setScene(Parent root) {
        Scene currentScene = primaryStage.getScene();

        if (currentScene == null) {
            // first screen: create the scene, size the window to fit it and center it
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/ui/themes/default-theme.css").toExternalForm());

            primaryStage.setScene(scene);
            primaryStage.sizeToScene();
            primaryStage.centerOnScreen();
        } else {
            // later screens: only swap the content, so the window keeps the size and position
            currentScene.setRoot(root);
        }
    }


    public static void main(String[] args) {
        launch(args);
        // The old UI
//        ConsoleUI ui = new ConsoleUI();
//        ui.start();
    }
}