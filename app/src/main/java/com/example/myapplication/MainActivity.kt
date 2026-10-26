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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var screen by rememberSaveable { mutableStateOf("Home") }
                var consentGiven by rememberSaveable { mutableStateOf(false) }
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    when (screen) {
                        "Home" -> {
                            HomeScreen(Modifier.padding(padding)) { screen = it }
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
    }
}

@Composable
private fun RegistrationScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    var studentName by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("Register Student", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = studentName,
            onValueChange = { studentName = it },
            label = { Text("Student name") },
            singleLine = true
        )
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