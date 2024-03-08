package hu.krafcsikgergo.wakeonwan


import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.krafcsikgergo.wakeonwan.services.ApiImplementation
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.NetworkManager
import hu.krafcsikgergo.wakeonwan.services.Schedule
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter


@Composable
fun SchedulesScreen(onBack: () -> Unit) {
    var turnOn by remember { mutableStateOf(false) }
    var allDaysSelected by remember { mutableStateOf(false) }
    val daysOfWeek = remember { List(7) { mutableStateOf(false) } }
    var time by remember { mutableStateOf(LocalTime.of(0, 0)) }
    val schedules = remember { mutableStateListOf<Schedule>() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Load schedules from API
    LaunchedEffect(Unit) {
        schedules.addAll(NetworkManager().getSchedules())
    }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        // Create a new schedule title
        Text(
            "Create a new schedule",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        // Switch for Turn On/Off
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Turn", modifier = Modifier
                    .padding(end = 16.dp)
            )
            Text("On", modifier = Modifier.padding(end = 8.dp))
            Switch(checked = turnOn, onCheckedChange = { turnOn = it })
            Text("Off", modifier = Modifier.padding(start = 8.dp))
        }

        // Select All Checkbox
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = allDaysSelected,
                onCheckedChange = { isChecked ->
                    allDaysSelected = isChecked
                    daysOfWeek.forEach { it.value = isChecked }
                },
                modifier = Modifier.padding(start = 0.dp)
            )
            Text("Select All")
        }


        // Day Checkboxes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                CheckboxWithLabel(
                    label = label,
                    checked = daysOfWeek[index].value,
                    onCheckedChange = { checked ->
                        daysOfWeek[index].value = checked
                        allDaysSelected = daysOfWeek.all { it.value }
                    })
            }
        }

        // Time Picker
        TimePicker(time) { newTime -> time = newTime }

        Spacer(modifier = Modifier.height(18.dp))

        // Save Button
        Button(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
            onClick = {
                val newSchedule = Schedule(time, turnOn, daysOfWeek.map { it.value })
                schedules.add(newSchedule)
                coroutineScope.launch {
                    NetworkManager().saveSchedule(newSchedule)
                }
                Toast.makeText(context, "Schedule saved", Toast.LENGTH_SHORT).show()
            }) {
            Text("Save")
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Schedules List title
        Text(
            "Schedules",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        if (schedules.isEmpty()) {
            Text(
                "No schedules yet",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // List of Schedules
        schedules.forEachIndexed { index, schedule ->
            ScheduleItem(schedule, onDelete = {
                schedules.removeAt(index)
                coroutineScope.launch {
                    NetworkManager().deleteSchedule(index)
                }
                Toast.makeText(context, "Schedule deleted", Toast.LENGTH_SHORT).show()
            })
        }

        // Back Button
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Back")
        }
    }
}

@Composable
fun CheckboxWithLabel(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
fun TimePicker(selectedTime: LocalTime, onTimeSelected: (LocalTime) -> Unit) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Time: ${selectedTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
            modifier = Modifier.padding(end = 16.dp),
            fontSize = 24.sp
        )

        // Display a button that shows the selected time and opens the time picker dialog when clicked
        Button(onClick = { showDialog = true }) {
            Text("Change Time")
        }
    }

    // When showDialog is true, show the time picker dialog
    if (showDialog) {
        // Dismiss the dialog once the time is selected or cancelled
        val onDismissRequest = { showDialog = false }

        // Initialize the time picker dialog
        val timePickerDialog = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onTimeSelected(LocalTime.of(hourOfDay, minute))
                onDismissRequest()
            },
            selectedTime.hour,
            selectedTime.minute,
            true // is24HourView
        )

        timePickerDialog.setOnCancelListener { onDismissRequest() }
        timePickerDialog.setOnDismissListener { onDismissRequest() }

        // Show the dialog
        timePickerDialog.show()
    }
}

@Composable
fun ScheduleItem(schedule: Schedule, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${schedule.time.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${if (schedule.turnOn) "On" else "Off"} - ${
                schedule.days.mapIndexedNotNull { index, selected ->
                    if (selected) listOf(
                        "M",
                        "T",
                        "W",
                        "T",
                        "F",
                        "S",
                        "S"
                    )[index] else null
                }.joinToString()
            }"
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
    }
}