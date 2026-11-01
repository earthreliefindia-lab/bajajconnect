# Bajaj Connect (OpenRide Companion)

<p align="center">
  <strong>A modern, native Android companion application for connected motorcycle riders.</strong>
  <br/>
  Independent native Kotlin & Jetpack Compose implementation built for Android 14/15/16 and Samsung Galaxy One UI.
</p>

<p align="center">
  <a href="https://github.com/earthreliefindia-lab/bajajconnect/releases/latest">
    <img src="https://img.shields.io/badge/⬇%20DOWNLOAD%20LATEST%20APK-v1.0.0--beta.2-00C853?style=for-the-badge&logo=android&logoColor=white" alt="Download Latest APK"/>
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-14%20%7C%2015%20%7C%2016-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android Version"/>
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"/>
  <img src="https://img.shields.io/badge/Target%20Device-Samsung%20Galaxy%20S23%20Ultra-1428A0?style=flat-square&logo=samsung&logoColor=white" alt="Samsung S23 Ultra"/>
  <img src="https://img.shields.io/badge/Build-Gradle%20Kotlin%20DSL-02303A?style=flat-square&logo=gradle&logoColor=white" alt="Gradle"/>
  <img src="https://img.shields.io/badge/Release-GitHub%20Actions%20CI-2088FF?style=flat-square&logo=githubactions&logoColor=white" alt="GitHub Actions"/>
</p>

<p align="center">
  <a href="https://github.com/earthreliefindia-lab/bajajconnect/releases">All Releases</a> •
  <a href="https://github.com/earthreliefindia-lab/bajajconnect/issues">Report an Issue</a> •
  <a href="https://github.com/earthreliefindia-lab/bajajconnect">Source Code</a>
</p>

---

## 💡 Project Ideation & Vision

Traditional motorcycle companion apps often suffer from sluggish hybrid web wrappers, heavy background battery drain, rigid map ecosystems, and poor compatibility with modern Android versions.

**Bajaj Connect (OpenRide Companion)** is an independent, ground-up architectural rebuild engineered with native Kotlin and Jetpack Compose. It aims to deliver:

1. **Native Performance & Efficiency:** 10.7 MB lightweight native APK replacing heavy hybrid engines, delivering 60–120 FPS fluid animations on Samsung Dynamic AMOLED displays.
2. **Open Navigation Experience:** Unshackles riders from proprietary map silos by seamlessly resolving shared WhatsApp locations, Google Maps links (`maps.app.goo.gl`), and raw coordinates, while cleanly integrating Mappls Points of Interest (POI).
3. **Hardware Cluster Connectivity:** Re-engineered Bluetooth Low Energy (BLE) GATT packet framing compatible with motorcycle digital clusters (both segment LCD and color TFT units).
4. **Rider-First Safety:** Automatic call intercept with customizable auto-reply SMS when calls are declined via handlebar controls.
5. **Modern Android 15/16 & One UI Alignment:** Edge-to-edge rendering, per-app language support, compliant foreground service types (`location` + `connectedDevice`) preventing aggressive OS battery killing, and high-contrast OLED dark themes.

---

## 📱 Core Architecture & 4-Tab Navigation

```
com.openride.companion
├── cluster/       # BLE GATT protocol framing (LCD 2763 & TFT 0265 profiles)
├── location/      # Location intents, WhatsApp URL expander & Mappls POI search
├── model/         # Vehicle telemetry, Trip logs, Navigation maneuvers
├── repository/    # State management and vehicle telemetry flows
├── telephony/     # PhoneState intercept & Auto-Reply SMS dispatcher
├── ui/            # Jetpack Compose Material 3 screens & OLED theme
└── updater/       # In-App GitHub Releases OTA update checker
```

### 1. Dashboard Tab (`DashboardTab.kt`)
* **Hero Vehicle Status:** Real-time Bluetooth connection state, bike model visualization, and battery voltage indicator.
* **Live Telemetry Gauges:** Fuel percentage, Distance to Empty (DTE), Trip A / Trip B odometers, and scheduled maintenance countdown.
* **Cluster Hardware Profile Switcher:** Toggle between **Cluster 2763** (Monochrome LCD) and **Cluster 0265** (Full Color TFT) packet framing.

### 2. Navigation & Smart Maps Tab (`NavigationTab.kt`)
* **Local Search Engine:** Instant address lookup, shop/restaurant search, and reverse geocoding.
* **Mappls POI Quick Chips:** One-tap category search for motorcycle-critical destinations:
  * ⛽ **Fuel Stations** (`TRNPMP`)
  * ⚡ **EV Charging Hubs** (`TRNECS`)
  * 🔵 **CNG Stations** (`TRNCNG`)
  * 🍴 **Food & Dining** (`FODIND`)
  * 🏥 **Hospitals & Emergency** (`HLTHSP`)
  * 🏧 **ATMs & Banking** (`FINATM`)
  * 🅿️ **Parking Locations** (`TRNPRK`)
