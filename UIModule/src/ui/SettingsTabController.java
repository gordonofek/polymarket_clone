package ui;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;

import java.net.URL;

/**
 * Controller for the Settings tab (settings-tab.fxml).
 * Writes the animation and theme choices into AppState; the rest of the UI follows them.
 */
public class SettingsTabController {

    @FXML private CheckBox animationsToggle;
    @FXML private ComboBox<String> themeSelector;

    private AppState state;

    // called by MainController once the whole window is loaded
    public void init(AppState state) {
        this.state = state;
    }

    @FXML
    private void handleAnimationToggle() {
        state.setAnimationsEnabled(animationsToggle.isSelected());
        animationsToggle.setText(state.isAnimationsEnabled() ? "On" : "Off");
    }

    @FXML
    private void handleThemeChange() {
        String selectedTheme = themeSelector.getValue();

        String cssPath;
        if ("Dark Mode".equals(selectedTheme)) {
            cssPath = "/ui/themes/dark-theme.css";
        } else if ("Ocean Blue".equals(selectedTheme)) {
            cssPath = "/ui/themes/ocean-theme.css";
        } else if ("Light Purple".equals(selectedTheme)) {
            cssPath = "/ui/themes/purple-theme.css";
        } else {
            cssPath = "/ui/themes/default-theme.css";
        }

        URL cssUrl = getClass().getResource(cssPath);
        if (cssUrl == null) {
            System.out.println("Warning: CSS file not found for " + selectedTheme);
            // Fallback to default if the file is missing
            cssUrl = getClass().getResource("/ui/themes/default-theme.css");
        }

        // Only swap the stylesheet if we actually found one, so the current theme stays otherwise
        if (cssUrl != null) {
            state.getThemeStylesheets().setAll(cssUrl.toExternalForm());
        }
    }
}