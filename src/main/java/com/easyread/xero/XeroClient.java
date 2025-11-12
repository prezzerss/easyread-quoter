package com.easyread.xero;

import okhttp3.*;
import java.awt.Desktop;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.file.Paths;


public class XeroClient {

	// After
	// use identity host for PKCE desktop
	private static final String AUTH_BASE = "https://login.xero.com/identity";
	private static final String TOKEN_URL = "https://identity.xero.com/connect/token";

	// Try a fresh port and localhost; add BOTH redirects in the Xero app
	private static final int LOCAL_PORT = 30123;
	// private static final String REDIRECT  = "http://localhost:" + LOCAL_PORT + "/callback";
	
	private static String redirectUri() {
	    // On Render, set XERO_REDIRECT to: https://<your-service>.onrender.com/callback
	    // Locally, it falls back to the loopback URL.
	    return System.getenv().getOrDefault("XERO_REDIRECT", "http://127.0.0.1:8721/callback");
	}




	private static final String TOKEN_BASE = "https://identity.xero.com";
	private static final String API_BASE   = "https://api.xero.com";



    private final OkHttpClient http = new OkHttpClient();
    private final String clientId;
    private final Path tokenFile;
    private final Path tenantFile;

    private String accessToken;
    private String refreshToken;
    private Instant accessExpiry;
    private String tenantId;

    public XeroClient(String clientId) {
        this.clientId = clientId;

        // Use DATA_DIR (Render will set DATA_DIR=/data; locally falls back to ./data)
        String dataDir = System.getenv().getOrDefault("DATA_DIR", "data");
        Path base = Paths.get(dataDir);
        try {
            Files.createDirectories(base);
        } catch (IOException ioe) {
            throw new RuntimeException("Could not create DATA_DIR: " + dataDir, ioe);
        }

        this.tokenFile  = base.resolve("xero_tokens.json");
        this.tenantFile = base.resolve("xero_tenant.json");

        loadTokens();
        loadTenant();
    }


    /* ======================= PUBLIC API ======================= */

    public void ensureAuthenticated() throws Exception {
        if (accessToken == null || accessExpiry == null || Instant.now().isAfter(accessExpiry.minusSeconds(60))) {
            if (refreshToken != null) {
                tryRefresh();
            } else {
                doInteractiveLogin();
            }
        }
        if (tenantId == null || tenantId.isBlank()) {
            pickTenant();
        }
    }

    /** Create (or find) a Contact; returns ContactID */
    public String getOrCreateContact(String name, String email) throws Exception {
        ensureAuthenticated();

        // Try find by email
        Request findReq = new Request.Builder()
                .url(API_BASE + "/api.xro/2.0/Contacts?where=EmailAddress%3D%22" + url(email) + "%22")
                .get()
                .header("Authorization", "Bearer " + accessToken)
                .header("xero-tenant-id", tenantId)
                .header("Accept", "application/json")
                .build();
        try (Response r = http.newCall(findReq).execute()) {
            if (!r.isSuccessful()) {
                throw new IOException("Find contact failed: HTTP " + r.code() + "\n" + bodyString(r));
            }
            JSONObject resp = requireJson(r);
            JSONArray arr = resp.getJSONArray("Contacts");
            if (arr.length() > 0) {
                return arr.getJSONObject(0).getString("ContactID");
            }
        }


        // Create new
        JSONObject contact = new JSONObject()
                .put("Name", name)
                .put("EmailAddress", email);

        JSONObject body = new JSONObject().put("Contacts", new JSONArray().put(contact));

        Request createReq = new Request.Builder()
                .url(API_BASE + "/api.xro/2.0/Contacts")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .header("xero-tenant-id", tenantId)
                .build();
        try (Response r = http.newCall(createReq).execute()) {
            if (!r.isSuccessful()) {
                throw new IOException("Create contact failed: HTTP " + r.code() + "\n" + bodyString(r));
            }
            JSONObject resp = requireJson(r);
            return resp.getJSONArray("Contacts").getJSONObject(0).getString("ContactID");
        }

    }

