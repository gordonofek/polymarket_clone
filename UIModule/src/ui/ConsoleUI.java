package ui;

import engine.Engine;
import engine.EngineInterface;
import engine.dto.CloseEventReceiptDTO;
import engine.dto.EventDetailsDTO;
import engine.dto.EventSummaryDTO;
import engine.dto.TradeReceiptDTO;
import internals.ActionType;
// ייתכן שתצטרך להוסיף כאן ייבוא (import) ל-Exceptions שיצרת, למשל:
// import engine.files.StateSaveException;
// import engine.files.StateLoadException;

import java.util.List;
import java.util.Scanner;

public class ConsoleUI {

    private EngineInterface engine;
    private boolean isRunning;
    private Scanner scanner;

    private static List<String> MENU_OPTIONS = List.of(
            "Load System Details (XML)",
            "Display All Events",
            "Event Trading Status",
            "Participate in Event",
            "Close Event",
            "Save System State",
            "Load System State",
            "Exit"
    );

    public ConsoleUI() {
        this.engine = new Engine();
        this.isRunning = true;
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        System.out.println("Welcome to Guess Market!");

        while (isRunning) {
            printMenu();
            int choice = getUserInput();
            processInput(choice);
        }

        System.out.println("Goodbye!");
        scanner.close();
    }

    private void printMenu()
    {
        System.out.println("###### Main Menu ######");
        for (int i=0;i<MENU_OPTIONS.size();i++)
        {
            System.out.println((i+1) + "). " + MENU_OPTIONS.get(i));
        }
        System.out.print("Please enter your choice by typing a number (1-" + MENU_OPTIONS.size() + "): ");
    }

    private int getUserInput() {
        try {
            String input = scanner.nextLine();
            int numInput = Integer.parseInt(input);
            // 1 <= input <= MENU.OPTIONS.size()
            if (numInput > MENU_OPTIONS.size() || numInput < 1) return -1;
            return numInput;
        }
        catch (NumberFormatException exception)
        {
            return -1; // invalid option
        }
    }

    private void processInput(int choice)
    {
        switch (choice) {
            case 1 -> handleLoadData();
            case 2 -> handleDisplayEvents();
            case 3 -> handleEventStatus();
            case 4 -> handleParticipate();
            case 5 -> handleCloseEvent();
            case 6 -> handleSaveState();
            case 7 -> handleLoadState();
            case 8 -> this.isRunning = false;
            default -> System.out.println("Invalid input, please enter a number between 1 and "+MENU_OPTIONS.size());
        }
    }

    /**
     *  ################ INPUT HELPERS ################
     */

    private int readIntInRange(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
                System.out.printf("Invalid input. Please enter a number between %d and %d.%n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
    }

    private int readPositiveInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(input);
                if (value > 0) {
                    return value;
                }
                System.out.println("Amount must be greater than 0.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
    }


    /**
     * Helper method to print summary or full details of an event according to commands 2/3.
     */

    private void printEventStateDetails(EventDetailsDTO details) {
        System.out.printf("Event Pool Value: %.2f%n", details.poolValue());
        System.out.printf("Total Commission Collected: %.2f%n", details.totalCommission());

        System.out.println("\nOptions Current State:");
        for (int i=0;i<details.options().size(); i++) {
            var option = details.options().get(i);
            System.out.printf("  %d) %s - Current Value: %.2f | Shares Bought: %d%n",
                    (i + 1), option.optionName(), option.currentPrice(), option.sharesBought());
        }

        System.out.println("\nTrade History:");
        if (details.tradeHistory().isEmpty()) {
            System.out.println("  No trades have occurred yet.");
        } else {
            for (int i = details.tradeHistory().size() - 1; i >= 0; i--) {
                var trade = details.tradeHistory().get(i);
                System.out.printf("  - Bought %d shares of %s | Total Paid: %.2f%n",
                        trade.amount(), trade.optionName(), trade.pricePaid());
            }
        }
    }

    private void printEventSummaryDetails(int index, EventSummaryDTO event)
    {
        System.out.printf("%d) Id: %d | Name: %s%n   Description: %s%n   Commission: %d%% (%s)%n   Options: %s%n   Status: %s%n%n",
                index,
                event.id(),
                event.name(),
                event.description(),
                event.commission(),
                event.commissionType(),
                String.join(", ", event.options()),
                event.status());
    }



    /**
     *  ################ HANDLERS ################
     */

    private void handleLoadData() {
        System.out.print("Enter full path for the xml file (with file extension): ");
        String filePath = scanner.nextLine().trim();

        try {
            engine.loadDataFromXML(filePath);
            System.out.println("File successfully loaded!");
        }
        catch (Exception e)
        {
            System.out.println("Failed to load the file: " + e.getMessage());
        }
    }

    private void handleDisplayEvents() {
        List<EventSummaryDTO> allEvents = engine.getAllEvents();
        if (allEvents.isEmpty()) {
            System.out.println("No events are currently loaded");
            return;
        }
        System.out.println();
        for (int i=0;i<allEvents.size(); i++)
        {
            printEventSummaryDetails(i+1, allEvents.get(i));
        }
    }

    private void handleEventStatus() {
        List<EventSummaryDTO> allEvents = engine.getAllEvents();
        if (allEvents.isEmpty()) {
            System.out.println("No events are currently loaded");
            return;
        }
        handleDisplayEvents();
        int index = readIntInRange(
                "Enter the number (not the id) of the event from the list above (1-" + allEvents.size() + "):",
                1, allEvents.size());
        int eventId = allEvents.get(index - 1).id();

        try {
            EventDetailsDTO details = engine.getEventState(eventId);

            System.out.println("\n##### Event Details: " + details.name() + " #####");
            System.out.println("Status: " + details.status());

            printEventStateDetails(details);

            if ("CLOSED".equals(details.status())) {
                System.out.println("\n*** Event is CLOSED ***");
                System.out.println("Winning Option: " + details.winningOption());
            }
            System.out.println();

        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a valid number.");
        } catch (Exception e) {
            System.out.println("An unexpected error occurred: " + e.getMessage());
        }
    }

    private void handleParticipate() {
        // fetch active events
        List<EventSummaryDTO> activeEvents = engine.getActiveEvents();
        if(activeEvents.isEmpty()) {
            System.out.println("There are no active events right now");
            return;
        }
        System.out.println("###### Active Events ######");
        for (int i=0;i<activeEvents.size();i++) {
            printEventSummaryDetails(i+1, activeEvents.get(i));
        }
        // select event
        int index = readIntInRange(
                "Enter the number of the event to purchase (1-" + activeEvents.size() + "): ",
                1, activeEvents.size());
        int eventId = activeEvents.get(index - 1).id();
        try {
            // show current state of the chosen event
            EventDetailsDTO details = engine.getEventState(eventId);
            System.out.println("\n### Current State of: " + details.name() + " ###");
            printEventStateDetails(details);

            // select option
            int optionIndex = readIntInRange(
                    "Enter the number of the option that you would like to back (1-" + details.options().size() + "): ",
                    1, details.options().size());
            String chosenOption = details.options().get(optionIndex - 1).optionName();

            // select amount
            int amount = readPositiveInt("Enter the amount of shares you want to buy: ");

            // execute trade
            TradeReceiptDTO receipt = engine.placeOrder(eventId, chosenOption, amount, ActionType.BUY, 0.0);

            if (receipt.isSuccess()) {
                System.out.println("\n*** Trade Successful! ***");
                double sharesCost = receipt.totalCost() - receipt.commission();
                System.out.printf("Total Paid: %.2f%n", receipt.totalCost());
                System.out.printf("  -> Shares Cost: %.2f%n", sharesCost);
                System.out.printf("  -> Commission: %.2f%n", receipt.commission());

                // fetch the updated state to show the user the updated market
                EventDetailsDTO updatedDetails = engine.getEventState(eventId);
                System.out.println("\n### Updated Event State ###");
                printEventStateDetails(updatedDetails);
                System.out.println();
            }
            else {
                System.out.println("\nTrade Failed: " + receipt.message());
            }
        }
        catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a valid number.");
        } catch (Exception e) {
            System.out.println("unexpected error occurred: " + e.getMessage());
        }
    }

