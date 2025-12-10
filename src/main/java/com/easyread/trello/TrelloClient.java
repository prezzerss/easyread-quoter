package com.easyread.trello;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Path;



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
    
    private Map<String, String> loadTemplates() {
        Map<String, String> map = new HashMap<>();
        String json = null;

        // 1) Try external file first: data/templates.json
        Path external = Paths.get("data", "templates.json");
        try {
            if (Files.exists(external)) {
                System.out.println("Loading templates from " + external.toAbsolutePath());
                json = Files.readString(external, StandardCharsets.UTF_8);
            } else {
                // 2) Fallback: try to load default from classpath /data/templates.json
                System.out.println("data/templates.json not found on disk, trying classpath resource /data/templates.json");
                try (InputStream in = getClass().getResourceAsStream("/data/templates.json")) {
                    if (in != null) {
                        json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    } else {
                        System.err.println("No templates.json found on disk or in resources; using no templates.");
                        return map;
                    }
                }
            }

            // Now parse the JSON string
            json = json.trim();
            if (json.startsWith("{")) json = json.substring(1);
            if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

            if (!json.isBlank()) {
                String[] entries = json.split(",");
                for (String entry : entries) {
                    String[] parts = entry.split(":");
                    if (parts.length == 2) {
                        String rawKey = parts[0].trim();
                        String rawVal = parts[1].trim();

                        String key = rawKey.replaceAll("^\"|\"$", "");
                        String val = rawVal.replaceAll("^\"|\"$", "");

                        map.put(key, val);
                    }
                }
            }

            System.out.println("Loaded " + map.size() + " templates from templates.json");
        } catch (Exception e) {
            System.err.println("Warning: couldn't read templates.json, using no templates. " + e.getMessage());
        }

        return map;
    }

    
    public String createFullCardForQuote(
            String companyName,
            String jobName,
            int quoteNumber,
            String briefText,
            String clientEmail,
            double estHours
    ) throws IOException {

        // 1. Build final Trello card title
        // e.g. "5462 | Safeguarding Policy | Example Council"
        String cardTitle = quoteNumber
                + " | "
                + jobName
                + " | "
                + companyName;

        // 2. Load template mapping
        Map<String, String> templates = loadTemplates();
        String templateCardId = templates.get(companyName);

        // 3. Create the card (either from template or fresh)
        String cardId;
        if (templateCardId != null && !templateCardId.isEmpty()) {
            // clone from template
            String urlStr = "https://api.trello.com/1/cards" +
                    "?key=" + urlencode(apiKey) +
                    "&token=" + urlencode(token) +
                    "&idList=" + urlencode(listId) +
                    "&name=" + urlencode(cardTitle) +
                    "&idCardSource=" + urlencode(templateCardId) +
                    "&keepFromSource=all";

            System.out.println("DEBUG Trello POST URL:\n" + urlStr);

            HttpURLConnection conn = post(urlStr, "");
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("Failed to create card from template. HTTP " + code + " " + readStream(conn.getErrorStream()));
            }
            String response = readStream(conn.getInputStream());
            cardId = extractJsonField(response, "id");

        } else {
            // normal fresh card
            String urlStr = "https://api.trello.com/1/cards" +
                    "?key=" + urlencode(apiKey) +
                    "&token=" + urlencode(token) +
                    "&idList=" + urlencode(listId) +
                    "&name=" + urlencode(cardTitle);

            HttpURLConnection conn = post(urlStr, "");
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("Failed to create card. HTTP " + code + " " + readStream(conn.getErrorStream()));
            }
            String response = readStream(conn.getInputStream());
            cardId = extractJsonField(response, "id");
        }

     // 4. Append info to the existing description instead of overwriting
        String appendText =
                "\n\n---\n\n" +
                "Client email: " + clientEmail;

        String descUrl = "https://api.trello.com/1/cards/" + cardId +
                "/desc?key=" + urlencode(apiKey) +
                "&token=" + urlencode(token) +
                "&value=" + urlencode(appendText);

        HttpURLConnection descConn = putNoBody(descUrl);
        System.out.println("Appended extra info to template description.");


        // 5. Set custom field "Status" = with us
        setStatusWithUs(cardId);

        // 6. Set custom field "Estimated hours"
        setEstimatedHours(cardId, estHours);

        return cardId;
    }
    
    private static HttpURLConnection putNoBody(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(false);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }
    
    private static HttpURLConnection post(String urlStr, String body) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // 👉 This is the critical line:
        conn.setRequestMethod("POST");      // not GET!

        conn.setDoOutput(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        if (body != null && !body.isEmpty()) {
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        }
        return conn;
    }
    
    public void uploadAttachmentToCard(String cardId, String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            System.out.println("No file to attach (filePath empty). Skipping attachment.");
            return;
        }

        File f = new File(filePath);
        if (!f.exists() || !f.isFile()) {
            System.out.println("File does not exist or is not a file: " + filePath);
            return;
        }

        String urlStr = "https://api.trello.com/1/cards/" + cardId + "/attachments";

        // We'll build a multipart/form-data POST manually
        String boundary = "----EasyReadBoundary" + System.currentTimeMillis();

        URL url = new URL(urlStr
                + "?key=" + urlencode(apiKey)
                + "&token=" + urlencode(token));

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream os = conn.getOutputStream();
             BufferedOutputStream bos = new BufferedOutputStream(os);
             FileInputStream fis = new FileInputStream(f)) {

            // Part 1: the "file" field
            String fileHeader =
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + f.getName() + "\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n";
            bos.write(fileHeader.getBytes(StandardCharsets.UTF_8));

            // File bytes
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            bos.write("\r\n".getBytes(StandardCharsets.UTF_8));

            // Part 2: optional "name" field (nice human-readable label)
            String namePart =
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"name\"\r\n\r\n" +
                    f.getName() + "\r\n";
            bos.write(namePart.getBytes(StandardCharsets.UTF_8));

            // Finish multipart
            String endMarker = "--" + boundary + "--\r\n";
            bos.write(endMarker.getBytes(StandardCharsets.UTF_8));

            bos.flush();
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Attachment upload failed. HTTP " + code + " " + readStream(conn.getErrorStream()));
        } else {
            System.out.println("Attachment uploaded successfully for card " + cardId);
        }
    }





}
