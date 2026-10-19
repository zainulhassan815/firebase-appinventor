# Sending

The extension receives messages. It has no blocks to send them, on purpose: sending
needs your Firebase project's **service account key**, and anything inside an app can be
extracted. Whoever holds the key can message every user of your app. Keep it on a
server.

There are three ways to send.

## Firebase console

Messaging → new campaign → **Firebase Notification messages**. Send a test message to a
token, or send to a topic or to every installation of an Android app in the project.
Under **Additional options → Custom data**, add the keys `NotificationOpened` receives.

Good for testing and for announcements written by hand.

## Your own server

Servers send through the [FCM HTTP v1 API](https://firebase.google.com/docs/cloud-messaging/send/v1-api).
Create the key in Project settings → Service accounts → **Generate new private key**.
Firebase's Admin SDKs (Node.js, Python, Java, Go, C#) turn it into access tokens and
send for you:

```js
import { initializeApp, cert } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';

initializeApp({ credential: cert(serviceAccount) });

await getMessaging().send({
  token,
  notification: { title: 'New message', body: 'Ali: are you coming?' },
  data: { chat: '42' },
  android: { priority: 'high' },
});
```

Without an Admin SDK, sign a Google OAuth access token with the key (scope
`https://www.googleapis.com/auth/firebase.messaging`) and post the message yourself:

```
POST https://fcm.googleapis.com/v1/projects/<project-id>/messages:send
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "message": {
    "topic": "news",
    "notification": { "title": "Hello", "body": "From the server" },
    "data": { "article": "17" },
    "android": { "priority": "high" }
  }
}
```

The target is one of `token` or `topic`. Rules for the message:

- `data` values must be text: send `"17"`, not `17`.
- Use `"priority": "high"` for messages the user should see now. Android delays
  normal-priority messages while the phone sleeps.
- To reach a closed app, include `notification`. See
  [what happens in each app state](messaging.md#messages).

When a token no longer exists, because the app was uninstalled or the token was
deleted, the API answers `404` with the error code `UNREGISTERED`. Remove that token
from your database.

Any server that can keep a secret works: Cloud Functions for Firebase, a Supabase Edge
Function, or your own backend. Trigger the send from your app through your server, such
as with the [Supabase extension](https://github.com/zainulhassan815/supabase-appinventor)'s
`SupabaseFunctions`, and let the server check who may message whom.

## From a computer, for testing

The device tests send with [`tests/e2e/admin.py`](../tests/e2e/admin.py), which needs
Python and the `cryptography` package:

```
python3 tests/e2e/admin.py service-account.json send token <token> '{"notification": {"title": "Hi", "body": "Test"}}'
python3 tests/e2e/admin.py service-account.json send topic news '{"data": {"n": "1"}}'
```

Never commit the key file.
