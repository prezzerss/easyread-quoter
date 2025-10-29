package com.easyread.trello;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TrelloClient {

    private final String apiKey;
    private final String token;
    private final String listId;

    // IDs for custom fields
    private final String statusFieldId;
    private final String statusOptionWithUsId;
    private final String estHoursFieldId;

    public TrelloClient(String apiKey,
                        String token,
                        String listId,
                        String statusFieldId,
                        String statusOptionWithUsId,
                        String estHoursFieldId) {
        this.apiKey = apiKey;
        this.token = token;
        this.listId = listId;
        this.statusFieldId = statusFieldId;
        this.statusOptionWithUsId = statusOptionWithUsId;
        this.estHoursFieldId = estHoursFieldId;
    }

    // 1. Create the Trello card and return its card ID
    public String createCard(String name, String description) throws IOException {
        String urlStr = "https://api.trello.com/1/cards";

        String data = "key=" + urlencode(apiKey) +
                "&token=" + urlencode(token) +
                "&idList=" + urlencode(listId) +
                "&name=" + urlencode(name) +
                "&desc=" + urlencode(description);

        HttpURLConnection conn = post(urlStr, data);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("Failed to create card. HTTP " + code + " " + readStream(conn.getErrorStream()));
        }

        String response = readStream(conn.getInputStream());
        // The response is JSON, and includes "id":"xxxxx"
        String cardId = extractJsonField(response, "id");
        return cardId;
    }

    // 2. Set dropdown custom field "Status" = "with us"
    public void setStatusWithUs(String cardId) throws IOException {
        // PUT /1/card/{idCard}/customField/{idCustomField}/item
        String urlStr = "https://api.trello.com/1/cards/" + cardId +
                "/customField/" + statusFieldId + "/item";

        // dropdown fields use "idValue"
        String jsonBody = "{\"idValue\":\"" + statusOptionWithUsId + "\"}";

        putJson(urlStr, jsonBody);
    }

    // 3. Set numeric/text custom field "Estimated hours"
    public void setEstimatedHours(String cardId, double hours) throws IOException {
        String urlStr = "https://api.trello.com/1/cards/" + cardId +
                "/customField/" + estHoursFieldId + "/item";

        String hoursAsString = Double.toString(hours);
        // text-type custom field uses value.text
        String jsonBody = "{\"value\":{\"number\":\"" + hoursAsString + "\"}}";

        putJson(urlStr, jsonBody);
    }

    // --- helpers below ---

    private static String urlencode(String s) throws UnsupportedEncodingException {
        return URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
    }

    private static HttpURLConnection post(String urlStr, String body) throws IOException {
        URL url = new URL(urlStr + "?" + body);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(false);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    private void putJson(String urlStr, String json) throws IOException {
        URL url = new URL(urlStr + "?key=" + urlencode(apiKey) + "&token=" + urlencode(token));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("PUT failed. HTTP " + code + " " + readStream(conn.getErrorStream()));
        }
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    // SUPER simple JSON field parser for "id":"..."
    private static String extractJsonField(String json, String fieldName) {
        // WARNING: hacky, but fine because Trello response is simple here.
        // Looks for "fieldName":"value"
        String pattern = "\"" + fieldName + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }
}
