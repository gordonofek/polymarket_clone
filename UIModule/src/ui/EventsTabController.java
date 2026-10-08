package ui;

import dto.*;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import javafx.animation.ScaleTransition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import ui.network.ClientUtil;

/**
 * Controller for the Events tab (events-tab.fxml):
 * the event list with its filters, the event details panel, trading and the market maker actions.
 */
public class EventsTabController {

    private AppState state;
    private ClientUtil clientUtil;

    // Active user - events
    @FXML private Label userNameLabelEvents;
    @FXML private Label userBalanceLabelEvents;

    // Filters
    @FXML private TextField searchField;
    @FXML private Button clearFiltersBtn;
    @FXML private ComboBox<String> methodFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> commissionFilter;

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

    // called by MainController once the whole window is loaded
    public void init(AppState state) {
        this.state = state;
        this.clientUtil = state.getClientUtil();

        userNameLabelEvents.setMinWidth(Region.USE_PREF_SIZE);
        userBalanceLabelEvents.setMinWidth(Region.USE_PREF_SIZE);
        setupEventListeners();

        // animations for market maker buttons
        UiUtils.attachClickAnimation(startEventBtn, state);
        UiUtils.attachClickAnimation(closeEventBtn, state);
    }

    // shows a freshly loaded list of events
    public void showEvents(List<EventSummaryDTO> events)
    {
        this.allEvents = events;
        updateEventList();
    }

    // clears the selected event so nothing points at the previously loaded file
    public void reset()
    {
        selectedEvent = null;

        // event details panel
        detailIdLabel.setText("");
        detailMethodLabel.setText("");
        detailMethodLabel.getStyleClass().setAll("label");
        detailStatusLabel.setText("");
        detailStatusLabel.getStyleClass().setAll("label");
        detailTitleLabel.setText("Select an event to view details");
        detailDescLabel.setText("Click any event tile on the left panel to display full specifications here.");
        detailCommLabel.setText("N/A");
        optionsContainer.getChildren().clear();
        participationsHistoryListView.getItems().clear();
        eventparticipationsListView.getItems().clear();
        priceHistoryChart.getData().clear();
        mmControlsBox.setVisible(false);
        mmControlsBox.setManaged(false);
    }

    private void updateEventList()
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
        // 1. Create the skeleton tile IMMEDIATELY so you have an object to return
        GridPane tile = new GridPane();
        tile.getStyleClass().add("event-tile");

        // Set column constraints
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHalignment(HPos.RIGHT);
        tile.getColumnConstraints().addAll(col1, col2);

        // Populate the static parts of the tile (from EventSummaryDTO)
        Label idLabel = new Label("#" + eventSummary.name());
        idLabel.getStyleClass().add("event-id-label");

        String methodStr = eventSummary.tradingMethodType();
        Label methodLabel = new Label(eventSummary.tradingMethodType().replace("_", " "));
        methodLabel.getStyleClass().addAll("method-badge", "method-" + methodStr.toLowerCase());

        HBox topLeftBox = new HBox(8, idLabel, methodLabel);
        topLeftBox.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label(eventSummary.status().replace("_", " "));
        statusLabel.getStyleClass().addAll("status-badge", "status-" + eventSummary.status().toLowerCase());
        statusLabel.setMinWidth(Label.USE_PREF_SIZE);

        Label titleLabel = new Label(eventSummary.name());
        titleLabel.getStyleClass().add("event-title-label");

        Label optionsHeader = new Label("Available Options:");
        optionsHeader.getStyleClass().add("options-header-label");

        Label descLabel = new Label(eventSummary.description());
        descLabel.setWrapText(true);
        descLabel.getStyleClass().add("event-desc-label");
        GridPane.setValignment(descLabel, VPos.TOP);

        Label commTitle = new Label("Commission:");
        commTitle.getStyleClass().add("comm-title-label");

        String commTypeStr = eventSummary.commissionType().replace("_", " ");
        Label commVal = new Label(eventSummary.commission() + "% (" + commTypeStr + ")");
        commVal.getStyleClass().add("comm-val-label");

