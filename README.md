# Bajaj Ride Connect

<p align="center">
  <strong>An independent, native Android companion application faithfully recreating and enhancing the official Bajaj Ride Connect experience.</strong>
  <br/>
  Engineered from the ground up with native Kotlin and Jetpack Compose for Android 14/15/16 and Samsung Galaxy One UI.
</p>

<p align="center">
  <a href="https://github.com/earthreliefindia-lab/bajajconnect/releases/latest">
    <img src="https://img.shields.io/badge/⬇%20DOWNLOAD%20LATEST%20APK-v1.0.0--beta.4-0066FF?style=for-the-badge&logo=android&logoColor=white" alt="Download Latest APK"/>
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-14%20%7C%2015%20%7C%2016-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android Version"/>
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"/>
  <img src="https://img.shields.io/badge/Design%20System-Bajaj%20Royal%20Blue-0066FF?style=flat-square&logo=motorcycle&logoColor=white" alt="Bajaj Blue"/>
  <img src="https://img.shields.io/badge/Target%20Device-Samsung%20Galaxy%20S23%20Ultra-1428A0?style=flat-square&logo=samsung&logoColor=white" alt="Samsung S23 Ultra"/>
  <img src="https://img.shields.io/badge/Build-Gradle%20Kotlin%20DSL-02303A?style=flat-square&logo=gradle&logoColor=white" alt="Gradle"/>
</p>

<p align="center">
  <a href="https://github.com/earthreliefindia-lab/bajajconnect/releases">All Releases</a> •
  <a href="https://github.com/earthreliefindia-lab/bajajconnect/issues">Report an Issue</a> •
  <a href="https://github.com/earthreliefindia-lab/bajajconnect">Source Code</a>
</p>

---

## 🏍️ Project Vision & APK Analysis

Through in-depth static reverse-engineering and inspection of the original **`Bajaj Ride Connect.apk`** (utilizing AAPT, Hermes bytecode string extraction, and resource mapping), this project completely reconstructs the application into clean, native Android code while retaining its visual identity, screen flows, and motorcycle features:

1. **Authentic Bajaj Design System:** Recreated with the iconic **Bajaj Royal Blue** (`#0066FF`), deep navy card containers (`#0E1726`), electric cyan accents (`#00E5FF`), and high-contrast typography optimized for Samsung Dynamic AMOLED displays.
2. **Multi-Model Garage:** Supports the full family of connected Bajaj motorcycles:
   * **Pulsar Series:** `Pulsar NS400Z`, `Pulsar N250`, `Pulsar N160`, `Pulsar 150`, `Pulsar RS200`
   * **Dominar Series:** `Dominar 400 Dual Channel ABS`, `Dominar 250`, `Dominar 220`
   * **Chetak Series:** `Chetak EV (Premium)`
3. **Dynamic ABS & Engine Ride Modes:**
   * 🛣️ **Road Mode:** Balanced for city and highway roads, offering ideal everyday comfort and traction.
   * 🌧️ **Rain Mode:** Smoother ABS intervention with enhanced traction control and stability on wet surfaces.
   * 🏜️ **Off-Road Mode:** Minimum stopping distance with adequate tire traction on unpaved gravel/dirt roads.
   * ⚡ **Sport Mode:** Reduced ABS intervention for aggressive braking and maximum throttle responsiveness.
4. **Interactive In-App Map & Routing:** Embedded dark-theme map with real OSRM road routing, pulsing rider marker, destination pins, and high-visibility neon route line.
5. **Accurate GPS Telemetry:** Genuine speed tracking that reads `0 km/h` when standing still (no mock speeds).
6. **Smart Open Navigation & WhatsApp Links:** Instant resolution of shortened Google Maps (`maps.app.goo.gl`) links, raw coordinates, and integration into Android's "Open with" app chooser.

---

## 📱 5-Tab Application Architecture

```
com.openride.companion
├── cluster/       # BLE GATT protocol framing (LCD Type 2763 & TFT Type 0265)
├── location/      # LocationIntentParser, LocalSearchManager & RouteNavigationManager
├── model/         # VehicleInfo, RideMode, ServiceCenter, VehicleDocument, LiveNavInstruction
├── repository/    # State management for vehicle status, trips, documents, and service centers
├── service/       # ClusterRelayService (Android 14/15/16 compliant foreground service)
├── telephony/     # CallSmsManager (TelephonyCallback & Auto-Reply SMS engine)
├── ui/            # Jetpack Compose 5-tab navigation, Bajaj Theme & MapView
└── updater/       # In-App GitHub Releases OTA update checker
```

