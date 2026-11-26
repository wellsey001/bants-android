package com.wellsey.bants.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.wellsey.bants.data.Bants
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface Screen {
    data object Home : Screen
    data class Chat(val id: Long) : Screen
    data object NewChat : Screen
    data object AddContact : Screen
    data object Requests : Screen
    data object NewGroup : Screen
    data object Profile : Screen
    data class ChatInfo(val id: Long) : Screen
    data class AddMembers(val id: Long) : Screen
}

/** A tiny back stack: enough for this app without pulling in a navigation library. */
class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()

    fun go(screen: Screen) {
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun replace(screen: Screen) {
        stack[stack.lastIndex] = screen
    }

    fun home() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

@Composable
fun BantsRoot(openConversation: MutableStateFlow<Long?>) {
    val loggedIn by Bants.loggedIn.collectAsState()
    if (!loggedIn) {
        AuthScreen()
        return
    }

    val nav = remember { Navigator() }
    val open by openConversation.collectAsState()
    LaunchedEffect(open) {
        val id = open ?: return@LaunchedEffect
        nav.home()
        nav.go(Screen.Chat(id))
        openConversation.value = null
    }

    AskForNotifications()
    val context = LocalContext.current
    LaunchedEffect(Unit) { Bants.notices.collect { toast(context, it) } }
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }

    when (val screen = nav.current) {
        Screen.Home -> HomeScreen(nav)
        is Screen.Chat -> key(screen.id) { ChatScreen(screen.id, nav) }
        Screen.NewChat -> NewChatScreen(nav)
        Screen.AddContact -> AddContactScreen(nav)
        Screen.Requests -> RequestsScreen(nav)
        Screen.NewGroup -> NewGroupScreen(nav)
        Screen.Profile -> ProfileScreen(nav)
        is Screen.ChatInfo -> key(screen.id) { ChatInfoScreen(screen.id, nav) }
        is Screen.AddMembers -> key(screen.id) { AddMembersScreen(screen.id, nav) }
    }
}

@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
