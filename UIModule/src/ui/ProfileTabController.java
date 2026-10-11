package ui;

import dto.*;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import ui.network.ClientUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Profile tab (profile-tab.fxml).
 * Always shows the logged in (active) user: balance, portfolio and an overview of a picked event.
 */
public class ProfileTabController {

    // profile info
    @FXML private Label profileUsernameLabel;
    @FXML private Label profileBalanceLabel;
    @FXML private Button addFundsBtn;
    @FXML private Button withdrawFundsBtn;

    // balance history (deposits and withdrawals, newest first)
    @FXML private TableView<BalanceHistoryItemDTO> balanceTableView;
    @FXML private TableColumn<BalanceHistoryItemDTO, String> historyActionCol;
    @FXML private TableColumn<BalanceHistoryItemDTO, String> historyAmountCol;
    @FXML private TableColumn<BalanceHistoryItemDTO, String> historyDetailsCol;

    private static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

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

    // the portfolio row whose overview is shown
    private PortfolioItemDTO viewedItem;

    // balance from the last refresh, shown in the deposit/withdraw dialog
    private double currentBalance;

    // called by MainController once the whole window is loaded
    public void init(AppState state) {
        this.state = state;
        this.clientUtil = state.getClientUtil();

        setupPortfolioTable();
        setupFundsButtons();
        setupBalanceHistoryTable();

        autoRefresher = new Timeline(new KeyFrame(Duration.seconds(2), e -> refresh()));
        autoRefresher.setCycleCount(Timeline.INDEFINITE);
        autoRefresher.play();
    }

    // reloads the active user's balance and portfolio from the server
    public void refresh()
    {
        String username = state.getActiveUser();
        if (username == null) {
            return;
        }

        Task<UserDetailsDTO> fetchUserDTOTask = new Task<>()
        {
            @Override
            protected UserDetailsDTO call() throws Exception
            {
                return clientUtil.getUserDetails(username);
            }
        };

        fetchUserDTOTask.setOnSucceeded(e ->
        {
            UserDetailsDTO details = fetchUserDTOTask.getValue();
            profileUsernameLabel.setText(details.name());
            currentBalance = details.balance();
            profileBalanceLabel.setText(String.format("$%.2f", details.balance()));
            balanceTableView.getItems().setAll(details.balanceHistory());

            // keep the selected row (and its overview) across refreshes
            portfolioTableView.getItems().setAll(details.portfolio());
            if (viewedItem != null) {
                for (PortfolioItemDTO item : details.portfolio()) {
                    if (isSameHolding(item, viewedItem)) {
                        viewedItem = item;
                        portfolioTableView.getSelectionModel().select(item);
                        break;
                    }
                }
            }
        });

        fetchUserDTOTask.setOnFailed(e -> {
            System.err.println("Failed to load profile: " + fetchUserDTOTask.getException().getMessage());
        });

        Thread thread = new Thread(fetchUserDTOTask);
        thread.setDaemon(true);
        thread.start();
    }

    // clears the event overview (e.g. after a new file is loaded); the active user stays
    public void reset() {
        viewedItem = null;
        portfolioTableView.getSelectionModel().clearSelection();
        portfolioTableView.getItems().clear();
        userEventOptionsBox.getChildren().clear();
        userEventTitleLabel.setText("Select an event from your portfolio to view its current state.");
    }

