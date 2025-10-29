# Easy Read Quoter

A JavaFX desktop tool built for **Easy Read Online** to automate and streamline the internal quoting process.

---

## 🚀 Features
- Auto-increments quote numbers
- Creates job folders locally (`~/EasyReadQuotes`)
- Saves quote info to a JSON database
- Integrates with Trello:
  - Creates cards in the correct list
  - Sets status = “with us”
  - Fills “Estimated Hours” field
- Generates Xero-ready text blocks for quotes

---

## 🧰 Tech Stack
- Java 21  
- JavaFX (GUI)  
- Maven  
- Jackson (JSON handling)  
- Trello REST API  

---

## 💡 Why it exists
Before this tool, quoting involved:
- Manually tracking job numbers
- Re-entering details into Xero and Trello
- Copy-pasting client info repeatedly

This app reduces the process from **8 minutes to under 1** while keeping human flexibility for pricing decisions.

---

## 📖 Documentation
See [`devlog.md`](./devlog.md) for a detailed development log and reflection notes.