* **Turn-by-Turn (TBT) HUD:** Live maneuver icons, distance to next turn, ETA, and target street display.

### 3. Trips & Ride Telemetry Tab (`TripsTab.kt`)
* **Riding History:** Chronological ride records with total distance, travel time, and route metadata.
* **Advanced Telemetry Metrics:** Average speed, top speed, left/right maximum lean angles, and calculated fuel efficiency (km/L).

### 4. Vehicle & Rider Settings Tab (`VehicleSettingsTab.kt`)
* **BLE Cluster Sync:** Scan for motorcycle clusters, initiate pairing, and toggle live telemetry stream.
* **Auto-Reply SMS Engine:** Customize automated response text sent when an incoming call is rejected from handlebar controls while riding:
  > *"I am currently riding my motorcycle. Will call you back once I arrive safely."*
* **In-App GitHub OTA Updater:** Check for the latest release on GitHub, view release notes, and install updates in-place.

---

## 🗺️ Smart Location & WhatsApp Link Integration

When someone shares a location via WhatsApp or a Google Maps link, the app interprets it directly without requiring manual coordinate copying:

* **Shortened Link Resolution:** Automatically resolves shortened `maps.app.goo.gl` redirects via an asynchronous HTTP client to extract precise destination latitude/longitude.
* **Android System App Chooser:** Registered in `AndroidManifest.xml` as a standard map/navigation handler:
  * Coordinates: `geo:lat,lng` and `geo:0,0?q=...`
  * Navigation intents: `google.navigation:q=lat,lng`
  * Web links: `https://maps.google.com/...` and `https://goo.gl/maps/...`
* **Friendly Coexistence:** Appears as an option alongside Google Maps in Android's "Open with" sheet without claiming or pretending to be Google Maps.

---

## 🏍️ Motorcycle Digital Cluster Protocol (BLE GATT)

The app communicates with motorcycle digital instrument clusters over custom GATT characteristics:

* **GATT Service:** `0000FFF0-0000-1000-8000-00805F9B34FB`
* **GATT Characteristic:** `0000FFF1-0000-1000-8000-00805F9B34FB`

### Packet Framing:
| OpCode | Purpose | Payload Structure |
|---|---|---|
| `0x01` | Turn-by-Turn Navigation | `[0x01, ManeuverCode, DistHigh, DistLow, EtaHigh, EtaLow, ...StreetNameBytes, 0x00]` |
| `0x02` | Incoming Call Notification | `[0x02, ...CallerNameBytes, 0x00, ...PhoneNumberBytes, 0x00]` |
| `0x03` | Vehicle Telemetry Sync | `[0x03, SpeedKph, RpmHigh, RpmLow, FuelPct, Gear, 0x00]` |

---

## 📥 Download & Installation

### Option 1: Direct Download (Latest Release)
Download the verified APK directly from the repository releases:
👉 [**Download `openride-v1.0.0-beta.2.apk`**](https://github.com/earthreliefindia-lab/bajajconnect/releases/latest)

1. Open the link above and download the APK asset.
2. Tap the APK file on your Samsung Galaxy device.
3. If prompted by Samsung One UI, select **Allow from this source**.
4. Tap **Install** (or **Update**).

### Option 2: Command Line (GitHub CLI)
```powershell
# Authenticate GitHub CLI (first-time setup)
gh auth login

# Download the latest release APK
gh release download v1.0.0-beta.2 --repo earthreliefindia-lab/bajajconnect --pattern "*.apk" --dir ".\release-download"
```

---

## 🛠️ Building From Source

### Prerequisites
* Android Studio Ladybug (2024.2+) or Hedgehog+
* JDK 17 or JDK 21
* Android SDK 34 / 35 installed
* Git

### Build Instructions
```bash
# Clone the repository
git clone https://github.com/earthreliefindia-lab/bajajconnect.git
cd bajajconnect/open-ride-companion

# Build release APK
./gradlew assembleRelease
```
The compiled APK will be generated at:
`open-ride-companion/app/build/outputs/apk/release/app-release.apk`

---

## 🔄 Automated CI/CD Workflow

Every version tag (`v*.*.*`) pushed to the repository automatically triggers the [GitHub Actions Release Workflow](.github/workflows/release.yml):
1. Sets up Ubuntu runner with JDK 17 and Android SDK.
2. Configures Gradle build cache.
3. Compiles `assembleRelease`.
4. Signs the generated APK.
5. Publishes a new GitHub Release with changelog notes and the downloadable APK asset.

---

## ⚠️ Disclaimer

**Bajaj Connect (OpenRide Companion)** is an independent, community-driven experimental project developed for research, improved usability, and personal hardware interoperability. It is **not** an official application of, nor is it affiliated with, sponsored by, or endorsed by Bajaj Auto Ltd. or MapmyIndia/Mappls. All trademarks, vehicle names, and brand assets belong to their respective owners.

---

<p align="center">
  <strong>Built with ❤️ for passionate motorcycle riders.</strong>
</p>