    private void handleCloseEvent() {
        List<EventSummaryDTO> activeEvents = engine.getActiveEvents();
        if(activeEvents.isEmpty()) {
            System.out.println("There are no active events to close.");
            return;
        }

        // display active events
        System.out.println("###### Active Events ######");
        for (int i=0;i<activeEvents.size();i++) {
            printEventSummaryDetails(i+1, activeEvents.get(i));
        }

        // select event
        int eventIndex = readIntInRange(
                "Enter the number of the event to close (1-" + activeEvents.size() + "): ", 1, activeEvents.size());
        int eventId = activeEvents.get(eventIndex - 1).id();

        // show options to pick a winner
        EventDetailsDTO details = engine.getEventState(eventId);
        System.out.println("###### Select the Winning option for: " + details.name() + "######");
        for (int i=0;i<details.options().size();i++)
        {
            System.out.printf(" %d) %s%n", i+1, details.options().get(i).optionName());
        }

        // pick a winner
        int optionIndex = readIntInRange(
                "Enter the number of the winning option (1-" + details.options().size() + "): ",
                        1, details.options().size());
        String winningOption = details.options().get(optionIndex - 1).optionName();

        // close the event
        CloseEventReceiptDTO receipt = engine.closeEvent(eventId, winningOption);

        if (receipt.isSuccess()) {
            System.out.println("\n *** Event Closed Successfully! ***");
            System.out.println("Winning Option: " + receipt.winningOption());
            System.out.printf("Total Payout to winners: %.2f%n", receipt.totalPayout());
            System.out.printf("Commission Taken (On-Close): %.2f%n", receipt.commissionTaken());
            System.out.printf("Leftover Subsidy Returned to Market Maker: %.2f%n", receipt.leftoverPool());

            // show the final updated state of event
            System.out.println("\n### Final Event State ###");
            EventDetailsDTO finalDetails = engine.getEventState(eventId);
            printEventStateDetails(finalDetails);
            System.out.println();
        }
        else {
            System.out.println("\nFailed to close event: " + receipt.message());
        }
    }

    private void handleSaveState() {
        System.out.println("Enter the full path to save the system state (without file extension): ");
        String path = scanner.nextLine().trim();
        String fullPath = path + ".dat";

        try {
            engine.saveState(fullPath);
            System.out.println("System state successfully saved to: " + fullPath);
        } catch (Exception e) { // תופס את StateSaveException או שגיאות כלליות
            System.out.println("Failed to save state: " + e.getMessage());
        }
    }

    private void handleLoadState() {
        System.out.println("Enter the full path to load the system state from (without file extension): ");
        String path = scanner.nextLine().trim();
        String fullPath = path + ".dat";

        try {
            engine.loadState(fullPath);
            System.out.println("System state successfully loaded from: " + fullPath);
        } catch (Exception e) { // תופס את StateLoadException או שגיאות כלליות
            System.out.println("Failed to load state: " + e.getMessage());
        }
    }
}