package com.easyread;

import com.easyread.model.QuoteRecord;
import com.easyread.storage.QuoteDatabase;
import com.easyread.trello.TrelloClient;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class MainApp extends Application {

    private QuoteRecord currentRecord; // the most recent quote we created in this session

    @Override
    public void start(Stage primaryStage) {

        //
        // 1. CONNECT TO LOCAL JSON DATABASE
        //
        String dbPath = "/Users/Shared/EasyReadQuoter/quotes_db.json"; // << change if you store it somewhere else
        QuoteDatabase db = new QuoteDatabase(dbPath);
        System.out.println("DB file is at: " + dbPath);


        //
        // ---------------- TAB 1: QUOTE DETAILS ----------------
        //
        VBox quoteDetailsLayout = new VBox(10);
        quoteDetailsLayout.setPadding(new Insets(15));

        TextField clientNameField = new TextField();
        TextField clientEmailField = new TextField();
        TextField docTitleField = new TextField();
        TextArea specialNotesArea = new TextArea();

        TextField wordCountField = new TextField();
        TextField hoursField = new TextField();
        TextField priceField = new TextField();

        TextField filePathField = new TextField();
        filePathField.setEditable(false);

        Button browseFileButton = new Button("Browse file…");

        Label quoteNumberLabel = new Label("Quote number: (not generated yet)");
        Button createPackageButton = new Button("Create Package");


        //
        // ---------------- TAB 2: TRELLO ----------------
        //
        VBox trelloLayout = new VBox(10);
        trelloLayout.setPadding(new Insets(15));

        Label trelloLabel = new Label("Trello Card Preview");
        TextArea trelloPreviewArea = new TextArea();
        trelloPreviewArea.setEditable(false);
        trelloPreviewArea.setPrefRowCount(10);

        Button createTrelloButton = new Button("Create Trello Card");


        trelloLayout.getChildren().addAll(
                trelloLabel,
                trelloPreviewArea,
                createTrelloButton
        );

        Tab trelloTab = new Tab("Trello", trelloLayout);
        trelloTab.setClosable(false);


        //
        // ---------------- TAB 3: XERO (PASTE-BLOCK HELPER) ----------------
        //
        VBox xeroLayout = new VBox(10);
        xeroLayout.setPadding(new Insets(15));

        Label xeroLabel = new Label("Xero Paste Block");
        TextArea xeroInfoArea = new TextArea();
        xeroInfoArea.setEditable(false);
        xeroInfoArea.setPrefRowCount(10);

        Button copyXeroButton = new Button("Copy All to Clipboard");

        xeroLayout.getChildren().addAll(
                xeroLabel,
                xeroInfoArea,
                copyXeroButton
        );

        Tab xeroTab = new Tab("Xero", xeroLayout);
        xeroTab.setClosable(false);


        //
        // -------- BROWSE FILE BUTTON ACTION --------
        //
        browseFileButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select client document");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Documents", "*.docx", "*.pdf", "*.*")
            );
            File f = chooser.showOpenDialog(primaryStage);
            if (f != null) {
                filePathField.setText(f.getAbsolutePath());
            }
        });


        //
        // -------- CREATE PACKAGE BUTTON ACTION --------
        //
        createPackageButton.setOnAction(e -> {
            try {
                // Build a record from current UI values
                QuoteRecord rec = new QuoteRecord();
                rec.clientName = clientNameField.getText();
                rec.clientEmail = clientEmailField.getText();
                rec.documentTitle = docTitleField.getText();
                rec.specialNotes = specialNotesArea.getText();

                rec.finalWordCount = Integer.parseInt(wordCountField.getText());
                rec.estimatedHours = Double.parseDouble(hoursField.getText());
                rec.finalPriceGBP = Double.parseDouble(priceField.getText());

                rec.attachedFilePath = filePathField.getText();

                // Create base folder for all jobs in the user's home directory
                String baseFolder = System.getProperty("user.home") + "/EasyReadQuotes";
                File baseDir = new File(baseFolder);
                baseDir.mkdirs();

                // Make safe-ish strings for folder names
                String safeClient = rec.clientName.replaceAll("[^A-Za-z0-9 _-]", "_");
                String safeDoc = rec.documentTitle.replaceAll("[^A-Za-z0-9 _-]", "_");

                // We peek at the next number from DB to build the folder name
                int nextNum = db.getNextQuoteNumber();
                String jobFolderPath = baseFolder + "/" + nextNum + " - " + safeClient + " - " + safeDoc;
                File jobDir = new File(jobFolderPath);
                jobDir.mkdirs();
                rec.jobFolderPath = jobFolderPath;

                // Save the record to the DB (this assigns quoteNumber and writes quotes_db.json)
                db.addQuote(rec);

                // Keep in memory for Trello/Xero tabs
                currentRecord = rec;

                // Update the label on screen to show the assigned quote number
                quoteNumberLabel.setText("Quote number: " + rec.quoteNumber);

                //
                // Build Trello preview text from this record
                //
                String trelloCardTitle = rec.quoteNumber
                        + " | "
                        + rec.documentTitle
                        + " | "
                        + rec.clientName;

                String trelloDesc =
                        "Brief\n"
                        + rec.specialNotes + "\n\n"
                        + "Company Preferences\n"
                        + "(TBC)\n";

                // Show in Trello tab so you can review/edit before sending
                trelloPreviewArea.setText(
                        "Card Title:\n" + trelloCardTitle +
                        "\n\n---\n\n" +
                        trelloDesc +
                        "\n---\n\n" +
                        "Estimated hours: " + rec.estimatedHours + "\n"
                );

                //
                // Build Xero paste block
                //
                String xeroBlock =
                        "Quote number: " + rec.quoteNumber + "\n" +
                        "Client email: " + rec.clientEmail + "\n\n" +
                        "Line item:\n" +
                        "Easy Read version of \"" + rec.documentTitle + "\"\n\n" +
                        "Price (ex VAT): £" + rec.finalPriceGBP + "\n" +
                        "Estimated hours: " + rec.estimatedHours + "h\n" +
                        "(Word count used: " + rec.finalWordCount + ")\n";

                xeroInfoArea.setText(xeroBlock);

                //
                // Confirmation popup
                //
                Alert ok = new Alert(Alert.AlertType.INFORMATION);
                ok.setTitle("Quote Saved");
                ok.setHeaderText("Quote " + rec.quoteNumber + " created");
                ok.setContentText(
                        "Job folder:\n" + rec.jobFolderPath +
                        "\n\nDB file:\n" + dbPath
                );
                ok.showAndWait();

            } catch (Exception ex) {
                ex.printStackTrace();
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Error");
                err.setHeaderText("Could not create package");
                err.setContentText(ex.getMessage());
                err.showAndWait();
            }
        });


        //
        // -------- COPY XERO INFO TO CLIPBOARD BUTTON ACTION --------
        //
        copyXeroButton.setOnAction(e -> {
            if (currentRecord == null) {
                Alert warn = new Alert(Alert.AlertType.WARNING);
                warn.setTitle("No quote yet");
                warn.setHeaderText("Please Create Package first");
                warn.setContentText("We don't have quote data to copy.");
                warn.showAndWait();
                return;
            }

            String block = xeroInfoArea.getText();
            ClipboardContent content = new ClipboardContent();
            content.putString(block);
            Clipboard.getSystemClipboard().setContent(content);

            Alert copied = new Alert(Alert.AlertType.INFORMATION);
            copied.setTitle("Copied");
            copied.setHeaderText("Xero details copied");
            copied.setContentText("Paste directly into Xero.");
            copied.showAndWait();
        });


        //
        // -------- CREATE TRELLO CARD BUTTON ACTION --------
        //
        // NOTE: You'll fill these IDs in after we fetch them from Trello.
        final String TRELLO_KEY = "9a03517de4f4abef7e5c51cd06487ae3";
        final String TRELLO_TOKEN = "ATTAa42ced1f5f30e3139ca63b8fa768bafd1581e699b38e873824d721d0c4c825ae759A7D9F";
        final String TRELLO_LIST_ID = "6867b3f24ff9b2f54aa8b931"; // e.g. the ID of 'Follow up' or 'To Quote'

        final String STATUS_FIELD_ID = "68680660756e5ba27c9f8d46"; // Trello custom field: Status
        final String STATUS_OPTION_WITH_US_ID = "68680660756e5ba27c9f8d47"; // option inside Status for "with us"
        final String EST_HOURS_FIELD_ID = "6888df8d59a5fa3d6f2b1072"; // Trello custom field: Estimated hours

        TrelloClient trelloClient = new TrelloClient(
                TRELLO_KEY,
                TRELLO_TOKEN,
                TRELLO_LIST_ID,
                STATUS_FIELD_ID,
                STATUS_OPTION_WITH_US_ID,
                EST_HOURS_FIELD_ID
        );

        createTrelloButton.setOnAction(e -> {
            if (currentRecord == null) {
                Alert warn = new Alert(Alert.AlertType.WARNING);
                warn.setTitle("No quote yet");
                warn.setHeaderText("Please Create Package first");
                warn.setContentText("We don't have quote data to send to Trello.");
                warn.showAndWait();
                return;
            }

            try {
                // Build title in your required format:
                // "5462 | Safeguarding Policy | Example Council"
                String cardTitle = currentRecord.quoteNumber
                        + " | "
                        + currentRecord.documentTitle
                        + " | "
                        + currentRecord.clientName;

                // Build description body:
                String description =
                        "Brief\n"
                        + currentRecord.specialNotes + "\n\n"
                        + "Company Preferences\n"
                        + "(TBC)\n";

                // Step 1: Create card in Trello list
                String cardId = trelloClient.createCard(cardTitle, description);

                // Step 2: Set status = "with us" (custom field dropdown)
                trelloClient.setStatusWithUs(cardId);

                // Step 3: Set estimated hours (custom field text/number)
                trelloClient.setEstimatedHours(cardId, currentRecord.estimatedHours);

                Alert done = new Alert(Alert.AlertType.INFORMATION);
                done.setTitle("Trello");
                done.setHeaderText("Card created");
                done.setContentText("New Trello card ID:\n" + cardId);
                done.showAndWait();

            } catch (Exception ex) {
                ex.printStackTrace();
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Trello error");
                err.setHeaderText("Could not create Trello card");
                err.setContentText(ex.getMessage());
                err.showAndWait();
            }
        });


        //
        // -------- LAYOUT FOR TAB 1 (QUOTE DETAILS) --------
        //
        quoteDetailsLayout.getChildren().addAll(
                new Label("Client Name:"), clientNameField,
                new Label("Client Email:"), clientEmailField,
                new Label("Document Title / Job Name:"), docTitleField,
                new Label("Special Requests / Notes (Brief):"), specialNotesArea,

                new Label("Final Billable Word Count:"), wordCountField,
                new Label("Estimated Hours:"), hoursField,
                new Label("Final Price £:"), priceField,

                new Label("Client File:"),
                new HBox(10, filePathField, browseFileButton),

                quoteNumberLabel,
                createPackageButton
        );

        //
        // -------- PUT ALL TABS IN THE WINDOW --------
        //
        Tab quoteTab = new Tab("Quote Details", quoteDetailsLayout);
        quoteTab.setClosable(false);

        TabPane tabPane = new TabPane(quoteTab, trelloTab, xeroTab);

        Scene scene = new Scene(tabPane, 650, 750);
        primaryStage.setTitle("Easy Read Quoter");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
