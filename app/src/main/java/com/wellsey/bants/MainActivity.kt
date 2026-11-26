package com.wellsey.bants

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wellsey.bants.ui.BantsRoot
import com.wellsey.bants.ui.BantsTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Set when a notification is tapped, so the app opens that chat. */
    private val openConversation = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            BantsTheme {
                BantsRoot(openConversation)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_CONVERSATION, -1L) ?: -1L
        if (id > 0) openConversation.value = id
    }

    companion object {
        const val EXTRA_CONVERSATION = "conversationId"
    }
}
