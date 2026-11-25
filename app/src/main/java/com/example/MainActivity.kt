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
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedSoft
import com.example.ui.theme.MetaCheckTheme
import com.example.ui.viewmodel.MainViewModel
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
            SetStatusBarAppearance(lightBackground = true)
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

    val viewModel: MainViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                MainViewModel(
                    repository = AppRepository(app),
                    preferences = AppPreferences(app),
                    currentUserId = user.uid,
                    currentUserName = user.displayName ?: user.email?.substringBefore("@") ?: "Usuario MA",
                    currentUserEmail = user.email,
                    isEmailVerified = user.isEmailVerified
                )
            }
        }
    )

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
    val closingAttachmentDraft by viewModel.closingAttachmentDraft.collectAsStateWithLifecycle()

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
        SetStatusBarAppearance(lightBackground = true)
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

    SetStatusBarAppearance(lightBackground = false)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BrandNavy,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = {
                        Column {
                            MueblesAmericaLogo(
                                globeSize = 23.dp,
                                fontSize = 16.sp,
                                showTrademark = false
                            )
                            Text(
                                text = stringResource(R.string.brand_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.92f)
                            )
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

                // Sub-header strip inspired by Muebles América SEV palette (Red role tag + White branch strip)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .background(BrandRed)
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (isAdmin) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = if (isAdmin) "Admin" else "Sesión",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.reopenBranchSelection() }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag("switch_branch_pill"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = "Cambiar sucursal",
                                tint = BrandNavy,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = branchLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = BrandNavy
                            )
                        }
                        Text(
                            text = "Cambiar",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandNavy.copy(alpha = 0.72f)
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = BorderSubtle)
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(thickness = 1.dp, color = BorderSubtle)
                NavigationBar(
                    containerColor = Color.White,
                    contentColor = BrandNavy
                ) {
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
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandNavy,
                                selectedTextColor = BrandNavy,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                        selectedMetric = selectedMetric,
                        chartSeries = chartSeries,
                        showDailyBriefing = showDailyBriefing,
                        closingAttachmentDraft = closingAttachmentDraft,
                        onUpdateAttachmentDraft = { viewModel.updateClosingAttachmentDraft(it) },
                        onLoadEntryAttachmentChunks = { entry -> viewModel.loadClosingAttachmentChunks(entry) },
                        onDismissBriefing = { viewModel.dismissDailyBriefingForToday() },
                        onSelectMetric = { viewModel.selectMetric(it) },
                        onSaveDailyClosing = { qId, g, c, nc, ma, req, notes, draft ->
                            viewModel.saveDailyClosingSales(
                                quotaId = qId,
                                globalSales = g,
                                creditSales = c,
                                newCustomers = nc,
                                appMaOperations = ma,
                                newCustomerRequests = req,
                                notes = notes,
                                attachmentDraft = draft
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

@Composable
private fun SetStatusBarAppearance(lightBackground: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightBackground
            controller.isAppearanceLightNavigationBars = true
        }
    }
}
