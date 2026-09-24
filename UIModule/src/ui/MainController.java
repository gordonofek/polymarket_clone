package ui;

import engine.EngineInterface;
import engine.dto.*;

import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * Controller for the main window (test.fxml): the file loading bar.
 * Each tab has its own controller, which gets the shared AppState from here.
 */
public class MainController {

    private final AppState state = new AppState();
    private final EngineInterface engine = state.getEngine();

    // Top part
    @FXML private Button loadFileBtn;
    @FXML private TextField filePathField;
    @FXML private ProgressBar progressBar;

    // Tab
    @FXML private Tab usersTab;

    // Events tab (events-tab.fxml), injected from <fx:include fx:id="eventsTabContent">
    @FXML private EventsTabController eventsTabContentController;

    // Users tab (users-tab.fxml), injected from <fx:include fx:id="usersTabContent">
    @FXML private UsersTabController usersTabContentController;

    // Settings tab (settings-tab.fxml), injected from <fx:include fx:id="settingsTabContent">
    @FXML private SettingsTabController settingsTabContentController;

    // the window's root; its stylesheets follow the theme chosen in the settings tab
    @FXML private VBox rootVBox;

    @FXML
    public void initialize() {

        // start from the theme set in test.fxml, then keep the window in sync with AppState
        state.getThemeStylesheets().setAll(rootVBox.getStylesheets());
        Bindings.bindContent(rootVBox.getStylesheets(), state.getThemeStylesheets());

        // all tabs share the same state
        eventsTabContentController.init(state);
        usersTabContentController.init(state);
        settingsTabContentController.init(state);

        if (usersTab != null) {
            usersTab.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                if (isNowSelected) usersTabContentController.refresh();
            });
        }
    }

    public void loadData(List<EventSummaryDTO> events)
    {
        // clear the selected event and user so nothing points at the previously loaded file
        eventsTabContentController.reset();
        state.setActiveUser(null);
        usersTabContentController.reset();

        eventsTabContentController.showEvents(events);
        usersTabContentController.refresh();
    }

    @FXML
    private void handleOpenFile()
    {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Guess Market XML File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));

        Stage stage = (Stage) loadFileBtn.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            String filePath = selectedFile.getAbsolutePath();

            loadFileBtn.setDisable(true);
            filePathField.setText("Loading: " + filePath);

            progressBar.setVisible(true);

            Task<List<EventSummaryDTO>> loadTask = new Task<>() {
                @Override
                protected List<EventSummaryDTO> call() throws Exception {
                    int totalSteps = 20;
                    for (int i = 1; i <= totalSteps; i++)
                    {
                        Thread.sleep(100);
                        updateProgress(i, totalSteps);
                    }
                    engine.loadDataFromXML(filePath);
                    return engine.getAllEvents();
                }
            };
            progressBar.progressProperty().bind(loadTask.progressProperty());

            loadTask.setOnSucceeded(e-> {
                progressBar.setVisible(false);

                loadFileBtn.setDisable(false);
                filePathField.setText(filePath);

                List<EventSummaryDTO> loadedEvents = loadTask.getValue();

                loadData(loadedEvents);


                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("Success");
                successAlert.setHeaderText(null);
                successAlert.setContentText("File loaded successfully into Guess Market");
                successAlert.showAndWait();
            });

            loadTask.setOnFailed(e-> {
                progressBar.setVisible(false);
                loadFileBtn.setDisable(false);
                filePathField.setText("Error loading file.");

                Throwable exception = loadTask.getException();

                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Loading Error");
                errorAlert.setHeaderText("Failed to load the XML file");
                errorAlert.setContentText(exception.getMessage());
                errorAlert.showAndWait();
            });
            new Thread(loadTask).start();
        }
    }

}