### 1. Home / Dashboard (`DashboardTab.kt`)
* **Top Header:** Bajaj Ride Connect brand mark, quick bike switcher dropdown, and Bluetooth cluster connection pill.
* **Garage Switcher:** One-tap switcher between multiple motorcycles in your garage.
* **Hero Telemetry Card:** Battery voltage (`12.8V`), Fuel percentage (`72%`), Distance to Empty (`~285 km`), Total Odometer (`4,120 km`).
* **Ride Mode Selector:** Live switcher between Road, Rain, Off-Road, and Sport modes.
* **Quick Action Grid:**
  * 🛠️ **Book Service:** Scheduled maintenance appointment booking.
  * 📁 **My Documents:** Digital locker with RC, Insurance, PUC, and Driving License.
  * 📍 **Locate Bike:** Displays last parked GPS location coordinates.
  * 🆘 **Roadside Assistance (RSA):** Direct toll-free emergency call to Bajaj 24x7 RSA (`1800 233 2453`).
* **Periodic Maintenance Card:** Days and kilometers remaining before next scheduled service.
* **Cluster Hardware Profile Switcher:** Toggle between LCD (`Cluster2763`) and TFT (`Cluster0265`).

### 2. Navigation (`NavigationTab.kt` & `NavigationMapComponent.kt`)
* **Interactive Map:** Dark-mode CartoDB / OSM map canvas with pinch-to-zoom and floating "My Location" recenter FAB.
* **Dual Search Engine:** Android Geocoder with automatic fallback to OpenStreetMap Nominatim for Indian place and landmark searches.
* **Mappls POI Chips:** Fuel (`TRNPMP`), EV Charging (`TRNECS`), Bike Repair, Food (`FODIND`), Hospitals (`HLTHSP`), ATMs (`FINATM`), Parking (`TRNPRK`).
* **WhatsApp Location Resolver:** Asynchronously unshortens `maps.app.goo.gl` redirects into exact coordinates.
* **Live Turn-by-Turn HUD:** Maneuver icon, next road name, distance to turn, remaining distance, ETA, and real-time GPS speed (`0 km/h` when still).
* **Cluster GATT Relay:** Streams opcode `0x01` maneuver frames to the motorcycle console over BLE.
* **Google Maps Handoff:** One-tap button to launch voice turn-by-turn guidance in Google Maps.

### 3. Trips (`TripsTab.kt`)
* **Riding History:** Past rides log with start/end addresses, distance, and duration.
* **Telemetry Metrics:** Average speed, top speed, maximum left/right lean angles (degrees), and fuel economy (km/L).

### 4. Service & Maintenance (`ServiceTab.kt`)
* **Periodic Inspection Tracker:** Details on upcoming Scheduled Service #3 (oil change, throttle sync, valve clearance).
* **Factory Specs Guide:** Engine oil grade (`20W50`), chain slack (`25-30 mm`), tire pressures (`Front: 28 / Rear: 32 PSI`).
* **Authorized Workshops:** Nearest authorized Bajaj care centers with distance, address, phone call shortcut, and instant slot booking.

### 5. Profile & Settings (`VehicleSettingsTab.kt`)
* **Rider Profile:** Verified membership card with emergency ICE contact.
* **Digital Vehicle Locker:** Verified documents view for Registration Certificate, Comprehensive Insurance, PUC, and Driving License.
* **BLE Cluster Sync:** Scan, pair, and live status monitor.
* **Auto-Reply SMS:** Custom message sent to callers when declined via handlebar controls.
* **In-App Updater:** Automated GitHub Releases version checker and seamless APK installer.

---

## 📥 Download & Installation

Download the latest verified release APK directly:
👉 [**Download `BajajRideConnect-v1.0.0.apk`**](https://github.com/earthreliefindia-lab/bajajconnect/releases/latest)

```powershell
# Or download via GitHub CLI:
gh release download v1.0.0-beta.4 --repo earthreliefindia-lab/bajajconnect --pattern "*.apk" --dir ".\release-download"
```

1. Transfer the `.apk` file to your device (e.g. via Quick Share or WhatsApp).
2. Tap the APK file to install or update without losing settings.

---

## 🛠️ Build From Source

```bash
cd open-ride-companion
./gradlew assembleRelease
```
The compiled APK will be created at:
`open-ride-companion/app/build/outputs/apk/release/app-release.apk`

---

## ⚠️ Disclaimer

**Bajaj Ride Connect** is an independent companion project built for personal interoperability, research, and enhanced user experience. It is not an official release by Bajaj Auto Ltd. or MapmyIndia/Mappls. All trademarks and brand names belong to their respective owners.
