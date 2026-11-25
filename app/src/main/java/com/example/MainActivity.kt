package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firestore.FirestoreProvider
import com.example.data.local.AppPreferences
import com.example.data.repository.AppRepository
import com.example.data.repository.AuthRepository
import com.example.notifications.NotificationHelper
import com.example.ui.components.MueblesAmericaLogo
import com.example.ui.components.SplashLaunchScreen
import com.example.ui.screens.AdminEvidencesScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BranchSelectionScreen
import com.example.ui.screens.HomeSalesScreen
import com.example.ui.screens.NotificationsSheet
import com.example.ui.screens.TasksChecklistScreen
import com.example.ui.screens.TeamAdminScreen
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedSoft
import com.example.ui.theme.MetaCheckTheme
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.NetworkMonitor
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FirestoreProvider.getInstance(applicationContext)
        NotificationHelper.ensureChannel(this)
        setContent {
            MetaCheckTheme {
                AppRoot()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Inicio", Icons.Filled.Assessment, Icons.Outlined.Assessment),
    TASKS("Tareas", Icons.Filled.Checklist, Icons.Outlined.Checklist),
    EVIDENCES("Evidencias", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    TEAM("Equipo", Icons.Filled.Group, Icons.Outlined.Group)
}

@Composable
private fun AppRoot(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showSplash by remember { mutableStateOf(true) }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        AuthRepository.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = {},
            onUnauthenticated = {}
        )
        delay(950L)
        showSplash = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val user = currentUser
        if (user == null) {
            AuthScreen(onAuthSuccess = {})
        } else {
            key(user.uid) {
                AuthenticatedApp(
                    user = user,
                    onSignOut = {
                        AuthRepository.signOut(credentialManager = credentialManager, scope = scope)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SplashLaunchScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedApp(
    user: FirebaseUser,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val networkMonitor = remember(context.applicationContext) {
        NetworkMonitor(context.applicationContext)
    }
    DisposableEffect(networkMonitor) {
        onDispose { networkMonitor.unregister() }
    }

    val viewModel: MainViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                MainViewModel(
                    repository = AppRepository(app, networkMonitor),
                    preferences = AppPreferences(app),
                    connectivityChecker = networkMonitor,
                    currentUserId = user.uid,
                    currentUserName = user.displayName ?: user.email?.substringBefore("@") ?: "Usuario MA",
                    currentUserEmail = user.email,
                    isEmailVerified = user.isEmailVerified
                )
            }
        }
    )

    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingTaskIds by viewModel.pendingTaskIds.collectAsStateWithLifecycle()
    val pendingSalesEntryIds by viewModel.pendingSalesEntryIds.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val lastCachedBranch by viewModel.lastCachedBranch.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdminState.collectAsStateWithLifecycle()
    val userProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val branchConfirmed by viewModel.branchConfirmedThisSession.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsState.collectAsStateWithLifecycle()
    val alertedIds by viewModel.alertedNotificationIds.collectAsStateWithLifecycle()
    val projectionState by viewModel.projectionSummaryState.collectAsStateWithLifecycle()
    val selectedMetric by viewModel.selectedMetric.collectAsStateWithLifecycle()
    val chartSeries by viewModel.selectedChartSeries.collectAsStateWithLifecycle()
    val showDailyBriefing by viewModel.showFirstEntryBriefingToday.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val evidencesState by viewModel.evidencesState.collectAsStateWithLifecycle()
    val teamUsers by viewModel.teamUsers.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(branchConfirmed) {
        if (branchConfirmed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.canPostNotifications(context)
        ) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(notifications, alertedIds) {
        val freshUnread = notifications.filter { !it.acknowledged && it.id !in alertedIds }
        if (freshUnread.isNotEmpty()) {
            freshUnread.forEach { notif ->
                NotificationHelper.showExtraordinaryTaskNotification(
                    context = context,
                    notificationId = notif.id,
                    title = notif.title,
                    message = notif.message
                )
            }
            viewModel.markNotificationsAlerted(freshUnread.map { it.id })
        }
    }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearActionMessage()
    }

    if (!isAdmin && (currentTab == AppTab.EVIDENCES || currentTab == AppTab.TEAM)) {
        currentTab = AppTab.HOME
    }

    if (!branchConfirmed) {
        BranchSelectionScreen(
            initialBranch = userProfile?.branchName ?: lastCachedBranch,
            isAdmin = isAdmin,
            userEmail = user.email,
            isBusy = isBusy,
            onConfirmBranch = { branch -> viewModel.confirmBranchSelection(branch) },
            onSignOut = onSignOut
        )
        return
    }

    if (currentTab != AppTab.HOME) {
        BackHandler { currentTab = AppTab.HOME }
    }

    val unreadCount = remember(notifications) { notifications.count { !it.acknowledged } }
    val branchLabel = userProfile?.effectiveBranchLabel ?: "Sucursal"

    val visibleTabs = remember(isAdmin) {
        if (isAdmin) listOf(AppTab.HOME, AppTab.TASKS, AppTab.EVIDENCES, AppTab.TEAM)
        else listOf(AppTab.HOME, AppTab.TASKS)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    Column {
                        MueblesAmericaLogo(
                            globeSize = 22.dp,
                            fontSize = 15.sp,
                            showTrademark = false
                        )
                        Text(
                            text = stringResource(R.string.brand_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandNavy
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clickable { viewModel.reopenBranchSelection() }
                                    .testTag("switch_branch_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Storefront,
                                        contentDescription = "Cambiar sucursal",
                                        tint = BrandNavy,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = branchLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandNavy
                                    )
                                }
                            }

                            Surface(
                                color = if (isAdmin) BrandRedSoft else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (isAdmin) {
                                        Icon(
                                            imageVector = Icons.Filled.AdminPanelSettings,
                                            contentDescription = null,
                                            tint = BrandRed,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isAdmin) "Admin" else "Empleado",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isAdmin) BrandRed else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showNotificationsSheet = true },
                        modifier = Modifier.testTag("notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge(containerColor = BrandRed, contentColor = Color.White) {
                                        Text(unreadCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Notificaciones"
                            )
                        }
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Cerrar sesión"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                visibleTabs.forEach { tab ->
                    val selected = tab == currentTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isOnline || pendingSyncCount > 0) {
                Surface(
                    color = if (!isOnline) WarningAmberSoft else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offline_sync_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (!isOnline) Icons.Filled.CloudOff else Icons.Filled.CloudSync,
                            contentDescription = null,
                            tint = if (!isOnline) WarningAmber else BrandNavy,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when {
                                !isOnline && pendingSyncCount > 0 ->
                                    "Sin conexión · $pendingSyncCount cambio(s) en caché listos para sincronizar"
                                !isOnline ->
                                    "Sin conexión · Modo local activo (puedes marcar tareas y tomar fotos)"
                                else ->
                                    "Sincronizando $pendingSyncCount cambio(s) con el servidor..."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (!isOnline) WarningAmber else BrandNavy
                        )
                    }
                }
            }

            if (unreadCount > 0) {
                Surface(
                    onClick = { showNotificationsSheet = true },
                    color = BrandRedSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tienes $unreadCount tarea(s) extraordinaria(s) nueva(s)",
                                style = MaterialTheme.typography.labelMedium,
                                color = BrandRed
                            )
                        }
                        Text(
                            text = "Ver",
                            style = MaterialTheme.typography.labelLarge,
                            color = BrandRed
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    AppTab.HOME -> HomeSalesScreen(
                        branchLabel = branchLabel,
                        isAdmin = isAdmin,
                        isBusy = isBusy,
                        projectionState = projectionState,
                        pendingSalesEntryIds = pendingSalesEntryIds,
                        selectedMetric = selectedMetric,
                        chartSeries = chartSeries,
                        showDailyBriefing = showDailyBriefing,
                        onDismissBriefing = { viewModel.dismissDailyBriefingForToday() },
                        onSelectMetric = { viewModel.selectMetric(it) },
                        onSaveDailyClosing = { qId, g, c, nc, ma, req, notes, attName, attMime, attBase64 ->
                            viewModel.saveDailyClosingSales(
                                qId,
                                g,
                                c,
                                nc,
                                ma,
                                req,
                                notes,
                                attName,
                                attMime,
                                attBase64
                            )
                        },
                        onSaveBiweeklyQuota = { t, s, e, d, g, c, nc, ma, req, onDone ->
                            viewModel.saveBiweeklyQuota(t, s, e, d, g, c, nc, ma, req, onDone)
                        }
                    )

                    AppTab.TASKS -> TasksChecklistScreen(
                        isAdmin = isAdmin,
                        isBusy = isBusy,
                        tasksState = tasksState,
                        pendingTaskIds = pendingTaskIds,
                        evidencesState = evidencesState,
                        teamUsers = teamUsers,
                        currentUserProfile = userProfile,
                        onCreateTask = { assignees, title, desc, type, priority, dueDate, dueTime, deadlineLabel, onSuccess ->
                            viewModel.createTask(assignees, title, desc, type, priority, dueDate, dueTime, deadlineLabel, onSuccess)
                        },
                        onCompleteTaskWithEvidence = { task, base64, onSuccess ->
                            viewModel.completeTaskWithEvidence(task, base64, onSuccess)
                        }
                    )

                    AppTab.EVIDENCES -> AdminEvidencesScreen(evidencesState = evidencesState)

                    AppTab.TEAM -> TeamAdminScreen(
                        currentUserId = user.uid,
                        teamUsers = teamUsers,
                        isBusy = isBusy,
                        onToggleAdmin = { target, makeAdmin ->
                            viewModel.toggleUserAdminRole(target, makeAdmin)
                        },
                        onSeedChecklist = { target ->
                            viewModel.seedDailyChecklist(target)
                        }
                    )
                }
            }
        }
    }

    if (showNotificationsSheet) {
        NotificationsSheet(
            notifications = notifications,
            onAcknowledge = { id -> viewModel.acknowledgeNotification(id) },
            onOpenTasks = { currentTab = AppTab.TASKS },
            onDismiss = { showNotificationsSheet = false }
        )
    }
}
