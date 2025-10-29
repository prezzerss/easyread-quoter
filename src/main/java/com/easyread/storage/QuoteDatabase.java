package com.easyread.storage;

import com.easyread.model.QuoteRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Handles reading & writing the quotes_db.json file on disk.
public class QuoteDatabase {

    private static final ObjectMapper mapper =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final File dbFile;
    private int lastQuoteNumber;
    private List<QuoteRecord> quotes;

    // Represents the structure we store in JSON.
    public static class DatabaseWrapper {
        public int lastQuoteNumber;
        public List<QuoteRecord> quotes;
    }

    public QuoteDatabase(String filePath) {
        this.dbFile = new File(filePath);

        if (dbFile.exists()) {
            // Load existing file
            try {
                DatabaseWrapper w = mapper.readValue(dbFile, DatabaseWrapper.class);
                this.lastQuoteNumber = w.lastQuoteNumber;
                this.quotes = (w.quotes != null) ? w.quotes : new ArrayList<>();
            } catch (IOException e) {
                System.out.println("Couldn't read DB, starting fresh.");
                this.lastQuoteNumber = 0;
                this.quotes = new ArrayList<>();
            }
        } else {
            // No file yet: start fresh and immediately create it
            this.lastQuoteNumber = 0;
            this.quotes = new ArrayList<>();
            saveDatabase();
        }
    }

    // Get the number we'll assign next (e.g. 5462)
    public int getNextQuoteNumber() {
        return lastQuoteNumber + 1;
    }

    // Add a new quote record and persist it
    public void addQuote(QuoteRecord record) {
        record.quoteNumber = getNextQuoteNumber();
        quotes.add(record);
        lastQuoteNumber = record.quoteNumber;
        saveDatabase();
    }

    // Actually write to quotes_db.json
    private void saveDatabase() {
        try {
            DatabaseWrapper w = new DatabaseWrapper();
            w.lastQuoteNumber = this.lastQuoteNumber;
            w.quotes = this.quotes;
            mapper.writeValue(dbFile, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