        HBox commBox = new HBox(6, commTitle, commVal);
        commBox.setAlignment(Pos.BOTTOM_LEFT);
        commBox.setPadding(new Insets(10, 0, 0, 0));

        // Create an empty box for the dynamic options
        HBox optionsBox = new HBox(8);
        optionsBox.setAlignment(Pos.TOP_RIGHT);

        // Add static components to the grid
        tile.add(topLeftBox, 0, 0);
        tile.add(statusLabel, 1, 0);
        tile.add(titleLabel, 0, 1);
        tile.add(optionsHeader, 1, 1);
        tile.add(descLabel, 0, 2);
        tile.add(optionsBox, 1, 2);
        GridPane.setRowSpan(optionsBox, 2);
        tile.add(commBox, 0, 3);

        ScaleTransition clickAnimation = UiUtils.createPulseAnimation(tile, 200, 1.02);
        tile.setOnMouseClicked(mouseEvent -> {
            UiUtils.playIfEnabled(clickAnimation, state);
            handleTileClick(eventSummary);
        });

        // 2. Fetch the dynamic details in the background
        Task<EventDetailsDTO> fetchEventStateTask = new Task<>() {
            @Override
            protected EventDetailsDTO call() throws Exception {
                return clientUtil.getEventDetails(eventSummary.name());
            }
        };

        fetchEventStateTask.setOnSucceeded(e ->
        {
            // 3. When data arrives, populate the existing optionsBox
            EventDetailsDTO details = fetchEventStateTask.getValue();

            for (OptionDetailsDTO option : details.options()) {
                Button optBtn = new Button(String.format("%s\n$%.2f", option.optionName(), option.currentPrice()));
                optBtn.getStyleClass().add("option-preview-btn");
                optBtn.setTextAlignment(TextAlignment.CENTER);
                optBtn.setMinWidth(Button.USE_PREF_SIZE);

                if (eventSummary.status().equals("CLOSED") && details.winningOption() != null && details.winningOption().equals(option.optionName())) {
                    optBtn.getStyleClass().add("winning-option-btn");
                }

                optionsBox.getChildren().add(optBtn);
            }
        });

        fetchEventStateTask.setOnFailed(e -> {
            System.err.println("Failed to load event details for tile: " + fetchEventStateTask.getException().getMessage());
        });

        Thread thread = new Thread(fetchEventStateTask);
        thread.setDaemon(true);
        thread.start();

