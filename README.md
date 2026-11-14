# JY-COMPUTERS Student Management System (JY-SMS)

Official Android mobile application for **JY-COMPUTERS** (Shop 4 Maiangwa Street, Dagbana, Jikwoyi Phase 3 Extension, FCT Abuja, Nigeria).

## Key Features & Modules
- **Authentication & Role-Based Access (`ADMINISTRATOR`, `INSTRUCTOR`, `STUDENT`)**: Google Sign-In powered by Jetpack `CredentialManager` and Firebase Authentication, backed by zero-trust Firestore Security Rules.
- **Branding & Material Design 3**: Navy (`#0A2240`), Emerald (`#00A86B`), and Gold (`#F59E0B`) theme with `Plus Jakarta Sans` & `Inter` typography, Light/Dark mode persistence, and Adaptive `NavigationBar` / `NavigationRail` + `ModalNavigationDrawer`.
- **Student Registration & Records**: Auto-generated Student ID (`JY-YYYY-XXXX`), passport photo picker via Android Photo Picker (`PickVisualMedia`), duplicate registration prevention, search & filter, and PDF / Excel (CSV) export.
- **Official JY-COMPUTERS Course Catalogue (NGN)**: Preloaded with all 9 official training programmes (`Computer Training - Basic Course`, `Certificate Course`, `Diploma 1 Course`, `Diploma 2 Course`, `Computer Repairs`, `Advanced Repairs`, `Understanding Computer Troubleshooting`, `Coding`, and `Android App Development`), plus Administrator CRUD and enrolment confirmation.
- **Payments & Receipts (NGN)**:
  - **Online Checkout (Paystack & Flutterwave abstraction)**: Strict server-side verification architecture (no gateway secret keys stored in the Android APK).
  - **OPay Direct Bank Transfer**: Displays official details (`OPay` • `JY-COMPUTERS` • `6118366524`), accepts transfer reference + proof image upload, and routes through Admin verification before updating balances and generating printable **PDF Payment Receipts**.
- **Academic Tracking & Certificates**:
  - Daily attendance marking (`PRESENT`, `ABSENT`, `LATE`, `EXCUSED`) with percentage calculation.
  - Score entry (`CA 40 + Exam 60 = Total 100`) with automatic grading (`A`–`F`).
  - Official printable **PDF Certificates** with verification QR matrix and online Certificate ID authenticity lookup.
- **Real-Time Student Chat & Moderation**: Private DMs (member search, online indicator, typing status, unread counts, emoji bar, image/file attachments), Course Group Chats + General Community (`general_community`), user blocking, message reporting, and Admin moderation tools.
- **Announcements, Notifications, Reports & Offline Cache**: Institutional & course announcements, notification preferences, 7 one-tap PDF/CSV export reports, Room offline database cache, and direct Click-to-Call / WhatsApp / Maps Contact Us actions.

## Clean Architecture Layout
- `app/src/main/java/com/example/core/` — Configuration (`InstitutionConfig`), NGN & date formatters, PDF/CSV `ExportUtils`, `PaymentGateway` abstraction, and Firestore error handling.
- `app/src/main/java/com/example/domain/model/` — Firestore-annotated domain models (`UserModels`, `AcademicModels`, `PaymentModels`, `CommunicationModels`).
- `app/src/main/java/com/example/data/local/` — Room offline cache (`JySmsDatabase`, `JySmsDao`, entities) and DataStore `AppPreferencesRepository`.
- `app/src/main/java/com/example/data/repository/` — `AuthRepository`, `StudentRepository`, `CourseEnrolmentRepository`, `PaymentRepository`, `AcademicRepository`, `CommunicationRepository`.
- `app/src/main/java/com/example/ui/` — Auth & splash screens, Dashboard, Student Management, Courses & Enrolments, Payments, Attendance/Results/Certificates, Chat, Announcements/Reports/Settings, and Navigation shell.

## Firebase & Production Backend Setup
1. **Firebase Project & Firestore Rules**:
   - Firestore Enterprise database ID is configured in `app/src/main/res/values/firebase_applet_config.xml` and protected by `firestore.rules`.
   - The bootstrap administrator account (`raphaelajah84@gmail.com`) is automatically elevated on verified Google Sign-In; additional Administrators and Instructors can be assigned under **Settings > User Role & Access Management**.
2. **Paystack & Flutterwave Server Verification (Required for Live Online Checkout)**:
   - Never embed `PAYSTACK_SECRET_KEY` or `FLUTTERWAVE_SECRET_KEY` in the Android client.
   - Deploy Cloud Functions or an HTTPS backend exposing:
     - `POST /payments/paystack/initialize` and `GET /payments/paystack/verify?reference=...`
     - `POST /payments/flutterwave/initialize` and `GET /payments/flutterwave/verify?reference=...`
   - Paste your deployed HTTPS base URL in **Settings > Paystack / Flutterwave Backend Endpoint** inside the app as Administrator, and validate webhooks server-side before writing `SUCCESSFUL` to `payments/{id}`.
3. **Optional Firebase Storage & Cloud Messaging (FCM)**:
   - Enable **Firebase Storage** in the Firebase Console if you want cross-device remote hosting for passport photographs, OPay transfer proof screenshots, and chat attachments (currently stored by URI reference).
   - Enable **Firebase Cloud Messaging (FCM)** and connect a Cloud Function trigger on `announcements`, `notifications`, and `conversations/{id}/messages` for background push delivery when the app is closed.

## Local Rules Verification
Run the Firestore security rules suites against the local emulators:
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
gradle :app:testDebugUnitTest --tests '*Rule*'
```
