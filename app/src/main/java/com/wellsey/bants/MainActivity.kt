package com.wellsey.bants

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wellsey.bants.ui.BantsRoot
import com.wellsey.bants.ui.BantsTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : FragmentActivity() {
    /** Set when a notification is tapped, so the app opens that chat. */
    private val openConversation = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        // With the lock on, don't show chats in the recent-apps preview.
        if (AppLock.enabled && android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        setContent {
            BantsTheme {
                val locked by AppLock.locked.collectAsState()
                if (locked) {
                    com.wellsey.bants.ui.LockScreen(onUnlock = { AppLock.prompt(this) })
                    androidx.compose.runtime.LaunchedEffect(Unit) { AppLock.prompt(this@MainActivity) }
                } else {
                    BantsRoot(openConversation)
                }
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
