package com.easyread.model;

public class QuoteRecord {
    public int quoteNumber;          // e.g. 5462
    public String clientName;
    public String clientEmail;
    public String documentTitle;
    public String specialNotes;

    public int finalWordCount;
    public double estimatedHours;
    public double finalPriceGBP;

    public String attachedFilePath;   // where the client's doc lives on disk
    public String jobFolderPath;      // folder we made for this quote
}
