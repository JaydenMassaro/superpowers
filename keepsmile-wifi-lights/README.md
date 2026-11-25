# Keepsmile Wi-Fi Lights (Android)

Turns your Keepsmile (Bluetooth LE) lights on automatically when your phone connects to a chosen Wi-Fi network.

Keepsmile lights are controlled over Bluetooth, not Wi-Fi, so this app watches for the Wi-Fi connection
and then talks to the light over Bluetooth LE. It has no third-party dependencies and no network access.

## Install
Download the `app-debug.apk` from the latest **Build Keepsmile Wi-Fi Lights APK** run under the repo's
Actions tab (or open `keepsmile-wifi-lights/` in Android Studio and press Run). Allow "install unknown apps" when asked.

## Set up (once)
1. Connect to the Wi-Fi you want to use, open the app, tap **Use the Wi-Fi I'm connected to now**.
2. Close the official Keepsmile app (it can hold the light's single Bluetooth connection), power on the light,
   tap **Find light**, pick it.
3. Tap **Test ON** / **Test OFF**. If the light reacts, you're done. If not, see "If the test does nothing".
4. Flip **Turn lights on automatically**. Grant Location ("Allow all the time"), Bluetooth and notifications.
   Set the app's battery setting to **Unrestricted** so Android doesn't kill the watcher.

Android treats the Wi-Fi name as location data, so Location must be enabled on the phone for it to be readable.

## If the test does nothing
Different Keepsmile models use different command bytes. The defaults are the common ELK-BLEDOM frames:

    ON : 7E 00 04 F0 00 01 FF 00 EF
    OFF: 7E 00 04 00 00 00 FF 00 EF

To find yours: enable **Developer options -> Enable Bluetooth HCI snoop log**, toggle the light in the Keepsmile app,
pull the log (`adb bugreport`), open it in Wireshark and look for ATT "Write Command/Request" packets sent to the
light. Paste those bytes into the app's ON/OFF fields.

## Not verified on hardware
This was written without access to a Keepsmile light or a phone. CI checks that it compiles; the Bluetooth
command bytes are the thing most likely to need adjusting.
