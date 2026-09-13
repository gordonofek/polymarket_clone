import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("test.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);

        scene.getStylesheets().add(getClass().getResource("/ui/themes/default-theme.css").toExternalForm());

        primaryStage.setTitle("Guess Market");
        primaryStage.setScene(scene);

        primaryStage.setMinWidth(900.0);
        primaryStage.setMinHeight(600.0);

        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
//        ConsoleUI ui = new ConsoleUI();
//        ui.start();
    }
}
