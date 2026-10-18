# Changelog

## 1.0.0 (unreleased)

First release, with one component, `FirebaseMessaging`:

- **Connecting:** `GoogleServicesFile` reads your project's `google-services.json`, using the Android app whose package matches, or else the first one. `InitializeApp` connects with the four values instead.
- **Tokens:** `GetToken`, `DeleteToken`, and `NewToken` when Firebase creates or replaces the token.
- **Topics:** `SubscribeToTopic` and `UnsubscribeFromTopic`.
- **Messages:** `MessageReceived(from, data, title, body)` while a screen is open, in front or in the background. Notification messages to a closed app show as notifications, and tapping one fires `NotificationOpened(data)`. `DeletedMessages` when FCM dropped pending messages.
- **Notifications:** `RequestNotificationPermission` and `NotificationsEnabled`; one notification channel for the app's and FCM's notifications, with `ChannelName` and `ChannelImportance`; `ShowNotification` for local notifications, with `NotificationIcon`.
- **Errors:** `Error(operation, message)`, or `Screen.ErrorOccurred` without a handler.
- **Sample:** `PushDemo.aia` shows the token, subscribes to a topic, logs messages and shows a local notification.

Needs Android 7.0 or newer with Google Play services, and an APK build: the AI Companion can't load Firebase.