    // shares and profit/loss change between refreshes, so rows are matched by event and option only
    private static boolean isSameHolding(PortfolioItemDTO a, PortfolioItemDTO b) {
        return a.eventName().equals(b.eventName()) && a.optionName().equals(b.optionName());
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
                return clientUtil.getUserEventOverview(username, event.name());
            }
        };

        fetchUserEventOverviewTask.setOnSucceeded(e ->
        {
            UserEventOverviewDTO overview = fetchUserEventOverviewTask.getValue();

            VBox contentBox = new VBox(10);

            Label commLabel = new Label(String.format("Total Commission Paid: $%.2f", overview.totalCommissionPaid()));
            commLabel.getStyleClass().add("overview-comm-label");
            contentBox.getChildren().add(commLabel);

            if (isOrderBook) {
                Label obLabel = new Label("Order Book Holdings & Costs:");
                obLabel.getStyleClass().add("overview-header-label");
                contentBox.getChildren().add(obLabel);

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
            System.err.println("Failed to load event overview: " + fetchUserEventOverviewTask.getException().getMessage());
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

    private void setupFundsButtons() {
        UiUtils.attachClickAnimation(addFundsBtn, state);
        UiUtils.attachClickAnimation(withdrawFundsBtn, state);

        addFundsBtn.setOnAction(e ->
                FundsDialog.askDeposit(currentBalance, state.getThemeStylesheets())
                        .ifPresent(amount -> sendFundsRequest(true, amount)));

        withdrawFundsBtn.setOnAction(e ->
                FundsDialog.askWithdraw(currentBalance, state.getThemeStylesheets())
                        .ifPresent(amount -> sendFundsRequest(false, amount)));
    }

    // sends the deposit/withdraw to the server, then shows the new balance or the server's error
    private void sendFundsRequest(boolean isDeposit, double amount) {
        String username = state.getActiveUser();
        addFundsBtn.setDisable(true);
        withdrawFundsBtn.setDisable(true);

        Task<Double> fundsTask = new Task<>() {
            @Override
            protected Double call() throws Exception {
                return isDeposit
                        ? clientUtil.depositFunds(username, amount)
                        : clientUtil.withdrawFunds(username, amount);
            }
        };

        fundsTask.setOnSucceeded(e -> {
            addFundsBtn.setDisable(false);
            withdrawFundsBtn.setDisable(false);
            currentBalance = fundsTask.getValue();
            profileBalanceLabel.setText(String.format("$%.2f", currentBalance));
            // brings the new row into the balance history right away
            refresh();
            UiUtils.showAlert(Alert.AlertType.INFORMATION, isDeposit ? "Deposit" : "Withdrawal",
                    String.format("%s $%.2f.%nNew balance: $%.2f",
                            isDeposit ? "Deposited" : "Withdrew", amount, currentBalance),
                    state.getThemeStylesheets());
        });

        fundsTask.setOnFailed(e -> {
            addFundsBtn.setDisable(false);
            withdrawFundsBtn.setDisable(false);
            Throwable error = fundsTask.getException();
            String message = error instanceof java.net.ConnectException
                    ? "Can't reach the server. Is Tomcat running?"
                    : error.getMessage();
            UiUtils.showAlert(Alert.AlertType.ERROR, isDeposit ? "Deposit failed" : "Withdrawal failed",
                    message, state.getThemeStylesheets());
        });

        Thread thread = new Thread(fundsTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void setupBalanceHistoryTable() {
        historyActionCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().action().equals("DEPOSIT") ? "Deposit" : "Withdraw"));
        historyAmountCol.setCellValueFactory(cellData -> {
            BalanceHistoryItemDTO item = cellData.getValue();
            String sign = item.action().equals("DEPOSIT") ? "+" : "-";
            return new SimpleStringProperty(String.format("%s$%.2f", sign, item.amount()));
        });
        historyDetailsCol.setCellValueFactory(cellData -> {
            BalanceHistoryItemDTO item = cellData.getValue();
            String time = Instant.ofEpochMilli(item.timestamp()).atZone(ZoneId.systemDefault()).format(HISTORY_TIME_FORMAT);
            return new SimpleStringProperty(String.format("Balance: $%.2f  ·  %s", item.balanceAfter(), time));
        });

        balanceTableView.setRowFactory(tv -> UiUtils.createAnimatedRow(state));
        balanceTableView.setPlaceholder(new Label("No balance history yet."));
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

        // only a click on a different row loads a new overview (refresh() re-selects the same one)
        portfolioTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection == null || (viewedItem != null && isSameHolding(newSelection, viewedItem))) {
                return;
            }
            viewedItem = newSelection;

            Task<List<EventSummaryDTO>> fetchEventsTask = new Task<>() {
                @Override
                protected List<EventSummaryDTO> call() throws Exception {
                    return clientUtil.getEventsSummary();
                }
            };

            fetchEventsTask.setOnSucceeded(e -> {
                fetchEventsTask.getValue().stream()
                        .filter(ev -> ev.name().equals(newSelection.eventName()))
                        .findFirst()
                        .ifPresent(ev -> populateUserEventOverview(ev, state.getActiveUser()));
            });

            fetchEventsTask.setOnFailed(e -> {
                System.err.println("Failed to fetch events for portfolio click: " + fetchEventsTask.getException().getMessage());
            });

            Thread thread = new Thread(fetchEventsTask);
            thread.setDaemon(true);
            thread.start();
        });
    }
}