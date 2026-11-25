# Keepsmile Lights for iPhone

iOS can't watch Wi-Fi in the background from a custom app, but the **Shortcuts** app can. So:

- This app talks to your Keepsmile light over Bluetooth LE and exposes **Turn On / Turn Off Keepsmile Lights** actions to Shortcuts.
- A Shortcuts **Wi-Fi automation** runs the "Turn On" action whenever you connect to your network.

## Build and install (needs a Mac with Xcode)
```sh
brew install xcodegen
cd keepsmile-wifi-lights-ios
# edit PRODUCT_BUNDLE_IDENTIFIER in project.yml to something unique to you
xcodegen            # creates KeepsmileLights.xcodeproj
open KeepsmileLights.xcodeproj
```
In Xcode: select the KeepsmileLights target → Signing & Capabilities → choose your Team (a free Apple ID works),
plug in your iPhone, pick it as the run destination, press Run. On the phone, trust the developer under
Settings → General → VPN & Device Management. A free Apple ID install expires after 7 days (re-run from Xcode).

## Set up
1. Open the app, close the official Keepsmile app, power on the light, tap **Find light**, pick it.
2. Tap **Test ON** / **Test OFF**. If the light reacts, continue. If not, see below.
3. Shortcuts → Automation → **+** → **Wi-Fi** → choose your network, "Is Connected" → Next →
   Add Action → search "Turn On Keepsmile Lights" → Next → select **Run Immediately**, turn off Notify When Run → Done.

## If the test does nothing
Models differ in command bytes. Defaults are the common ELK-BLEDOM frames:

    ON : 7E 00 04 F0 00 01 FF 00 EF
    OFF: 7E 00 04 00 00 00 FF 00 EF

To find yours, install Apple's *Bluetooth logging profile* (developer.apple.com/bluetooth), toggle the light in the
Keepsmile app, take a sysdiagnose, and read the ATT writes in PacketLogger (Xcode "Additional Tools").
Paste those bytes into the app's fields.

## Not verified
Written without a Mac, iPhone or a Keepsmile light; it has never been compiled or run. Expect to fix small
compile errors and possibly the command bytes.
