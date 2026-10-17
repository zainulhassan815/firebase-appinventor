package dev.dreamerslab.firebase

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Task
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
import com.google.appinventor.components.runtime.util.ErrorMessages
import com.google.appinventor.components.runtime.util.MediaUtil
import com.google.appinventor.components.runtime.util.OnInitializeListener
import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.firebase.FirebaseOptions
import dev.dreamerslab.firebase.helpers.ChannelImportance
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.FirebaseMessaging as Messaging

@Extension(
    description = "Firebase Cloud Messaging: the device's registration token, topics, and the messages sent to " +
        "this app. Connect it to your Firebase project with GoogleServicesFile or InitializeApp. Send messages " +
        "from a server, never from the app: sending needs your project's service account key.",
    icon = "icon.png",
)
class FirebaseMessaging(container: ComponentContainer) : AndroidNonvisibleComponent(container.`$form`()),
    OnInitializeListener, OnNewIntentListener, OnResumeListener, OnDestroyListener {

    private val main = Handler(Looper.getMainLooper())
    private val notifications = Notifications(form)
    private val beforeInitialize = mutableListOf<Runnable>()
    private var initialized = false
    private var googleServicesFile = ""
    private var connected = false
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

    @SimpleProperty(description = "The google-services.json file of your Firebase project, uploaded as a media " +
        "file. Any Android app of the project works: the app's package name doesn't have to match.")
    fun GoogleServicesFile() = googleServicesFile

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_ASSET)
    @SimpleProperty
    fun GoogleServicesFile(path: String) {
        googleServicesFile = path
        if (path.isEmpty()) return
        attempt("GoogleServicesFile") {
            val json = MediaUtil.openMedia(form, path).bufferedReader().use { it.readText() }
            connect(FirebaseProject.options(json, form.packageName))
        }
    }

    @SimpleFunction(description = "Connects to a Firebase project without a google-services.json file. The " +
        "values are in the project settings: the app ID, the Web API key, the project ID and the project number.")
    fun InitializeApp(applicationId: String, apiKey: String, projectId: String, senderId: String) =
        attempt("InitializeApp") {
            connect(
                FirebaseOptions.Builder()
                    .setApplicationId(applicationId)
                    .setApiKey(apiKey)
                    .setProjectId(projectId)
                    .setGcmSenderId(senderId)
                    .build()
            )
        }

    @Suppress("DEPRECATION")
    @SimpleFunction(description = "Requests the device's registration token. Send it to your server, which uses " +
        "it to send messages to this device. GotToken receives it.")
    fun GetToken() = request("GetToken", { messaging().token }) { fire { GotToken(it) } }

    @Suppress("DEPRECATION")
    @SimpleFunction(description = "Deletes the registration token, so messages sent to it no longer arrive. " +
        "GetToken creates a new one.")
    fun DeleteToken() = request("DeleteToken", { messaging().deleteToken() }) { fire { TokenDeleted() } }

    @SimpleFunction(description = "Subscribes the device to a topic, so it receives the messages sent to it. " +
        "Topic names use letters, digits and -_.~%")
    fun SubscribeToTopic(topic: String) =
        request("SubscribeToTopic", { messaging().subscribeToTopic(topic) }) { fire { SubscribedToTopic(topic) } }

    @SimpleFunction(description = "Unsubscribes the device from a topic.")
    fun UnsubscribeFromTopic(topic: String) =
        request("UnsubscribeFromTopic", { messaging().unsubscribeFromTopic(topic) }) { fire { UnsubscribedFromTopic(topic) } }

    @SimpleProperty(description = "The name of the app's notification channel, shown in the app's notification " +
        "settings. Notifications from ShowNotification and from FCM messages use this channel.")
    fun ChannelName() = channelName

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "Notifications")
    @SimpleProperty
    fun ChannelName(name: String) {
        channelName = name
        if (initialized) createChannel()
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
    fun ChannelImportance(@Options(ChannelImportance::class) importance: String) = attempt("ChannelImportance") {
        channelImportance = requireNotNull(ChannelImportance.fromUnderlyingValue(importance)) {
            "Unknown importance: $importance"
        }
        if (initialized) createChannel()
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
                fire { NotificationPermissionResult(granted) }
            }
        } else {
            fire { NotificationPermissionResult(notifications.enabled) }
        }
    }

    @SimpleFunction(description = "Shows a notification. Tapping it opens the app, and NotificationOpened " +
        "receives data, a dictionary of text values.")
    fun ShowNotification(title: String, body: String, data: YailDictionary) = attempt("ShowNotification") {
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

    @SimpleEvent(description = "Firebase replaced the registration token while the app was open. Send the new " +
        "one to your server.")
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
        reportError(operation, message)
    }

    override fun onInitialize() {
        initialized = true
        createChannel()
        notificationOpened(form.intent)
        beforeInitialize.forEach(main::post)
        beforeInitialize.clear()
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
        fire { MessageReceived(message.from.orEmpty(), data, notification?.title.orEmpty(), notification?.body.orEmpty()) }
    }

    internal fun newToken(token: String) = fire { NewToken(token) }

    internal fun deletedMessages() = fire { DeletedMessages() }

    private fun notificationOpened(intent: Intent?) {
        val data = Notifications.takeOpenedData(intent) ?: return
        fire { NotificationOpened(YailDictionary.makeDictionary(HashMap<Any, Any>(data))) }
    }

    private fun createChannel() = notifications.createChannel(channelName, channelImportance.importance())

    private fun icon(): Icon? = notificationIcon.takeIf { it.isNotEmpty() }?.let { path ->
        Icon.createWithBitmap(MediaUtil.openMedia(form, path).use(BitmapFactory::decodeStream))
    }

    private fun connect(options: FirebaseOptions) {
        FirebaseProject.start(form.applicationContext, options)
        connected = true
    }

    private fun messaging(): Messaging {
        check(connected) { "Set GoogleServicesFile or call InitializeApp first" }
        return Messaging.getInstance()
    }

    private fun <T> request(operation: String, task: () -> Task<T>, done: (T) -> Unit) = attempt(operation) {
        task().addOnCompleteListener { result ->
            if (result.isSuccessful) done(result.result)
            else fail(operation, result.exception?.message ?: "$operation failed")
        }
    }

    private fun attempt(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail(operation, e.message ?: e.toString())
        }
    }

    private fun fail(operation: String, message: String) = fire { reportError(operation, message) }

    private fun fire(event: Runnable) {
        main.post {
            if (initialized) event.run() else beforeInitialize += event
        }
    }

    private fun reportError(operation: String, message: String) {
        if (!EventDispatcher.dispatchEvent(this, "Error", operation, message)) {
            form.ErrorOccurred(this, operation, ErrorMessages.ERROR_EXTENSION_ERROR, message)
        }
    }

    internal companion object {
        @Volatile
        var active: FirebaseMessaging? = null
    }
}
