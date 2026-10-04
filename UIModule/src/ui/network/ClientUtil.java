package ui.network;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import engine.dto.*;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.List;

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


    /**
     * A POST request matching your TradeServlet.
     * Because your Servlet uses request.getParameter()[cite: 17, 18], you must send the data 
     * as an application/x-www-form-urlencoded string (like a standard HTML form submission), NOT as a JSON body.
     */
    public TradeReceiptDTO placeOrder(String username, String eventName, String optionName, String tradeType, int amount, double priceLimit) throws Exception
    {

        // Build the URL-encoded string matching your servlet's expected parameters
        String formData = "userName=" + URLEncoder.encode(username, StandardCharsets.UTF_8) +
                "&eventName=" + URLEncoder.encode(eventName, StandardCharsets.UTF_8) +
                "&optionName=" + URLEncoder.encode(optionName, StandardCharsets.UTF_8) +
                "&tradeType=" + URLEncoder.encode(tradeType, StandardCharsets.UTF_8) +
                "&amount=" + amount +
                "&priceLimit=" + priceLimit;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/tradeRequest"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return gson.fromJson(response.body(), TradeReceiptDTO.class);
        } else {
            throw new Exception("Trade failed: " + response.body());
        }
    }
}