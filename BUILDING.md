# Building locally on WSL2

One-time setup of a build environment inside a WSL2 distro (Ubuntu/Debian
assumed), then the actual build is a single Gradle command.

## 1. Install a JDK and unzip

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk unzip
```

## 2. Install the Android SDK command-line tools

```bash
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip -d cmdline-tools
mv cmdline-tools/cmdline-tools cmdline-tools/latest
rm commandlinetools-linux-*.zip
```

Add the environment variables to your shell profile:

```bash
cat >> ~/.bashrc <<'EOF'
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
EOF
source ~/.bashrc
```

## 3. Install the SDK packages this project needs

```bash
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

## 4. Clone and build

```bash
git clone https://github.com/DaanyaalSobani/truescreentime.git
cd truescreentime
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Keep the checkout on the Linux filesystem (e.g. `~/truescreentime`), not
under `/mnt/c/...` — Gradle builds are several times slower on the Windows
mount, and file-watching misbehaves there.

## 5. Installing on your phone

USB devices aren't visible inside WSL2 by default, so pick one of:

- **Wireless debugging (easiest):** on the phone enable Developer options →
  Wireless debugging, then inside WSL2:

  ```bash
  sudo apt install -y adb
  adb pair <phone-ip>:<pairing-port>   # code shown on the phone
  adb connect <phone-ip>:<port>
  adb install app/build/outputs/apk/debug/app-debug.apk
  ```

- **Windows-side adb:** install platform-tools on Windows and point it at
  the APK through the WSL share:

  ```powershell
  adb install \\wsl$\Ubuntu\home\<you>\truescreentime\app\build\outputs\apk\debug\app-debug.apk
  ```

- **USB passthrough:** `usbipd-win` can attach the phone's USB port to
  WSL2 if you prefer cabled `adb` from Linux.

## Troubleshooting

- `SDK location not found` — make sure `ANDROID_HOME` is exported in the
  shell that runs Gradle, or create `local.properties` in the repo root
  with `sdk.dir=/home/<you>/android-sdk`.
- License errors during the first build — re-run `yes | sdkmanager --licenses`.
- Out-of-memory in Gradle — WSL2 defaults to half your RAM; raise it in
  `%UserProfile%\.wslconfig` (`[wsl2]` → `memory=8GB`) and `wsl --shutdown`.
