package dev.dreamerslab.firebase

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        FirebaseMessaging.active?.messageReceived(message)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        FirebaseMessaging.active?.newToken(token)
    }

    override fun onDeletedMessages() {
        FirebaseMessaging.active?.deletedMessages()
    }
}
