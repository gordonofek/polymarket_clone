package ui;

import dto.*;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import ui.network.ClientUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;

/**
 * Controller for the Users tab (users-tab.fxml).
 * Picking a user here only shows that user's details; the active (logged in) user never changes.
 */
public class UsersTabController {

    // users list
    @FXML private TableView<UserSummaryDTO> usersTableView;
    @FXML private TableColumn<UserSummaryDTO, String> userNameCol;
    @FXML private TableColumn<UserSummaryDTO, Number> userBalanceCol;

    // top header
    @FXML private Label userNameLabel;
    @FXML private Label userBalanceLabel;

    // portfolio table
    @FXML private TableView<PortfolioItemDTO> portfolioTableView;
    @FXML private TableColumn<PortfolioItemDTO, String> portfolioEventCol;
    @FXML private TableColumn<PortfolioItemDTO, String> portfolioOptionCol;
    @FXML private TableColumn<PortfolioItemDTO, Number> portfolioSharesCol;
    @FXML private TableColumn<PortfolioItemDTO, String> portfolioStatusCol;
    @FXML private TableColumn<PortfolioItemDTO, Number> portfolioProfitLossCol;

    // event overview
    @FXML private Label userEventTitleLabel;
    @FXML private VBox userEventOptionsBox;

    private AppState state;
    private ClientUtil clientUtil;
    private Timeline autoRefresher;

    // the user whose details are shown (null = show the logged in user)
    private String viewedUser;

    // called by MainController once the whole window is loaded
    public void init(AppState state) {
        this.state = state;
        this.clientUtil = state.getClientUtil();

        setupUsersTable();
        setupPortfolioTable();

        autoRefresher = new Timeline(new KeyFrame(Duration.seconds(2), e -> refresh()));
        autoRefresher.setCycleCount(Timeline.INDEFINITE);
        autoRefresher.play();
    }

    // reloads the users list from the engine and refresh the active user
    public void refresh()
    {
        Task<List<UserSummaryDTO>> fetchUsersTask = new Task<>()
        {
            @Override
            protected List<UserSummaryDTO> call() throws Exception
            {
                // This runs in the background thread.
                // We call the synchronous method from ClientUtil here.
                return clientUtil.getUsersSummary();
            }
        };

        fetchUsersTask.setOnSucceeded(e ->
        {
            // This block safely runs back on the JavaFX UI Thread
            List<UserSummaryDTO> users = fetchUsersTask.getValue();
            // set before setAll, so re-selecting the row below doesn't count as a new click
            String userToShow = viewedUser != null ? viewedUser : state.getActiveUser();
            viewedUser = userToShow;
            usersTableView.getItems().setAll(users);
            if (userToShow != null) {
                for (UserSummaryDTO user : users) {
                    if (user.name().equals(userToShow)) {
                        usersTableView.getSelectionModel().select(user);
                        loadUserDetails(user);
                        break;
                    }
                }
            }
        });

        fetchUsersTask.setOnFailed(e -> {
            // Show an Alert box here so you know if Tomcat is down
            System.err.println("Failed to load users: " + fetchUsersTask.getException().getMessage());
        });

        Thread thread = new Thread(fetchUsersTask);
        thread.setDaemon(true);
        thread.start();
    }

    // clears everything shown for the previously selected user
    public void reset() {
        viewedUser = null;
        usersTableView.getSelectionModel().clearSelection();
        //userNameLabel.setText("Select a user...");
        userBalanceLabel.setText("$0.00");
        portfolioTableView.getItems().clear();
        userEventOptionsBox.getChildren().clear();
        userEventTitleLabel.setText("Select an event from the portfolio to view its current state.");
    }

