package ui;

import dto.*;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import ui.network.ClientUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.List;

/**
 * Controller for the Users tab (users-tab.fxml): the public users directory.
 * Picking a user shows the events they are market maker of; the active (logged in) user never changes.
 * The logged in user's own balance and portfolio are in the Profile tab.
 */
public class UsersTabController {

    // users list
    @FXML private TableView<UserSummaryDTO> usersTableView;
    @FXML private TableColumn<UserSummaryDTO, String> userNameCol;
    @FXML private TableColumn<UserSummaryDTO, Number> userBalanceCol;

    // top banner
    @FXML private Label userNameLabel;

    // market maker events table
    @FXML private TableView<EventSummaryDTO> mmEventsTableView;
    @FXML private TableColumn<EventSummaryDTO, String> mmEventIdCol;
    @FXML private TableColumn<EventSummaryDTO, String> mmEventNameCol;
    @FXML private TableColumn<EventSummaryDTO, String> mmEventStatusCol;
    @FXML private TableColumn<EventSummaryDTO, String> mmEventMethodCol;

    private AppState state;
    private ClientUtil clientUtil;
    private Timeline autoRefresher;

    // the user whose details are shown (null = show the logged in user)
    private String viewedUser;

    // events from the last refresh, so a click can filter them without another request
    private List<EventSummaryDTO> allEvents = List.of();

    // what one refresh brings back from the server
    private record UsersAndEvents(List<UserSummaryDTO> users, List<EventSummaryDTO> events) {}

    // called by MainController once the whole window is loaded
    public void init(AppState state) {
        this.state = state;
        this.clientUtil = state.getClientUtil();

        setupUsersTable();
        setupMmEventsTable();

//        autoRefresher = new Timeline(new KeyFrame(Duration.seconds(2), e -> refresh()));
//        autoRefresher.setCycleCount(Timeline.INDEFINITE);
//        autoRefresher.play();
    }

    // reloads the users and events from the server and re-shows the viewed user
    public void refresh()
    {
        Task<UsersAndEvents> fetchTask = new Task<>()
        {
            @Override
            protected UsersAndEvents call() throws Exception
            {
                return new UsersAndEvents(clientUtil.getUsersSummary(), clientUtil.getEventsSummary());
            }
        };

        fetchTask.setOnSucceeded(e ->
        {
            UsersAndEvents result = fetchTask.getValue();
            allEvents = result.events();

            // set before setAll, so re-selecting the row below doesn't count as a new click
            String userToShow = viewedUser != null ? viewedUser : state.getActiveUser();
            viewedUser = userToShow;
            usersTableView.getItems().setAll(result.users());
            if (userToShow != null) {
                for (UserSummaryDTO user : result.users()) {
                    if (user.name().equals(userToShow)) {
                        usersTableView.getSelectionModel().select(user);
                        showUser(user.name());
                        break;
                    }
                }
            }
        });

        fetchTask.setOnFailed(e -> {
            System.err.println("Failed to load users: " + fetchTask.getException().getMessage());
        });

        Thread thread = new Thread(fetchTask);
        thread.setDaemon(true);
        thread.start();
    }

    // clears everything shown for the previously selected user
    public void reset() {
        viewedUser = null;
        allEvents = List.of();
        usersTableView.getSelectionModel().clearSelection();
        userNameLabel.setText("Select a user...");
        mmEventsTableView.getItems().clear();
    }

    // fills the banner and the events this user is market maker of
    private void showUser(String username) {
        userNameLabel.setText(username);
        mmEventsTableView.getItems().setAll(allEvents.stream()
                .filter(event -> username.equals(event.marketMaker()))
                .toList());
    }

    /**
     * #########################################
     *      Initialize Helper function
     * #########################################
     */

    private void setupUsersTable() {
        userNameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        userBalanceCol.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().balance()));
        userBalanceCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number balance, boolean empty) {
                super.updateItem(balance, empty);
                if (empty || balance == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f", balance.doubleValue()));
                }
            }
        });

        usersTableView.setRowFactory(tv -> UiUtils.createAnimatedRow(state));

        usersTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            // only a click on a different user changes the view (refresh() re-selects the same one)
            if (newSelection != null && !newSelection.name().equals(viewedUser)) {
                viewedUser = newSelection.name();
                showUser(newSelection.name());
            }
        });
    }

    private void setupMmEventsTable() {
        // events have no id, so the row number is shown instead
        mmEventIdCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });
        mmEventNameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        mmEventStatusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().status().replace("_", " ")));
        mmEventMethodCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().tradingMethodType().replace("_", " ")));

        mmEventsTableView.setRowFactory(tv -> UiUtils.createAnimatedRow(state));
        mmEventsTableView.setPlaceholder(new Label("This user isn't market maker of any event."));
    }
  
  private void setupPortfolioTable()
    {
        portfolioEventCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().eventName()));
        portfolioOptionCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().optionName()));
        portfolioSharesCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().shares()));
        portfolioStatusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().status().replace("_", " ")));
        portfolioProfitLossCol.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().profitLoss()));
        portfolioProfitLossCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number pl, boolean empty) {
                super.updateItem(pl, empty);
                if (empty || pl == null) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", pl.doubleValue()));
                }
            }
        });

        portfolioTableView.setRowFactory(tv -> UiUtils.createAnimatedRow(state));

        // The Listener: Trigger a Task ONLY when a row is clicked
        portfolioTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {

                // Define the Task to fetch the event list from Tomcat
                Task<List<EventSummaryDTO>> fetchEventsTask = new Task<>() {
                    @Override
                    protected List<EventSummaryDTO> call() throws Exception {
                        return clientUtil.getEventsSummary();
                    }
                };

                // When the server responds, process the click on the UI thread
                fetchEventsTask.setOnSucceeded(e -> {
                    List<EventSummaryDTO> events = fetchEventsTask.getValue();

                    EventSummaryDTO selectedPortfolioEvent = events.stream()
                            .filter(ev -> ev.name().equals(newSelection.eventName()))
                            .findFirst()
                            .orElse(null);

                    if (selectedPortfolioEvent != null) {
                        populateUserEventOverview(selectedPortfolioEvent, state.getActiveUser());
                    }
                });

                fetchEventsTask.setOnFailed(e -> {
                    System.err.println("Failed to fetch events for portfolio click: " + fetchEventsTask.getException().getMessage());
                });

                Thread thread = new Thread(fetchEventsTask);
                thread.setDaemon(true);
                thread.start();
            }
        });
    }
}