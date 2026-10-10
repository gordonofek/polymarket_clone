package ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import ui.network.ClientUtil;

/**
 * State shared by every part of the UI.
 *
 * The window is split into separate controllers, one per tab, and they don't know about each other.
 * Anything more than one of them need is here instead.
 * One instance is created by MainController and handed to whoever needs it.
 * The values are JavaFX properties so other parts of the UI can listen for changes.
 */
public class AppState {

    private ClientUtil clientUtil = new ClientUtil();
    private int localVersion = 0; // מתחיל מ-0 כדי להביא הכל בפעם הראשונה

    public int getLocalVersion() { return localVersion; }
    public void setLocalVersion(int localVersion) { this.localVersion = localVersion; }


    // the user we are trading as (null = no user selected)
    private final ObjectProperty<String> activeUser = new SimpleObjectProperty<>(null);

    // turned on/off from the settings tab, checked by every animation
    private final BooleanProperty animationsEnabled = new SimpleBooleanProperty(true);

    // stylesheets of the current theme: set by the settings tab, used by the window and every popup
    private final ObservableList<String> themeStylesheets = FXCollections.observableArrayList();

    public ClientUtil getClientUtil() {
        return clientUtil;
    }

    public String getActiveUser() {
        return activeUser.get();
    }



    public void setActiveUser(String username) {
        activeUser.set(username);
    }

    public ObjectProperty<String> activeUserProperty() {
        return activeUser;
    }

    public boolean isAnimationsEnabled() {
        return animationsEnabled.get();
    }

    public void setAnimationsEnabled(boolean enabled) {
        animationsEnabled.set(enabled);
    }

    public BooleanProperty animationsEnabledProperty() {
        return animationsEnabled;
    }

    public ObservableList<String> getThemeStylesheets() {
        return themeStylesheets;
    }
}