        // 4. Return the skeleton immediately so the UI loop can continue
        return tile;
    }

    // 2. BUNDLE THE DATA: Create a local record to hold all 3 server responses
    record TileDataBundle(EventDetailsDTO history, List<ParticipantHoldingDTO> participants, boolean isMarketMaker) {}

    private void handleTileClick(EventSummaryDTO eventSummary) {
        this.selectedEvent = eventSummary;
        boolean isOrderBook = eventSummary.tradingMethodType().contains("ORDER_BOOK");

        // 1. IMMEDIATE UI UPDATES (Run instantly so the UI feels responsive)
        detailIdLabel.setText("#" + eventSummary.name());

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

        // Clear previous data while waiting for the server
        optionsContainer.getChildren().clear();
        participationsHistoryListView.getItems().clear();
        eventparticipationsListView.getItems().clear();

        if (historyTitleLabel != null) {
            historyTitleLabel.setText(isOrderBook ? "Order Book Queue" : "Participation History");
        }

        // 3. BACKGROUND FETCH
        Task<TileDataBundle> fetchTileDataTask = new Task<>() {
            @Override
            protected TileDataBundle call() throws Exception {
                EventDetailsDTO history = clientUtil.getEventDetails(eventSummary.name());
                List<ParticipantHoldingDTO> participants = clientUtil.getEventParticipants(eventSummary.name());

                boolean isMarketMaker = false;
                if (state.getActiveUser() != null) {
                    isMarketMaker = clientUtil.isUserMarketMaker(state.getActiveUser(), eventSummary.name());
                }

                return new TileDataBundle(history, participants, isMarketMaker);
            }
        };

        // 4. DELEGATE UI POPULATION TO HELPER METHODS
        fetchTileDataTask.setOnSucceeded(e -> {
            TileDataBundle data = fetchTileDataTask.getValue();

            populateOptionsList(data.history(), eventSummary, isOrderBook);
            populateHistoryList(data.history(), isOrderBook);
            populateParticipantsList(data.participants());
            updateMarketMakerControls(data.isMarketMaker(), eventSummary.status());
            updatePriceGraph(data.history());
        });

        fetchTileDataTask.setOnFailed(e -> {
            System.err.println("Failed to load details for tile click: " + fetchTileDataTask.getException().getMessage());
        });

        Thread thread = new Thread(fetchTileDataTask);
        thread.setDaemon(true);
        thread.start();
    }

    // --- HELPER METHODS ---

    private void populateOptionsList(EventDetailsDTO historyDetails, EventSummaryDTO eventSummary, boolean isOrderBook) {
        for (OptionDetailsDTO option : historyDetails.options()) {
            double price = option.currentPrice();
            String optionName = option.optionName();

            HBox row = new HBox(20);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMinHeight(Region.USE_PREF_SIZE);
            row.getStyleClass().add("option-card");
            row.setPadding(new Insets(10, 15, 10, 15));

            Label nameLbl = new Label(optionName);
            nameLbl.getStyleClass().add("option-name-label");
            nameLbl.setMinWidth(Region.USE_PREF_SIZE);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label statsLbl = new Label();
            statsLbl.getStyleClass().add("option-percent-label");
            statsLbl.setTextAlignment(TextAlignment.CENTER);
            statsLbl.setWrapText(false);
            statsLbl.setMinWidth(0);

            Label priceLbl = new Label(String.format("$%.2f", price));
            priceLbl.getStyleClass().add("option-price-label");
            priceLbl.setMinWidth(Region.USE_PREF_SIZE);

            HBox actionButtons = new HBox(10);
            actionButtons.setAlignment(Pos.CENTER_RIGHT);
            actionButtons.setMinWidth(Region.USE_PREF_SIZE);

            String buyText = isOrderBook ? "Buy" : String.format("Buy %.2f¢", price * 100);
            Button buyBtn = new Button(buyText);
            buyBtn.getStyleClass().add("buy-option-btn");
            buyBtn.setMinWidth(Button.USE_PREF_SIZE);
            buyBtn.setOnAction(evt -> handleBuyAction(eventSummary, optionName));
            UiUtils.attachClickAnimation(buyBtn, state);
            actionButtons.getChildren().add(buyBtn);

            if(isOrderBook) {
                Button sellBtn = new Button("Sell");
                sellBtn.getStyleClass().add("sell-option-btn");
                sellBtn.setMinWidth(Button.USE_PREF_SIZE);
                sellBtn.setOnAction(evt -> handleSellAction(eventSummary, optionName));
                UiUtils.attachClickAnimation(sellBtn, state);
                actionButtons.getChildren().add(sellBtn);
            }

            if (isOrderBook) {
                statsLbl.setText(String.format("Bid: $%.2f  |  Ask: $%.2f\nMid: $%.2f  |  Spread: $%.2f",
                        option.highestBid(), option.lowestAsk(), option.midPrice(), option.spread()));

                Region rightSpacer = new Region();
                HBox.setHgrow(rightSpacer, Priority.ALWAYS);
                row.getChildren().addAll(nameLbl, spacer, statsLbl, rightSpacer, priceLbl, actionButtons);
            } else {
                statsLbl.setText(String.format("%.1f%%", price * 100));
                row.getChildren().addAll(nameLbl, spacer, statsLbl, priceLbl, actionButtons);
            }
            optionsContainer.getChildren().add(row);
        }
    }

    private void populateHistoryList(EventDetailsDTO historyDetails, boolean isOrderBook) {
        if (isOrderBook) {
            boolean hasPendingOrders = false;
            for (OptionDetailsDTO option : historyDetails.options()) {
                for (PendingRequestDTO req : option.buyQueue()) {
                    hasPendingOrders = true;
                    addPendingRequestLabel(req, option.optionName(), true);
                }
                for (PendingRequestDTO req : option.sellQueue()) {
                    hasPendingOrders = true;
                    addPendingRequestLabel(req, option.optionName(), false);
                }
            }
            if (!hasPendingOrders) {
                participationsHistoryListView.getItems().add(new Label("The Order Book is currently empty."));
            }
        } else {
            List<TradeRecordDTO> history = historyDetails.tradeHistory();
            if(history.isEmpty()) {
                Label emptyLbl = new Label("No trades recorded yet for this event. :C");
                participationsHistoryListView.getItems().add(emptyLbl);
            } else {
                for (TradeRecordDTO trade : history) {
                    Label tradeLabel = UiUtils.createTradeLabel(trade);
                    tradeLabel.setMaxWidth(Double.MAX_VALUE);
                    participationsHistoryListView.getItems().add(tradeLabel);
                }
            }
        }
    }

    private void populateParticipantsList(List<ParticipantHoldingDTO> participantsDetails) {
        if(participantsDetails.isEmpty()) {
            Label emptyParticipantsLbl = new Label("No Users have taken part in this event. :C");
            eventparticipationsListView.getItems().add(emptyParticipantsLbl);
        } else {
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
    }

    private void updateMarketMakerControls(boolean isMarketMaker, String status) {
        mmControlsBox.setVisible(isMarketMaker);
        mmControlsBox.setManaged(isMarketMaker);

        boolean canStat = isMarketMaker && status.equals("NOT_STARTED");
        startEventBtn.setVisible(canStat);
        startEventBtn.setManaged(canStat);

        boolean canClose = isMarketMaker && status.equals("OPEN");
        closeEventBtn.setVisible(canClose);
        closeEventBtn.setManaged(canClose);
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

    /** #####################################
     *      Dialog Helped function
     *  #####################################
     */

    // applies the current theme to the popup
    private void styleDialog(Dialog<?> dialog) {
        UiUtils.styleDialog(dialog, state.getThemeStylesheets());
    }

    @FXML
    private void handleStartEventAction() {
        if (selectedEvent == null || state.getActiveUser() == null) return;

        Task<TradeReceiptDTO> fetchTradeReceiptDTO = new Task<>() {
            @Override
            protected TradeReceiptDTO call() throws Exception {
                return clientUtil.startEvent(state.getActiveUser(), selectedEvent.name());
            }
        };

        fetchTradeReceiptDTO.setOnSucceeded(e -> {
            TradeReceiptDTO tradeReceipt = fetchTradeReceiptDTO.getValue();

            // Check the game logic success/failure INSIDE the Succeeded block
            if (tradeReceipt.isSuccess()) {
                showAlert(Alert.AlertType.INFORMATION, "Event Started",
                        String.format("Event successfully opened!\nCost deducted: $%.2f", tradeReceipt.totalCost()));
                updateEvents();
            } else {
                showAlert(Alert.AlertType.ERROR, "Cannot start the event", tradeReceipt.message());
            }
        });

        fetchTradeReceiptDTO.setOnFailed(e -> {
            // Handle actual network or Java exceptions here
            showAlert(Alert.AlertType.ERROR, "Network Error",
                    "Failed to connect to server: " + fetchTradeReceiptDTO.getException().getMessage());
        });

        Thread thread = new Thread(fetchTradeReceiptDTO);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleCloseEventAction() {
        if (selectedEvent == null || state.getActiveUser() == null) return;

        // TASK 1: Fetch the options for the dropdown menu
        Task<EventDetailsDTO> fetchDetailsTask = new Task<>() {
            @Override
            protected EventDetailsDTO call() throws Exception {
                return clientUtil.getEventDetails(selectedEvent.name());
            }
        };

        fetchDetailsTask.setOnSucceeded(e ->
        {
            EventDetailsDTO details = fetchDetailsTask.getValue();
            List<String> optionNames = new ArrayList<>();
            for (OptionDetailsDTO option : details.options()) {
                optionNames.add(option.optionName());
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(optionNames.get(0), optionNames);
            dialog.setTitle("Close Event");
            dialog.setHeaderText("Closing: " + selectedEvent.name());
            dialog.setContentText("Select the winning option:");
            styleDialog(dialog);

            // Wait for user to select an option and click OK
            dialog.showAndWait().ifPresent(winningOption -> {

                // TASK 2: Send the close command to the server
                Task<CloseEventReceiptDTO> closeTask = new Task<>() {
                    @Override
                    protected CloseEventReceiptDTO call() throws Exception {
                        return clientUtil.closeEvent(selectedEvent.name(), winningOption);
                    }
                };

                closeTask.setOnSucceeded(evt -> {
                    CloseEventReceiptDTO receipt = closeTask.getValue();
                    if (receipt.isSuccess()) {
                        String msg = String.format("Event Closed!\nWinner: %s\nTotal Payout: $%.2f\nCommission Taken: $%.2f\nReturned to MM: $%.2f",
                                receipt.winningOption(), receipt.totalPayout(), receipt.commissionTaken(), receipt.leftoverPool());
                        showAlert(Alert.AlertType.INFORMATION, "Event Closed", msg);
                        updateEvents();
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Error Closing Event", receipt.message());
                    }
                });

                Thread t2 = new Thread(closeTask);
                t2.setDaemon(true);
                t2.start();
            });
        });

        Thread t1 = new Thread(fetchDetailsTask);
        t1.setDaemon(true);
        t1.start();
    }

    public void updateEvents() {
        Task<List<EventSummaryDTO>> fetchEventsTask = new Task<>() {
            @Override
            protected List<EventSummaryDTO> call() throws Exception {
                return clientUtil.getEventsSummary();
            }
        };

        fetchEventsTask.setOnSucceeded(e -> {
            this.allEvents = fetchEventsTask.getValue();

            // Re-find the selected event from the fresh data
            for (EventSummaryDTO event : this.allEvents) {
                if (selectedEvent != null && Objects.equals(event.name(), selectedEvent.name())) {
                    this.selectedEvent = event;
                    break;
                }
            }

            updateEventList();
            updateBalance();
            if (selectedEvent != null) {
                handleTileClick(selectedEvent);
            }
        });

        Thread thread = new Thread(fetchEventsTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void handleBuyAction(EventSummaryDTO event, String optionName) {
        boolean isOrderBook = event.tradingMethodType().toUpperCase().contains("ORDER_BOOK");
        showTradeDialog(event, optionName, ActionType.BUY, isOrderBook);
        updateBalance();
    }

    private void handleSellAction(EventSummaryDTO event, String optionName) {
        showTradeDialog(event, optionName, ActionType.SELL, true);
        updateBalance();
    }

    // A temporary container to hold the validated input from the dialog
    private record TradeInput(int amount, double limitPrice) {}

    private void showTradeDialog(EventSummaryDTO event, String optionName, ActionType action, boolean isOrderBook) {
        if (state.getActiveUser() == null) {
            showAlert(Alert.AlertType.WARNING, "No User Selected", "Please go to the Users tab and select a user to trade.");
            return;
        }

        Dialog<TradeInput> dialog = new Dialog<>();
        dialog.setTitle(action.name() + " Shares - " + optionName);
        dialog.setHeaderText("Event: " + event.name() + "\nOption: " + optionName);
        styleDialog(dialog);

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

        if (isOrderBook) {
            grid.add(new Label("Price limit ($) (Price Per Share):"), 0, 1);
            grid.add(priceField, 1, 1);
        }

        dialog.getDialogPane().setContent(grid);

        // 1. SYNCHRONOUS LOCAL VALIDATION
        // This ensures the user typed actual numbers > 0 before the dialog closes.
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, actionEvent -> {
            try {
                int amount = Integer.parseInt(amountField.getText().trim());
                if (amount <= 0) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Input", "Amount must be greater than 0.");
                    actionEvent.consume();
                    return;
                }
                if (isOrderBook) {
                    double priceLimit = Double.parseDouble(priceField.getText().trim());
                    if (priceLimit <= 0) {
                        showAlert(Alert.AlertType.ERROR, "Invalid Input", "Price limit must be greater than 0.");
                        actionEvent.consume();
                    }
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter valid numeric values.");
                actionEvent.consume();
            }
        });

        // 2. EXTRACT THE DATA
        // If validation passed, convert the text fields into our TradeInput record.
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                int amount = Integer.parseInt(amountField.getText().trim());
                double limit = isOrderBook ? Double.parseDouble(priceField.getText().trim()) : 0.0;
                return new TradeInput(amount, limit);
            }
            return null;
        });

        // 3. LAUNCH THE BACKGROUND TASK
        // Once the dialog closes, send the TradeInput to Tomcat.
        dialog.showAndWait().ifPresent(input ->
        {

            Task<TradeReceiptDTO> tradeTask = new Task<>()
            {
                @Override
                protected TradeReceiptDTO call() throws Exception {
                    return clientUtil.placeOrder(state.getActiveUser(), event.name(), optionName, input.amount(), action, input.limitPrice());
                }
            };

            tradeTask.setOnSucceeded(e ->
            {
                TradeReceiptDTO receipt = tradeTask.getValue();

                if (receipt.isSuccess()) {
                    String msg = (receipt.totalCost() == 0.0 && receipt.commission() == 0.0)
                            ? "Trade submitted to the queue."
                            : String.format("Trade successful!\nTotal Paid: $%.2f\nCommission: $%.2f", receipt.totalCost(), receipt.commission());

                    showAlert(Alert.AlertType.INFORMATION, "Success", msg);
                    updateEvents(); // Refresh the UI with the new prices
                } else {
                    showAlert(Alert.AlertType.ERROR, "Trade Failed", receipt.message());
                }
            });

            tradeTask.setOnFailed(e -> {
                showAlert(Alert.AlertType.ERROR, "Network Error", "Failed to place order: " + tradeTask.getException().getMessage());
            });

            Thread thread = new Thread(tradeTask);
            thread.setDaemon(true);
            thread.start();
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        UiUtils.showAlert(type, title, content, state.getThemeStylesheets());
    }



    private void updateBalance() {
        String activeUser = state.getActiveUser();
        if (activeUser == null) return;

        Task<UserDetailsDTO> balanceTask = new Task<>() {
            @Override
            protected UserDetailsDTO call() throws Exception {
                return clientUtil.getUserDetails(activeUser);
            }
        };

        balanceTask.setOnSucceeded(e -> {
            UserDetailsDTO details = balanceTask.getValue();
            userBalanceLabelEvents.setText(String.format("$%.2f", details.balance()));
        });

        Thread thread = new Thread(balanceTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void addPendingRequestLabel(PendingRequestDTO req, String optionName, boolean isBuy) {
        String text = String.format("[%s] User: %s | Option: %s | Amount: %d | Limit: $%.2f",
                isBuy ? "BUY" : "SELL", req.username(), optionName, req.amount(), req.price());
        Label reqLabel = UiUtils.createHistoryLabel(text, isBuy);
        reqLabel.setMaxWidth(Double.MAX_VALUE);
        participationsHistoryListView.getItems().add(reqLabel);
    }

    /**
     * #########################################
     *      Initialize Helper function
     * #########################################
     */

    private void setupEventListeners() {
        // the active user is picked in the Users tab; update the Events tab to match
        state.activeUserProperty().addListener((obs, oldUser, newUser) -> {
            if (newUser == null) {
                userNameLabelEvents.setText("Select a user...");
                userBalanceLabelEvents.setText("$0.00");
            } else {
                userNameLabelEvents.setText(newUser);
                updateBalance();
            }
            if (selectedEvent != null) {
                handleTileClick(selectedEvent);
            }
        });

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
    }
}
