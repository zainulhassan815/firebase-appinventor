# Testing

The tests build real App Inventor apps with App Inventor's build server, run them on an
Android device, and send them real messages through FCM.

## Setup

- A checkout of [rush-cli](https://github.com/shreyashsaitwal/rush-cli) next to this
  repository (or `RUSH_CLI`), whose `tool/e2e/buildserver.sh` builds App Inventor's
  build server once. The sample test and the block images also need its blocks
  editor: `ant -f blocklyeditor/build.xml BlocklyTestbed` in that App Inventor checkout.
- JDK 11, Node.js, `npm install` in `tests/e2e`, and Python with the `cryptography`
  package.
- A device with Google Play services on `adb`, unlocked. Set `ADB` to use another adb.
- A Firebase project. Put its `google-services.json` and a service account key
  (Project settings → Service accounts → Generate new private key) in
  `tests/e2e/secrets/` as `google-services.json` and `service-account.json`, or point
  `FIREBASE_SECRETS` at another folder. `secrets/` is ignored by git.

## Scenarios

```
tests/e2e/run.sh tests/e2e/scenarios/messages.yail
```

A scenario is a screen's YAIL with one `FirebaseMessaging` component, `Messaging1`,
that reports lines into a Label. Its `.steps.json` drives the phone: install, launch,
press Home, kill the process, send a message, tap a notification, and wait for or
expect report lines. `@APP_ID@`, `@API_KEY@`, `@PROJECT_ID@` and `@SENDER_ID@` are
filled in from `google-services.json`. When a step fails, the screenshot is saved in
`.e2e/<scenario>/failure.png`.

| Scenario | Checks |
|---|---|
| `token` | `GetToken` returns a token |
| `initialize` | `InitializeApp` connects without a file; `Error` reports blocks used before it, a second project and an invalid topic |
| `messages` | data and notification messages in front and in the background reach `MessageReceived` |
| `topics` | a topic message arrives after subscribing, and not after unsubscribing |
| `opened` | a notification for a killed app shows on the app's channel, and tapping it fires `NotificationOpened` with its data |
| `resume` | the same for an app in the background |
| `local` | `ShowNotification` shows a notification with `NotificationIcon`, and tapping it fires `NotificationOpened` |
| `permission` | `RequestNotificationPermission` shows Android's dialog and reports the answer |

## The sample

```
tests/e2e/sample.sh
```

Packs `PushDemo.aia`, selects the secret `google-services.json`, compiles the sample's
blocks to YAIL with App Inventor's own generator (`tests/e2e/blocks/compile.js`), builds
the APK, and goes through every button: token, subscribe, a topic message, a local
notification and its tap, unsubscribe.

## Block images

```
unzip -p out/dev.dreamerslab.firebase.aix dev.dreamerslab.firebase/components.json > components.json
python3 tests/e2e/blocks/palette.py components.json palette
node tests/e2e/blocks/images.js <appinventor> palette/Screen1.scm palette/<id>.bky components.json docs/images/blocks <id>
python3 docs/blocks.py
```

`palette.py` writes a screen with the component and a `.bky` file per block, named by
the block's id (like `FirebaseMessaging1.GetToken` or `helper.ChannelImportance`), and
prints the ids. `images.js` opens each in App Inventor's blocks editor and saves the
image App Inventor exports, which contains the block. Render each block from its own
file. `docs/blocks.py` then writes [Blocks](blocks.md).

The guides' examples come from the sample:

```
node tests/e2e/blocks/images.js <appinventor> samples/push-demo/src/appinventor/ai_dreamerslab/PushDemo/Screen1.{scm,bky} \
  components.json docs/images/examples Screen1.Initialize FirebaseMessaging1.GotToken …
```
