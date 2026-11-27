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
import com.wellsey.bants.data.HomeMode
import com.wellsey.bants.data.Servers
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface Screen {
    data object Home : Screen
    data class Chat(val id: Long, val jumpTo: Long? = null) : Screen
    data object NewChat : Screen
    data object AddContact : Screen
    data object Requests : Screen
    data object NewGroup : Screen
    data object Profile : Screen
    data class ChatInfo(val id: Long) : Screen
    data class Wallpaper(val id: Long) : Screen
    data class AddMembers(val id: Long) : Screen
    data object Archived : Screen
    data object Search : Screen
    data object Sounds : Screen
    data object Quality : Screen
    data object CallSettings : Screen
    // Servers
    data class Server(val id: Long) : Screen
    data class Channel(val id: Long) : Screen
    data class ServerMembers(val id: Long) : Screen
    data class ServerSettings(val id: Long) : Screen
    data class ServerRoles(val id: Long) : Screen
    data class RoleEdit(val serverId: Long, val roleId: Long) : Screen
    data class ServerProfile(val id: Long) : Screen
    data object Emojis : Screen
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

    val openChannel by Servers.openChannelRequest.collectAsState()
    LaunchedEffect(openChannel) {
        val id = openChannel ?: return@LaunchedEffect
        Servers.openChannelRequest.value = null
        val c = Servers.channel(id) ?: return@LaunchedEffect
        Servers.setMode(HomeMode.Servers)
        nav.home()
        nav.go(Screen.Server(c.serverId))
        nav.go(Screen.Channel(id))
    }

    AskForNotifications()
    val context = LocalContext.current
    LaunchedEffect(Unit) { Bants.notices.collect { toast(context, it) } }
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }

    when (val screen = nav.current) {
        Screen.Home -> {
            val mode by Servers.mode.collectAsState()
            val serversOn by Servers.available.collectAsState()
            if (serversOn && mode == HomeMode.Servers) ServersHomeScreen(nav) else HomeScreen(nav)
        }
        is Screen.Chat -> key(screen.id, screen.jumpTo) { ChatScreen(screen.id, nav, screen.jumpTo) }
        Screen.NewChat -> NewChatScreen(nav)
        Screen.AddContact -> AddContactScreen(nav)
        Screen.Requests -> RequestsScreen(nav)
        Screen.NewGroup -> NewGroupScreen(nav)
        Screen.Profile -> ProfileScreen(nav)
        is Screen.ChatInfo -> key(screen.id) { ChatInfoScreen(screen.id, nav) }
        is Screen.Wallpaper -> key(screen.id) { ChatStyleScreen(screen.id, nav) }
        is Screen.AddMembers -> key(screen.id) { AddMembersScreen(screen.id, nav) }
        Screen.Archived -> ArchivedScreen(nav)
        Screen.Search -> SearchScreen(nav)
        Screen.Sounds -> SoundsScreen(nav)
        Screen.Quality -> QualityScreen(nav)
        Screen.CallSettings -> CallSettingsScreen(nav)
        is Screen.Server -> key(screen.id) { ServerScreen(screen.id, nav) }
        is Screen.Channel -> key(screen.id) { ChannelScreen(screen.id, nav) }
        is Screen.ServerMembers -> key(screen.id) { ServerMembersScreen(screen.id, nav) }
        is Screen.ServerProfile -> key(screen.id) { ServerProfileScreen(screen.id, nav) }
        Screen.Emojis -> EmojisScreen(nav)
        is Screen.ServerSettings -> key(screen.id) { ServerSettingsScreen(screen.id, nav) }
        is Screen.ServerRoles -> key(screen.id) { ServerRolesScreen(screen.id, nav) }
        is Screen.RoleEdit -> key(screen.serverId, screen.roleId) { RoleEditScreen(screen.serverId, screen.roleId, nav) }
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
