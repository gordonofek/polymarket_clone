package ui;

import engine.Engine;
import engine.EngineInterface;
import engine.dto.*;

import internals.ActionType;
import javafx.animation.Animation;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javafx.animation.ScaleTransition;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

public class MainController {

    EngineInterface engine = new Engine();

    // Top part
    @FXML private Button loadFileBtn;
    @FXML private TextField filePathField;
    @FXML ProgressBar progressBar;

    // Active user - events
    @FXML private Label userNameLabelEvents;
    @FXML private Label userBalanceLabelEvents;

    // Tab
    @FXML private Tab usersTab;

    // Filters
    @FXML private TextField searchField;
    @FXML private Button clearFiltersBtn;
    @FXML ComboBox<String> methodFilter;
    @FXML ComboBox<String> statusFilter;
    @FXML ComboBox<String> commissionFilter;

    // Events display
    @FXML private VBox eventsContainer;

    // Events
    private List<EventSummaryDTO> allEvents = new ArrayList<>();
    private EventSummaryDTO selectedEvent = null;

    // Events details and Trade panel
    @FXML private Label detailIdLabel;
    @FXML private Label detailMethodLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailDescLabel;
    @FXML private Label detailCommLabel;
    @FXML private VBox optionsContainer;
    @FXML private Label historyTitleLabel;
    @FXML private ListView<Label> participationsHistoryListView;
    @FXML private ListView<Label> eventparticipationsListView;

    @FXML private LineChart<Number, Number> priceHistoryChart;

    @FXML private HBox mmControlsBox;
    @FXML private Button startEventBtn;
    @FXML private Button closeEventBtn;

    // users list
    @FXML private TableView<UserSummaryDTO> usersTableView;
    @FXML private TableColumn<UserSummaryDTO, String> userNameCol;
    @FXML private TableColumn<UserSummaryDTO, Number> userBalanceCol;
    private String activeUsername = null;
    @FXML private Label activeUserDisplayLabel;

    // top header
    @FXML private Label userNameLabel;
    @FXML private Label userBalanceLabel;

    // portfolio table
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

    // Settings tab
    @FXML private VBox rootVBox;
    @FXML private CheckBox animationsToggle;
    @FXML private ComboBox<String> themeSelector;
    private boolean animationsEnabled = true;

    @FXML
    public void initialize() {

        setupUsersTable();
        setupPortfolioTable();
        setupEventListeners();

        // animations for market maker buttons
        attachClickAnimation(startEventBtn);
        attachClickAnimation(closeEventBtn);
    }

    public void loadData(List<EventSummaryDTO> events)
    {
        this.allEvents = events;
        updateEventList();

        List<UserSummaryDTO> users = engine.getAllUsers();
        usersTableView.getItems().setAll(users);
    }

    public void updateEventList()
    {
        // clear the events
        eventsContainer.getChildren().clear();
        // get the filters
        String search = searchField.getText().toLowerCase();
        String method = methodFilter.getValue();
        String status = statusFilter.getValue();
        String commission = commissionFilter.getValue();

        List<EventSummaryDTO> filteredEvents = allEvents.stream()
                .filter(event -> search.isEmpty() || event.name().toLowerCase().contains(search))
                .filter(event -> method == null || method.contains("All") || event.tradingMethodType().equalsIgnoreCase(method.replace(" ", "_")))
                .filter(event -> status == null || status.contains("All") || event.status().equalsIgnoreCase(status.replace(" ", "_")))
                .filter(event -> commission == null || commission.contains("All") || event.commissionType().equalsIgnoreCase(commission.replace(" ", "_")))
                .toList();

        // for every matching event, create a tile
        for (EventSummaryDTO event : filteredEvents) {
            GridPane tile = createEventTile(event);
            eventsContainer.getChildren().add(tile);
        }
    }

