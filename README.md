# Agpeya NIV — Native Android

A modern native Android reader for the public Agpeya.org Coptic Book of Hours experience, built with Kotlin + Jetpack Compose.

## What it does

- Native Material 3 / Jetpack Compose UI — **no WebView**.
- Home screen for the seven canonical hours plus Veil, Special, Other, About, and Contact.
- English / Arabic / bilingual reading modes.
- Dark theme, adjustable font size, and adjustable line spacing.
- Prayer content is fetched from `https://agpeya.org/` at runtime rather than bundled as a copied text database.
- Scripture headings are converted into native “Open Scripture in NIV” cards.
- Tapping a scripture card launches `https://www.bible.com/bible/111/...NIV` with a normal Android `ACTION_VIEW` intent, allowing the installed Bible.com/YouVersion app or browser to handle it.
- Psalm links account for the Agpeya/Coptic psalm numbering convention described by the site (for example, Agpeya Psalm 50 corresponds to Psalm 51 in common published versions).

## Tech stack

- Kotlin 2.4.10
- Android Gradle Plugin 9.4.1
- Gradle 9.6
- Jetpack Compose BOM 2026.08.00
- AndroidX Activity 1.13.0
- Jsoup 1.23.2 for HTML parsing
- minSdk 26 / targetSdk 37 / compileSdk 37

## Open in Android Studio

Use a current Android Studio release that supports AGP 9.4.x. Sync the project, then run the `app` configuration on an Android device or emulator with network access.

The repository intentionally contains no generated APK or copied Agpeya prayer corpus.

## Important publishing note

This is an unofficial implementation. It fetches Agpeya.org content at runtime and links to Bible.com/NIV rather than redistributing the Bible text. Before distributing the app publicly, confirm that you have the necessary rights/permission to use Agpeya.org's content, branding, and any associated assets.
