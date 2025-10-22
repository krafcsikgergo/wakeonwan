package hu.krafcsikgergo.wakeonwan.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.krafcsikgergo.wakeonwan.services.receiver.Schedule
import hu.krafcsikgergo.wakeonwan.ui.composables.TopRow
import org.koin.androidx.compose.koinViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    mode: String = "receiver",
    serverId: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = koinViewModel<SchedulesViewModel>()
    val uiState = viewModel.uiState

    // Dialog state
    var showAddScheduleDialog by remember { mutableStateOf(false) }
    
    // Pull-to-refresh state
    val pullToRefreshState = rememberPullToRefreshState()

    // Handle toast messages
    uiState.lastOperationMessage?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .show()
            viewModel.clearOperationMessage()
        }
    }

    // Handle error messages
    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .show()
            viewModel.clearError()
        }
    }

    // Initialize ViewModel with mode and serverId
    LaunchedEffect(mode, serverId) {
        viewModel.initialize(mode, serverId)
    }

    Scaffold(
        topBar = {
            val title = if (uiState.mode == "sender" && uiState.serverName != null) {
                "Schedules - ${uiState.serverName}"
            } else {
                "Schedules"
            }
            
            TopRow(
                title = title,
                switchToText = "Go back",
                onNavigate = onBack,
                icon = Icons.Default.ArrowBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddScheduleDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Schedule",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = {
                viewModel.loadSchedules()
            },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                if (uiState.schedules.isEmpty()) {
                    EmptySchedulesState()
                } else {
                    SchedulesList(
                        schedules = uiState.schedules,
                        mode = uiState.mode,
                        onDeleteSchedule = { schedule ->
                            viewModel.deleteSchedule(schedule.id)
                        },
                        onToggleSchedule = { scheduleId ->
                            viewModel.toggleScheduleEnabled(scheduleId)
                        }
                    )
                }
            }
        }
    }

    // Add Schedule Dialog
    if (showAddScheduleDialog) {
        AddScheduleDialog(
            onDismiss = { showAddScheduleDialog = false },
            onConfirm = { time, turnOn, days ->
                viewModel.createSchedule(
                    time = time,
                    turnOn = turnOn,
                    days = days
                )
                showAddScheduleDialog = false
            }
        )
    }
}

@Composable
fun EmptySchedulesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Schedule,
            contentDescription = "No schedules",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No schedules yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the + button to create your first schedule",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SchedulesList(
    schedules: List<Schedule>,
    mode: String,
    onDeleteSchedule: (Schedule) -> Unit,
    onToggleSchedule: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        items(schedules) { schedule ->
            ScheduleCard(
                schedule = schedule,
                mode = mode,
                onDelete = { onDeleteSchedule(schedule) },
                onToggleEnabled = { onToggleSchedule(schedule.id) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleCard(
    schedule: Schedule,
    mode: String,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (schedule.enabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Top row: Action icon + Time/Action label + Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Action Icon (Wake/Sleep)
                Card(
                    modifier = Modifier.size(48.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (schedule.turnOn)
                            MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = if (schedule.enabled) 1f else 0.5f
                            )
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(
                                alpha = if (schedule.enabled) 1f else 0.5f
                            )
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (schedule.turnOn) Icons.Default.PowerSettingsNew else Icons.Default.PowerOff,
                            contentDescription = if (schedule.turnOn) "Wake Up" else "Shutdown",
                            tint = if (schedule.turnOn)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                    alpha = if (schedule.enabled) 1f else 0.5f
                                )
                            else
                                MaterialTheme.colorScheme.onErrorContainer.copy(
                                    alpha = if (schedule.enabled) 1f else 0.5f
                                ),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Time and Action Label
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Time
                    Text(
                        text = schedule.timeInLocalTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (schedule.enabled) 1f else 0.6f
                        )
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Action Label
                    Text(
                        text = if (schedule.turnOn) "Wake Up" else "Shutdown",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (schedule.turnOn)
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = if (schedule.enabled) 1f else 0.6f
                            )
                        else
                            MaterialTheme.colorScheme.error.copy(
                                alpha = if (schedule.enabled) 1f else 0.6f
                            ),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Delete Button - Larger size, closer to corner
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(44.dp)
                        .offset(x = 8.dp, y = (-8).dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Schedule",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Days Row - Full width
            DaysRow(selectedDays = schedule.days, enabled = schedule.enabled)

            Spacer(modifier = Modifier.height(16.dp))

            // Enable/Disable row (works in both receiver and sender modes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (schedule.enabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (schedule.enabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}

@Composable
fun DaysRow(selectedDays: List<Boolean>, enabled: Boolean = true) {
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        dayLabels.forEachIndexed { index, label ->
            DayChip(
                label = label,
                isSelected = index < selectedDays.size && selectedDays[index],
                enabled = enabled
            )
        }
    }
}

@Composable
fun DayChip(
    label: String,
    isSelected: Boolean,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier.size(32.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(
                alpha = if (enabled) 1f else 0.5f
            )
        else
            MaterialTheme.colorScheme.outline.copy(
                alpha = if (enabled) 0.3f else 0.15f
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimary.copy(
                        alpha = if (enabled) 1f else 0.6f
                    )
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.6f
                    ),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (time: LocalTime, turnOn: Boolean, days: List<Boolean>) -> Unit
) {
    var selectedTime by remember { mutableStateOf(LocalTime.of(7, 0)) }
    var turnOn by remember { mutableStateOf(true) }
    val selectedDays = remember { List(7) { mutableStateOf(false) } }
    var showTimePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Validation
    val isValid = selectedDays.any { it.value }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Schedule,
                contentDescription = "Add Schedule",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                "Add New Schedule",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Action Toggle Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Action",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (turnOn) Icons.Default.PowerSettingsNew else Icons.Default.PowerOff,
                                    contentDescription = null,
                                    tint = if (turnOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (turnOn) "Wake Up" else "Shutdown",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (turnOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Switch(
                                checked = turnOn,
                                onCheckedChange = { turnOn = it }
                            )
                        }
                    }
                }

                // Time Selection Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    onClick = { showTimePicker = true }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Time",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Days Selection Section
                Column {
                    Text(
                        "Days of Week",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DaySelectionRow(selectedDays = selectedDays)

                    if (!isValid) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Please select at least one day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        selectedTime,
                        turnOn,
                        selectedDays.map { it.value }
                    )
                },
                enabled = isValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerDialog = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                selectedTime = LocalTime.of(hourOfDay, minute)
                showTimePicker = false
            },
            selectedTime.hour,
            selectedTime.minute,
            true
        )

        timePickerDialog.setOnCancelListener { showTimePicker = false }
        timePickerDialog.setOnDismissListener { showTimePicker = false }

        DisposableEffect(Unit) {
            timePickerDialog.show()
            onDispose {
                timePickerDialog.dismiss()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySelectionRow(selectedDays: List<MutableState<Boolean>>) {
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val allSelected = selectedDays.all { it.value }
    val someSelected = selectedDays.any { it.value }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                onClick = {
                    val newValue = !allSelected
                    selectedDays.forEach { it.value = newValue }
                },
                label = {
                    Text(
                        "All",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                selected = allSelected,
                modifier = Modifier.height(36.dp)
            )
        }
        itemsIndexed(dayLabels) { index, label ->
            FilterChip(
                onClick = { selectedDays[index].value = !selectedDays[index].value },
                label = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                selected = selectedDays[index].value,
                modifier = Modifier.height(36.dp)
            )
        }
    }
}
