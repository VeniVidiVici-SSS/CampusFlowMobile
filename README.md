# CampusFlow 🎓

**An intelligent, context-aware AI assistant built natively for Android to help university students manage their academic schedules, mess menus, and coursework.**

Developed for **Amazon Hackon 2026**.

## 🚀 Overview

CampusFlow is not just a chat wrapper. It is a deeply integrated native Android application powered by Google's Gemini Flash SDK and Amazon Web Services (AWS). It acts as a localized AI Brain that actively manages your daily college life, completely through natural language, while keeping your data seamlessly synced to the cloud.

## ✨ Key Features

- **Native Productivity Dashboard**: The app launches into a custom Jetpack Compose dashboard featuring a full-month interactive calendar and a dynamically unified feed of your classes, mess menus, and custom tasks. State-based navigation ensures instantaneous switching between the Dashboard and the AI Assistant.
- **In-Context RAG Document Q&A**: The app leverages the massive token context window of the Gemini 2.0 architecture alongside native Android `PdfRenderer` and Apache POI. You can upload `.xlsx`, `.csv`, `.docx`, `.pptx`, `.pdf`, or even `.jpg` files. If it's a timetable, the AI extracts the schedule. If it's a 20-page syllabus or lecture notes, you can ask endless follow-up questions and the AI will act as a conversational tutor with perfect semantic recall.
- **Smart Bin-Packing Task Scheduler**: Provide a list of tasks and their durations (e.g., *"Schedule 2 hours of math and 1 hr of reading today"*). A native Android backend algorithm computes your strict time boundaries, and the AI actively bin-packs your tasks into the empty gaps of your day without overlaps. If it doesn't fit, it will conversationally suggest alternatives.
- **Intuitive NLP CRUD Operations**: Complete Create, Read, Update, and Delete control over your classes, mess menus, and custom recurring events using purely natural language. Tell the AI: *"Actually, shift my Saturday DSA class to Sunday at 5 PM,"* and it handles the complex database transactions seamlessly without asking rigid formatting questions.
- **AWS Cloud Synchronization**: Fully integrated with AWS DynamoDB for NoSQL database management and AWS S3 for secure file/schedule storage. Your data is persistently backed up to the cloud.
- **Automated Push Notifications & Recurrence**: The app mathematically intercepts structured JSON intents from the AI to configure native Android `AlarmManager` alerts. It supports complex recurrences (Daily, Weekly, Monthly) and will automatically ping you exactly 15 minutes before your events begin.
- **Deep Alarm Scrubbing**: If you delete a single instance of a recurring class via chat (e.g., *"Cancel today's physics class"*), the app dynamically scrubs that single cryptographic `PendingIntent` from the Android System without breaking the rest of the series.
- **High-Availability Fallback Mesh**: Built-in silent failover system. If the primary AI model hits a Free-Tier rate limit, the app instantly catches the exception and hot-swaps its brain to a secondary Gemini model in milliseconds, ensuring zero downtime during demos.
- **Generalized Academic Tutor**: Beyond scheduling, the AI is unlocked to act as a full academic tutor capable of writing code, solving math problems, and explaining complex concepts.

## 🛠 Tech Stack

- **Frontend**: Kotlin, Jetpack Compose, Material Design 3
- **Cloud/Backend**: AWS DynamoDB, AWS S3
- **AI Engine**: Google Generative AI SDK (Gemini 2.0 and 3.1 multimodal family)
- **Background Tasks**: Android `AlarmManager`, `BroadcastReceiver`
- **File Parsing**: Apache POI (Word, PPT, Excel), Android `PdfRenderer`, Android `Bitmap`

## 📱 How to Use

1. **Explore the Dashboard:** Tap any date on the home calendar to instantly view your unified events for that day. Tap the floating chat button to open the AI Assistant.
2. **Schedule a Class:** Type *"Add Embedded Systems on Monday at 10:15 AM in Room 404"*.
3. **Schedule Food:** Type *"Add Pizza for Dinner on Friday at 8:00 PM"*.
4. **Custom Events & Recurrence:** Type *"Schedule a weekly team standup every Wednesday at 9 AM"*.
5. **Multimodal Import:** Tap the `+` attach icon to upload an image, PDF, or spreadsheet. The AI will read it and ask if it missed anything!
6. **Query:** Type *"What is my schedule for today?"* or *"When is my next class?"*
7. **Update/Delete:** Type *"Cancel my Friday Dinner"* or *"Move Monday's Embedded Systems class to Tuesday"*.
8. **Study:** Ask the AI to write a Python script or explain a physics concept.

## ⚙️ Setup & Installation

1. Clone the repository.
2. Add your AWS and Gemini API Keys:
   - Create a `local.properties` file in the root directory.
   - Add the following lines:
     ```properties
     GEMINI_API_KEY="your_api_key_here"
     AWS_ACCESS_KEY_ID="your_aws_key"
     AWS_SECRET_ACCESS_KEY="your_aws_secret"
     AWS_REGION="ap-south-1"
     AWS_S3_BUCKET_NAME="your_bucket_name"
     ```
3. Configure your AWS DynamoDB backend:
   - Create the following three tables in your AWS Console (they are not auto-created):
   - **`CampusFlow_Classes`** -> Partition Key: `courseName` (String) | Sort Key: `dayOfWeek` (String)
   - **`CampusFlow_MessMenu`** -> Partition Key: `mealType` (String) | Sort Key: `dayOfWeek` (String)
   - **`CampusFlow_CustomEvents`** -> Partition Key: `eventName` (String) | Sort Key: `date` (String)
4. Sync Gradle and run the app on an Android Emulator or physical device (Android 13+ recommended for notification permissions).