    private void loadUserDetails(UserSummaryDTO user)
    {
        Task<UserDetailsDTO> fetchUserDTOTask = new Task<>()
        {
            @Override
            protected UserDetailsDTO call() throws Exception
            {
                // This runs in the background thread.
                // We call the synchronous method from ClientUtil here.
                return clientUtil.getUserDetails(user.name());
            }
        };

        fetchUserDTOTask.setOnSucceeded(e ->
        {
            // This block safely runs back on the JavaFX UI Thread
            UserDetailsDTO details = fetchUserDTOTask.getValue();
            userNameLabel.setText(details.name());
            userBalanceLabel.setText(String.format("$%.2f", details.balance()));
            portfolioTableView.getItems().clear();
            userEventOptionsBox.getChildren().clear();
            userEventTitleLabel.setText("Select an event from the portfolio to view its current state.");

            portfolioTableView.getItems().addAll(details.portfolio());
        });

        fetchUserDTOTask.setOnFailed(e -> {
            // Show an Alert box here so you know if Tomcat is down
            System.err.println("Failed to load users: " + fetchUserDTOTask.getException().getMessage());
        });

        Thread thread = new Thread(fetchUserDTOTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void populateUserEventOverview(EventSummaryDTO event, String username) {

        userEventTitleLabel.setText("Overview for: " + event.name());
        userEventOptionsBox.getChildren().clear();

        boolean isOrderBook = event.tradingMethodType().contains("ORDER_BOOK");
        boolean isClosed = event.status().equals("CLOSED");

        Task<UserEventOverviewDTO> fetchUserEventOverviewTask = new Task<>()
        {
            @Override
            protected UserEventOverviewDTO call() throws Exception
            {
                // This runs in the background thread.
                // We call the synchronous method from ClientUtil here.
                return clientUtil.getUserEventOverview(username, event.name());
            }
        };

        fetchUserEventOverviewTask.setOnSucceeded(e ->
        {
            // This block safely runs back on the JavaFX UI Thread
            UserEventOverviewDTO overview = fetchUserEventOverviewTask.getValue();

            VBox contentBox = new VBox(10);

            Label commLabel = new Label(String.format("Total Commission Paid: $%.2f", overview.totalCommissionPaid()));
            commLabel.getStyleClass().add("overview-comm-label");
            contentBox.getChildren().add(commLabel);

            if (isOrderBook) {
                Label obLabel = new Label("Order Book Holdings & Costs:");
                obLabel.getStyleClass().add("overview-header-label");
                contentBox.getChildren().add(obLabel);

                // Loop through options and display Total Paid
                for (Map.Entry<String, Double> entry : overview.optionInvestments().entrySet()) {
                    Label optLabel = new Label(String.format("• %s: Spent $%.2f", entry.getKey(), entry.getValue()));
                    contentBox.getChildren().add(optLabel);
                }

                if (isClosed) {
                    Label plLabel = new Label(String.format("Total Profit/Loss: $%.2f", overview.profitLoss()));
                    plLabel.getStyleClass().add("overview-pl-label");
                    contentBox.getChildren().add(plLabel);
                }
            } else {
                Label lmsrLabel = new Label("LMSR Trade History:");
                lmsrLabel.getStyleClass().add("overview-header-label");
                contentBox.getChildren().add(lmsrLabel);

                if (overview.userTradeHistory().isEmpty()) {
                    contentBox.getChildren().add(new Label("No trades recorded yet."));
                } else {
                    for (TradeRecordDTO trade : overview.userTradeHistory()) {
                        contentBox.getChildren().add(UiUtils.createTradeLabel(trade));
                    }
                }

                if (isClosed) {
                    Label winnerLabel = new Label("Winning Option: " + overview.winningOption());
                    winnerLabel.getStyleClass().add("overview-winner-label");
                    contentBox.getChildren().add(winnerLabel);
                }
            }

            userEventOptionsBox.getChildren().add(contentBox);
        });

        fetchUserEventOverviewTask.setOnFailed(e -> {
            // Show an Alert box here so you know if Tomcat is down
            System.err.println("Failed to load users: " + fetchUserEventOverviewTask.getException().getMessage());
        });

        Thread thread = new Thread(fetchUserEventOverviewTask);
        thread.setDaemon(true);
        thread.start();
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
            // only a click on a different user loads new details (refresh() re-selects the same one)
            if (newSelection != null && !newSelection.name().equals(viewedUser)) {
                viewedUser = newSelection.name();
                loadUserDetails(newSelection);
            }
        });
    }

    private void setupPortfolioTable() {
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
                        populateUserEventOverview(selectedPortfolioEvent, viewedUser);
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