package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        System.loadLibrary("sqlcipher")
        setContent {
            MyApplicationTheme {
                var screen by rememberSaveable { mutableStateOf("Home") }
                var consentGiven by rememberSaveable { mutableStateOf(false) }
                var enrollmentStudentId by rememberSaveable { mutableStateOf(0) }
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    when (screen) {
                        "Home" -> {
                            HomeScreen(Modifier.padding(padding)) { destination ->
                                if (destination == "Consent") consentGiven = false
                                screen = destination
                                }
                            }

                        "Consent" -> {
                            Column(modifier = Modifier.padding(padding).padding(24.dp)) {
                                Text("Consent and Privacy", style = MaterialTheme.typography.headlineMedium)
                                Text("Face samples will be used to recognize students and record attendance.")
                                Text("Face data is sensitive biometric information. The app will store it securely on this device and use it only for attendance matching.")

                                Row(
                                    modifier = Modifier.toggleable(
                                        value = consentGiven,
                                        role = Role.Checkbox,
                                        onValueChange = { consentGiven = it }
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = consentGiven,
                                        onCheckedChange = null
                                    )
                                    Text("I confirm the student has agreed to the use of their face data for attendance.")
                                }

                                Button(
                                    onClick = { screen = "Register Student" },
                                    enabled = consentGiven
                                ) {
                                    Text("Continue")
                                }

                                Button(onClick = { screen = "Home" }) {
                                    Text("Back")
                                }
                            }
                        }

                        "Register Student" -> {
                            RegistrationScreen(Modifier.padding(padding)) { screen = "Home" }
                        }

                        "View Students" -> {
                            StudentListScreen(
                                modifier = Modifier.padding(padding),
                                onCaptureFace = { id ->
                                    enrollmentStudentId = id
                                    screen = "Enroll Face"
                                }
                            ) { screen = "Home" }
                        }

                        "Take Attendance" -> {
                            CameraScreen(
                                lifecycleOwner = this@MainActivity,
                                modifier = Modifier.padding(padding)
                            ) { screen = "Home" }
                        }

                        "Enroll Face" -> {
                            CameraScreen(
                                lifecycleOwner = this@MainActivity,
                                modifier = Modifier.padding(padding),
                                enrollmentStudentId = enrollmentStudentId
                            ) { screen = "View Students" }
                        }

                        else -> {
                            PlaceholderScreen(screen, Modifier.padding(padding)) { screen = "Home" }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Smart Attendance", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onOpen("Consent") }) { Text("Register Student") }
        Button(onClick = { onOpen("Take Attendance") }) { Text("Take Attendance") }
        Button(onClick = { onOpen("View Reports") }) { Text("View Reports") }
        Button(onClick = { onOpen("View Students") }) { Text("View Students") }
    }
}

@Composable
private fun RegistrationScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    var studentName by rememberSaveable { mutableStateOf("") }
    var studentClass by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("Register Student", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = studentName,
            onValueChange = { studentName = it },
            label = { Text("Student name") },
            singleLine = true
        )
        OutlinedTextField(
            value = studentClass,
            onValueChange = { studentClass = it },
            label = { Text("Class") },
            singleLine = true
        )
        Button(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(context).studentDao().insert(
                            Student(
                                name = studentName.trim(),
                                className = studentClass.trim()
                            )
                        )
                    }
                    onBack()
                }
            },
            enabled = studentName.isNotBlank() && studentClass.isNotBlank()
        ) {
            Text("Save student")
        }
        Button(onClick = onBack) { Text("Cancel") }
    }
}
@Composable
private fun StudentListScreen(
    modifier: Modifier = Modifier,
    onCaptureFace: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var students by remember { mutableStateOf<List<Student>?>(null) }

    LaunchedEffect(Unit) {
        students = withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).studentDao().getAll()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("Registered Students", style = MaterialTheme.typography.headlineMedium)

        val registeredStudents = students
        when {
            registeredStudents == null -> Text("Loading students...")
            registeredStudents.isEmpty() -> Text("No students registered yet.")
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(registeredStudents) { student ->
                    ListItem(
                        headlineContent = { Text(student.name) },
                        supportingContent = { Text(student.className) },
                        trailingContent = {
                            Button(onClick = { onCaptureFace(student.id) }) { Text("Add face") }
                        }
                    )
                }
            }
        }

        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("This section will be added in the next steps.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}
