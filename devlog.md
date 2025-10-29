# Devlog – Easy Read Quoter

### 🗓️ Entry #1 – Project Setup and First Functional Build
**Date:** 29 October 2025  
**Author:** Presley Dobson  

---

### 🧠 Overview
This project is an internal desktop tool for **Easy Read Online**, built to automate the company’s quoting process.  
The aim is to reduce repetitive admin tasks by connecting our workflow across email, Trello, and Xero.

---

### ⚙️ Problem
Our existing quoting workflow was fully manual:
- Manually checking and assigning job numbers  
- Saving client files by hand  
- Logging quotes in Xero  
- Creating Trello cards with job info, hours, and status manually  

This caused slow turnaround times and frequent errors.

---

### 🎯 Goal
To build a **local desktop tool** that automates the boring steps (like job number, Trello card creation, and folder setup) while keeping manual control over pricing and hours for flexibility.

---

### 🧩 Technology choices
| Area | Decision | Why |
|------|-----------|-----|
| Programming language | **Java (JavaFX)** | Already part of my apprenticeship, easy to make a standalone app |
| Build tool | **Maven** | Handles dependencies and structure cleanly |
| Storage | **JSON file (Jackson)** | Lightweight, no database needed |
| Integration | **Trello REST API** | Allows automatic card creation and field updates |
| GUI | **JavaFX** | Cross-platform desktop UI, simple for local use |

---

### 🛠️ Implementation milestones
- ✅ Created Maven project and set up JavaFX  
- ✅ Built a clean GUI with form inputs and buttons  
- ✅ Added local JSON “database” to store quotes and last job number  
- ✅ Automated job folder creation on Mac (`~/EasyReadQuotes/`)  
- ✅ Integrated Trello API to:
  - Create a new card in the correct list
  - Set the status field to “with us”
  - Fill estimated hours  
- ✅ Fixed Trello API type errors (`number` vs `text` fields)  
- ✅ Confirmed working app with live Trello integration  

---

### 📈 Results
The new tool reduces quote creation time from ~8 minutes to < 1 minute.  
It ensures consistent naming, numbering, and status tracking while allowing the manager to adjust pricing as needed.

---

### 🔮 Next steps
- Add file upload (attach client file directly to Trello card)  
- Add a settings panel for API keys and field IDs (editable via GUI)  
- Build `.dmg` / `.exe` installer for easy deployment  
- Create an audit/history tab in the app  

---

### 💬 Reflection
This project was a huge learning curve in connecting APIs, managing JSON data, and designing a GUI that’s both functional and intuitive.  
It also showed me how automation doesn’t always mean removing humans from the process — it’s about giving them the right tools to focus on the meaningful parts.
