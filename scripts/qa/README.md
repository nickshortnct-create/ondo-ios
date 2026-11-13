# Ondo search and downloads smoke test

These Python scripts use generated artwork and a 12-second sine-wave WAV. The mock
Subsonic library contains two songs, an album, an artist and a playlist. SoulSync
search returns a third song; adding it creates a mock download and makes it
available in the library after five seconds. No music or credentials are fetched
from another service.

Use a disposable, rooted Android emulator with **no existing Ondo installation**.
Do not replace a real account's preferences. The UI driver refuses physical
devices and requires Ondo in the foreground. Python 3 and `adb` are the only
dependencies. Set `ADB`, `ONDO_QA_DEVICE` (default `emulator-5554`), `ONDO_QA_DIR`
(default `/tmp/ondo-qa`), or `ONDO_QA_PORT` (default `8765`) when needed.

1. Check the target emulator and ensure this returns no installed package:

   ```sh
   adb -s emulator-5554 shell pm list packages com.nickshortnct.ondo
   ```

2. Start fixtures in another terminal. Events are logged in the QA directory;
   Subsonic auth query parameters and Authorization headers are omitted.

   ```sh
   python3 scripts/qa/ondo-search-fixtures.py
   ```

3. Install the release APK and connect localhost:

   ```sh
   adb -s emulator-5554 install androidApp/build/outputs/apk/release/Ondo.apk
   adb -s emulator-5554 reverse tcp:8765 tcp:8765
   adb -s emulator-5554 shell pm list packages -U com.nickshortnct.ondo
   ```

   Record the app's reported UID, then use that UID only for the app's firewall
   and file ownership below. Ondo currently has public provider URLs, so this
   rule is necessary even with fake integrations configured:

   ```sh
   ondoQaUid=REPLACE_WITH_REPORTED_UID
   adb -s emulator-5554 shell su 0 iptables -I OUTPUT -m owner --uid-owner "$ondoQaUid" ! -d 127.0.0.1/32 -j REJECT
   python3 scripts/qa/ondo-search-fixtures.py --write-prefs /tmp/ondo-qa/prefs.xml
   adb -s emulator-5554 shell su 0 mkdir -p /data/user/0/com.nickshortnct.ondo/shared_prefs
   adb -s emulator-5554 shell su 0 chown "$ondoQaUid:$ondoQaUid" /data/user/0/com.nickshortnct.ondo/shared_prefs
   adb -s emulator-5554 push /tmp/ondo-qa/prefs.xml /data/local/tmp/ondo-qa-prefs.xml
   adb -s emulator-5554 shell su 0 cp /data/local/tmp/ondo-qa-prefs.xml /data/user/0/com.nickshortnct.ondo/shared_prefs/com.nickshortnct.ondo_preferences.xml
   adb -s emulator-5554 shell su 0 chown "$ondoQaUid:$ondoQaUid" /data/user/0/com.nickshortnct.ondo/shared_prefs/com.nickshortnct.ondo_preferences.xml
   adb -s emulator-5554 shell su 0 chmod 660 /data/user/0/com.nickshortnct.ondo/shared_prefs/com.nickshortnct.ondo_preferences.xml
   adb -s emulator-5554 shell su 0 restorecon /data/user/0/com.nickshortnct.ondo/shared_prefs/com.nickshortnct.ondo_preferences.xml
   adb -s emulator-5554 shell monkey -p com.nickshortnct.ondo -c android.intent.category.LAUNCHER 1
   ```

4. Use `python3 scripts/qa/ondo-emulator-driver.py ui` to inspect labels and
   bounds. The driver supports `tap 'Exact label'`, `input 'QA'`, `enter`, `back`,
   `shot name`, `xy x y`, and `swipe x1 y1 x2 y2 duration`. Wait for each action to
   finish before starting another. Verify these paths:

   - Search `QA`; Songs, Albums, Artists and Playlists filter the results. Public
     providers blocked by the firewall can produce the partial-failure banner;
     the available library and SoulSync results should remain usable.
   - Download `QA Morning Light` from its song menu. Downloads → This device
     shows `Available offline`. Search and status filters retain the right rows;
     direct Play uses the saved file without another `/rest/stream` event.
   - Add `QA New Song` once. Dismiss/reopen its sheet or scroll away/back; it
     stays pending, and the request log contains only one POST `/wishlist`.
     For a slower request after the first import, POST a JSON object to
     `http://127.0.0.1:8765/qa/reset` with `title: "QA Pending Song"`,
     `request_delay: 8` and `import_delay: 30`. This changes only the local mock
     fixture and lets you close the UI while the request is still being accepted.
   - Downloads → Library progresses to Recently finished. `Find in library`
     opens a search for `QA New Song`, which has a normal library playback row.
   - Force-stop Ondo. Emit preferences again with `--offline`, repeat only the
     push/copy/ownership/context steps, then launch. Search shows its offline
     banner and distinguishes saved/unsaved songs. `QA Family Artist Morning
     Light` matches the saved song. Saved playback works without a stream call;
     unsaved playback/queue actions are unavailable.

5. When finished, stop only Ondo, remove the app-specific firewall rule and
   reverse forwarding, and stop the fixture server with Ctrl-C:

   ```sh
   adb -s emulator-5554 shell am force-stop com.nickshortnct.ondo
   adb -s emulator-5554 shell su 0 iptables -D OUTPUT -m owner --uid-owner "$ondoQaUid" ! -d 127.0.0.1/32 -j REJECT
   adb -s emulator-5554 reverse --remove tcp:8765
   adb -s emulator-5554 shell rm -f /data/local/tmp/ondo-qa-prefs.xml /data/local/tmp/ondo-ui.xml
   ```

Screenshots and sanitized events can be copied to the ignored `qa/` directory.
The fixture does not validate real provider availability, MusicBrainz or catalog
content quality; use it for UI, identity, queue and local-file regressions.
