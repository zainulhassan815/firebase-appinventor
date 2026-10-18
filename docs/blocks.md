# Blocks

Every block of the Firebase extension. Each image contains its block, so you can drag it straight into your workspace.

- [FirebaseMessaging](#firebasemessaging)
  - [Connecting](#connecting)
  - [Token](#token)
  - [Topics](#topics)
  - [Messages](#messages)
  - [Notifications](#notifications)
  - [Errors](#errors)
- [Dropdowns](#dropdowns)

## FirebaseMessaging

Firebase Cloud Messaging: the device's registration token, topics, and the messages sent to this app. Connect it to your Firebase project with GoogleServicesFile or InitializeApp. Send messages from a server, never from the app: sending needs your project's service account key. Guide: [Messaging](messaging.md).

### Connecting

![get GoogleServicesFile](images/blocks/FirebaseMessaging1-getGoogleServicesFile.png) ![set GoogleServicesFile](images/blocks/FirebaseMessaging1-setGoogleServicesFile.png)

**GoogleServicesFile** (read-write): The google-services.json file of your Firebase project, uploaded as a media file. Any Android app of the project works: the app's package name doesn't have to match.

![InitializeApp](images/blocks/FirebaseMessaging1-InitializeApp.png)

**InitializeApp:** Connects to a Firebase project without a google-services.json file. The values are in the project settings: the app ID, the Web API key, the project ID and the project number.

### Token

![GetToken](images/blocks/FirebaseMessaging1-GetToken.png)

**GetToken:** Requests the device's registration token. Send it to your server, which uses it to send messages to this device. GotToken receives it.

![GotToken](images/blocks/FirebaseMessaging1-GotToken.png)

**GotToken:** The registration token GetToken requested.

![NewToken](images/blocks/FirebaseMessaging1-NewToken.png)

**NewToken:** Firebase created or replaced the registration token while the app was open, such as on the app's first start. Send it to your server.

![DeleteToken](images/blocks/FirebaseMessaging1-DeleteToken.png)

**DeleteToken:** Deletes the registration token, so messages sent to it no longer arrive. GetToken creates a new one.

![TokenDeleted](images/blocks/FirebaseMessaging1-TokenDeleted.png)

**TokenDeleted:** DeleteToken finished.

### Topics

![SubscribeToTopic](images/blocks/FirebaseMessaging1-SubscribeToTopic.png)

**SubscribeToTopic:** Subscribes the device to a topic, so it receives the messages sent to it. Topic names use letters, digits and -_.~%.

![SubscribedToTopic](images/blocks/FirebaseMessaging1-SubscribedToTopic.png)

**SubscribedToTopic:** The device is subscribed to the topic.

![UnsubscribeFromTopic](images/blocks/FirebaseMessaging1-UnsubscribeFromTopic.png)

**UnsubscribeFromTopic:** Unsubscribes the device from a topic.

![UnsubscribedFromTopic](images/blocks/FirebaseMessaging1-UnsubscribedFromTopic.png)

**UnsubscribedFromTopic:** The device is unsubscribed from the topic.

### Messages

![MessageReceived](images/blocks/FirebaseMessaging1-MessageReceived.png)

**MessageReceived:** A message arrived while the app was running. from is the sender ID or /topics/<name>, data holds the message's data, and title and body its notification, if it has one. Android shows notification messages itself only while the app is in the background.

![NotificationOpened](images/blocks/FirebaseMessaging1-NotificationOpened.png)

**NotificationOpened:** The app was opened by tapping a notification: one Android showed for an FCM message, or one from ShowNotification. data holds the message's data. Put this component on Screen1: notifications open the app's first screen.

![DeletedMessages](images/blocks/FirebaseMessaging1-DeletedMessages.png)

**DeletedMessages:** Firebase deleted messages that waited too long for the device. Fetch the latest state from your server.

### Notifications

![RequestNotificationPermission](images/blocks/FirebaseMessaging1-RequestNotificationPermission.png)

**RequestNotificationPermission:** Asks the user to allow notifications. Android 13 and later need this before any notification shows. NotificationPermissionResult receives the answer.

![NotificationPermissionResult](images/blocks/FirebaseMessaging1-NotificationPermissionResult.png)

**NotificationPermissionResult:** Whether the user allowed notifications.

![get NotificationsEnabled](images/blocks/FirebaseMessaging1-getNotificationsEnabled.png)

**NotificationsEnabled** (read-only): Whether the user allows this app's notifications.

![ShowNotification](images/blocks/FirebaseMessaging1-ShowNotification.png)

**ShowNotification:** Shows a notification. Tapping it opens the app, and NotificationOpened receives data, a dictionary of text values.

![get ChannelName](images/blocks/FirebaseMessaging1-getChannelName.png) ![set ChannelName](images/blocks/FirebaseMessaging1-setChannelName.png)

**ChannelName** (read-write): The name of the app's notification channel, shown in the app's notification settings. Notifications from ShowNotification and from FCM messages use this channel.

![get ChannelImportance](images/blocks/FirebaseMessaging1-getChannelImportance.png) ![set ChannelImportance](images/blocks/FirebaseMessaging1-setChannelImportance.png)

**ChannelImportance** (read-write): How the channel's notifications interrupt the user. Android applies it when the channel is first created: after that, only the user can change it, in the app's settings.

![get NotificationIcon](images/blocks/FirebaseMessaging1-getNotificationIcon.png) ![set NotificationIcon](images/blocks/FirebaseMessaging1-setNotificationIcon.png)

**NotificationIcon** (read-write): A media file shown as ShowNotification's icon in the status bar: a white shape on a transparent background. Without one, notifications use the app icon.

### Errors

![Error](images/blocks/FirebaseMessaging1-Error.png)

**Error:** An operation failed. Without this event, the screen's ErrorOccurred event receives the error.

## Dropdowns

![ChannelImportance](images/blocks/helper-ChannelImportance.png)

**ChannelImportance:** Min, Low, Default, High
