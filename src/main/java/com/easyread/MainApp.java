package com.easyread;

import java.nio.file.Path;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.beans.value.ObservableValue;
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
import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.easyread.storage.ClientDatabase;

import java.io.File;

public class MainApp extends Application {

    private QuoteRecord currentRecord; // most recent quote we created in this session
    
 // Refreshes the list of companies shown in the Clients tab
    private void refreshCompanyList() {
        // TODO: if you have a ListView or TableView for companies, refresh its items here
        System.out.println("Company list refreshed.");
    }

    // Refreshes the details section (contacts + jobs)
    private void refreshDetails() {
        // TODO: if you have details displayed for a selected company, refresh here
        System.out.println("Details refreshed.");
    }


    @Override
    public void start(Stage primaryStage) {

        //
        // 1. CONNECT TO LOCAL JSON DATABASE
        //
    	// Use a portable data directory (Render sets DATA_DIR=/data via env var)
    	// Locally, it falls back to ./data
    	String dataDir = System.getenv().getOrDefault("DATA_DIR", "data");
    	java.nio.file.Path dataPath = java.nio.file.Paths.get(dataDir);
    	try {
    	    java.nio.file.Files.createDirectories(dataPath);
    	} catch (Exception ioe) {
    	    throw new RuntimeException("Could not create data directory: " + dataDir, ioe);
    	}

    	// Quotes DB lives under DATA_DIR
    	String dbPath = java.nio.file.Paths.get(dataDir, "quotes_db.json").toString();
    	QuoteDatabase db = new QuoteDatabase(dbPath);
    	System.out.println("DB file is at: " + dbPath);
    	
    	
        
        String clientsDbPath = java.nio.file.Paths.get(dataDir, "clients_db.json").toString();
        ClientDatabase clientDb = new ClientDatabase(clientsDbPath);
        System.out.println("Clients DB file is at: " + clientsDbPath);
        
        




        //
        // ---------------- TAB 1: QUOTE DETAILS ----------------
        //
        VBox quoteDetailsLayout = new VBox(10);
        quoteDetailsLayout.setPadding(new Insets(15));

        // --- INPUT FIELDS (TAB 1) ---
        TextField companyNameField = new TextField();   // Company / Organisation
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
        // ---------------- TAB 3: XERO ----------------
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
     // ---------------- TAB 4: CLIENTS (CRM) ----------------
     //
     VBox clientsLayout = new VBox(10);
     clientsLayout.setPadding(new Insets(15));

     // Search bar
     TextField clientSearchField = new TextField();
     clientSearchField.setPromptText("Search companies…");

     // Company list
     ListView<String> companyListView = new ListView<>();
     companyListView.setPrefWidth(240);
     companyListView.setMaxWidth(280);

     // Details panel (right side)
     TextField companyNameEditField = new TextField();
     companyNameEditField.setPromptText("Company name");

     Button renameCompanyButton = new Button("Rename Company");

     Label contactsLabel = new Label("Contacts");
     ListView<String> contactsListView = new ListView<>();
     contactsListView.setPrefHeight(120);

     TextField newContactField = new TextField();
     newContactField.setPromptText("Add contact email");
     Button addContactButton = new Button("Add Contact");
     Button removeContactButton = new Button("Remove Selected Contact");

     // Jobs table
     Label jobsLabel = new Label("Jobs");
     TableView<JobRow> jobsTable = new TableView<>();
     jobsTable.setPrefHeight(220);

     TableColumn<JobRow, Integer> colQuote = new TableColumn<>("Quote #");
     colQuote.setCellValueFactory(new PropertyValueFactory<>("quoteNumber"));
     colQuote.setPrefWidth(90);

     TableColumn<JobRow, String> colTitle = new TableColumn<>("Job Title");
     colTitle.setCellValueFactory(new PropertyValueFactory<>("jobTitle"));
     colTitle.setPrefWidth(300);

     jobsTable.getColumns().addAll(colQuote, colTitle);

     TextField editJobTitleField = new TextField();
     editJobTitleField.setPromptText("New job title for selected row");
     Button updateJobTitleButton = new Button("Update Job Title");
     Button removeJobButton = new Button("Remove Selected Job");
     
  // --- Delete company controls (Clients tab) ---
     TextField deleteCompanyField = new TextField();
     deleteCompanyField.setPromptText("Exact company name");

     Button deleteCompanyButton = new Button("Delete");


     // Export button (quick access)
     Button exportClientsCsvButton = new Button("Export CSV Now");

     // Lay out left/right
     VBox leftBox = new VBox(8, new Label("Companies"), clientSearchField, companyListView);
     leftBox.setPrefWidth(300);

     VBox rightBox = new VBox(8,
             new Label("Company"),
             companyNameEditField,
             renameCompanyButton,
             new Separator(),
             contactsLabel,
             contactsListView,
             new HBox(8, newContactField, addContactButton, removeContactButton),
             new Separator(),
             jobsLabel,
             jobsTable,
             new HBox(8, editJobTitleField, updateJobTitleButton, removeJobButton),
             new Separator(),
             exportClientsCsvButton,
             new Separator(),
             new Label("Delete Company"),
             new HBox(8, deleteCompanyField, deleteCompanyButton)
             
             

             
     );
     rightBox.setPrefWidth(520);

     HBox clientsSplit = new HBox(14, leftBox, rightBox);
     clientsLayout.getChildren().add(clientsSplit);

     // Helper to (re)load companies into the list, with optional filter
     Runnable refreshCompanyList = () -> {
         ObservableList<String> items = FXCollections.observableArrayList();
         for (String name : clientDb.getAllClients().keySet()) {
             String filter = clientSearchField.getText();
             if (filter == null || filter.isBlank() || name.toLowerCase().contains(filter.toLowerCase())) {
                 items.add(name);
             }
         }
         companyListView.setItems(items.sorted());
     };

     // Helper to load details for the selected company
     Runnable refreshDetails = () -> {
         String sel = companyListView.getSelectionModel().getSelectedItem();
         if (sel == null) {
             companyNameEditField.clear();
             contactsListView.setItems(FXCollections.observableArrayList());
             jobsTable.setItems(FXCollections.observableArrayList());
             return;
         }

         var map = clientDb.getAllClients();
         var info = map.get(sel);
         companyNameEditField.setText(sel);

         // contacts
         ObservableList<String> contacts = FXCollections.observableArrayList(info.contacts);
         contactsListView.setItems(contacts.sorted());

         // jobs
         ObservableList<JobRow> rows = FXCollections.observableArrayList();
         for (var j : info.jobs) {
             rows.add(new JobRow(j.quoteNumber, j.jobTitle));
         }
         jobsTable.setItems(rows);
     };

     clientSearchField.textProperty().addListener((obs, a, b) -> refreshCompanyList.run());
     companyListView.getSelectionModel().selectedItemProperty().addListener(
             (ObservableValue<? extends String> obs, String old, String now) -> refreshDetails.run()
     );

     // Rename company
     renameCompanyButton.setOnAction(e -> {
         String oldName = companyListView.getSelectionModel().getSelectedItem();
         String newName = companyNameEditField.getText();
         if (oldName == null || newName == null || newName.isBlank()) {
             alertInfo("Rename", "Pick a company and type a new name.");
             return;
         }
         if (clientDb.renameCompany(oldName, newName)) {
             // refresh left list & select new name
             refreshCompanyList.run();
             companyListView.getSelectionModel().select(newName);
             refreshDetails.run();
             // re-export CSV
             clientDb.exportCsv("/Users/Shared/EasyReadQuoter/clients_export.csv");
         } else {
             alertInfo("Rename", "Could not rename (name may already exist).");
         }
     });

     // Add / remove contacts
     addContactButton.setOnAction(e -> {
         String sel = companyListView.getSelectionModel().getSelectedItem();
         String email = newContactField.getText();
         if (sel == null || email == null || email.isBlank()) {
             alertInfo("Contacts", "Select a company and enter an email.");
             return;
         }
         if (clientDb.addContact(sel, email)) {
             newContactField.clear();
             refreshDetails.run();
             clientDb.exportCsv("/Users/Shared/EasyReadQuoter/clients_export.csv");
         } else {
             alertInfo("Contacts", "Could not add contact.");
         }
     });

     removeContactButton.setOnAction(e -> {
         String sel = companyListView.getSelectionModel().getSelectedItem();
         String email = contactsListView.getSelectionModel().getSelectedItem();
         if (sel == null || email == null) {
             alertInfo("Contacts", "Pick a company and a contact to remove.");
             return;
         }
         if (clientDb.removeContact(sel, email)) {
             refreshDetails.run();
             clientDb.exportCsv("/Users/Shared/EasyReadQuoter/clients_export.csv");
         } else {
             alertInfo("Contacts", "Could not remove contact.");
         }
     });

     // Update / remove jobs
     updateJobTitleButton.setOnAction(e -> {
         String sel = companyListView.getSelectionModel().getSelectedItem();
         JobRow row = jobsTable.getSelectionModel().getSelectedItem();
         String newTitle = editJobTitleField.getText();
         if (sel == null || row == null || newTitle == null) {
             alertInfo("Jobs", "Pick a company, select a job row, and enter a new title.");
             return;
         }
         if (clientDb.updateJobTitle(sel, row.getQuoteNumber(), newTitle)) {
             editJobTitleField.clear();
             refreshDetails.run();
             clientDb.exportCsv("/Users/Shared/EasyReadQuoter/clients_export.csv");
         } else {
             alertInfo("Jobs", "Could not update job title.");
         }
     });

     removeJobButton.setOnAction(e -> {
         String sel = companyListView.getSelectionModel().getSelectedItem();
         JobRow row = jobsTable.getSelectionModel().getSelectedItem();
         if (sel == null || row == null) {
             alertInfo("Jobs", "Pick a company and a job row to remove.");
             return;
         }
         if (clientDb.removeJob(sel, row.getQuoteNumber())) {
             refreshDetails.run();
             clientDb.exportCsv("/Users/Shared/EasyReadQuoter/clients_export.csv");
         } else {
             alertInfo("Jobs", "Could not remove job.");
         }
     });

     // Manual export
     exportClientsCsvButton.setOnAction(e -> {
         clientDb.exportCsv("/Users/Shared/EasyReadQuoter/clients_export.csv");
         alertInfo("Export", "Exported to /Users/Shared/EasyReadQuoter/clients_export.csv");
     });

     // Initial load
     refreshCompanyList.run();
     Tab clientsTab = new Tab("Clients", clientsLayout);
     clientsTab.setClosable(false);



        //
        // ---------------- TAB 4: EMAIL ----------------
        //
        VBox emailLayout = new VBox(10);
        emailLayout.setPadding(new Insets(15));

        Label emailHeaderLabel = new Label("Quote Email Preview");

        // "To:"
        TextField emailToField = new TextField();
        emailToField.setEditable(false);

        // "Subject:"
        TextField emailSubjectField = new TextField();
        emailSubjectField.setEditable(false);

        // "Body:"
        TextArea emailBodyArea = new TextArea();
        emailBodyArea.setPrefRowCount(12);
        emailBodyArea.setWrapText(true);

        Button openEmailClientButton = new Button("Open Email Draft");


        emailLayout.getChildren().addAll(
                emailHeaderLabel,
                new Label("To:"), emailToField,
                new Label("Subject:"), emailSubjectField,
                new Label("Body:"), emailBodyArea,
                openEmailClientButton
        );

        Tab emailTab = new Tab("Email", emailLayout);
        emailTab.setClosable(false);


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
                //
                // 1. Build a record from current UI values
                //
                QuoteRecord rec = new QuoteRecord();
                rec.companyName = companyNameField.getText();
                rec.clientEmail = clientEmailField.getText();
                rec.documentTitle = docTitleField.getText();
                rec.specialNotes = specialNotesArea.getText();

                rec.finalWordCount = Integer.parseInt(wordCountField.getText());
                rec.estimatedHours = Double.parseDouble(hoursField.getText());
                rec.finalPriceGBP = Double.parseDouble(priceField.getText());

                rec.attachedFilePath = filePathField.getText();

                //
                // 2. Create base folder ~/EasyReadQuotes (if missing)
                //
             // Put job folders under DATA_DIR so they persist on the server disk
                String baseFolder = java.nio.file.Paths.get(dataDir, "jobs").toString();
                File baseDir = new File(baseFolder);
                baseDir.mkdirs();


                //
                // 3. Build safe folder name and create job folder
                //
                String safeCompany =
                        (rec.companyName == null || rec.companyName.isBlank())
                                ? "NO_COMPANY"
                                : rec.companyName.replaceAll("[^A-Za-z0-9 _-]", "_").trim();

                String safeDoc =
                        (rec.documentTitle == null || rec.documentTitle.isBlank())
                                ? "NO_TITLE"
                                : rec.documentTitle.replaceAll("[^A-Za-z0-9 _-]", "_").trim();

                // Get next quote number BEFORE saving, so folder name is correct
                int nextNum = db.getNextQuoteNumber();

                String jobFolderPath =
                        baseFolder + "/" + nextNum + " - " + safeCompany + " - " + safeDoc;

                File jobDir = new File(jobFolderPath);
                jobDir.mkdirs();

                rec.jobFolderPath = jobFolderPath;

                //
                // 4. Save record to DB (this assigns rec.quoteNumber internally)
                //
                db.addQuote(rec);

                //
                // 5. Keep it in memory for Trello / Xero / Email tabs
                //
                currentRecord = rec;

                //
                // 6. Update visible quote number label
                //
                quoteNumberLabel.setText("Quote number: " + rec.quoteNumber);
                
             // Record this client+quote in the CRM-style ClientDatabase
                clientDb.recordQuote(
                        rec.companyName,        // company
                        rec.clientEmail,        // contact email
                        rec.quoteNumber,        // e.g. 5462
                        rec.documentTitle       // job / document title
                );
                
             // After updating the CRM JSON, also export a spreadsheet
                String crmCsvPath = "/Users/Shared/EasyReadQuoter/clients_export.csv";
                clientDb.exportCsv(crmCsvPath);
                System.out.println("Exported CRM CSV to: " + crmCsvPath);



                //
                // 7. Build Trello preview text
                //
                String trelloCardTitle = rec.quoteNumber
                        + " | "
                        + rec.documentTitle
                        + " | "
                        + rec.companyName;

                // We no longer overwrite Trello description in the API if you want to preserve template,
                // but we still show a summary here for review:
                String trelloDescPreview =
                        "Brief\n" +
                        rec.specialNotes + "\n\n" +
                        "Client email: " + rec.clientEmail + "\n\n" +
                        "Estimated hours: " + rec.estimatedHours + "\n";

                trelloPreviewArea.setText(
                        "Card Title:\n" + trelloCardTitle +
                        "\n\n---\n\n" +
                        trelloDescPreview
                );

                //
                // 8. Build Xero info block
                //
                String xeroBlock =
                        "Quote number: " + rec.quoteNumber + "\n" +
                        "Company: " + rec.companyName + "\n" +
                        "Client email: " + rec.clientEmail + "\n\n" +
                        "Line item:\n" +
                        "Easy Read version of \"" + rec.documentTitle + "\"\n\n" +
                        "Price (ex VAT): £" + rec.finalPriceGBP + "\n" +
                        "Estimated hours: " + rec.estimatedHours + "h\n" +
                        "(Word count used: " + rec.finalWordCount + ")\n";

                xeroInfoArea.setText(xeroBlock);
                
                

                //
                // 9. Build Email tab preview
                //
                String emailTo = rec.clientEmail;

                String emailSubject =
                        rec.quoteNumber + " | Quote for " + rec.documentTitle;

                String emailBody =
                        "Hi,\n\n" +
                        "Please find attached our Easy Read quote for \"" + rec.documentTitle + "\".\n\n" +
                        "The total cost will be £" + rec.finalPriceGBP + " (ex VAT).\n" +
                        "If you're happy to go ahead, just reply to confirm and we'll get you booked in.\n\n" +
                        "Best regards,\n" +
                        "Easy Read Online\n";

                emailToField.setText(emailTo);
                emailSubjectField.setText(emailSubject);
                emailBodyArea.setText(emailBody);

                //
                // 10. Confirmation popup
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
        
        Button createXeroQuoteBtn = new Button("Create in Xero");
        xeroLayout.getChildren().add(createXeroQuoteBtn);

        createXeroQuoteBtn.setOnAction(ev -> {
            if (currentRecord == null) {
                alert("Xero", "Create a package first."); return;
            }
            try {
            	//var xero = new com.easyread.xero.XeroClient("059309B562E941509530413753FA3715");
            	
            	
            	String xeroClientId = System.getenv("XERO_CLIENT_ID");
            	if (xeroClientId == null || xeroClientId.isBlank()) {
            	    alert("Xero", "XERO_CLIENT_ID env var is not set.");
            	    return;
            	}
            	var xero = new com.easyread.xero.XeroClient(xeroClientId);


                // 1) Contact
                String contactId = xero.getOrCreateContact(
                        currentRecord.companyName,
                        currentRecord.clientEmail
                );

                // 2) Create draft quote
                String ref = "Quote " + currentRecord.quoteNumber;
                String accountCode = "200"; // <- confirm with finance
                String quoteId = xero.createDraftQuote(
                        contactId,
                        currentRecord.documentTitle,
                        ref,
                        currentRecord.finalPriceGBP,
                        accountCode
                );

                // 3) Download PDF
                Path pdfPath = Path.of(currentRecord.jobFolderPath, "Quote_" + currentRecord.quoteNumber + ".pdf");
                xero.downloadQuotePdf(quoteId, pdfPath);

                // 4) Done
                alert("Xero", "Draft quote created.\nQuoteID: " + quoteId + "\nSaved:\n" + pdfPath);

            } catch (Exception ex) {
                ex.printStackTrace();
                alert("Xero Error", ex.getMessage());
            }
        });



        //
        // -------- COPY EMAIL TO CLIPBOARD BUTTON ACTION --------
        //
        /* copyEmailButton.setOnAction(e -> {
            String fullEmail =
                    "To: " + emailToField.getText() + "\n" +
                    "Subject: " + emailSubjectField.getText() + "\n\n" +
                    emailBodyArea.getText();

            ClipboardContent cc = new ClipboardContent();
            cc.putString(fullEmail);
            Clipboard.getSystemClipboard().setContent(cc);

            Alert copied = new Alert(Alert.AlertType.INFORMATION);
            copied.setTitle("Copied");
            copied.setHeaderText("Email copied to clipboard");
            copied.setContentText("Paste into your email client, attach the quote PDF, and send.");
            copied.showAndWait();
        }); */
        
        openEmailClientButton.setOnAction(e -> {
            if (currentRecord == null) {
                Alert warn = new Alert(Alert.AlertType.WARNING);
                warn.setTitle("No quote yet");
                warn.setHeaderText("Please Create Package first");
                warn.setContentText("We don't have quote data to build the email.");
                warn.showAndWait();
                return;
            }

            try {
                // Get the current filled values from the UI fields
                String to = emailToField.getText();
                String subject = emailSubjectField.getText();
                String body = emailBodyArea.getText();

                // URL-encode subject and body for safety (spaces, £, quotes, newlines)
                String encodedSubject = URLEncoder.encode(subject, StandardCharsets.UTF_8.toString())
                        .replace("+", "%20");
                String encodedBody = URLEncoder.encode(body, StandardCharsets.UTF_8.toString())
                        .replace("+", "%20");

                // Build mailto URI
                // format: mailto:someone@example.com?subject=...&body=...
                String mailto = "mailto:" + to +
                        "?subject=" + encodedSubject +
                        "&body=" + encodedBody;

                // Launch user's default mail client with this draft
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().mail(new URI(mailto));
                } else {
                    throw new UnsupportedOperationException("Desktop API is not supported on this system.");
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Email Error");
                err.setHeaderText("Couldn't open your email client");
                err.setContentText(ex.getMessage());
                err.showAndWait();
            }
        });



        //
        // -------- CREATE TRELLO CARD BUTTON ACTION --------
        //
        final String TRELLO_KEY = "9a03517de4f4abef7e5c51cd06487ae3";
        final String TRELLO_TOKEN = "ATTAa42ced1f5f30e3139ca63b8fa768bafd1581e699b38e873824d721d0c4c825ae759A7D9F";
        final String TRELLO_LIST_ID = "6867b3f24ff9b2f54aa8b931";

        final String STATUS_FIELD_ID = "68680660756e5ba27c9f8d46";          // Status custom field
        final String STATUS_OPTION_WITH_US_ID = "68680660756e5ba27c9f8d47"; // "with us"
        final String EST_HOURS_FIELD_ID = "6888df8d59a5fa3d6f2b1072";       // Estimated hours

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
                // 1. Create/clone card in Trello
                String newCardId = trelloClient.createFullCardForQuote(
                        currentRecord.companyName,
                        currentRecord.documentTitle,
                        currentRecord.quoteNumber,
                        currentRecord.specialNotes,
                        currentRecord.clientEmail,
                        currentRecord.estimatedHours
                );

                // 2. Upload the client file as an attachment
                trelloClient.uploadAttachmentToCard(newCardId, currentRecord.attachedFilePath);

                // 3. Alert success
                Alert done = new Alert(Alert.AlertType.INFORMATION);
                done.setTitle("Trello");
                done.setHeaderText("Card created");
                done.setContentText(
                        "New Trello card ID:\n" + newCardId + "\n\n" +
                        "Attachment uploaded (if any)."
                );
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
     // -------- LAYOUT FOR TAB 1 (QUOTE DETAILS) --------
        quoteDetailsLayout.getChildren().addAll(
            new Label("Company / Organisation Name:"), companyNameField,
            new Label("Client Email:"), clientEmailField,
            new Label("Document Title / Job Name:"), docTitleField,
            new Label("Special Requests / Notes (Brief):"), specialNotesArea,

            new Label("Final Billable Word Count:"), wordCountField,
            new Label("Estimated Hours:"), hoursField,
            new Label("Final Price £:"), priceField,

            new Label("Client File:"),
            new HBox(10, filePathField, browseFileButton),

            quoteNumberLabel,
            createPackageButton   // <— last item, NO comma after this line
        );


        
        deleteCompanyButton.setOnAction(e -> {
            String target = deleteCompanyField.getText();
            if (target == null || target.isBlank()) {
                alertInfo("Delete Company", "Please enter the exact company name to delete.");
                return;
            }

            boolean deleted = clientDb.deleteCompany(target);
            if (deleted) {
                deleteCompanyField.clear();
                clientDb.exportCsv("/Users/Shared/EasyReadQuoter/clients_export.csv");
                refreshCompanyList();   // method, not Runnable
                refreshDetails();       // method, not Runnable
                alertInfo("Delete Company", "Company \"" + target + "\" deleted.");
            } else {
                alertInfo("Delete Company", "No company named \"" + target + "\" found.");
            }
        });







        //
        // -------- PUT ALL TABS IN THE WINDOW --------
        //
        Tab quoteTab = new Tab("Quote Details", quoteDetailsLayout);
        quoteTab.setClosable(false);

        TabPane tabPane = new TabPane(quoteTab, trelloTab, xeroTab, emailTab, clientsTab);

        Scene scene = new Scene(tabPane, 650, 800);
        primaryStage.setTitle("Easy Read Quoter");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
 // Simple row model for the jobs table
    public static class JobRow {
        private final int quoteNumber;
        private final String jobTitle;
        public JobRow(int quoteNumber, String jobTitle) {
            this.quoteNumber = quoteNumber;
            this.jobTitle = jobTitle;
        }
        public int getQuoteNumber() { return quoteNumber; }
        public String getJobTitle() { return jobTitle; }
    }
    
 // --- tiny helpers so we can call alert(...), alertInfo(...), alertError(...)
    private void alert(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }

    private void alertInfo(String title, String content) {  // same as alert, just explicit name
        alert(title, content);
    }

    private void alertError(String title, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(content);
        a.showAndWait();
    }



    public static void main(String[] args) {
        launch(args);
    }
    
    
}
