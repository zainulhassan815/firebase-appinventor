package dev.dreamerslab.firebase

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import com.google.appinventor.components.annotations.DesignerProperty
import com.google.appinventor.components.annotations.Extension
import com.google.appinventor.components.annotations.Options
import com.google.appinventor.components.annotations.SimpleEvent
import com.google.appinventor.components.annotations.SimpleFunction
import com.google.appinventor.components.annotations.SimpleProperty
import com.google.appinventor.components.common.PropertyTypeConstants
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent
import com.google.appinventor.components.runtime.ComponentContainer
import com.google.appinventor.components.runtime.EventDispatcher
import com.google.appinventor.components.runtime.OnDestroyListener
import com.google.appinventor.components.runtime.OnNewIntentListener
import com.google.appinventor.components.runtime.OnResumeListener
import com.google.appinventor.components.runtime.util.MediaUtil
import com.google.appinventor.components.runtime.util.OnInitializeListener
import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.firebase.messaging.RemoteMessage
import dev.dreamerslab.firebase.helpers.ChannelImportance
import com.google.firebase.messaging.FirebaseMessaging as Messaging

@Extension(
    description = "Firebase Cloud Messaging: the device's registration token, topics, and the messages sent to " +
        "this app. Set its Firebase property to the Firebase component. Send messages from a server, never from " +
        "the app: sending needs your project's service account key.",
    icon = "icon.png",
)
class FirebaseMessaging(container: ComponentContainer) : AndroidNonvisibleComponent(container.`$form`()),
    OnInitializeListener, OnNewIntentListener, OnResumeListener, OnDestroyListener {

    private val events = Events(form, this) { operation, message ->
        EventDispatcher.dispatchEvent(this, "Error", operation, message)
    }
    private val notifications = Notifications(form)
    private var firebase: Firebase? = null
    private var channelName = "Notifications"
    private var channelImportance = ChannelImportance.Default
    private var notificationIcon = ""

    init {
        active = this
        form.registerForOnInitialize(this)
        form.registerForOnNewIntent(this)
        form.registerForOnResume(this)
        form.registerForOnDestroy(this)
    }

    @SimpleProperty(description = "The Firebase component that connects to your project.")
    fun Firebase(): Firebase? = firebase

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT + ":dev.dreamerslab.firebase.Firebase")
    @SimpleProperty
    fun Firebase(firebase: Firebase) {
        this.firebase = firebase
    }

    @Suppress("DEPRECATION")
    @SimpleFunction(description = "Requests the device's registration token. Send it to your server, which uses " +
        "it to send messages to this device. GotToken receives it.")
    fun GetToken() = events.request("GetToken", { messaging().token }) { events.fire { GotToken(it) } }

    @Suppress("DEPRECATION")
    @SimpleFunction(description = "Deletes the registration token, so messages sent to it no longer arrive. " +
        "GetToken creates a new one.")
    fun DeleteToken() = events.request("DeleteToken", { messaging().deleteToken() }) { events.fire { TokenDeleted() } }

    @SimpleFunction(description = "Subscribes the device to a topic, so it receives the messages sent to it. " +
        "Topic names use letters, digits and -_.~%")
    fun SubscribeToTopic(topic: String) =
        events.request("SubscribeToTopic", { messaging().subscribeToTopic(topic) }) { events.fire { SubscribedToTopic(topic) } }

    @SimpleFunction(description = "Unsubscribes the device from a topic.")
    fun UnsubscribeFromTopic(topic: String) =
        events.request("UnsubscribeFromTopic", { messaging().unsubscribeFromTopic(topic) }) { events.fire { UnsubscribedFromTopic(topic) } }

    @SimpleProperty(description = "The name of the app's notification channel, shown in the app's notification " +
        "settings. Notifications from ShowNotification and from FCM messages use this channel.")
    fun ChannelName() = channelName

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "Notifications")
    @SimpleProperty
    fun ChannelName(name: String) {
        channelName = name
        if (events.initialized) createChannel()
    }

    @SimpleProperty(description = "How the channel's notifications interrupt the user. Android applies it when " +
        "the channel is first created: after that, only the user can change it, in the app's settings.")
    @Options(ChannelImportance::class)
    fun ChannelImportance(): String = channelImportance.toUnderlyingValue()

    @DesignerProperty(
        editorType = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs = ["Min", "Low", "Default", "High"],
        defaultValue = "Default",
    )
    @SimpleProperty
    fun ChannelImportance(@Options(ChannelImportance::class) importance: String) = events.attempt("ChannelImportance") {
        channelImportance = requireNotNull(ChannelImportance.fromUnderlyingValue(importance)) {
            "Unknown importance: $importance"
        }
        if (events.initialized) createChannel()
    }

    @SimpleProperty(description = "A media file shown as ShowNotification's icon in the status bar: a white " +
        "shape on a transparent background. Without one, notifications use the app icon.")
    fun NotificationIcon() = notificationIcon

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_ASSET)
    @SimpleProperty
    fun NotificationIcon(path: String) {
        notificationIcon = path
    }

    @SimpleProperty(description = "Whether the user allows this app's notifications.")
    fun NotificationsEnabled() = notifications.enabled

    @SimpleFunction(description = "Asks the user to allow notifications. Android 13 and later need this before " +
        "any notification shows. NotificationPermissionResult receives the answer.")
    fun RequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            form.askPermission(Manifest.permission.POST_NOTIFICATIONS) { _, granted ->
                events.fire { NotificationPermissionResult(granted) }
            }
        } else {
            events.fire { NotificationPermissionResult(notifications.enabled) }
        }
    }

    @SimpleFunction(description = "Shows a notification. Tapping it opens the app, and NotificationOpened " +
        "receives data, a dictionary of text values.")
    fun ShowNotification(title: String, body: String, data: YailDictionary) = events.attempt("ShowNotification") {
        createChannel()
        notifications.show(title, body, data.entries.associate { (key, value) -> "$key" to "$value" }, icon())
    }

    @SimpleEvent(description = "The registration token GetToken requested.")
    fun GotToken(token: String) {
        EventDispatcher.dispatchEvent(this, "GotToken", token)
    }

    @SimpleEvent(description = "DeleteToken finished.")
    fun TokenDeleted() {
        EventDispatcher.dispatchEvent(this, "TokenDeleted")
    }

    @SimpleEvent(description = "Firebase created or replaced the registration token while the app was open, " +
        "such as on the app's first start. Send it to your server.")
    fun NewToken(token: String) {
        EventDispatcher.dispatchEvent(this, "NewToken", token)
    }

    @SimpleEvent(description = "The device is subscribed to the topic.")
    fun SubscribedToTopic(topic: String) {
        EventDispatcher.dispatchEvent(this, "SubscribedToTopic", topic)
    }

    @SimpleEvent(description = "The device is unsubscribed from the topic.")
    fun UnsubscribedFromTopic(topic: String) {
        EventDispatcher.dispatchEvent(this, "UnsubscribedFromTopic", topic)
    }

    @SimpleEvent(description = "A message arrived while the app was running. from is the sender ID or " +
        "/topics/<name>, data holds the message's data, and title and body its notification, if it has one. " +
        "Android shows notification messages itself only while the app is in the background.")
    fun MessageReceived(from: String, data: YailDictionary, title: String, body: String) {
        EventDispatcher.dispatchEvent(this, "MessageReceived", from, data, title, body)
    }

    @SimpleEvent(description = "The app was opened by tapping a notification: one Android showed for an FCM " +
        "message, or one from ShowNotification. data holds the message's data. Put this component on Screen1: " +
        "notifications open the app's first screen.")
    fun NotificationOpened(data: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "NotificationOpened", data)
    }

    @SimpleEvent(description = "Whether the user allowed notifications.")
    fun NotificationPermissionResult(granted: Boolean) {
        EventDispatcher.dispatchEvent(this, "NotificationPermissionResult", granted)
    }

    @SimpleEvent(description = "Firebase deleted messages that waited too long for the device. Fetch the " +
        "latest state from your server.")
    fun DeletedMessages() {
        EventDispatcher.dispatchEvent(this, "DeletedMessages")
    }

    @SimpleEvent(description = "An operation failed. Without this event, the screen's ErrorOccurred event " +
        "receives the error.")
    fun Error(operation: String, message: String) {
        EventDispatcher.dispatchEvent(this, "Error", operation, message)
    }

    override fun onInitialize() {
        createChannel()
        notificationOpened(form.intent)
    }

    override fun onNewIntent(intent: Intent) = notificationOpened(intent)

    override fun onResume() {
        active = this
    }

    override fun onDestroy() {
        if (active === this) active = null
    }

    internal fun messageReceived(message: RemoteMessage) {
        val data = YailDictionary.makeDictionary(HashMap<Any, Any>(message.data))
        val notification = message.notification
        events.fire { MessageReceived(message.from.orEmpty(), data, notification?.title.orEmpty(), notification?.body.orEmpty()) }
    }

    internal fun newToken(token: String) = events.fire { NewToken(token) }

    internal fun deletedMessages() = events.fire { DeletedMessages() }

    private fun notificationOpened(intent: Intent?) {
        val data = Notifications.takeOpenedData(intent) ?: return
        events.fire { NotificationOpened(YailDictionary.makeDictionary(HashMap<Any, Any>(data))) }
    }

    private fun createChannel() = notifications.createChannel(channelName, channelImportance.importance())

    private fun icon(): Icon? = notificationIcon.takeIf { it.isNotEmpty() }?.let { path ->
        Icon.createWithBitmap(MediaUtil.openMedia(form, path).use(BitmapFactory::decodeStream))
    }

    private fun messaging(): Messaging {
        checkNotNull(firebase) { "Set the Firebase property" }.requireApp()
        return Messaging.getInstance()
    }

    internal companion object {
        @Volatile
        var active: FirebaseMessaging? = null
    }
}
