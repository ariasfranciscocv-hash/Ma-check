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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firestore.FirestoreProvider
import com.example.data.local.AppPreferences
import com.example.data.model.BranchOptions
import com.example.data.model.UiState
import com.example.data.repository.AppRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.EmployeeIdentityRepository
import com.example.data.repository.QuotaRepositoryExtensions
import com.example.notifications.NotificationHelper
import com.example.ui.components.MueblesAmericaLogo
import com.example.ui.components.SplashLaunchScreen
import com.example.ui.screens.AdminEvidencesScreen
import com.example.ui.screens.AdminQuotasScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.EmployeeIdentityScreen
import com.example.ui.screens.EmployeeProfileModalDialog
import com.example.ui.screens.EmployeeProfileTabScreen
import com.example.ui.screens.HomeSalesScreen
import com.example.ui.screens.NotificationsSheet
import com.example.ui.screens.TasksChecklistScreen
import com.example.ui.screens.TeamAdminScreen
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedSoft
import com.example.ui.theme.MetaCheckTheme
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.AppCrashGuard
import com.example.utils.TechnicalErrorReport
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCrashGuard.install(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        runCatching { FirestoreProvider.getInstance(applicationContext) }
            .onFailure { AppCrashGuard.reportNonFatal("Error al inicializar Firestore", it) }
        runCatching { NotificationHelper.ensureChannel(this) }
            .onFailure { AppCrashGuard.reportNonFatal("Error al crear canal de notificaciones", it) }
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
    QUOTAS("Cuotas", Icons.Filled.EditCalendar, Icons.Outlined.EditCalendar),
    MY_CARD("Mi Ficha", Icons.Filled.Badge, Icons.Outlined.Badge),
    EVIDENCES("Evidencias", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    TEAM("Equipo", Icons.Filled.Group, Icons.Outlined.Group)
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember {
        runCatching { Firebase.auth }
            .onFailure { AppCrashGuard.reportNonFatal("Error al inicializar Firebase Auth", it) }
            .getOrNull()
    }
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }
            .onFailure { AppCrashGuard.reportNonFatal("Error al inicializar CredentialManager", it) }
            .getOrNull()
    }

    var showSplash by remember { mutableStateOf(true) }
    val authFlow = remember(auth) { auth?.authStateFlow() ?: flowOf(null) }
    val initialUser = remember(auth) { runCatching { auth?.currentUser }.getOrNull() }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = initialUser)
    val crashReport by AppCrashGuard.errorState.collectAsStateWithLifecycle()

    LaunchedEffect(credentialManager) {
        if (credentialManager != null) {
            runCatching {
                AuthRepository.attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    scope = scope,
                    onAuthSuccess = {},
                    onUnauthenticated = {}
                )
            }.onFailure {
                AppCrashGuard.reportNonFatal("Error en inicio de sesión automático", it)
            }
        }
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
                        if (credentialManager != null) {
                            AuthRepository.signOut(credentialManager = credentialManager, scope = scope)
                        } else {
                            runCatching { auth?.signOut() }
                        }
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

        crashReport?.let { report ->
            TechnicalErrorOverlay(
                report = report,
                onDismiss = { AppCrashGuard.clearError() }
            )
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
                    isEmailVerified = user.isEmailVerified,
                    quotaExtensions = QuotaRepositoryExtensions(app),
                    identityRepository = EmployeeIdentityRepository(app)
                )
            }
        }
    )

    val isAdmin by viewModel.isAdminState.collectAsStateWithLifecycle()
    val isRoleResolved by viewModel.isRoleResolved.collectAsStateWithLifecycle()
    val isIdentityResolved by viewModel.isEmployeeIdentityResolved.collectAsStateWithLifecycle()
    val employeeIdentity by viewModel.currentEmployeeIdentity.collectAsStateWithLifecycle()
    val employeeIdentities by viewModel.employeeIdentities.collectAsStateWithLifecycle()
    val selectedBranch by viewModel.selectedBranch.collectAsStateWithLifecycle()
    val userProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val branchConfirmed by viewModel.branchConfirmedThisSession.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val technicalError by viewModel.technicalErrorState.collectAsStateWithLifecycle()
    val crashReport by AppCrashGuard.errorState.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsState.collectAsStateWithLifecycle()
    val alertedIds by viewModel.alertedNotificationIds.collectAsStateWithLifecycle()
    val projectionState by viewModel.projectionSummaryState.collectAsStateWithLifecycle()
    val adminGeneralSummary by viewModel.adminGeneralSummaryState.collectAsStateWithLifecycle()
    val selectedMetric by viewModel.selectedMetric.collectAsStateWithLifecycle()
    val chartSeries by viewModel.selectedChartSeries.collectAsStateWithLifecycle()
    val showDailyBriefing by viewModel.showFirstEntryBriefingToday.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val dailyTaskTemplates by viewModel.dailyTaskTemplatesState.collectAsStateWithLifecycle()
    val scheduleNowMillis by viewModel.scheduleTickerState.collectAsStateWithLifecycle()
    val activeQuotaState by viewModel.activeQuotaState.collectAsStateWithLifecycle()
    val activeQuota = (activeQuotaState as? UiState.Success)?.data
    val activeQuotasState by viewModel.activeQuotasState.collectAsStateWithLifecycle()
    val activeQuotas = (activeQuotasState as? UiState.Success)?.data ?: emptyList()
    val branchSalesSummaries by viewModel.branchSalesSummariesState.collectAsStateWithLifecycle()
    val evidencesState by viewModel.evidencesState.collectAsStateWithLifecycle()
    val teamUsers by viewModel.teamUsers.collectAsStateWithLifecycle()
    val closingAttachmentDraft by viewModel.closingAttachmentDraft.collectAsStateWithLifecycle()
    val employeeParticipationStats by viewModel.employeeParticipationStatsState.collectAsStateWithLifecycle()
    val activeCycleInfo by viewModel.activeCycleInfoState.collectAsStateWithLifecycle()
    val cycleArchives by viewModel.cycleArchivesState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showAdminBranchMenu by remember { mutableStateOf(false) }
    var showEmployeeProfileModal by remember { mutableStateOf(false) }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkDailyScheduleAndClosingReset()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(scheduleNowMillis, isRoleResolved, isAdmin, userProfile?.branchName, employeeIdentity?.branchName) {
        if (isRoleResolved) {
            viewModel.checkDailyScheduleAndClosingReset(scheduleNowMillis)
        }
    }

    LaunchedEffect(branchConfirmed, isAdmin, isRoleResolved, employeeIdentity?.isComplete) {
        if (isRoleResolved && (isAdmin || employeeIdentity?.isComplete == true) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
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

    if (!isAdmin && (currentTab == AppTab.QUOTAS || currentTab == AppTab.EVIDENCES || currentTab == AppTab.TEAM)) {
        currentTab = AppTab.HOME
    } else if (isAdmin && currentTab == AppTab.MY_CARD) {
        currentTab = AppTab.HOME
    }

    if (!isRoleResolved || (!isAdmin && !isIdentityResolved)) {
        SetStatusBarAppearance(lightBackground = true)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = BrandNavy)
        }
        return
    }

    val currentIdentity = employeeIdentity
    if (!isAdmin && (currentIdentity == null || !currentIdentity.isComplete)) {
        SetStatusBarAppearance(lightBackground = true)
        EmployeeIdentityScreen(
            existingIdentity = currentIdentity,
            userEmail = user.email,
            defaultNameHint = user.displayName,
            isBusy = isBusy,
            onRegisterIdentity = { fullName, payrollNumber, branchName, onErr ->
                viewModel.registerEmployeeIdentity(
                    fullName = fullName,
                    payrollNumber = payrollNumber,
                    branchName = branchName,
                    onError = onErr
                )
            },
            onAssignReleasedBranch = { branchName, onErr ->
                viewModel.assignBranchToReleasedIdentity(
                    branchName = branchName,
                    onError = onErr
                )
            },
            onSignOut = onSignOut
        )
        return
    }

    if (currentTab != AppTab.HOME) {
        BackHandler { currentTab = AppTab.HOME }
    }

    val unreadCount = remember(notifications) { notifications.count { !it.acknowledged } }
    val branchLabel = selectedBranch.ifBlank {
        if (isAdmin) BranchOptions.ALL_BRANCHES_OPTION
        else currentIdentity?.branchName?.takeIf { it.isNotBlank() }
            ?: userProfile?.effectiveBranchLabel?.takeIf { it.isNotBlank() }
            ?: "Sucursal"
    }

    val visibleTabs = remember(isAdmin) {
        if (isAdmin) listOf(AppTab.HOME, AppTab.TASKS, AppTab.QUOTAS, AppTab.EVIDENCES, AppTab.TEAM)
        else listOf(AppTab.HOME, AppTab.TASKS, AppTab.MY_CARD)
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
                        val canOpenProfile = !isAdmin && currentIdentity != null
                        Column(
                            modifier = Modifier
                                .let { base ->
                                    if (canOpenProfile) {
                                        base
                                            .clickable { showEmployeeProfileModal = true }
                                            .testTag("topbar_open_profile_area")
                                    } else {
                                        base
                                    }
                                }
                        ) {
                            MueblesAmericaLogo(
                                globeSize = 23.dp,
                                fontSize = 16.sp,
                                showTrademark = false
                            )
                            Text(
                                text = if (!isAdmin && currentIdentity != null) {
                                    "Nómina: ${currentIdentity.displayPayroll} | ${currentIdentity.branchName ?: branchLabel}"
                                } else {
                                    stringResource(R.string.brand_subtitle)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (!isAdmin && currentIdentity != null) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.95f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("topbar_identity_subtitle")
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
                            .let { base ->
                                if (!isAdmin && currentIdentity != null) {
                                    base
                                        .clickable { showEmployeeProfileModal = true }
                                        .testTag("subheader_payroll_chip")
                                } else {
                                    base
                                }
                            }
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
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Badge,
                                contentDescription = "Ver mi ficha",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = if (isAdmin) "Admin" else "Nómina ${currentIdentity?.displayPayroll.orEmpty()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        val rowModifier = Modifier
                            .fillMaxWidth()
                            .let { base ->
                                when {
                                    isAdmin -> base.clickable { showAdminBranchMenu = true }
                                    currentIdentity != null -> base.clickable { showEmployeeProfileModal = true }
                                    else -> base
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag("switch_branch_pill")

                        Row(
                            modifier = rowModifier,
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Storefront,
                                    contentDescription = if (isAdmin) "Cambiar sucursal" else "Sucursal asignada",
                                    tint = BrandNavy,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (isAdmin) branchLabel else "${currentIdentity?.fullName ?: "Empleado"} · $branchLabel",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = BrandNavy,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isAdmin) {
                                    Text(
                                        text = "Filtrar",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandNavy.copy(alpha = 0.72f)
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Seleccionar sucursal",
                                        tint = BrandNavy.copy(alpha = 0.72f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Text(
                                        text = "Mi Ficha",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandNavy.copy(alpha = 0.8f)
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Ficha de solo lectura",
                                        tint = BrandNavy.copy(alpha = 0.65f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        if (isAdmin) {
                            DropdownMenu(
                                expanded = showAdminBranchMenu,
                                onDismissRequest = { showAdminBranchMenu = false }
                            ) {
                                val options = listOf(BranchOptions.ALL_BRANCHES_OPTION) + BranchOptions.ALL_BRANCHES
                                options.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option,
                                                fontWeight = if (option == branchLabel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (option == branchLabel) BrandRed else BrandNavy
                                            )
                                        },
                                        onClick = {
                                            viewModel.selectDashboardBranch(option)
                                            showAdminBranchMenu = false
                                        }
                                    )
                                }
                            }
                        }
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
                            label = {
                                Text(
                                    text = tab.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
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
                        employeeIdentity = currentIdentity,
                        projectionState = projectionState,
                        adminGeneralSummary = adminGeneralSummary,
                        selectedMetric = selectedMetric,
                        chartSeries = chartSeries,
                        showDailyBriefing = showDailyBriefing,
                        closingAttachmentDraft = closingAttachmentDraft,
                        selectedBranchFilter = branchLabel,
                        activeQuotas = activeQuotas,
                        branchSalesSummaries = branchSalesSummaries,
                        activeCycleInfo = activeCycleInfo,
                        onSelectBranchFilter = { branch -> viewModel.selectDashboardBranch(branch) },
                        onOpenQuotasTab = if (isAdmin) { { currentTab = AppTab.QUOTAS } } else null,
                        onCloseAndResetBiweeklyCycle = if (isAdmin) {
                            { onDone -> viewModel.closeAndResetBiweeklyCycle { onDone() } }
                        } else null,
                        onUpdateAttachmentDraft = { viewModel.updateClosingAttachmentDraft(it) },
                        onLoadEntryAttachmentChunks = { entry -> viewModel.loadClosingAttachmentChunks(entry) },
                        onDismissBriefing = { viewModel.dismissDailyBriefingForToday() },
                        onSelectMetric = { viewModel.selectMetric(it) },
                        onUpdateGeneralIndicatorTarget = { metric, newTarget, onDone ->
                            viewModel.updateGeneralIndicatorTarget(metric, newTarget, onDone)
                        },
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
                        onSaveBiweeklyQuota = { t, s, e, d, g, c, nc, ma, req, branch, applyAll, onDone ->
                            viewModel.saveBiweeklyQuota(
                                title = t,
                                startDate = s,
                                endDate = e,
                                totalDays = d,
                                targetGlobalSales = g,
                                targetCreditSales = c,
                                targetNewCustomers = nc,
                                targetAppMaOperations = ma,
                                targetNewCustomerRequests = req,
                                branchName = branch,
                                applyToAllIndividualBranches = applyAll,
                                onSuccess = onDone
                            )
                        }
                    )

                    AppTab.TASKS -> TasksChecklistScreen(
                        isAdmin = isAdmin,
                        isBusy = isBusy,
                        nowMillis = scheduleNowMillis,
                        tasksState = tasksState,
                        dailyTaskTemplates = dailyTaskTemplates,
                        evidencesState = evidencesState,
                        teamUsers = teamUsers,
                        currentUserProfile = userProfile,
                        employeeIdentity = currentIdentity,
                        activeQuota = activeQuota,
                        onCreateTask = { assignees, title, desc, type, priority, dueDate, dueTime, deadlineLabel, onSuccess ->
                            viewModel.createTask(assignees, title, desc, type, priority, dueDate, dueTime, deadlineLabel, onSuccess)
                        },
                        onSaveDailyTaskTemplate = { existingId, title, desc, priority, daysOfWeek, targetBranches, dueTime, deadlineLabel, dueDayOffset, onSuccess ->
                            viewModel.saveDailyTaskTemplate(
                                existingTemplateId = existingId,
                                title = title,
                                description = desc,
                                priority = priority,
                                daysOfWeek = daysOfWeek,
                                targetBranches = targetBranches,
                                dueTime = dueTime,
                                deadlineLabel = deadlineLabel,
                                dueDayOffset = dueDayOffset,
                                onSuccess = onSuccess
                            )
                        },
                        onDeleteDailyTaskTemplate = { template, task, removeBranchOnly, onSuccess ->
                            viewModel.deleteDailyTaskTemplatePermanently(
                                template = template,
                                task = task,
                                removeBranchOnly = removeBranchOnly,
                                onSuccess = onSuccess
                            )
                        },
                        onCompleteTaskWithEvidenceFiles = { task, files, onError, onSuccess ->
                            viewModel.completeTaskWithEvidenceFiles(
                                task = task,
                                files = files,
                                onError = onError,
                                onSuccess = onSuccess
                            )
                        },
                        onConfirmBranchResetForQuota = { branchName, branchUsers ->
                            viewModel.confirmBranchChecklistResetForQuota(branchName, branchUsers)
                        },
                        onLoadEvidenceFiles = { evidence ->
                            viewModel.loadTaskEvidenceFiles(evidence)
                        },
                        onUpdateDailyTask = { task, title, description, priority, dueTime, deadlineLabel, applyToAll, onSuccess ->
                            viewModel.updateDailyTask(
                                task = task,
                                title = title,
                                description = description,
                                priority = priority,
                                dueTime = dueTime,
                                deadlineLabel = deadlineLabel,
                                applyToAllBranchesSameDay = applyToAll,
                                onSuccess = onSuccess
                            )
                        },
                        onDeleteDailyTask = { task, deleteAll, onSuccess ->
                            viewModel.deleteDailyTask(
                                task = task,
                                deleteAllBranchesSameDay = deleteAll,
                                onSuccess = onSuccess
                            )
                        }
                    )

                    AppTab.QUOTAS -> AdminQuotasScreen(
                        activeQuotas = activeQuotas,
                        adminGeneralSummary = adminGeneralSummary,
                        branchSalesSummaries = branchSalesSummaries,
                        activeCycleInfo = activeCycleInfo,
                        cycleArchives = cycleArchives,
                        isBusy = isBusy,
                        onCloseAndResetBiweeklyCycle = { onDone ->
                            viewModel.closeAndResetBiweeklyCycle { onDone() }
                        },
                        onSaveBiweeklyQuota = { t, s, e, d, g, c, nc, ma, req, branch, applyAll, onDone ->
                            viewModel.saveBiweeklyQuota(
                                title = t,
                                startDate = s,
                                endDate = e,
                                totalDays = d,
                                targetGlobalSales = g,
                                targetCreditSales = c,
                                targetNewCustomers = nc,
                                targetAppMaOperations = ma,
                                targetNewCustomerRequests = req,
                                branchName = branch,
                                applyToAllIndividualBranches = applyAll,
                                onSuccess = onDone
                            )
                        }
                    )

                    AppTab.MY_CARD -> {
                        if (currentIdentity != null) {
                            EmployeeProfileTabScreen(
                                identity = currentIdentity,
                                stats = employeeParticipationStats
                            )
                        }
                    }

                    AppTab.EVIDENCES -> AdminEvidencesScreen(
                        evidencesState = evidencesState,
                        onLoadEvidenceFiles = { evidence ->
                            viewModel.loadTaskEvidenceFiles(evidence)
                        }
                    )

                    AppTab.TEAM -> TeamAdminScreen(
                        currentUserId = user.uid,
                        teamUsers = teamUsers,
                        employeeIdentities = employeeIdentities,
                        activeQuota = activeQuota,
                        activeQuotas = activeQuotas,
                        branchSalesSummaries = branchSalesSummaries,
                        isBusy = isBusy,
                        onOpenQuotasTab = { currentTab = AppTab.QUOTAS },
                        onReleaseEmployeeBranch = { identity ->
                            viewModel.releaseEmployeeBranch(identity)
                        },
                        onDeleteEmployeeIdentity = { identity ->
                            viewModel.deleteEmployeeIdentityCompletely(identity)
                        },
                        onToggleAdmin = { target, makeAdmin ->
                            viewModel.toggleUserAdminRole(target, makeAdmin)
                        },
                        onSeedChecklist = { target ->
                            viewModel.seedDailyChecklist(target)
                        },
                        onConfirmBranchResetForQuota = { branchName, branchUsers ->
                            viewModel.confirmBranchChecklistResetForQuota(branchName, branchUsers)
                        },
                        onLoadEntryAttachmentChunks = { entry ->
                            viewModel.loadClosingAttachmentChunks(entry)
                        },
                        onSaveBiweeklyQuota = { t, s, e, d, g, c, nc, ma, req, branch, applyAll, onDone ->
                            viewModel.saveBiweeklyQuota(
                                title = t,
                                startDate = s,
                                endDate = e,
                                totalDays = d,
                                targetGlobalSales = g,
                                targetCreditSales = c,
                                targetNewCustomers = nc,
                                targetAppMaOperations = ma,
                                targetNewCustomerRequests = req,
                                branchName = branch,
                                applyToAllIndividualBranches = applyAll,
                                onSuccess = onDone
                            )
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

    if (showEmployeeProfileModal && !isAdmin && currentIdentity != null) {
        EmployeeProfileModalDialog(
            identity = currentIdentity,
            stats = employeeParticipationStats,
            onDismiss = { showEmployeeProfileModal = false }
        )
    }

    if (technicalError != null && crashReport == null) {
        TechnicalErrorOverlay(
            report = TechnicalErrorReport(
                title = "Error inesperado en operación",
                message = technicalError.orEmpty(),
                stackTrace = "",
                threadName = "viewModelScope"
            ),
            onDismiss = { viewModel.clearTechnicalError() }
        )
    }
}

@Composable
private fun TechnicalErrorOverlay(
    report: TechnicalErrorReport,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(report.timestampMillis) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .systemBarsPadding()
            .padding(20.dp)
            .testTag("technical_error_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Error técnico",
                        tint = BrandRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = report.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandNavy
                        )
                        Text(
                            text = "Hilo: ${report.threadName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = BrandRedSoft,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            text = report.message,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandRed,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (report.stackTrace.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = report.stackTrace,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = BrandNavy,
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(report.fullTechnicalText))
                            copied = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_technical_error_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copiar detalle técnico",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copied) "Copiado" else "Copiar detalle")
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dismiss_technical_error_button")
                    ) {
                        Text("Continuar")
                    }
                }
            }
        }
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
