# WebDash — Android

> WebView wrapper for the WebDash dashboard launcher. Builds a signed AAB for Play Store and a signed APK for sideloading, fully automated via GitHub Actions.

---

## CI/CD Overview

| Trigger | What runs |
|---|---|
| Push to `main` / `master` | Debug APK only (fast feedback) |
| `workflow_dispatch` (manual) | Signed AAB + signed APK |
| `git tag v*` | Signed AAB + APK → GitHub Release + Play Store upload |

---

## One-time Setup

### Step 1 — Generate a keystore (do this once, store it safely)

```bash
keytool -genkey -v \
  -keystore webdash-release.jks \
  -alias webdash \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

You will be prompted for a store password, key alias, and key password. Save all three.

> Back up webdash-release.jks securely. If you lose it you cannot update your Play Store app. Never commit it to git.

### Step 2 — Encode keystore as base64

```bash
base64 -i webdash-release.jks | tr -d '\n'
```

Copy the output — this is your KEYSTORE_BASE64 secret.

### Step 3 — Add GitHub Secrets

Go to your repo → Settings → Secrets and variables → Actions → New repository secret

| Secret name | Value |
|---|---|
| KEYSTORE_BASE64 | Base64-encoded keystore (Step 2) |
| STORE_PASSWORD | Keystore store password |
| KEY_ALIAS | Key alias (e.g. webdash) |
| KEY_PASSWORD | Key password |
| PLAY_SERVICE_ACCOUNT_JSON | Google Play service account JSON (Step 4) |

### Step 4 — Google Play service account (for auto-upload)

1. Go to Google Play Console → Setup → API access
2. Link to a Google Cloud project
3. Click Create new service account → go to Google Cloud Console
4. IAM & Admin → Service Accounts → Create, role: Service Account User, download JSON key
5. Back in Play Console → grant the service account Release manager permissions
6. Paste the full JSON key contents as the PLAY_SERVICE_ACCOUNT_JSON secret

### Step 5 — Create your Play Store app listing

Before the first upload, manually create the app in Play Console:

1. All apps → Create app, package name: com.webdash.app
2. Complete store listing (title, description, screenshots, content rating)
3. The workflow uploads to the internal test track — promote to production manually

---

## Releasing a new version

1. Bump versionCode and versionName in app/build.gradle
2. Update distribution/whatsnew/whatsnew-en-US with release notes (max 500 chars)
3. Tag and push:

```bash
git add .
git commit -m "Release v1.1.0"
git tag v1.1.0
git push origin main --tags
```

GitHub Actions will build, sign, create a GitHub Release, and upload to Play Store automatically.

---

## Local development

No env vars needed for local debug builds:

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

For a local signed release build:

```bash
export SIGNING_STORE_FILE=/path/to/webdash-release.jks
export SIGNING_STORE_PASSWORD=yourpassword
export SIGNING_KEY_ALIAS=webdash
export SIGNING_KEY_PASSWORD=yourkeypassword
./gradlew bundleRelease assembleRelease
```
