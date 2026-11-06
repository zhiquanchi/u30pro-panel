package com.u30pro.panel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.u30pro.panel.data.model.Station
import com.u30pro.panel.ui.screens.ApnScreen
import com.u30pro.panel.ui.screens.ConnectScreen
import com.u30pro.panel.ui.screens.DiagnosticsScreen
import com.u30pro.panel.ui.screens.ManageScreen
import com.u30pro.panel.ui.screens.OverviewScreen
import com.u30pro.panel.ui.screens.SettingsScreen
import com.u30pro.panel.ui.screens.SmsScreen
import com.u30pro.panel.ui.screens.StationsScreen

@Composable
fun PanelApp(state: PanelUiState, viewModel: PanelViewModel) {
    if (state.phase == Phase.Connect) {
        ConnectScreen(
            state = state,
            onConnect = viewModel::connect,
        )
    } else {
        MainShell(state = state, viewModel = viewModel)
    }
}

@Composable
private fun MainShell(state: PanelUiState, viewModel: PanelViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }

    BackHandler(enabled = tab == 2 && state.manageRoute != ManageRoute.ROOT) {
        viewModel.backToManage()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("状态") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = {
                        tab = 1
                        viewModel.refreshStations()
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("设备") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = {
                        tab = 2
                        viewModel.openManageRoot()
                    },
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    label = { Text("管理") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = { Text("诊断") },
                )
                NavigationBarItem(
                    selected = tab == 4,
                    onClick = { tab = 4 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("应用") },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> OverviewScreen(
                    state = state,
                    onRefresh = viewModel::refreshStations,
                )
                1 -> StationsScreen(
                    state = state,
                    onRefresh = viewModel::refreshStations,
                    onBlock = { viewModel.requestBlock(it, true) },
                    onUnblock = { viewModel.requestBlock(it, false) },
                    onRename = viewModel::requestRename,
                )
                2 -> when (state.manageRoute) {
                    ManageRoute.APN -> ApnScreen(
                        state = state,
                        onBack = viewModel::backToManage,
                        onRefresh = viewModel::refreshSettings,
                        onSave = viewModel::saveApn,
                        onDelete = viewModel::deleteApn,
                        onSetDefault = viewModel::setDefaultApn,
                    )
                    ManageRoute.SMS -> SmsScreen(
                        state = state,
                        onBack = viewModel::backToManage,
                        onRefresh = viewModel::refreshSms,
                        onSend = viewModel::sendSms,
                        onDelete = viewModel::deleteSms,
                        onMarkRead = viewModel::markSmsRead,
                    )
                    ManageRoute.ROOT -> ManageScreen(
                        state = state,
                        onRefresh = viewModel::refreshSettings,
                        onWrite = viewModel::requestSettingWrite,
                        onOpenApn = viewModel::openApn,
                        onOpenSms = viewModel::openSms,
                        onConfirmWrite = viewModel::confirmPendingWrite,
                        onDismissWrite = viewModel::dismissPendingWrite,
                    )
                }
                3 -> DiagnosticsScreen(
                    state = state,
                    onRerunProbe = viewModel::rerunProbe,
                    onExport = viewModel::exportDiagnostics,
                )
                else -> SettingsScreen(
                    state = state,
                    onPollInterval = viewModel::setPollInterval,
                    onLogout = viewModel::logout,
                )
            }
        }
    }

    state.blockTarget?.let { station ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBlockDialog,
            title = { Text(if (state.blockValue) "拉黑设备？" else "解除拉黑？") },
            text = {
                Text(
                    if (state.blockValue) {
                        "${station.displayName}（${station.mac}）会被加入 ACL 黑名单并立即断开，之后无法连接该 WiFi。"
                    } else {
                        "${station.displayName}（${station.mac}）会从 ACL 黑名单移除，可以重新连接。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmBlock) {
                    Text(if (state.blockValue) "拉黑" else "解黑")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBlockDialog) { Text("取消") }
            },
        )
    }

    state.renameTarget?.let { station ->
        RenameDialog(
            station = station,
            onDismiss = viewModel::dismissRenameDialog,
            onConfirm = viewModel::confirmRename,
        )
    }
}

@Composable
private fun RenameDialog(
    station: Station,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(station.mac) { mutableStateOf(station.hostname.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名设备") },
        text = {
            Column {
                Text(
                    text = station.mac,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("显示名称") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}