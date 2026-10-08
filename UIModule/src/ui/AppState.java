package ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
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

    // the logged in user we are trading as; set once after login and never changes
    private final ReadOnlyObjectWrapper<String> activeUser = new ReadOnlyObjectWrapper<>(null);

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
        if (activeUser.get() != null) {
            throw new IllegalStateException("The active user is already set to " + activeUser.get());
        }
        activeUser.set(username);
    }

    // read only: other parts of the UI can listen to it but not change it
    public ReadOnlyObjectProperty<String> activeUserProperty() {
        return activeUser.getReadOnlyProperty();
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