    /** Creates a DRAFT Quote and returns the QuoteID. */
    public String createDraftQuote(String contactId,
                                   String jobTitle,
                                   String referenceText,
                                   double unitAmount,
                                   String accountCode) throws Exception {
        ensureAuthenticated();

        JSONObject line = new JSONObject()
                .put("Description", "Easy Read version of \"" + jobTitle + "\"")
                .put("Quantity", 1)
                .put("UnitAmount", unitAmount)
                .put("AccountCode", accountCode);

        JSONObject quote = new JSONObject()
                .put("Contact", new JSONObject().put("ContactID", contactId))
                .put("Date", java.time.LocalDate.now().toString())
                .put("ExpiryDate", java.time.LocalDate.now().plusDays(14).toString())
                .put("Title", jobTitle)
                .put("Reference", referenceText)
                .put("Status", "DRAFT")
                .put("LineItems", new JSONArray().put(line));

        JSONObject body = new JSONObject().put("Quotes", new JSONArray().put(quote));

        Request req = new Request.Builder()
                .url(API_BASE + "/api.xro/2.0/Quotes")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .header("xero-tenant-id", tenantId)
                .build();

        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful()) {
                throw new IOException("Create quote failed: HTTP " + r.code() + "\n" + bodyString(r));
            }
            JSONObject resp = requireJson(r);
            return resp.getJSONArray("Quotes").getJSONObject(0).getString("QuoteID");
        }
    }

    /** Downloads the Quote PDF to the given path. */
    public void downloadQuotePdf(String quoteId, Path dest) throws Exception {
        ensureAuthenticated();
        Request req = new Request.Builder()
                .url(API_BASE + "/api.xro/2.0/Quotes/" + quoteId + "/pdf")
                .get()
                .header("Authorization", "Bearer " + accessToken)
                .header("xero-tenant-id", tenantId)
                .header("Accept", "application/pdf")
                .build();
        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new IOException("Download PDF failed: " + r);
            Files.createDirectories(dest.getParent());
            try (InputStream in = r.body().byteStream();
                 OutputStream out = Files.newOutputStream(dest)) {
                in.transferTo(out);
            }
        }
    }

    /* ======================= AUTH / TENANT ======================= */

    private void doInteractiveLogin() throws Exception {
    	String verifier  = randomString(64);
    	String challenge = base64UrlNoPad(sha256(verifier));
    	String scope = String.join(" ",
    	        List.of("offline_access",
    	                "accounting.contacts","accounting.transactions","accounting.settings"));

    	String state = UUID.randomUUID().toString();
    	String authUrl = AUTH_BASE + "/connect/authorize?" +
    	        "response_type=code" +
    	        "&client_id=" + url(clientId) +
    	        "&redirect_uri=" + url(redirectUri()) +
    	        "&scope=" + url(scope) +
    	        "&code_challenge=" + url(challenge) +
    	        "&code_challenge_method=S256" +
    	        "&prompt=login" +                 // <— force fresh login screen
    	        "&state=" + url(state);

    	System.out.println("\n[Xero] Authorize URL:\n" + authUrl + "\n");

    	// tiny local HTTP server to catch the redirect
    	final String[] codeHolder = new String[1];
    	var server = com.sun.net.httpserver.HttpServer.create(
    	        new java.net.InetSocketAddress("127.0.0.1", LOCAL_PORT), 0);

    	server.createContext("/callback", exchange -> {
    	    String q = exchange.getRequestURI().getQuery();
    	    Map<String,String> map = parseQuery(q);
    	    codeHolder[0] = map.get("code");
    	    String body = "<html><body style='font:14px system-ui'>OK, you can close this window.</body></html>";
    	    exchange.sendResponseHeaders(200, body.length());
    	    try (var os = exchange.getResponseBody()) { os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    	});
    	server.start();
    	System.out.println("[Xero] Local redirect listener started on " + redirectUri());

    	java.awt.Desktop.getDesktop().browse(new java.net.URI(authUrl));

    	// Wait for code with a timeout (90s)
    	long start = System.currentTimeMillis();
    	while (codeHolder[0] == null && System.currentTimeMillis() - start < 90_000) {
    	    Thread.sleep(150);
    	}
    	server.stop(0);

    	if (codeHolder[0] == null) {
    	    throw new IOException("Did not receive redirect from Xero. "
    	            + "Check that the exact redirect URI is registered in the Xero app: "
    	            + redirectUri() + "  (and try in a private/incognito window).");
    	}
    	System.out.println("[Xero] Received auth code.");

        Desktop.getDesktop().browse(new URI(authUrl));

        // wait for code
        while (codeHolder[0] == null) Thread.sleep(150);

     // Exchange code → tokens  (add debug)
     // Exchange code → tokens  (add debug)
        RequestBody form = new FormBody.Builder()
                .add("grant_type","authorization_code")
                .add("client_id", clientId)
                .add("code", codeHolder[0])
                .add("redirect_uri", redirectUri())
                .add("code_verifier", verifier)
                .build();

        Request req = new Request.Builder()
                .url(TOKEN_URL + "/connect/token")
                .post(form)
                .header("Accept", "application/json")
                .build();

        try (Response r = http.newCall(req).execute()) {
            String raw = (r.body() == null) ? "" : r.body().string();
            System.out.println("[Xero] Token exchange HTTP " + r.code());
            System.out.println("[Xero] Token exchange body:\n" + raw);

            if (!r.isSuccessful()) {
                throw new IOException("Token exchange failed. HTTP " + r.code() + "\n" + first(raw, 1200));
            }
            JSONObject tok = new JSONObject(raw);
            applyAndSaveTokens(tok);
        }




    }

    private void tryRefresh() throws Exception {
    	RequestBody form = new FormBody.Builder()
    	        .add("grant_type","refresh_token")
    	        .add("client_id", clientId)
    	        .add("refresh_token", refreshToken)
    	        .build();

    	Request req = new Request.Builder()
    	        .url(TOKEN_URL + "/connect/token")
    	        .post(form)
    	        .header("Accept", "application/json")
    	        .build();

    	try (Response r = http.newCall(req).execute()) {
    	    String raw = (r.body() == null) ? "" : r.body().string();
    	    System.out.println("[Xero] Refresh HTTP " + r.code());
    	    System.out.println("[Xero] Refresh body:\n" + raw);

    	    if (!r.isSuccessful()) {
    	        // fall back to interactive login
    	        throw new IOException("Refresh failed. HTTP " + r.code() + "\n" + first(raw, 1200));
    	    }
    	    JSONObject tok = new JSONObject(raw);
    	    applyAndSaveTokens(tok);
    	}


    }

    private void pickTenant() throws Exception {
        Request req = new Request.Builder()
                .url(API_BASE + "/connections")
                .get()
                .header("Authorization","Bearer " + accessToken)
                .build();

        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful()) {
                throw new IOException("Get connections failed: HTTP " + r.code() + "\n" + bodyString(r));
            }
            String s = bodyString(r);
            JSONArray arr;
            try { arr = new JSONArray(s); }
            catch (Exception e) {
                throw new IOException("Connections response not JSON array:\n"
                        + s.substring(0, Math.min(800, s.length())));
            }
            if (arr.length() == 0) throw new IOException("No Xero organisations linked to this user.");
            tenantId = arr.getJSONObject(0).getString("tenantId");
            saveTenant();
        }


    }

    /* ======================= UTIL / STATE ======================= */

    private void applyAndSaveTokens(JSONObject tok) throws Exception {
        accessToken  = tok.getString("access_token");
        refreshToken = tok.getString("refresh_token");
        accessExpiry = Instant.now().plusSeconds(tok.getLong("expires_in"));
        JSONObject toSave = new JSONObject()
                .put("access_token", accessToken)
                .put("refresh_token", refreshToken)
                .put("expires_at", accessExpiry.toEpochMilli());
        Files.createDirectories(tokenFile.getParent());
        Files.writeString(tokenFile, toSave.toString(2), StandardCharsets.UTF_8);
    }

    private void loadTokens() {
        try {
            if (!Files.exists(tokenFile)) return;
            JSONObject tok = new JSONObject(Files.readString(tokenFile, StandardCharsets.UTF_8));
            accessToken  = tok.getString("access_token");
            refreshToken = tok.getString("refresh_token");
            accessExpiry = Instant.ofEpochMilli(tok.getLong("expires_at"));
        } catch (Exception ignored) {}
    }

    private void saveTenant() throws Exception {
        JSONObject j = new JSONObject().put("tenantId", tenantId);
        Files.createDirectories(tenantFile.getParent());
        Files.writeString(tenantFile, j.toString(2), StandardCharsets.UTF_8);
    }

    private void loadTenant() {
        try {
            if (!Files.exists(tenantFile)) return;
            JSONObject j = new JSONObject(Files.readString(tenantFile, StandardCharsets.UTF_8));
            tenantId = j.getString("tenantId");
        } catch (Exception ignored) {}
    }

    /* ---------------- small helpers ---------------- */

    private static Map<String,String> parseQuery(String q) {
        Map<String,String> m = new HashMap<>();
        if (q == null) return m;
        for (String p : q.split("&")) {
            String[] kv = p.split("=",2);
            m.put(urlDecode(kv[0]), kv.length>1 ? urlDecode(kv[1]) : "");
        }
        return m;
    }
    private static String url(String s){ return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String urlDecode(String s) {
        try {
            // Use the String-based overload so it compiles on all JDKs
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 always exists; wrap just to satisfy the compiler
            throw new RuntimeException(e);
        }
    }


    private static byte[] sha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(s.getBytes(StandardCharsets.UTF_8));
    }
    private static String base64UrlNoPad(byte[] b){
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    private static String randomString(int len){
        byte[] b = new byte[len];
        new java.security.SecureRandom().nextBytes(b);
        return base64UrlNoPad(b).substring(0, len);
    }
    
    public void smokeTest() throws Exception {
        ensureAuthenticated();
        var req = new Request.Builder()
                .url("https://api.xero.com/api.xro/2.0/Organisations")
                .get()
                .header("Authorization", "Bearer " + accessToken)
                .header("xero-tenant-id", tenantId)
                .build();
        try (var r = http.newCall(req).execute()) {
            System.out.println("[Xero] Organisations response " + r.code() + ": " + r.body().string());
        }
    }
    
 // ---- Response helpers ----
    private static String bodyString(okhttp3.Response r) throws java.io.IOException {
        okhttp3.ResponseBody b = r.body();
        return (b == null) ? "" : b.string();
    }

    private static boolean isJson(okhttp3.Response r) {
        okhttp3.MediaType mt = (r.body() != null) ? r.body().contentType() : null;
        return mt != null && "application".equals(mt.type())
               && (mt.subtype().contains("json") || mt.subtype().contains("javascript"));
    }

    private static org.json.JSONObject requireJson(okhttp3.Response r) throws java.io.IOException {
        String s = bodyString(r);
        if (!isJson(r)) {
            throw new java.io.IOException("HTTP " + r.code() + " " + r.message() + "\n"
                    + "Non-JSON response:\n"
                    + s.substring(0, Math.min(800, s.length())));
        }
        try {
            return new org.json.JSONObject(s);
        } catch (Exception e) {
            throw new java.io.IOException("Could not parse JSON. HTTP " + r.code() + "\n"
                    + s.substring(0, Math.min(800, s.length())), e);
        }
    }
    
    private static String first(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + " ...";
    }

}

