# Groww Lock (Android)

1 buy order per day on Groww. After your buy, Groww is blocked until 9:15 AM on the next trading day.

IMPORTANT: this project was written without being compiled, because the build tools could not run
where I made it. If the build fails, copy the error text and send it back and it will be fixed.

## Get the APK (easiest: GitHub builds it for you, free)

1. Create a free account at github.com and make a new repository (private is fine).
2. Upload everything in this folder to it. On the repository page: Add file -> Upload files.
   Drag in all the files and folders, including the hidden `.github` folder. If your computer hides
   it, unzip first and show hidden files, or use GitHub Desktop.
3. Open the Actions tab. The "Build APK" run starts by itself (or press Run workflow).
4. Wait about 3 to 5 minutes. When it is green, open the run and download `groww-lock-apk`
   at the bottom (a zip containing `app-debug.apk`).
5. Send the APK to your phone and open it.

If you use Android Studio instead: open this folder, wait for the sync, then Build -> Build APK(s).

## Install on the phone

1. Allow installing from the source you used (Chrome, Files, WhatsApp, etc.) when Android asks.
2. If Play Protect warns about an unknown app, choose Install anyway. This is your own app.

## Set up the app

1. Open Groww Lock. Enter your Groww API key and secret (Groww app -> Settings -> Trading APIs,
   the TOTP method). Press Save. The secret is stored only on this phone.
2. Tap "Open Accessibility settings", find Groww Lock and turn it on.
   - Android 13 and newer may grey the switch out for apps installed outside the Play Store.
     Fix: Settings -> Apps -> Groww Lock -> three dots (top right) -> Allow restricted settings.
     Then try again.
3. Phone Settings -> Apps -> Groww Lock -> Battery -> Unrestricted, so Android does not stop it.
4. Press "Check my orders now". You should see "Read N orders" and a status list.
5. Press "Preview the Limit reached screen" to see what the block looks like.

## Test it once with a small real buy order

- After the order, wait a few seconds. The top box should say LOCKED until tomorrow 9:15 AM.
- Open Groww. You should be sent to the home screen and see the red Limit reached page.
- If the "LOCKED until" time looks 5 hours 30 minutes wrong, tick the "already IST" box.
- If Groww opens and nothing is blocked, the Groww package name may differ on your phone. Check
  it in Settings -> Apps -> Groww -> App details, and type it into the package field.
- If a rejected or failed order locks you, send me the "Statuses seen" text and I will add it.

## Known gaps

- It sees the order after Groww has it, so a second order placed in the first 3 seconds can get through.
- You can switch the blocker off or uninstall the app. Ask a friend or family member to set the
  phone lock, or keep the Accessibility switch under their control. A Device Admin lock is a possible next step.
- It does not block groww.in in a browser or Groww on a laptop. Use a DNS filter (for example NextDNS) for that.
- Selling is blocked too while you are locked, because the whole Groww app is covered.
- The API secret on the phone could be used to trade. Do not share the phone data or APK settings.
- Market holidays are only skipped if you type them into the holidays box.
