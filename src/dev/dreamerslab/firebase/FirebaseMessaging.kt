package dev.dreamerslab.firebase

import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.appinventor.components.annotations.DesignerProperty
import com.google.appinventor.components.annotations.Extension
import com.google.appinventor.components.annotations.SimpleEvent
import com.google.appinventor.components.annotations.SimpleFunction
import com.google.appinventor.components.annotations.SimpleProperty
import com.google.appinventor.components.common.PropertyTypeConstants
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent
import com.google.appinventor.components.runtime.ComponentContainer
import com.google.appinventor.components.runtime.EventDispatcher
import com.google.appinventor.components.runtime.OnDestroyListener
import com.google.appinventor.components.runtime.OnResumeListener
import com.google.appinventor.components.runtime.util.ErrorMessages
import com.google.appinventor.components.runtime.util.MediaUtil
import com.google.appinventor.components.runtime.util.OnInitializeListener
import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.FirebaseMessaging as Messaging

@Extension(
    description = "Firebase Cloud Messaging: the device's registration token, topics, and the messages sent to " +
        "this app. Connect it to your Firebase project with GoogleServicesFile or InitializeApp. Send messages " +
        "from a server, never from the app: sending needs your project's service account key.",
    icon = "icon.png",
)
class FirebaseMessaging(container: ComponentContainer) : AndroidNonvisibleComponent(container.`$form`()),
    OnInitializeListener, OnResumeListener, OnDestroyListener {

    private val main = Handler(Looper.getMainLooper())
    private val beforeInitialize = mutableListOf<Runnable>()
    private var initialized = false
    private var googleServicesFile = ""
    private var connected = false

    init {
        active = this
        form.registerForOnInitialize(this)
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
        beforeInitialize.forEach(main::post)
        beforeInitialize.clear()
    }

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
