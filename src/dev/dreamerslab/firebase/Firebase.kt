package dev.dreamerslab.firebase

import com.google.appinventor.components.annotations.DesignerProperty
import com.google.appinventor.components.annotations.Extension
import com.google.appinventor.components.annotations.SimpleEvent
import com.google.appinventor.components.annotations.SimpleFunction
import com.google.appinventor.components.annotations.SimpleProperty
import com.google.appinventor.components.common.PropertyTypeConstants
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent
import com.google.appinventor.components.runtime.Component
import com.google.appinventor.components.runtime.ComponentContainer
import com.google.appinventor.components.runtime.EventDispatcher
import com.google.appinventor.components.runtime.util.MediaUtil
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

@Extension(
    description = "Connects the app to your Firebase project. The other Firebase components use it through " +
        "their Firebase property.",
    icon = "icon.png",
)
class Firebase(container: ComponentContainer) : AndroidNonvisibleComponent(container.`$form`()), Component {

    private val events = Events(form, this) { operation, message ->
        EventDispatcher.dispatchEvent(this, "Error", operation, message)
    }
    private var googleServicesFile = ""
    internal var app: FirebaseApp? = null
        private set

    @SimpleProperty(description = "The google-services.json file of your Firebase project, uploaded as a media " +
        "file. Any Android app of the project works: the app's package name doesn't have to match.")
    fun GoogleServicesFile() = googleServicesFile

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_ASSET)
    @SimpleProperty
    fun GoogleServicesFile(path: String) {
        googleServicesFile = path
        if (path.isEmpty()) return
        events.attempt("GoogleServicesFile") {
            val json = MediaUtil.openMedia(form, path).bufferedReader().use { it.readText() }
            connect(FirebaseProject.options(json, form.packageName))
        }
    }

    @SimpleFunction(description = "Connects to a Firebase project without a google-services.json file. The " +
        "values are in the project settings: the app ID, the Web API key, the project ID and the project number.")
    fun InitializeApp(applicationId: String, apiKey: String, projectId: String, senderId: String) =
        events.attempt("InitializeApp") {
            connect(
                FirebaseOptions.Builder()
                    .setApplicationId(applicationId)
                    .setApiKey(apiKey)
                    .setProjectId(projectId)
                    .setGcmSenderId(senderId)
                    .build()
            )
        }

    @SimpleProperty(description = "The ID of the connected Firebase project, or empty before it connects.")
    fun ProjectId(): String = app?.options?.projectId.orEmpty()

    @SimpleEvent(description = "Connecting failed. Without this event, the screen's ErrorOccurred event " +
        "receives the error.")
    fun Error(operation: String, message: String) {
        EventDispatcher.dispatchEvent(this, "Error", operation, message)
    }

    internal fun requireApp(): FirebaseApp =
        checkNotNull(app) { "Set the Firebase component's GoogleServicesFile or call its InitializeApp first" }

    private fun connect(options: FirebaseOptions) {
        app = FirebaseProject.start(form.applicationContext, options)
    }
}
