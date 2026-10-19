# Changelog

## 1.0.0 (unreleased)

First release, with three components.

`Firebase` connects to your project:

- `GoogleServicesFile` reads your project's `google-services.json`, using the Android app whose package matches, or else the first one. `InitializeApp` connects with the four values instead. `ProjectId`.

`FirebaseMessaging`, for push notifications:
- **Tokens:** `GetToken`, `DeleteToken`, and `NewToken` when Firebase creates or replaces the token.
- **Topics:** `SubscribeToTopic` and `UnsubscribeFromTopic`.
- **Messages:** `MessageReceived(from, data, title, body)` while a screen is open, in front or in the background. Notification messages to a closed app show as notifications, and tapping one fires `NotificationOpened(data)`. `DeletedMessages` when FCM dropped pending messages.
- **Notifications:** `RequestNotificationPermission` and `NotificationsEnabled`; one notification channel for the app's and FCM's notifications, with `ChannelName` and `ChannelImportance`; `ShowNotification` for local notifications, with `NotificationIcon`.

`FirebaseRemoteConfig`, for values you change in the Firebase console:

- **Defaults:** `SetDefaults` with a dictionary of texts, numbers, booleans, lists or dictionaries.
- **Fetching:** `FetchAndActivate`, or `Fetch` and `Activate` separately, with `MinimumFetchInterval`.
- **Values:** `GetString`, `GetNumber`, `GetBoolean`, `GetJson` and `GetAll`.
- **Live updates:** `ListenForUpdates` and `ConfigUpdated(keys)`.

Every component reports failures through `Error(operation, message)`, or `Screen.ErrorOccurred` without a handler.

The `FirebaseDemo.aia` sample shows a welcome text from Remote Config, the token, subscribes to a topic, logs messages and shows a local notification.

Needs Android 7.0 or newer with Google Play services, and an APK build: the AI Companion can't load Firebase.
