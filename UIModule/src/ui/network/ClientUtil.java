package ui.network;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import engine.dto.*;
import internals.ActionType;
import javafx.application.Platform;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;

public class ClientUtil {

    private static final String BASE_URL = "http://localhost:8080";
    private final HttpClient client;
    private final Gson gson;

    public ClientUtil() {
        this.client = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    public void uploadFile(File xmlFile) throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/FileUpload"))
                .header("Content-Type", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofFile(xmlFile.toPath()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Upload failed: " + response.body());
        }
    }

    public void login(String username) throws Exception {
        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/login?userName=" + encodedUsername))
                .GET() // Or POST, depending on how you wrote your LoginServlet
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Login failed: " + response.body());
        }
    }

    public void fetchDeltaUpdates(int currentVersion, Consumer<EventsDeltaDTO> onSuccess, Consumer<String> onError) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/events?version=" + currentVersion))
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        // המרה של ה-JSON לאובייקט
                        EventsDeltaDTO delta = gson.fromJson(response.body(), EventsDeltaDTO.class);
                        // חובה להריץ את העדכון על ה-JavaFX Application Thread
                        Platform.runLater(() -> onSuccess.accept(delta));
                    } else {
                        Platform.runLater(() -> onError.accept("Failed to fetch updates"));
                    }
                })
                .exceptionally(e -> {
                    Platform.runLater(() -> onError.accept(e.getMessage()));
                    return null;
                });
    }

    public List<EventSummaryDTO> getEventsSummary() throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/events"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Type listType = new TypeToken<List<EventSummaryDTO>>(){}.getType();
            return gson.fromJson(response.body(), listType);
        } else {
            throw new Exception("Failed to fetch events: " + response.body());
        }
    }

    public EventDetailsDTO getEventDetails(String eventName) throws Exception
    {
        // Encode the event name to handle spaces (e.g., "World Cap Winner" -> "World%20Cap%20Winner")
        String encodedName = URLEncoder.encode(eventName, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/events?eventName=" + encodedName))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return gson.fromJson(response.body(), EventDetailsDTO.class);
        } else {
            throw new Exception("Failed to fetch event details: " + response.body());
        }
    }

    public List<ParticipantHoldingDTO> getEventParticipants(String eventName) throws Exception {
        // עוטפים את ה-eventName ב-URLEncoder כדי למנוע שגיאות עם רווחים
        String encodedEventName = URLEncoder.encode(eventName, StandardCharsets.UTF_8);
        String url = BASE_URL + "/events?eventName=" + encodedEventName + "&action=participants";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Server error: " + response.statusCode());
        }

        Type listType = new TypeToken<List<ParticipantHoldingDTO>>(){}.getType();
        return gson.fromJson(response.body(), listType);
    }

    public TradeReceiptDTO startEvent(String username, String eventName) throws Exception {
        String url = "http://localhost:8080/polymarket/events";

        // 1. Format the parameters just like a web form submission
        String formData = "action=startEvent&eventName=" + eventName + "&username=" + username;

        // 2. Build the POST request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded") // Tells Tomcat to expect form data
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        // 3. Send the request
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Server error: " + response.statusCode());
        }

        // 4. Parse the returned JSON back into the TradeReceiptDTO
        return new Gson().fromJson(response.body(), TradeReceiptDTO.class);
    }

    public CloseEventReceiptDTO closeEvent(String eventName, String winningOption) throws Exception {
        String url = "http://localhost:8080/polymarket/events";

        // 1. Format the parameters for the POST body
        String formData = "action=closeEvent&eventName=" + eventName + "&winningOption=" + winningOption;

        // 2. Build the POST request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        // 3. Send the request
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Server error: " + response.statusCode());
        }

        // 4. Parse the returned JSON back into a CloseEventReceiptDTO
        return new Gson().fromJson(response.body(), CloseEventReceiptDTO.class);
    }

    public List<UserSummaryDTO> getUsersSummary() throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/users"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Type listType = new TypeToken<List<UserSummaryDTO>>(){}.getType();
            return gson.fromJson(response.body(), listType);
        } else {
            throw new Exception("Failed to fetch events: " + response.body());
        }
    }

    public UserDetailsDTO getUserDetails(String username) throws Exception
    {
        // Encode the event name to handle spaces (e.g., "World Cap Winner" -> "World%20Cap%20Winner")
        String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/users?userName=" + encodedName))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return gson.fromJson(response.body(), UserDetailsDTO.class);
        } else {
            throw new Exception("Failed to fetch event details: " + response.body());
        }
    }

    public UserEventOverviewDTO getUserEventOverview(String userName, String eventName) throws Exception
    {
        // Encode the event name to handle spaces (e.g., "World Cap Winner" -> "World%20Cap%20Winner")
        String encodedUserName = URLEncoder.encode(userName, StandardCharsets.UTF_8);
        String encodedEventName = URLEncoder.encode(eventName, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/users?userName=" + encodedUserName + "&eventName=" + encodedEventName))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return gson.fromJson(response.body(), UserEventOverviewDTO.class);
        } else {
            throw new Exception("Failed to fetch event details: " + response.body());
        }
    }

    public boolean isUserMarketMaker(String username, String eventName) throws Exception {
        // עוטפים גם את ה-username וגם את ה-eventName ב-URLEncoder
        String encodedEventName = URLEncoder.encode(eventName, StandardCharsets.UTF_8);
        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);

        String url = BASE_URL + "/events?eventName=" + encodedEventName
                + "&action=isMarketMaker&username=" + encodedUsername;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Server error: " + response.statusCode());
        }

        return gson.fromJson(response.body(), Boolean.class);
    }


    /**
     * A POST request matching your TradeServlet.
     * Because your Servlet uses request.getParameter()[cite: 17, 18], you must send the data 
     * as an application/x-www-form-urlencoded string (like a standard HTML form submission), NOT as a JSON body.
     */
    public TradeReceiptDTO placeOrder(String username, String eventName, String optionName, int amount, ActionType action, double limitPrice) throws Exception {
        String url = "http://localhost:8080/polymarket/events";

        // 1. Format all parameters into a single form data string
        String formData = "action=placeOrder" +
                "&username=" + username +
                "&eventName=" + eventName +
                "&optionName=" + optionName +
                "&amount=" + amount +
                "&actionType=" + action.name() +
                "&limitPrice=" + limitPrice;

        // 2. Build the POST request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        // 3. Send the request
        // (Assuming your HttpClient variable is named 'client')
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Server error: " + response.statusCode());
        }

        // 4. Parse the returned JSON back into a TradeReceiptDTO
        return new Gson().fromJson(response.body(), TradeReceiptDTO.class);
    }
}