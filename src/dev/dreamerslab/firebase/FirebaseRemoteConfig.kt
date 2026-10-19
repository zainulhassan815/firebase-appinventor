package dev.dreamerslab.firebase

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
import com.google.appinventor.components.runtime.util.JsonUtil
import com.google.appinventor.components.runtime.util.OnInitializeListener
import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.appinventor.components.runtime.util.YailList
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfig as RemoteConfig

@Extension(
    description = "Firebase Remote Config: values you change in the Firebase console, such as texts, numbers " +
        "and feature switches, without publishing a new version of the app. Set its Firebase property to the " +
        "Firebase component.",
    icon = "icon.png",
)
class FirebaseRemoteConfig(container: ComponentContainer) : AndroidNonvisibleComponent(container.`$form`()),
    OnInitializeListener, OnDestroyListener {

    private val events = Events(form, this) { operation, message ->
        EventDispatcher.dispatchEvent(this, "Error", operation, message)
    }
    private var firebase: Firebase? = null
    private var defaults = emptyMap<String, Any>()
    private var minimumFetchInterval = DEFAULT_FETCH_INTERVAL
    private var listenForUpdates = false
    private var updates: ConfigUpdateListenerRegistration? = null

    init {
        form.registerForOnInitialize(this)
        form.registerForOnDestroy(this)
    }

    @SimpleProperty(description = "The Firebase component that connects to your project.")
    fun Firebase(): Firebase? = firebase

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT + ":dev.dreamerslab.firebase.Firebase")
    @SimpleProperty
    fun Firebase(firebase: Firebase) {
        this.firebase = firebase
    }

    @SimpleProperty(description = "How many seconds fetched values stay fresh. Fetch and FetchAndActivate only " +
        "contact Firebase when the last fetch is older. Firebase limits how often an app may fetch: keep 12 hours " +
        "(43200) in published apps, and use 0 while you develop.")
    fun MinimumFetchInterval() = minimumFetchInterval

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_INTEGER, defaultValue = "43200")
    @SimpleProperty
    fun MinimumFetchInterval(seconds: Int) {
        minimumFetchInterval = seconds.coerceAtLeast(0)
    }

    @SimpleProperty(description = "Whether to listen for values published in the Firebase console while the app " +
        "is open. ConfigUpdated fires when new values have been fetched: call Activate to use them.")
    fun ListenForUpdates() = listenForUpdates

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
    @SimpleProperty
    fun ListenForUpdates(listen: Boolean) {
        listenForUpdates = listen
        if (events.initialized) followUpdates()
    }

    @SimpleFunction(description = "Sets the values used for keys that Firebase has no value for, such as before " +
        "the first fetch or without a connection. Values are texts, numbers, booleans, lists or dictionaries.")
    fun SetDefaults(values: YailDictionary) {
        defaults = values.entries.associate { (key, value) -> "$key" to value }
    }

    @SimpleFunction(description = "Fetches the latest values from Firebase and activates them. Activated " +
        "receives whether any value changed.")
    fun FetchAndActivate() = events.request(
        "FetchAndActivate",
        { config().let { config -> config.fetch(minimumFetchInterval.toLong()).onSuccessTask { config.activate() } } },
    ) { updated -> events.fire { Activated(updated) } }

    @SimpleFunction(description = "Fetches the latest values from Firebase without using them yet, so values " +
        "don't change while the user looks at them. Fetched fires when they are ready: call Activate, for " +
        "example the next time the app starts.")
    fun Fetch() = events.request("Fetch", { config().fetch(minimumFetchInterval.toLong()) }) { events.fire { Fetched() } }

    @SimpleFunction(description = "Uses the values fetched last. Activated receives whether any value changed.")
    fun Activate() = events.request("Activate", { config().activate() }) { updated -> events.fire { Activated(updated) } }

    @SimpleFunction(description = "The value of key as text, or empty text if it has none.")
    fun GetString(key: String): String = value(key).orEmpty()

    @SimpleFunction(description = "The value of key as a number, or 0 if it has none or isn't a number.")
    fun GetNumber(key: String): Double = read("GetNumber", key, 0.0) { it.toDouble() }

    @SimpleFunction(description = "The value of key as a boolean, such as true, yes or 1. False if it has none.")
    fun GetBoolean(key: String): Boolean = read("GetBoolean", key, false) { text ->
        when (text.lowercase()) {
            in TRUE_TEXTS -> true
            in FALSE_TEXTS -> false
            else -> throw IllegalArgumentException("$text isn't a boolean")
        }
    }

    @SimpleFunction(description = "The value of key read as JSON: a dictionary, a list, or a single value. Empty " +
        "text if it has none or isn't valid JSON.")
    fun GetJson(key: String): Any = read("GetJson", key, "") { JsonUtil.getObjectFromJson(it, true) }

    @SimpleFunction(description = "Every key with a value, from Firebase or the defaults, as a dictionary of texts.")
    fun GetAll(): YailDictionary {
        val remote = connectedConfig()?.all?.mapValues { (_, value) -> value.asString() }.orEmpty()
        val all = defaults.mapValues { (_, value) -> text(value) } + remote
        return YailDictionary.makeDictionary(HashMap<Any, Any>(all))
    }

    @SimpleEvent(description = "Fetch finished. Call Activate to use the fetched values.")
    fun Fetched() {
        EventDispatcher.dispatchEvent(this, "Fetched")
    }

    @SimpleEvent(description = "The fetched values are in use. updated is true when any value changed.")
    fun Activated(updated: Boolean) {
        EventDispatcher.dispatchEvent(this, "Activated", updated)
    }

    @SimpleEvent(description = "New values were published and fetched while the app was open. keys lists the " +
        "keys that changed. Call Activate to use them.")
    fun ConfigUpdated(keys: YailList) {
        EventDispatcher.dispatchEvent(this, "ConfigUpdated", keys)
    }

    @SimpleEvent(description = "An operation failed. Without this event, the screen's ErrorOccurred event " +
        "receives the error.")
    fun Error(operation: String, message: String) {
        EventDispatcher.dispatchEvent(this, "Error", operation, message)
    }

    override fun onInitialize() = followUpdates()

    override fun onDestroy() {
        updates?.remove()
        updates = null
    }

    private fun followUpdates() {
        updates?.remove()
        updates = null
        if (!listenForUpdates) return
        events.attempt("ListenForUpdates") {
            updates = config().addOnConfigUpdateListener(object : ConfigUpdateListener {
                override fun onUpdate(update: ConfigUpdate) {
                    events.fire { ConfigUpdated(YailList.makeList(update.updatedKeys.sorted())) }
                }

                override fun onError(error: FirebaseRemoteConfigException) =
                    events.fail("ListenForUpdates", error.message ?: error.code.name)
            })
        }
    }

    private fun <T> read(operation: String, key: String, fallback: T, parse: (String) -> T): T {
        val text = value(key)?.takeIf { it.isNotEmpty() } ?: return fallback
        return try {
            parse(text)
        } catch (e: Exception) {
            events.fail(operation, "The value of $key isn't valid: ${e.message}")
            fallback
        }
    }

    private fun value(key: String): String? {
        val remote = connectedConfig()?.getValue(key)
        if (remote != null && remote.source != RemoteConfig.VALUE_SOURCE_STATIC) return remote.asString()
        return defaults[key]?.let(::text)
    }

    private fun text(value: Any): String = when (value) {
        is YailDictionary, is YailList -> JsonUtil.getJsonRepresentation(value)
        else -> value.toString()
    }

    private fun connectedConfig(): RemoteConfig? = firebase?.app?.let(RemoteConfig::getInstance)

    private fun config(): RemoteConfig =
        RemoteConfig.getInstance(checkNotNull(firebase) { "Set the Firebase property" }.requireApp())

    private companion object {
        const val DEFAULT_FETCH_INTERVAL = 43200
        val TRUE_TEXTS = setOf("1", "true", "t", "yes", "y", "on")
        val FALSE_TEXTS = setOf("0", "false", "f", "no", "n", "off")
    }
}
