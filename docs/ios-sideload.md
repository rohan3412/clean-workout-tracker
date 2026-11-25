# Installing Clean on an iPhone (sideloading)

Clean is not on the App Store. The iPhone build is an unsigned `.ipa`, made on GitHub's macOS runner, that you sign with your
own Apple ID while installing it. A free Apple ID is enough.

## Get the .ipa

Download `Clean-unsigned.ipa` from the release on GitHub's **Releases** page, next to the APKs. Or, for the newest build: open
**Actions**, then the latest **Build APKs** run on `main`, and download the **clean-ios** artifact. Unzip it to get
`Clean-unsigned.ipa`.

## Install it from Windows with Sideloadly

1. Install **iTunes** from apple.com/itunes (the download from Apple's site, not the Microsoft Store one) and **Sideloadly**
   from sideloadly.io.
2. Plug the iPhone in with a cable, unlock it and tap **Trust** on the phone.
3. Open Sideloadly, drop `Clean-unsigned.ipa` onto it, type the Apple ID of the phone's owner and press **Start**. Sign in when
   asked (Sideloadly sends the sign-in to Apple only, to make the signing certificate).
4. On the iPhone, open **Settings > General > VPN & Device Management**, tap the Apple ID under Developer App and tap **Trust**.
5. iOS 16 and later: **Settings > Privacy & Security > Developer Mode**, switch it on and restart when asked.

A Mac can do the same with Sideloadly for macOS or AltStore.

## Every 7 days

An app signed with a free Apple ID stops opening after 7 days. Plug the phone in and run the same Sideloadly install again
(the same `.ipa` or a newer one). The app's data is kept, because it is the same app being re-signed. Sideloadly's
"Automatically refresh" option can do this over Wi-Fi while the PC is on. A free Apple ID can have three sideloaded apps at
a time. A paid Apple Developer account makes the signature last a year.

## Moving data between phones

On the old phone, go to **Settings > Backup > Back up everything**. Send the file to the iPhone (AirDrop, a cable, Files). On the
iPhone, go to **Settings > Backup > Restore a backup** and pick it. Android and iPhone backups are the same format, both ways. A Strong
CSV export imports the same as on Android.

## What the iPhone version does not have

- **Health:** HealthKit cannot be signed with a free Apple ID, so the Health section is hidden.
- **Home-screen widgets:** these need an App Group, which a free Apple ID cannot sign. Long-pressing the app icon gives
  quick actions instead (start a workout, resume one, log bodyweight).
- **Live rest countdown on the lock screen:** iOS has no ongoing notifications. The rest end still rings as a notification.

The app is offline on iPhone as on Android: it never goes online.
