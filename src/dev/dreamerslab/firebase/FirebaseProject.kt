package dev.dreamerslab.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.json.JSONObject

internal object FirebaseProject {

    fun options(googleServices: String, packageName: String): FirebaseOptions {
        val config = JSONObject(googleServices)
        val project = config.getJSONObject("project_info")
        val clients = config.getJSONArray("client").let { array -> List(array.length(), array::getJSONObject) }
        require(clients.isNotEmpty()) { "The google-services.json file has no Android app" }
        val client = clients.firstOrNull { it.packageName() == packageName } ?: clients.first()
        return FirebaseOptions.Builder()
            .setApplicationId(client.getJSONObject("client_info").getString("mobilesdk_app_id"))
            .setApiKey(client.getJSONArray("api_key").getJSONObject(0).getString("current_key"))
            .setProjectId(project.getString("project_id"))
            .setGcmSenderId(project.getString("project_number"))
            .setStorageBucket(project.optString("storage_bucket").ifEmpty { null })
            .build()
    }

    fun start(context: Context, options: FirebaseOptions): FirebaseApp {
        val running = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: return FirebaseApp.initializeApp(context, options)
        check(running.options == options) { "Firebase is already connected to project ${running.options.projectId}" }
        return running
    }

    private fun JSONObject.packageName() =
        getJSONObject("client_info").getJSONObject("android_client_info").getString("package_name")
}
