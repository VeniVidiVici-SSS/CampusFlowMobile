# CampusFlow 🎓

**An intelligent, context-aware AI assistant built natively for Android to help university students manage their academic schedules, mess menus, and coursework.**

Developed for **Amazon Hackon 2026**.

## 🚀 Overview

CampusFlow is not just a chat wrapper. It is a deeply integrated native Android application powered by Google's Gemini Flash SDK. It acts as a localized AI Brain that actively reads your device's SQL database to manage your daily college life, completely through natural language.

## ✨ Key Features

- **On-Device RAG (Retrieval-Augmented Generation)**: The AI actively queries your local Android SQLite database in the background before answering. When you ask *"What's for dinner tomorrow?"*, it actually knows.
- **NLP CRUD Operations**: Complete Create, Read, Update, and Delete control over your schedule using purely natural language. Tell the AI: *"Actually, shift my Saturday DSA class to Sunday at 5 PM,"* and it handles the database transactions seamlessly.
- **Automated Push Notifications**: The app mathematically intercepts structured JSON intents from the AI to configure native Android `AlarmManager` alerts. It will automatically ping you exactly 15 minutes before your classes or meals begin.
- **Deep Alarm Scrubbing**: If you delete or shift a class via chat, the app hunts down the cryptographic `PendingIntent` and securely unregisters the old alarm from the Android System.
- **High-Availability Fallback Mesh**: Built-in silent failover system. If the primary AI model hits a Free-Tier rate limit, the app instantly catches the exception and hot-swaps its brain to a secondary Gemini model in milliseconds, ensuring zero downtime during demos.
- **Excel Bulk Import**: Uses Apache POI `DataFormatter` to accurately parse uploaded `.xlsx` spreadsheets, allowing students to import their entire semester schedule instantly.
- **Generalized Academic Tutor**: Beyond scheduling, the AI is unlocked to act as a full academic tutor capable of writing code, solving math problems, and explaining complex concepts.

## 🛠 Tech Stack

- **Frontend**: Kotlin, Jetpack Compose, Material Design 3
- **Local Database**: Room (SQLite), Coroutines/Flow
- **AI Engine**: Google Generative AI SDK (Gemini Flash/Pro family)
- **Background Tasks**: Android `AlarmManager`, `BroadcastReceiver`
- **File Parsing**: Apache POI (Excel `.xlsx` processing)

## 📱 How to Use

1. **Schedule a Class:** Type *"Add Embedded Systems on Monday at 10:15 AM in Room 404"*.
2. **Schedule Food:** Type *"Add Pizza for Dinner on Friday at 8:00 PM"*.
3. **Bulk Import:** Click the `+` icon to upload an Excel file formatted with `[Course, Day, Time, Location]`.
4. **Query:** Type *"What is my schedule for today?"* or *"When is my next class?"*
5. **Update/Delete:** Type *"Cancel my Friday Dinner"* or *"Move Monday's Embedded Systems class to Tuesday"*.
6. **Study:** Ask the AI to write a Python script or explain a physics concept.

## ⚙️ Setup & Installation

1. Clone the repository.
2. Add your Gemini API Key:
   - Create a `local.properties` file in the root directory.
   - Add the following line: `GEMINI_API_KEY="your_api_key_here"`
3. Sync Gradle and run the app on an Android Emulator or physical device (Android 13+ recommended for notification permissions).
