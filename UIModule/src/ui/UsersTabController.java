package ui;

import engine.EngineInterface;
import engine.dto.*;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

/**
 * Controller for the Users tab (users-tab.fxml).
 * Picking a user here sets the active user in AppState, which the rest of the UI listens to.
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
    private EngineInterface engine;

    // called by MainController once the whole window is loaded
    public void init(AppState state) {
        this.state = state;
        this.engine = state.getEngine();

        setupUsersTable();
        setupPortfolioTable();
    }

    // reloads the users list from the engine and reselects the active user
    public void refresh() {
        List<UserSummaryDTO> updatedUsers = engine.getAllUsers();
        usersTableView.getItems().setAll(updatedUsers);
        String activeUser = state.getActiveUser();
        if(activeUser != null) {
            for (UserSummaryDTO user : updatedUsers) {
                if (user.name().equals(activeUser)) {
                    // reselect the user in the table
                    usersTableView.getSelectionModel().select(user);
                    loadUserDetails(user);
                    break;
                }
            }
        }
    }

    // clears everything shown for the previously selected user
    public void reset() {
        usersTableView.getSelectionModel().clearSelection();
        userNameLabel.setText("Select a user...");
        userBalanceLabel.setText("$0.00");
        portfolioTableView.getItems().clear();
        userEventOptionsBox.getChildren().clear();
        userEventTitleLabel.setText("Select an event from the portfolio to view its current state.");
    }

    private void loadUserDetails(UserSummaryDTO user)
    {
        UserDetailsDTO details = engine.getUserDetails(user.name());
        userNameLabel.setText(details.name());
        userBalanceLabel.setText(String.format("$%.2f", details.balance()));
        portfolioTableView.getItems().clear();
        userEventOptionsBox.getChildren().clear();
        userEventTitleLabel.setText("Select an event from the portfolio to view its current state.");

        portfolioTableView.getItems().addAll(details.portfolio());
    }

    private void populateUserEventOverview(EventSummaryDTO event, String username) {
        userEventTitleLabel.setText("Overview for: " + event.name());
        userEventOptionsBox.getChildren().clear();

        boolean isOrderBook = event.tradingMethodType().contains("ORDER_BOOK");
        boolean isClosed = event.status().equals("CLOSED");

        // Fetch the compiled data from the Engine!
        UserEventOverviewDTO overview = engine.getUserEventOverview(username, event.id());

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
            if (newSelection != null) {
                loadUserDetails(newSelection);
                // the Events tab listens to this and updates itself
                state.setActiveUser(newSelection.name());
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

        portfolioTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                // Find the event the portfolio row belongs to
                EventSummaryDTO selectedPortfolioEvent = engine.getAllEvents().stream()
                        .filter(e -> e.name().equals(newSelection.eventName()))
                        .findFirst()
                        .orElse(null);

                if (selectedPortfolioEvent != null) {
                    populateUserEventOverview(selectedPortfolioEvent, state.getActiveUser());
                }
            }
        });
    }
}