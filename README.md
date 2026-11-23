# ⚡ VoltWidget

**A lightweight Android widget for monitoring electricity outage schedules in Ukraine.**

VoltWidget brings electricity outage information directly to your Android home screen, helping you keep track of scheduled and upcoming power outages without opening additional applications.

> Built to make electricity schedules easier to access, understand, and monitor.

---

## ✨ Features

- **Home Screen Widget** — access outage information directly from your home screen.
- **Automatic Updates** — background data refresh using Android WorkManager.
- **Offline Data Access** — locally stored information using Room Database.
- **Network Integration** — HTTP requests and HTML parsing for retrieving schedule data.
- **Native Android Experience** — built with Kotlin and Jetpack technologies.

## 🛠 Tech Stack

| Technology | Purpose |
|---|---|
| Kotlin | Main programming language |
| Jetpack Compose | Application UI |
| Jetpack Glance | Android home screen widget |
| Room | Local database |
| WorkManager | Background updates |
| OkHttp | HTTP networking |
| Jsoup | HTML parsing |
| Gradle | Build automation |

## 🏗 Project Structure

```text
Volt/
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           └── res/
├── gradle/
│   └── wrapper/
├── .github/
│   └── workflows/
│       └── build-apk.yml
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

## 📦 Installation

1. Download the latest available APK from GitHub Actions Artifacts.
2. Open the downloaded APK on your Android device.
3. Allow installation from the selected source if Android requests permission.
4. Complete the installation and add the widget to your home screen.

## 🔨 Build from Source

Requirements:
- JDK 17
- Android SDK Platform 37
- Gradle Wrapper (included)

Build a debug APK:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew assembleDebug
```

The generated APK will be available at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions

The repository includes a GitHub Actions workflow for automated APK builds.

To trigger a build manually:

1. Open the repository on GitHub.
2. Navigate to **Actions**.
3. Select **Build Android APK**.
4. Click **Run workflow**.
5. Download the generated APK from the workflow artifacts.

## 🗺 Roadmap

- [ ] Improve widget customization.
- [ ] Expand outage schedule data sources.
- [ ] Improve offline synchronization.
- [ ] Refine the application interface.

## 📄 License

License information has not yet been specified.