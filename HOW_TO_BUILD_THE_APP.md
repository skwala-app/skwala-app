# SK WALA Android app – build it and publish it on Google Play

## What the app has
- **Professional start screen** (SK WALA logo, name, tagline) and a native **bottom menu**: Home · Results · Messages · More
- **More** menu: Share SK WALA app, Rate on Google Play, Notification settings, Contact, Privacy policy, About, Reload
- **Notifications** (checked about once an hour): new mock tests, announcements, information and personal messages from you – tapping one opens the right page
- Uses **your existing SK WALA website** – all tests, results, payments, PYQ / Full-Length folders and messages come from it automatically
- **Screenshots and screen recording are blocked** inside the app
- UPI buttons open GPay / PhonePe / Paytm / BHIM; the online payment page opens in the browser
- Shared PDFs open in the phone's PDF viewer; Download saves to *Downloads/SK WALA*
- "No internet – Try again" screen, Android 16 back gesture support
- Targets **Android 16 (API 36)** – required by Google Play for new apps – and runs on Android 6.0 and newer

---

## Part A – Build the app (your laptop)

### A0. Three settings to check (file `app/src/main/res/values/strings.xml`)
| Setting | What to put |
|---|---|
| `site_url` | Your SK WALA **Web app URL** from Apps Script → Deploy → Manage deployments (the deployment you update with ✏️ → New version) |
| `privacy_url` | Link of the **Privacy Policy** page on your Google Site (Part B1) |
| `contact_email` | The email students may write to (shown in More → Contact). Leave empty to hide it. |

### A1. Install Android Studio (free)
**developer.android.com/studio** → Download → install with default options → start it once and let it download the Android SDK.
In **Tools → SDK Manager** make sure **Android 16 (API 36)** is ticked.

### A2. Open the project
Unzip **SKWALA-Android.zip** → Android Studio → **Open** → choose the **SKWALA-Android** folder → wait for "Gradle sync finished" (first time 5–15 min).
If Android Studio offers to upgrade the Android Gradle Plugin, you may accept.

### A3. Try it on your phone first
Phone: Settings → About phone → tap **Build number** 7 times → Developer options → **USB debugging** ON → connect by USB → in Android Studio press ▶ **Run**.
Check: login, a test, result, messages, a PDF, a payment button, notifications (send yourself an announcement and wait up to an hour, or reopen the app later).

### A4. Build the file for Google Play (.aab)
1. **Build → Generate Signed App Bundle / APK… → Android App Bundle → Next**
2. **Create new…** key store: e.g. `Documents\skwala-key.jks`, a password (write it down), Alias `skwala`, 25 years, Shreenath Kulal, IN → OK
   **Keep this .jks file and the passwords safe for ever** – every future update needs them.
3. **release → Create**. The file: `app/release/app-release.aab`

(For an APK to share on WhatsApp choose **APK** in step 1 instead – same key.)

---

## Part B – Publish on Google Play

### B1. Before you start
- **Privacy Policy page** on your Google Site (Google Sites → + new page "Privacy Policy" → paste the text from `PLAY_STORE_LISTING.md` → Publish). Put its link in `privacy_url` (A0).
- **Google Play developer account**: **play.google.com/console** → sign up → *Personal* (or *Organization* if SK WALA is a registered business) → pay the one-time **US $25** → verify your identity (PAN/Aadhaar-style ID, phone, address). Verification can take a few days.

### B2. Create the app
Play Console → **Create app** → App name **SK WALA: JEE & KCET Mock Tests** → Default language English (India) → **App** → **Free** → accept declarations → Create.

### B3. Fill "Set up your app" (Dashboard checklist)
Everything you need to type is in **PLAY_STORE_LISTING.md**:
- App access (all features available without special access; students log in with their own details – give the reviewer a test name, mobile and Gmail and switch off the Gmail code for review, or give them a test account)
- Ads: **No ads**
- Content rating questionnaire → Category *Education / Reference*, answer **No** to violence, etc.
- Target audience: **16–17** and **18 and over** (JEE / KCET aspirants)
- Data safety: answers listed in the listing file
- Government app: No · Financial features: No (UPI payments for tests are handled by UPI apps / Razorpay) · Health: No
- **Store listing**: app name, short & full description, icon `app_icon_512.png`, feature graphic `feature_graphic_1024x500.png`, phone screenshots `phone_1.png … phone_6.png`, category **Education**, your contact email, privacy policy link

### B4. Closed test (needed for new personal accounts)
Google requires new personal developer accounts to run a **closed test with at least 12 testers who stay opted in for 14 days in a row** before the app can go public.
1. **Testing → Closed testing → Create track** → **Testers**: create an email list with **12+ Gmail IDs** (your students / colleagues / family – more than 12 is safer).
2. **Create release** → upload `app-release.aab` → release name 1.0 → **Save → Review → Start rollout**.
3. Send testers the **opt-in link** (shown on the Testers tab). Each tester opens it, taps **Become a tester**, installs SK WALA from Play and **uses it a few times** during the 14 days.
4. After 14 days: Dashboard → **Apply for production** → answer the questions about your test.

(Organization accounts and personal accounts created before 13 Nov 2023 can skip this and go straight to B5.)

### B5. Go live
**Production → Create release** → add the same .aab (or a newer one) → **Review release → Start rollout to Production**. Google's review usually takes from a few days up to about a week. When approved, students can search **"SK WALA"** on the Play Store and install it.

### B6. Updating the app later
Website changes (tests, messages, payments, design) need **no app update**.
Only if you change the app itself: raise `versionCode` (1 → 2) and `versionName` (1.0 → 1.1) in `app/build.gradle`, build a new .aab with the **same key**, Play Console → Production → Create release.

---

## Common problems
| Problem | Fix |
|---|---|
| Gradle sync fails | File → Settings → Build Tools → Gradle → Gradle JDK: choose the bundled JDK (17 or 21). Then File → Sync. |
| "SDK 36 not found" | Tools → SDK Manager → tick Android 16 (API 36) → Apply |
| App opens a Google "page not found" | `site_url` is wrong – copy the Web app URL again (A0) |
| No notifications | Phone Settings → Apps → SK WALA → Notifications ON; battery saver may delay them |
| Rejected for "limited functionality" | Reply in Play Console explaining the app's features (notifications, secure exam mode, file viewer, payments) – see PLAY_STORE_LISTING.md "Note for reviewers" |
