package ui;

import dto.*;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.List;

/**
 * Controller for the main window (test.fxml): the file loading bar.
 * Each tab has its own controller, which gets the shared AppState from here.
 */
public class MainController {

    private final AppState state = new AppState();
    // הוסף משתנה ברמת המחלקה:
    private Timeline deltaTimeline;

    // Top part
    @FXML private Button loadFileBtn;
    @FXML private TextField filePathField;
    @FXML private ProgressBar progressBar;

    // Tab
    @FXML private Tab eventsTab;
    @FXML private Tab usersTab;
    @FXML private Tab profileTab;

    // Profile tab (profile-tab.fxml), injected from <fx:include fx:id="profileTabContent">
    @FXML private ProfileTabController profileTabContentController;

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
        profileTabContentController.init(state);
        settingsTabContentController.init(state);

        if (usersTab != null) {
            usersTab.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                if (isNowSelected) usersTabContentController.refresh();
            });
        }
        // balance and prices may have changed in another tab (deposit/withdraw) or by other users
        eventsTab.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            if (isNowSelected) eventsTabContentController.updateEvents();
        });
        profileTab.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            if (isNowSelected) profileTabContentController.refresh();
        });
        startDeltaFetching();
    }

    public void loadData(List<EventSummaryDTO> events)
    {
        // clear the selected event and user so nothing points at the previously loaded file
        // the logged in user stays the same, only the users tab view is cleared
        eventsTabContentController.reset();
        usersTabContentController.reset();
        profileTabContentController.reset();

        eventsTabContentController.showEvents(events);
        usersTabContentController.refresh();
        profileTabContentController.refresh();
    }

    @FXML
    private void handleOpenFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Guess Market XML File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));

        Stage stage = (Stage) loadFileBtn.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            loadFileBtn.setDisable(true);
            filePathField.setText("Uploading to server...");
            progressBar.setVisible(true);

            // the upload runs in the background so the window doesn't freeze
            // the logged in user becomes the market maker of the uploaded events
            String uploader = state.getActiveUser();
            Task<Void> uploadTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    state.getClientUtil().uploadFile(selectedFile, uploader);
                    return null;
                }
            };

            // מה לעשות אם ההעלאה הצליחה
            uploadTask.setOnSucceeded(e -> {
                progressBar.setVisible(false);
                loadFileBtn.setDisable(false);
                filePathField.setText("Loaded: " + selectedFile.getName());

                // Show a success message
                UiUtils.showAlert(Alert.AlertType.INFORMATION, "Success", "File uploaded and loaded successfully!", state.getThemeStylesheets());

                // Refresh the events tab with the new data from the server
                eventsTabContentController.updateEvents();
                usersTabContentController.refresh();
                profileTabContentController.refresh();
            });

            // מה לעשות אם ההעלאה נכשלה
            uploadTask.setOnFailed(e -> {
                progressBar.setVisible(false);
                loadFileBtn.setDisable(false);
                filePathField.setText("Upload failed");

                UiUtils.showAlert(Alert.AlertType.ERROR, "Upload Failed", uploadTask.getException().getMessage(), state.getThemeStylesheets());
            });

            // הפעלת ה-Task בת'רד נפרד
            new Thread(uploadTask).start();
        }
    }

    public void setLoggedInUser(String username) {
        state.setActiveUser(username);

        // the server may already have events (uploaded by other users); the Events tab
        // starts selected, so its tab listener won't fire, so load them here
        eventsTabContentController.updateEvents();
    }

    public void startDeltaFetching() {
        deltaTimeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> {

            int currentVersion = state.getLocalVersion(); // מקבל את הגרסה מ-AppState

            state.getClientUtil().fetchDeltaUpdates(currentVersion,
                    // התיקון כאן: מורידים את המילה EventsDeltaDTO, וכותבים רק את שם המשתנה
                    deltaDTO -> {
                    // 1. השרת החזיר מידע! מעדכנים את הגרסה המקומית
                        state.setLocalVersion(deltaDTO.serverVersion());


                        System.out.println("CLIENT: Got Delta! New local version: " + state.getLocalVersion() + " | Events updated: " + deltaDTO.updatedEvents().size());
                        // 2. מחלקים את העבודה לכרטיסיות:

                        // א. עדכון טבלת האירועים המרכזית (רק מה שהשתנה!)
                        eventsTabContentController.mergeUpdatedEvents(deltaDTO.updatedEvents());

                        // ב. עדכון הפורטפוליו והיוזרים
                        // מכיוון שכשהמחיר של אירוע משתנה, השווי שלו בפורטפוליו משתנה,
                        // ומכיוון שבינתיים אין לך Delta ליוזרים, נקרא פשוט לרענון היוזרים הקיים
                        usersTabContentController.refresh();
                    },
                    // גם כאן, רק שם המשתנה error
                    error -> {
                        System.err.println("Delta fetch failed: " + error);
                    }
            );
        }));

        deltaTimeline.setCycleCount(Timeline.INDEFINITE);
        deltaTimeline.play();
    }
}
