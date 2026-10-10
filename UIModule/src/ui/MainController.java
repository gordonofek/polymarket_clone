package ui;

import engine.dto.*;

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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        startDeltaFetching();
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

            // אנחנו יוצרים Task כדי שבקשת הרשת לא תתקע את המסך הגרפי
            Task<Boolean> uploadTask = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    // 1. יצירת לקוח HTTP
                    HttpClient client = HttpClient.newHttpClient();

                    // 2. בניית הבקשה. שים לב שאנחנו שמים את הקובץ ישירות ב-Body!
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/FileUpload")) // ודא שזו הכתובת הנכונה
                            .header("Content-Type", "application/xml") // השרת ידע שזה XML
                            .POST(HttpRequest.BodyPublishers.ofFile(selectedFile.toPath()))
                            .build();

                    // 3. שליחת הבקשה והמתנה לתשובה מהשרת
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    // 4. בדיקה האם השרת החזיר קוד 200 (הצלחה) או שגיאה
                    if (response.statusCode() == 200) {
                        return true;
                    } else {
                        // אם יש שגיאה (למשל כפילות בשם או XML לא תקין), נזרוק את הודעת השגיאה שהשרת החזיר
                        throw new Exception(response.body());
                    }
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
            });

            // מה לעשות אם ההעלאה נכשלה
            uploadTask.setOnFailed(e -> {
                progressBar.setVisible(false);
                loadFileBtn.setDisable(false);
                filePathField.setText("Upload failed");

                // כאן תוכל להקפיץ Alert של שגיאה עם הטקסט: uploadTask.getException().getMessage()
            });

            // הפעלת ה-Task בת'רד נפרד
            new Thread(uploadTask).start();
        }
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