    private GridPane createEventTile(EventSummaryDTO eventSummary) {
        EventDetailsDTO details = engine.getEventState(eventSummary.id());

        GridPane tile = new GridPane();
        tile.getStyleClass().add("event-tile");

        // column constraints
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHalignment(HPos.RIGHT);
        tile.getColumnConstraints().addAll(col1, col2);

        // id, trading method, status
        Label idLabel = new Label("#" + eventSummary.id());
        idLabel.getStyleClass().add("event-id-label");

        String methodStr = eventSummary.tradingMethodType();
        Label methodLabel = new Label(eventSummary.tradingMethodType().replace("_", " "));
        methodLabel.getStyleClass().addAll("method-badge", "method-" + methodStr.toLowerCase());

        HBox topLeftBox = new HBox(8, idLabel, methodLabel);
        topLeftBox.setAlignment(Pos.CENTER_LEFT);

        // status
        Label statusLabel = new Label(eventSummary.status().replace("_", " "));
        statusLabel.getStyleClass().addAll("status-badge", "status-" + eventSummary.status().toLowerCase());
        statusLabel.setMinWidth(Label.USE_PREF_SIZE);

        // name, options header
        Label titleLabel = new Label(eventSummary.name());
        titleLabel.getStyleClass().add("event-title-label");

        Label optionsHeader = new Label("Available Options:");
        optionsHeader.getStyleClass().add("options-header-label");

        // description, options
        Label descLabel = new Label(eventSummary.description());
        descLabel.setWrapText(true);
        descLabel.getStyleClass().add("event-desc-label");
        GridPane.setValignment(descLabel, VPos.TOP); // Align text to top

        HBox optionsBox = new HBox(8);
        optionsBox.setAlignment(Pos.TOP_RIGHT);

        // buttons to event options
        for (OptionDetailsDTO option : details.options()) {
            Button optBtn = new Button(String.format("%s\n$%.2f", option.optionName(), option.currentPrice()));
            optBtn.getStyleClass().add("option-preview-btn");
            optBtn.setTextAlignment(TextAlignment.CENTER);
            optBtn.setMinWidth(Button.USE_PREF_SIZE);

            // Highlight if this is the winning option
            if (eventSummary.status().equals("CLOSED") && details.winningOption() != null && details.winningOption().equals(option.optionName())) {
                optBtn.getStyleClass().add("winning-option-btn");
            }

            optionsBox.getChildren().add(optBtn);
        }

        // commission
        Label commTitle = new Label("Commission:");
        commTitle.getStyleClass().add("comm-title-label");

        String commTypeStr = eventSummary.commissionType().replace("_", " ");
        Label commVal = new Label(eventSummary.commission() + "% (" + commTypeStr + ")");
        commVal.getStyleClass().add("comm-val-label");

        HBox commBox = new HBox(6, commTitle, commVal);
        commBox.setAlignment(Pos.BOTTOM_LEFT);
        commBox.setPadding(new Insets(10, 0, 0, 0));

        portfolioTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                // Find the event in allEvents
                EventSummaryDTO selectedPortfolioEvent = allEvents.stream()
                        .filter(e -> e.name().equals(newSelection.eventName()))
                        .findFirst()
                        .orElse(null);

                if (selectedPortfolioEvent != null) {
                    populateUserEventOverview(selectedPortfolioEvent, activeUsername);
                }
            }
        });

        // add to grid
        tile.add(topLeftBox, 0, 0);
        tile.add(statusLabel, 1, 0);

        tile.add(titleLabel, 0, 1);
        tile.add(optionsHeader, 1, 1);

        tile.add(descLabel, 0, 2);
        tile.add(optionsBox, 1, 2);
        GridPane.setRowSpan(optionsBox, 2); // Allow options box to span across rows 2 and 3

        tile.add(commBox, 0, 3);


        // ----------- Testing Animation --------------
        ScaleTransition clickAnimation = new ScaleTransition(Duration.millis(200), tile);
        clickAnimation.setFromX(1.0);
        clickAnimation.setFromY(1.0);
        clickAnimation.setToX(1.02);
        clickAnimation.setToY(1.02);
        clickAnimation.setAutoReverse(true);
        clickAnimation.setCycleCount(2);

        tile.setOnMouseClicked(e -> {
            if(animationsEnabled && clickAnimation.getStatus() != Animation.Status.RUNNING) {
                clickAnimation.playFromStart();
            }
            handleTileClick(eventSummary);
        });

        return tile;
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

    @FXML
    private void handleTileClick(EventSummaryDTO eventSummary)
    {
        this.selectedEvent = eventSummary;

        boolean isOrderBook = eventSummary.tradingMethodType().contains("ORDER_BOOK");

        detailIdLabel.setText("#" + eventSummary.id());

        String methodStr = eventSummary.tradingMethodType();
        detailMethodLabel.setText(methodStr.replace("_", " "));
        detailMethodLabel.getStyleClass().setAll("label", "method-badge", "method-" + methodStr.toLowerCase());

        String statusStr = eventSummary.status().replace("_", " ");
        detailStatusLabel.setText(statusStr);
        detailStatusLabel.getStyleClass().setAll("label", "status-badge", "status-" + eventSummary.status().toLowerCase());

        detailTitleLabel.setText(eventSummary.name());
        detailDescLabel.setText(eventSummary.description());

        String commTypeStr = eventSummary.commissionType().replace("_", " ");
        detailCommLabel.setText(eventSummary.commission() + "% (" + commTypeStr + ")");

        optionsContainer.getChildren().clear();

        EventDetailsDTO historyDetails = engine.getEventState(eventSummary.id());
        List<ParticipantHoldingDTO> participantsDetails = engine.getEventParticipants(eventSummary.id());

        if (historyTitleLabel != null) {
            historyTitleLabel.setText(isOrderBook ? "Order Book Queue" : "Participation History");
        }

        for (OptionDetailsDTO option : historyDetails.options()) {
            double price = option.currentPrice(); // LAST price in Order Book
            String optionName = option.optionName();

            HBox row = new HBox(20);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPrefHeight(50);
            row.getStyleClass().add("option-card");

            Label nameLbl = new Label(optionName);
            nameLbl.getStyleClass().add("option-name-label");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Dynamic Option Stats
            Label statsLbl = new Label();
            statsLbl.getStyleClass().add("option-percent-label");

            if (isOrderBook) {
                // Display the required Order Book statistics
                statsLbl.setText(String.format("Bid: $%.2f  |  Ask: $%.2f  |  Mid: $%.2f  |  Spread: $%.2f",
                        option.highestBid(), option.lowestAsk(), option.midPrice(), option.spread()));
            } else {
                // Display standard LMSR percentage
                statsLbl.setText(String.format("%.1f%%", price * 100));
            }

            Label priceLbl = new Label(String.format("$%.2f", price));
            priceLbl.getStyleClass().add("option-price-label");

            HBox actionButtons = new HBox(10);
            actionButtons.setAlignment(Pos.CENTER_RIGHT);

            String buyText = isOrderBook ? "Buy" : String.format("Buy %.2f¢", price * 100);
            Button buyBtn = new Button(buyText);
            buyBtn.getStyleClass().add("buy-option-btn");
            buyBtn.setOnAction(e -> handleBuyAction(eventSummary, optionName));
            attachClickAnimation(buyBtn);
            actionButtons.getChildren().add(buyBtn);

            if(isOrderBook) {
                Button sellBtn = new Button("Sell");
                sellBtn.getStyleClass().add("sell-option-btn");
                sellBtn.setOnAction(e -> handleSellAction(eventSummary, optionName));
                attachClickAnimation(sellBtn);
                actionButtons.getChildren().add(sellBtn);
            }

            row.getChildren().addAll(nameLbl, spacer, statsLbl, priceLbl, actionButtons);
            optionsContainer.getChildren().add(row);
        }

        // trade history of the event
        // trade history OR order book queues
        participationsHistoryListView.getItems().clear();

        if (isOrderBook) {
            // ORDER BOOK: Display pending Buy/Sell Queues
            boolean hasPendingOrders = false;
            for (OptionDetailsDTO option : historyDetails.options()) {

                // Add Buy Requests
                for (PendingRequestDTO req : option.buyQueue()) {
                    hasPendingOrders = true;
                    String text = String.format("[BUY] User: %s | Option: %s | Amount: %d | Limit: $%.2f",
                            req.username(), option.optionName(), req.amount(), req.price());
                    Label reqLabel = new Label(text);
                    reqLabel.setMaxWidth(Double.MAX_VALUE);
                    reqLabel.getStyleClass().add("history-buy"); // Reusing your green style
                    participationsHistoryListView.getItems().add(reqLabel);
                }

                // Add Sell Requests
                for (PendingRequestDTO req : option.sellQueue()) {
                    hasPendingOrders = true;
                    String text = String.format("[SELL] User: %s | Option: %s | Amount: %d | Limit: $%.2f",
                            req.username(), option.optionName(), req.amount(), req.price());
                    Label reqLabel = new Label(text);
                    reqLabel.setMaxWidth(Double.MAX_VALUE);
                    reqLabel.getStyleClass().add("history-sell"); // Reusing your red style
                    participationsHistoryListView.getItems().add(reqLabel);
                }
            }

            if (!hasPendingOrders) {
                participationsHistoryListView.getItems().add(new Label("The Order Book is currently empty."));
            }

        } else {
            // LMSR: Display executed trade history
            List<TradeRecordDTO> history = historyDetails.tradeHistory();
            if(history.isEmpty()) {
                Label emptyLbl = new Label("No trades recorded yet for this event. :C");
                participationsHistoryListView.getItems().add(emptyLbl);
            } else {
                for (TradeRecordDTO trade : history) {
                    String text = String.format("[%s] Option: %s | Amount: %d | Paid: $%.2f",
                            trade.action(), trade.optionName(), trade.amount(), trade.pricePaid());
                    Label tradeLabel = new Label(text);
                    tradeLabel.setMaxWidth(Double.MAX_VALUE);

                    if (trade.action() == ActionType.BUY) {
                        tradeLabel.getStyleClass().add("history-buy");
                    } else {
                        tradeLabel.getStyleClass().add("history-sell");
                    }
                    participationsHistoryListView.getItems().add(tradeLabel);
                }
            }
        }

        // users participating in the event
        eventparticipationsListView.getItems().clear();
        if(participantsDetails.isEmpty()) {
            Label emptyParticipantsLbl = new Label("No Users have taken part in this event. :C");
            eventparticipationsListView.getItems().add(emptyParticipantsLbl);
        }
        else {
            for (ParticipantHoldingDTO participant : participantsDetails) {
                String username = participant.username();
                for (Map.Entry<String, Integer> holding : participant.shares().entrySet()) {
                    String optionName = holding.getKey();
                    int shareCount = holding.getValue();
                    if (shareCount > 0 ) {
                        String text = String.format("User: %s | Option '%s' | Shares: %d", username, optionName, shareCount);
                        Label holdingLabel = new Label(text);
                        holdingLabel.getStyleClass().add("holding-label");
                        eventparticipationsListView.getItems().add(holdingLabel);
                    }
                }
            }
        }
        updatePriceGraph(historyDetails);

        boolean isMarketMaker = engine.isUserMarketMaker(activeUsername, eventSummary.id());
        String status = eventSummary.status();
        mmControlsBox.setVisible(isMarketMaker);
        mmControlsBox.setManaged(isMarketMaker);

        boolean canStat = isMarketMaker && status.equals("NOT_STARTED");
        startEventBtn.setVisible(canStat);
        startEventBtn.setManaged(canStat);

        boolean canClose = isMarketMaker && status.equals("OPEN");
        closeEventBtn.setVisible(canClose);
        closeEventBtn.setManaged(canClose);
    }

    /** #####################################
     *      Dialog Helped function
     *  #####################################
     */

    private void styleDialog(Dialog<?> dialog) {
        // Applying the current active theme to the popup
        if (rootVBox != null && !rootVBox.getStylesheets().isEmpty()) {
            dialog.getDialogPane().getStylesheets().addAll(rootVBox.getStylesheets());
        }
        // Adds a custom class so we can style the popup container
        dialog.getDialogPane().getStyleClass().add("modern-dialog");
    }

    @FXML
    private void handleStartEventAction() {
        if (selectedEvent == null || activeUsername == null) return;

        TradeReceiptDTO receipt = engine.startEvent(activeUsername, selectedEvent.id());

        if (receipt.isSuccess()) {
            showAlert(Alert.AlertType.INFORMATION, "Event Started", String.format("Event successfully opened!\nCost deducted: $%.2f", receipt.totalCost()));

            updateEvents();
        }
        else {
            showAlert(Alert.AlertType.ERROR, "Cannot start the event", receipt.message());
        }
    }

    @FXML
    private void handleCloseEventAction() {
        if (selectedEvent == null || activeUsername == null) return;

        EventDetailsDTO details = engine.getEventState(selectedEvent.id());
        List<String> optionNames = new ArrayList<>();
        for (OptionDetailsDTO option : details.options()) {
            optionNames.add(option.optionName());
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(optionNames.get(0), optionNames);
        dialog.setTitle("Close Event");
        dialog.setHeaderText("Closing: " + selectedEvent.name());
        dialog.setContentText("Select the winning option:");
        styleDialog(dialog);

        dialog.showAndWait().ifPresent(winningOption -> {
            CloseEventReceiptDTO receipt = engine.closeEvent(selectedEvent.id(), winningOption);

            if (receipt.isSuccess()) {
                String msg = String.format("Event Closed!\nWinner: %s\nTotal Payout: $%.2f\nCommission Taken: $%.2f\nReturned to MM: $%.2f",
                        receipt.winningOption(), receipt.totalPayout(), receipt.commissionTaken(), receipt.leftoverPool());
                showAlert(Alert.AlertType.INFORMATION, "Event Closed", msg);

                updateEvents();
            } else {
                showAlert(Alert.AlertType.ERROR, "Error Closing Event", receipt.message());
            }
        });
    }

    private void updateEvents()
    {
        this.allEvents = engine.getAllEvents();
        for (EventSummaryDTO event : this.allEvents) {
            if (event.id() == selectedEvent.id()) {
                this.selectedEvent = event;
                break;
            }
        }
        updateEventList();
        updateBalance(activeUsername);
        handleTileClick(selectedEvent);
    }

    private void handleBuyAction(EventSummaryDTO event, String optionName) {
        boolean isOrderBook = event.tradingMethodType().toUpperCase().contains("ORDER_BOOK");
        showTradeDialog(event, optionName, ActionType.BUY, isOrderBook);
        updateBalance(activeUsername);
    }

    private void handleSellAction(EventSummaryDTO event, String optionName) {
        showTradeDialog(event, optionName, ActionType.SELL, true);
        updateBalance(activeUsername);
    }

    private void showTradeDialog(EventSummaryDTO event, String optionName, ActionType action, boolean isOrderBook) {
        if (this.activeUsername == null) {
            showAlert(Alert.AlertType.WARNING, "No User Selected", "Please go to the Users tab and select a user to trade.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(action.name() + " Shares - " + optionName);
        dialog.setHeaderText("Event: " + event.name() + "\nOption: " + optionName);
        styleDialog(dialog);

        // Set the button types
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField amountField = new TextField();
        amountField.setPromptText("e.g. 10");
        grid.add(new Label("Amount of shares:"), 0, 0);
        grid.add(amountField, 1, 0);

        TextField priceField = new TextField();
        priceField.setPromptText("e.g. 0.50");

        // Only show the price limit field if it's an Order Book event
        if (isOrderBook) {
            grid.add(new Label("Price limit ($):"), 0, 1);
            grid.add(priceField, 1, 1);
        }

        dialog.getDialogPane().setContent(grid);

        // Convert the result when OK is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    int amount = Integer.parseInt(amountField.getText().trim());
                    if (amount <= 0) {
                        showAlert(Alert.AlertType.ERROR, "Invalid Input", "Amount must be greater than 0.");
                        return null;
                    }

                    double priceLimit = 0.0;
                    if (isOrderBook) {
                        priceLimit = Double.parseDouble(priceField.getText().trim());
                        if (priceLimit <= 0) {
                            showAlert(Alert.AlertType.ERROR, "Invalid Input", "Price limit must be greater than 0.");
                            return null;
                        }
                    }

                    // Send the trade to the engine
                    TradeReceiptDTO receipt = engine.placeOrder(activeUsername ,event.id(), optionName, amount, action, priceLimit);

                    if (receipt.isSuccess()) {
                        String msg;

                        // Check if the order was just queued (no money exchanged hands yet)
                        if (receipt.totalCost() == 0.0 && receipt.commission() == 0.0) {
                            msg = "Trade submitted to the queue.";
                        } else {
                            // The trade actually executed right away
                            msg = String.format("Trade successful!\nTotal Paid: $%.2f\nCommission: $%.2f",
                                    receipt.totalCost(), receipt.commission());
                        }

                        showAlert(Alert.AlertType.INFORMATION, "Success", msg);

                        // Immediately re-trigger the click event to refresh the right panel with new prices!
                        handleTileClick(event);
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Trade Failed", receipt.message());
                    }

                } catch (NumberFormatException e) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter valid numeric values.");
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        styleDialog(alert);
        alert.showAndWait();
    }

    private void updatePriceGraph(EventDetailsDTO details) {
        priceHistoryChart.getData().clear();

        for (OptionDetailsDTO option : details.options()) {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(option.optionName());

            int tradeCounter = 1;
            List<TradeRecordDTO> history = details.tradeHistory();

            // Loop backwards because history is newest-to-oldest, but graphs read left-to-right!
            for (int i = history.size() - 1; i >= 0; i--) {
                TradeRecordDTO trade = history.get(i);

                if (trade.optionName().equals(option.optionName())) {
                    // Calculate unit price so it fits on the 0.0 to 1.0 Y-axis
                    double unitPrice = trade.amount() > 0 ? trade.pricePaid() / trade.amount() : 0.0;
                    series.getData().add(new XYChart.Data<>(tradeCounter, unitPrice));
                    tradeCounter++;
                }
            }

            // Optional: Plot the CURRENT price as the final point on the graph
            XYChart.Data<Number, Number> lastPoint = new XYChart.Data<>(tradeCounter, option.currentPrice());
            series.getData().add(lastPoint);

            priceHistoryChart.getData().add(series);

            // Tag only the last point as the glowing "current price" dot (see
            // .poly-chart .endpoint-marker in default-theme.css). All other
            // per-point symbols stay invisible so the line itself reads as clean.
            if (lastPoint.getNode() != null) {
                lastPoint.getNode().getStyleClass().add("endpoint-marker");
            }
        }
    }

    private void updateBalance(String activeUsername) {
        UserDetailsDTO details = engine.getUserDetails(activeUsername);
        userBalanceLabelEvents.setText(String.format("$%.2f", details.balance()));
    }

    private void loadUserDetails(UserSummaryDTO user)
    {
        UserDetailsDTO details = engine.getUserDetails(user.name());
        userNameLabel.setText(details.name());
        userBalanceLabel.setText(String.format("$%.2f", details.balance()));
        portfolioTableView.getItems().clear();
        userEventOptionsBox.getChildren().clear();
        userEventTitleLabel.setText("Select an event from the portfolio to view it's current state");

        userNameLabelEvents.setText(details.name());
        userBalanceLabelEvents.setText(String.format("$%.2f", details.balance()));

        portfolioTableView.getItems().addAll(details.portfolio());
        activeUsername = details.name();
    }

    private void refreshUsersTab() {
        List<UserSummaryDTO> updatedUsers = engine.getAllUsers();
        usersTableView.getItems().setAll(updatedUsers);
        if(this.activeUsername != null) {
            for (UserSummaryDTO user : updatedUsers) {
                if (user.name().equals(this.activeUsername)) {
                    // reselect the user in the table
                    usersTableView.getSelectionModel().select(user);
                    loadUserDetails(user);
                    break;
                }
            }
        }
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
                    String text = String.format("[%s] Option: %s | Amount: %d | Paid: $%.2f",
                            trade.action(), trade.optionName(), trade.amount(), trade.pricePaid());
                    Label tradeLabel = new Label(text);

                    if (trade.action() == ActionType.BUY) {
                        tradeLabel.getStyleClass().add("history-buy");
                    } else {
                        tradeLabel.getStyleClass().add("history-sell");
                    }
                    contentBox.getChildren().add(tradeLabel);
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

    @FXML
    private void handleAnimationToggle() {
        this.animationsEnabled = animationsToggle.isSelected();
        animationsToggle.setText(animationsEnabled ? "On" : "Off");
    }

    @FXML
    private void handleThemeChange() {
        String selectedTheme = themeSelector.getValue();
        rootVBox.getStylesheets().clear();

        try {
            if (selectedTheme.equals("Dark Mode")) {
                rootVBox.getStylesheets().add(getClass().getResource("/ui/themes/dark-theme.css").toExternalForm());
            } else if (selectedTheme.equals("Ocean Blue")) {
                rootVBox.getStylesheets().add(getClass().getResource("/ui/themes/ocean-theme.css").toExternalForm());
            }
            else if(selectedTheme.equals("Light Purple")) {
                rootVBox.getStylesheets().add(getClass().getResource("/ui/themes/purple-theme.css").toExternalForm());
            }
            else {
                rootVBox.getStylesheets().add(getClass().getResource("/ui/themes/default-theme.css").toExternalForm());
            }
        } catch (NullPointerException e) {
            System.out.println("Warning: CSS file not found for " + selectedTheme);
            // Fallback to default if the file is missing
            rootVBox.getStylesheets().add(getClass().getResource("/ui/themes/default-theme.css").toExternalForm());
        }
    }

    private void attachClickAnimation(Button btn) {
        ScaleTransition pressAnim = new ScaleTransition(Duration.millis(100), btn);
        pressAnim.setToX(0.92);
        pressAnim.setToY(0.92);
        pressAnim.setAutoReverse(true);
        pressAnim.setCycleCount(2);

        btn.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (animationsEnabled && pressAnim.getStatus() != Animation.Status.RUNNING) {
                pressAnim.playFromStart();
            }
        });
    }

    /**
     * #########################################
     *      Initialize Helper function
     * #########################################
     */

    private void setupUsersTable() {
        userNameCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().name()));
        userBalanceCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().balance()));
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

        usersTableView.setRowFactory(tv -> {
            TableRow<UserSummaryDTO> row = new TableRow<>();
            ScaleTransition scaleAnim = new ScaleTransition(Duration.millis(100), row);
            scaleAnim.setToX(0.96);
            scaleAnim.setToY(0.96);
            scaleAnim.setAutoReverse(true);
            scaleAnim.setCycleCount(2);

            row.setOnMousePressed(event -> {
                if (!row.isEmpty() && animationsEnabled && scaleAnim.getStatus() != Animation.Status.RUNNING) {
                    scaleAnim.playFromStart();
                }
            });
            return row;
        });
    }

    private void setupPortfolioTable() {
        portfolioEventCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().eventName()));
        portfolioOptionCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().optionName()));
        portfolioSharesCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().shares()));
        portfolioStatusCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().status().replace("_", " ")));
        portfolioProfitLossCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().profitLoss()));
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

        portfolioTableView.setRowFactory(tv -> {
            TableRow<PortfolioItemDTO> row = new TableRow<>();
            ScaleTransition scaleAnim = new ScaleTransition(Duration.millis(100), row);
            scaleAnim.setToX(0.96);
            scaleAnim.setToY(0.96);
            scaleAnim.setAutoReverse(true);
            scaleAnim.setCycleCount(2);

            row.setOnMousePressed(event -> {
                if (!row.isEmpty() && animationsEnabled && scaleAnim.getStatus() != Animation.Status.RUNNING) {
                    scaleAnim.playFromStart();
                }
            });
            return row;
        });
    }

    private void setupEventListeners() {
        if (usersTab != null) {
            usersTab.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                if (isNowSelected) refreshUsersTab();
            });
        }

        searchField.textProperty().addListener((obs, oldText, newText) -> updateEventList());
        methodFilter.setOnAction(e -> updateEventList());
        statusFilter.setOnAction(e -> updateEventList());
        commissionFilter.setOnAction(e -> updateEventList());

        clearFiltersBtn.setOnAction(e -> {
            searchField.clear();
            methodFilter.getSelectionModel().selectFirst();
            statusFilter.getSelectionModel().selectFirst();
            commissionFilter.getSelectionModel().selectFirst();
        });

        usersTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                loadUserDetails(newSelection);
                this.activeUsername = newSelection.name();
                if (activeUserDisplayLabel != null) {
                    activeUserDisplayLabel.setText("Trading as: " + activeUsername);
                }
                if (selectedEvent != null) {
                    handleTileClick(selectedEvent);
                }
            }
        });
    }

}