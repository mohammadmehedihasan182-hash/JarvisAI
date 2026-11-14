# JARVIS AI — Android App (সম্পূর্ণ কোড)

ফিউচারিস্টিক ভয়েস অ্যাসিস্ট্যান্ট। বাংলা ও English দুই ভাষাতেই কথা বলে ও শোনে।

## কিভাবে APK বানাবেন (Android Studio দিয়ে)

1. এই ফোল্ডারটা unzip করুন।
2. **Android Studio** খুলুন → **Open** → এই `JARVIS-AI` ফোল্ডারটা সিলেক্ট করুন।
3. Gradle sync হতে দিন (ইন্টারনেট লাগবে, প্রথমবার একটু সময় লাগতে পারে)।
   - `gradle-wrapper.jar` না থাকলে Android Studio নিজে বানিয়ে নেয়; না হলে মেনু থেকে
     **File → Sync Project with Gradle Files** চাপুন।
4. নিজের ফোনে USB debugging চালু করে ফোন কানেক্ট করুন, অথবা
   **Build → Build App Bundle(s) / APK(s) → Build APK(s)** চাপুন।
5. তৈরি APK পাবেন: `app/build/outputs/apk/debug/app-debug.apk` — ফোনে কপি করে ইনস্টল করুন।

## প্রথমবার চালালে

- সব permission চাইবে — ভয়েস, কল, SMS, কন্টাক্ট, ক্যালেন্ডার, নোটিফিকেশনের অনুমতি দিন।
- **সেটিংস** ট্যাবে নিজের নাম ও ভাষা সেভ করুন — জার্ভিস মনে রাখবে।
- ওয়েক ওয়ার্ড চালু করলে: ফোনের **Settings → Apps → JARVIS AI → Battery → Unrestricted** করে দিন,
  নাহলে কিছু ফোন ব্যাকগ্রাউন্ডে অ্যাপটা বন্ধ করে দিতে পারে। ওয়েক ওয়ার্ডে ব্যাটারি একটু বেশি খরচ হয় —
  স্ক্রিনে স্থায়ী নোটিফিকেশন দেখা যাবে যখন সে শুনছে।

## যা যা আছে (ফিচার ম্যাপ)

| ফিচার | কোথায় |
|---|---|
| গাঢ় থিম, জ্বলজ্বলে রিং, অবতার, কথার বাবল, মাইক, বটম নেভ | `ui/GlowingRingView.kt`, `res/layout/fragment_home.xml`, `MainActivity.kt` |
| বাংলা+English ভয়েস (শোনা ও বলা) | `voice/SpeechManager.kt` |
| কথোপকথন মোড (চুপ থাকলে ঘুমিয়ে যায়) | `ui/HomeFragment.kt` |
| "জার্ভিস" ওয়েক ওয়ার্ড + স্থায়ী নোটিফিকেশন | `voice/WakeWordService.kt` |
| কমান্ড বোঝা (বাংলা+English) | `core/AssistantBrain.kt` |
| অ্যালার্ম ও রিমাইন্ডার | `core/ReminderScheduler.kt`, `core/AlarmReceiver.kt` |
| কল/SMS — সবসময় আগে "হ্যাঁ" জিজ্ঞেস করে | `core/AssistantBrain.kt` (confirmation), `core/CallSmsHelper.kt` |
| অ্যাপ খোলা (YouTube/WhatsApp/Chrome...) | `core/CallSmsHelper.openApp()` |
| ব্যাটারি, সময়, স্টোরেজ | `core/PhoneInfo.kt` |
| শিডিউল, ক্যালেন্ডার পড়া, সকালের সারাংশ, ১৫ মিনিট আগের রিমাইন্ডার, আগামীকালের খসড়া | `ui/ScheduleFragment.kt`, `core/ScheduleStore.kt` |
| কল এলে নাম বলা, মিসড কল মনে করানো | `call/CallReceiver.kt` |
| পছন্দ মনে রাখা (নাম/ভাষা/ভয়েস) | `core/Prefs.kt`, `ui/SettingsFragment.kt` |
| AI চ্যাট (key সার্ভারে, ফোনে নয়) | `ai/AiChatClient.kt` — `app/build.gradle.kts`-তে `AI_SERVER_URL` নিজের সার্ভারের ঠিকানা বসান |

## সীমাবদ্ধতা (সত্যি কথা)

- ফ্রি AI চ্যাটের জন্য নিজের একটা ছোট সার্ভার লাগবে যেখানে API key থাকবে; সার্ভার ছাড়া
  বিল্ট-ইন কমান্ডগুলো অফলাইনেই কাজ করে, অজানা কথায় জার্ভিস সেটা জানিয়ে দেবে।
- ইনকামিং কলে জার্ভিস নাম **বলে** দেবে এবং জিজ্ঞেস করবে "ধরবেন?", কিন্তু Android সাধারণ
  অ্যাপকে ভয়েস দিয়ে কল ধরতে/কাটতে দেয় না — ফোনের নিজের বাটনে চাপ দিতে হবে।
- কলারের সাথে AI-এর কথা বলা ও অন্য অ্যাপ নিজে চালানো — এই সংস্করণে নেই (পরে যোগ করা যাবে)।
