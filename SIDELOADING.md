# Install the Light Tickets beta

For **Light Phone III**. Version 0.4.3 was built and signed by Light, but **Tool Library approval is still pending as of November 14, 2026**. This is a community beta. You do not need to compile the source code.

Installation needs a Mac, Windows PC, or Linux computer and a USB data cable. After installation, the app works without the cable. Sending tickets wirelessly needs both devices on the same trusted private Wi-Fi.

## 1. Download two things

- From the [0.4.3 release](https://github.com/izzystst/light-tickets/releases/tag/v0.4.3), open **Assets** and download **light-tickets-0.4.3.apk**. The automatic “Source code” downloads are for developers.
- Download [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) for your computer and unzip it. You do not need Android Studio.

Move the APK into the unzipped `platform-tools` folder so the commands below can find it.

## 2. Prepare your phone

1. Enable Developer Mode for your phone in the [Light dashboard](https://dashboard.thelightphone.com/) under your phone's settings.
2. On the phone, open **Settings → Debug → Android Dev Mode** and enable USB debugging in Android's developer settings. Menu names may vary with your LightOS version.
3. Connect the unlocked phone to your computer. If asked **Allow USB debugging?**, approve your own computer.
4. If LightOS offers a choice of permitted tools, choose **SDK-built tools** to allow this Light-signed beta. Light describes this setting in its [tool-sharing guide](https://github.com/lightphone/light-sdk#sharing-your-tool). This is broader than approved tools only; you can change it back later, but this unapproved beta may then be hidden.

Rooting, unlocking the bootloader, and resetting the phone are not required.

## 3. Install

Open Terminal on a Mac/Linux computer or PowerShell on Windows **in the platform-tools folder**. On Windows, you can open the folder, type `powershell` in File Explorer's address bar, and press Enter. On a Mac, Terminal's `cd ` command followed by dragging the folder into the window changes to that folder.

**Mac or Linux:**

```sh
./adb devices
./adb install -r light-tickets-0.4.3.apk
```

**Windows PowerShell:**

```powershell
.\adb.exe devices
.\adb.exe install -r light-tickets-0.4.3.apk
```

The first command should list your phone with the word `device`. If it says `unauthorized`, unlock the phone and approve its debugging prompt, then run it again. Do not run the install command until the phone is listed as `device`. Installation should finish with **Success**.

## 4. Open and try it

Open the LightOS toolbox and look through its pages for **Light Tickets**. Open **Open practice ticket**, then **Full screen**. Try the practice code before relying on a real ticket. You can unplug the cable and turn USB debugging off after installation.

To add a file, choose **Receive wirelessly → Start receiving**. Scan the QR with an iPhone/Android phone, or type the displayed address into a laptop browser. Choose a PDF, PNG, JPEG, or WebP file; send it; then tap **Save** on the Light Phone. Saved copies open offline.

## If something goes wrong

- **No device listed:** try a data-capable cable, another USB port, and an unlocked phone with USB debugging enabled. Follow Android's [ADB setup guide](https://developer.android.com/tools/adb) if the computer needs additional setup or Windows drivers.
- **Installed but missing from the toolbox:** check dashboard Developer Mode and the phone's permitted-tool setting. A normal phone restart may refresh the list. Settings vary by LightOS; do not reset the phone to fix a missing entry.
- **Signature or update conflict:** stop and report the exact message in [GitHub Issues](https://github.com/izzystst/light-tickets/issues). Do not uninstall an existing copy just to force installation: uninstalling deletes its saved tickets. Older prototypes use different app IDs and do not share their ticket library with this beta.
- **Removing this beta:** use your device's app-uninstall controls, or `./adb uninstall io.github.izzystst.lighttickets` (Windows: `.\adb.exe uninstall io.github.izzystst.lighttickets`). This deletes the beta's saved copies. It does not delete the originals on your sending device.

## Before using a real ticket

Use only ticket PDFs/static images accepted by the issuer. This beta does not support rotating barcodes, provider logins, NFC admission, or payments. Practice QR scanning passed on a real phone; venue admission has not been tested. Keep a backup way to present important tickets while testing.

Wi-Fi transfer uses **unencrypted HTTP**: use trusted private Wi-Fi. Tickets are saved in private app storage, but Android backup exclusion remains unresolved. See [known issues](KNOWN_ISSUES.md) and [validation](VALIDATION.md).

The optional `SHA256SUMS.txt` release asset contains the APK fingerprint. The expected SHA-256 for `light-tickets-0.4.3.apk` is:

```text
110759f7e4cc2930d4e28fb55f19fb926aabba04a94bf44738bca2c077bf9ebd
```
