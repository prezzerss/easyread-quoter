package com.easyread.trello;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Path;
import org.json.JSONObject;

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
        
     // 4. Preserve *template* description (preferences etc.) and append our brief + email.
//      IMPORTANT: Trello's /desc endpoint SETS the description, it does not append.
//      So we first fetch the current desc, then write back: existing + appended block.
  String existingDesc = getCardDescription(cardId);

  StringBuilder block = new StringBuilder();
  block.append("\n\n---\n\n");
  block.append("Brief\n\n");
  if (briefText != null && !briefText.isBlank()) {
      block.append(briefText.trim());
  } else {
      block.append("(no brief provided)");
  }

  // Manager request: add client email into the brief block (append again)
  if (clientEmail != null && !clientEmail.isBlank()) {
      block.append("\n\nClient email: ").append(clientEmail.trim());
  }

  String newDesc = upsertBriefSection(existingDesc, briefText, clientEmail);
  updateCardDescription(cardId, newDesc);
  System.out.println("Updated card description (kept template prefs + appended brief/email).");

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
    
    
    private String getCardDescription(String cardId) throws IOException {
        String urlStr = "https://api.trello.com/1/cards/" + cardId +
                "?fields=desc" +
                "&key=" + urlencode(apiKey) +
                "&token=" + urlencode(token);

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("GET desc failed. HTTP " + code + " " + readStream(conn.getErrorStream()));
        }

        String response = readStream(conn.getInputStream());
        return new JSONObject(response).optString("desc", "");
    }

    private void updateCardDescription(String cardId, String newDesc) throws IOException {
        String urlStr = "https://api.trello.com/1/cards/" + cardId +
                "?key=" + urlencode(apiKey) +
                "&token=" + urlencode(token) +
                "&desc=" + urlencode(newDesc == null ? "" : newDesc);

        HttpURLConnection conn = putNoBody(urlStr);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("PUT desc failed. HTTP " + code + " " + readStream(conn.getErrorStream()));
        }
    }
    
    private String injectIntoBriefSection(String existingDesc, String briefText, String clientEmail) {
        String desc = (existingDesc == null) ? "" : existingDesc;

        String briefBody = (briefText != null && !briefText.isBlank())
                ? briefText.trim()
                : "(no brief provided)";

        if (clientEmail != null && !clientEmail.isBlank()) {
            briefBody += "\n\nClient email: " + clientEmail.trim();
        }

        // If template has a "Brief" heading, place content there.
        // We’ll insert AFTER the first "Brief" line, and BEFORE "Company Preferences" if it exists.
        int briefIdx = indexOfLine(desc, "Brief");
        if (briefIdx >= 0) {
            int insertPos = endOfLine(desc, briefIdx);

            // If there is "Company Preferences" later, insert before it (keeps template order)
            int prefsIdx = indexOfLineAfter(desc, "Company Preferences", insertPos);

            String before = desc.substring(0, insertPos);
            String after;

            if (prefsIdx >= 0) {
                // Keep any blank lines between Brief and Company Preferences, but replace them with our content + spacing
                after = desc.substring(prefsIdx);
                return before + "\n\n" + briefBody + "\n\n" + after;
            } else {
                // No prefs section found; just insert after Brief heading
                after = desc.substring(insertPos);
                return before + "\n\n" + briefBody + after;
            }
        }

        // Fallback: no "Brief" placeholder in template → append at end
        return desc + "\n\n---\n\nBrief\n\n" + briefBody;
    }

    private int indexOfLine(String text, String lineExact) {
        String needle1 = "\n" + lineExact + "\n";
        String needle2 = "\n" + lineExact + "\r\n";
        if (text.startsWith(lineExact + "\n") || text.startsWith(lineExact + "\r\n")) return 0;

        int i = text.indexOf(needle1);
        if (i >= 0) return i + 1; // points at line start (after \n)
        i = text.indexOf(needle2);
        if (i >= 0) return i + 1;
        return -1;
    }

    private int indexOfLineAfter(String text, String lineExact, int afterPos) {
        int i = text.indexOf("\n" + lineExact + "\n", afterPos);
        if (i >= 0) return i + 1;
        i = text.indexOf("\n" + lineExact + "\r\n", afterPos);
        if (i >= 0) return i + 1;
        if (text.startsWith(lineExact + "\n", afterPos) || text.startsWith(lineExact + "\r\n", afterPos)) return afterPos;
        return -1;
    }

    private int endOfLine(String text, int lineStartIdx) {
        int n = text.indexOf('\n', lineStartIdx);
        return (n >= 0) ? n : text.length();
    }
    
    private String upsertBriefSection(String existingDesc, String briefText, String clientEmail) {
        String desc = (existingDesc == null) ? "" : existingDesc;

        String briefBody = (briefText != null && !briefText.isBlank())
                ? briefText.trim()
                : "(no brief provided)";

        if (clientEmail != null && !clientEmail.isBlank()) {
            briefBody += "\n\nClient email: " + clientEmail.trim();
        }

        // Work line-by-line so we can respect Markdown headings
        String[] lines = desc.split("\\r?\\n", -1);

        int briefLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (isBriefHeading(lines[i])) {
                briefLine = i;
                break;
            }
        }

        // If no Brief heading exists at all, append a new properly formatted one
        if (briefLine == -1) {
            return desc + (desc.endsWith("\n") || desc.isEmpty() ? "" : "\n")
                    + "## Brief\n\n" + briefBody + "\n";
        }

        // Find next heading after Brief (e.g. "Company Preferences", any "# Something", etc.)
        int nextHeading = lines.length;
        for (int i = briefLine + 1; i < lines.length; i++) {
            if (isAnyHeading(lines[i])) {
                nextHeading = i;
                break;
            }
        }

        // Rebuild: keep everything up to and including Brief heading line,
        // then insert our body, then keep the rest from nextHeading onwards.
        StringBuilder out = new StringBuilder();

        // part 1: up to Brief heading line (inclusive)
        for (int i = 0; i <= briefLine; i++) {
            out.append(lines[i]).append("\n");
        }

        // part 2: our brief content
        out.append("\n").append(briefBody).append("\n\n");

        // part 3: rest of template from next heading onward
        for (int i = nextHeading; i < lines.length; i++) {
            out.append(lines[i]);
            if (i < lines.length - 1) out.append("\n");
        }

        return out.toString();
    }

    private boolean isBriefHeading(String line) {
        if (line == null) return false;
        String s = line.trim();
        // Matches: "Brief", "# Brief", "## Brief", etc.
        return s.equalsIgnoreCase("Brief") || s.matches("^#{1,6}\\s*Brief\\s*$");
    }

    private boolean isAnyHeading(String line) {
        if (line == null) return false;
        String s = line.trim();
        // Any markdown heading like "# Something"
        if (s.matches("^#{1,6}\\s+.+$")) return true;

        // Also treat these known section titles as headings even if not markdown
        return s.equalsIgnoreCase("Company Preferences") || s.equalsIgnoreCase("Brief");
    }


}
