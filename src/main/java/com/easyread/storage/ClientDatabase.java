package com.easyread.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * ClientDatabase stores a lightweight CRM in JSON.
 *
 * File shape (clients_db.json):
 *
 * {
 *   "23Red": {
 *     "contacts": ["clients@example.com", "altperson@23red.com"],
 *     "jobs": [
 *       { "quoteNumber": 5462, "jobTitle": "Another Report?" }
 *     ]
 *   },
 *   "NHS Region XYZ": {
 *     "contacts": ["comms@nhsxyz.org.uk"],
 *     "jobs": [
 *       { "quoteNumber": 5470, "jobTitle": "Waiting Room Poster v2" }
 *     ]
 *   }
 * }
 */
public class ClientDatabase {

    // ---- Inner classes that define the structure we store ----

    public static class ClientInfo {
        // unique emails for that company
        public Set<String> contacts = new LinkedHashSet<>();

        // running log of what we've quoted for them
        public List<JobInfo> jobs = new ArrayList<>();
    }

    public static class JobInfo {
        public int quoteNumber;
        public String jobTitle;
    }

    // ---- Fields ----

    private final String dbPath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Map of companyName -> info (contacts + jobs)
    private Map<String, ClientInfo> clientsByName = new LinkedHashMap<>();

    // ---- Constructor ----

    public ClientDatabase(String dbPath) {
        this.dbPath = dbPath;
        load();
    }

    // ---- Load from disk ----

    private void load() {
        try {
            if (!Files.exists(Paths.get(dbPath))) {
                // file doesn't exist yet, start empty
                clientsByName = new LinkedHashMap<>();
                return;
            }

            String json = Files.readString(Paths.get(dbPath), StandardCharsets.UTF_8);
            if (json.isBlank()) {
                clientsByName = new LinkedHashMap<>();
                return;
            }

            clientsByName = gson.fromJson(
                    json,
                    new TypeToken<LinkedHashMap<String, ClientInfo>>(){}.getType()
            );

            if (clientsByName == null) {
                clientsByName = new LinkedHashMap<>();
            }

        } catch (Exception e) {
            e.printStackTrace();
            clientsByName = new LinkedHashMap<>();
        }
    }

    // ---- Save to disk ----

    private void save() {
        try {
            File outFile = new File(dbPath);
            outFile.getParentFile().mkdirs();

            String json = gson.toJson(clientsByName);
            Files.writeString(Paths.get(dbPath), json, StandardCharsets.UTF_8);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Record a quote against a company.
     *
     * @param companyName  e.g. "23Red"
     * @param clientEmail  e.g. "client@23red.com"
     * @param quoteNumber  e.g. 5462
     * @param jobTitle     e.g. "Another Report?"
     */
    public void recordQuote(String companyName,
                            String clientEmail,
                            int quoteNumber,
                            String jobTitle) {

        if (companyName == null || companyName.isBlank()) {
            // no company? can't log this in CRM
            return;
        }

        // get or create the ClientInfo for this company
        ClientInfo info = clientsByName.get(companyName);
        if (info == null) {
            info = new ClientInfo();
            clientsByName.put(companyName, info);
        }

        // add contact email to set
        if (clientEmail != null && !clientEmail.isBlank()) {
            info.contacts.add(clientEmail.trim());
        }

        // add this job
        JobInfo job = new JobInfo();
        job.quoteNumber = quoteNumber;
        job.jobTitle = (jobTitle != null ? jobTitle : "");
        info.jobs.add(job);

        save();
    }

    /**
     * Permanently remove a company + all their data from the CRM.
     * @return true if we actually deleted them, false if they weren't there.
     */
    public boolean deleteCompany(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return false;
        }
        boolean removed = (clientsByName.remove(companyName) != null);
        if (removed) {
            save();
        }
        return removed;
    }

    /**
     * Export the entire CRM to a CSV file that Excel / Numbers can open.
     * One row per (company, contactEmail, job).
     *
     * CSV columns:
     * Company,ContactEmail,QuoteNumber,JobTitle
     */
    public void exportCsv(String csvPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("Company,ContactEmail,QuoteNumber,JobTitle\n");

        for (Map.Entry<String, ClientInfo> entry : clientsByName.entrySet()) {
            String companyName = entry.getKey();
            ClientInfo info = entry.getValue();

            // if no contacts yet, put an empty one just so rows still appear
            List<String> contactsList = new ArrayList<>(info.contacts);
            if (contactsList.isEmpty()) {
                contactsList.add("");
            }

            for (String contact : contactsList) {
                for (JobInfo job : info.jobs) {
                    // escape any commas or quotes in job titles
                    String safeJob = csvEscape(job.jobTitle);
                    String safeCompany = csvEscape(companyName);
                    String safeContact = csvEscape(contact);

                    sb.append(safeCompany).append(",");
                    sb.append(safeContact).append(",");
                    sb.append(job.quoteNumber).append(",");
                    sb.append(safeJob).append("\n");
                }
            }
        }

        try {
            File outFile = new File(csvPath);
            outFile.getParentFile().mkdirs();
            Files.writeString(Paths.get(csvPath), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // basic CSV cell escaper (wrap in quotes if needed)
    private String csvEscape(String raw) {
        if (raw == null) return "";
        boolean needsQuotes = raw.contains(",") || raw.contains("\"") || raw.contains("\n");
        String cleaned = raw.replace("\"", "\"\""); // escape quotes by doubling
        return needsQuotes ? ("\"" + cleaned + "\"") : cleaned;
    }

    /**
     * For future UI / debugging
     */
    public Map<String, ClientInfo> getAllClients() {
        return clientsByName;
    }
    
 // ---- Client editing helpers ----

    public boolean renameCompany(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.isBlank() || newName.isBlank()) return false;
        if (!clientsByName.containsKey(oldName)) return false;
        if (clientsByName.containsKey(newName)) return false; // avoid overwrite
        ClientInfo info = clientsByName.remove(oldName);
        clientsByName.put(newName, info);
        save();
        return true;
    }

    public boolean addContact(String companyName, String email) {
        if (companyName == null || email == null || companyName.isBlank() || email.isBlank()) return false;
        ClientInfo info = clientsByName.get(companyName);
        if (info == null) return false;
        boolean added = info.contacts.add(email.trim());
        if (added) save();
        return added;
    }

    public boolean removeContact(String companyName, String email) {
        if (companyName == null || email == null || companyName.isBlank() || email.isBlank()) return false;
        ClientInfo info = clientsByName.get(companyName);
        if (info == null) return false;
        boolean removed = info.contacts.remove(email.trim());
        if (removed) save();
        return removed;
    }

    public boolean updateJobTitle(String companyName, int quoteNumber, String newTitle) {
        if (companyName == null || companyName.isBlank()) return false;
        ClientInfo info = clientsByName.get(companyName);
        if (info == null) return false;
        for (JobInfo j : info.jobs) {
            if (j.quoteNumber == quoteNumber) {
                j.jobTitle = (newTitle != null ? newTitle : "");
                save();
                return true;
            }
        }
        return false;
    }

    public boolean removeJob(String companyName, int quoteNumber) {
        if (companyName == null || companyName.isBlank()) return false;
        ClientInfo info = clientsByName.get(companyName);
        if (info == null) return false;
        boolean changed = info.jobs.removeIf(j -> j.quoteNumber == quoteNumber);
        if (changed) save();
        return changed;
    }

}
