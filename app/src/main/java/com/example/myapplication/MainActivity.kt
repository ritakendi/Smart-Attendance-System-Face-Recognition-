package com.example.myapplication

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableIntStateOf
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
import java.io.OutputStream
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        System.loadLibrary("sqlcipher")
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var screen by rememberSaveable { mutableStateOf("Home") }
                var consentGiven by rememberSaveable { mutableStateOf(false) }
                var enrollmentStudentId by rememberSaveable { mutableStateOf(0) }
                var consentStudentId by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    when (screen) {
                        "Home" -> {
                            HomeScreen(Modifier.padding(padding)) { destination ->
                                if (destination == "Consent") {
                                    consentGiven = false
                                    consentStudentId = 0
                                }
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
                                    onClick = {
                                        if (consentStudentId == 0) {
                                            screen = "Register Student"
                                        } else {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AppDatabase.getInstance(context).studentDao()
                                                        .recordConsent(consentStudentId, System.currentTimeMillis())
                                                }
                                                consentStudentId = 0
                                                screen = "View Students"
                                            }
                                        }
                                    },
                                    enabled = consentGiven
                                ) {
                                    Text("Continue")
                                }

                                Button(onClick = {
                                    consentStudentId = 0
                                    screen = "Home"
                                }) {
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
                                onCaptureFace = { student ->
                                    enrollmentStudentId = student.id
                                    if (student.consentGivenAt == null) {
                                        consentGiven = false
                                        consentStudentId = student.id
                                        screen = "Consent"
                                    } else {
                                        screen = "Enroll Face"
                                    }
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

                        "View Reports" -> {
                            AttendanceReportScreen(Modifier.padding(padding)) { screen = "Home" }
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
                                className = studentClass.trim(),
                                consentGivenAt = System.currentTimeMillis()
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
    onCaptureFace: (Student) -> Unit,
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
                            Button(onClick = { onCaptureFace(student) }) {
                                Text(if (student.consentGivenAt == null) "Confirm consent" else "Add face")
                            }
                        }
                    )
                }
            }
        }

        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun AttendanceReportScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var date by rememberSaveable {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }
    var month by rememberSaveable {
        mutableStateOf(SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()))
    }
    var monthly by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<AttendanceStatus>?>(null) }
    var monthlyRows by remember { mutableStateOf<List<MonthlyAttendanceStatus>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val reportRows = rows?.filter {
        it.name.contains(filter, true) || it.className.contains(filter, true)
    }
    val monthlyReportRows = monthlyRows?.filter {
        it.name.contains(filter, true) || it.className.contains(filter, true)
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                if (monthly) {
                    writer.appendLine("Student,Class,Month,Days present")
                    monthlyReportRows.orEmpty().forEach { row ->
                        writer.appendLine("${row.name.csv()},${row.className.csv()},$month,${row.daysPresent}")
                    }
                } else {
                    writer.appendLine("Student,Class,Date,Status,Time")
                    reportRows.orEmpty().forEach { row ->
                        val time = row.markedAt?.let {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
                        }.orEmpty()
                        writer.appendLine("${row.name.csv()},${row.className.csv()},$date,${if (time.isEmpty()) "Not recorded" else "Present"},$time")
                    }
                }
            }
        }
    }
    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                val lines = buildList {
                    add("Smart Attendance Report")
                    add(if (monthly) "Month: $month" else "Date: $date")
                    if (filter.isNotBlank()) add("Filter: $filter")
                    add("")
                    if (monthly) {
                        add("Student | Class | Days present")
                        monthlyReportRows.orEmpty().forEach {
                            add("${it.name} | ${it.className} | ${it.daysPresent}")
                        }
                    } else {
                        add("Student | Class | Status | Time")
                        reportRows.orEmpty().forEach {
                            val time = it.markedAt?.let { stamp ->
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(stamp))
                            }.orEmpty()
                            add("${it.name} | ${it.className} | ${if (time.isEmpty()) "Not recorded" else "Present"} | $time")
                        }
                    }
                }
                writeAttendancePdf(output, lines)
            }
        }
    }

    LaunchedEffect(date, month, monthly, refresh) {
        val dao = AppDatabase.getInstance(context).attendanceDao()
        if (monthly) {
            monthlyRows = null
            monthlyRows = withContext(Dispatchers.IO) { dao.getForMonth(month) }
        } else {
            rows = null
            rows = withContext(Dispatchers.IO) { dao.getForDate(date) }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("Attendance Report", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { monthly = !monthly }) {
            Text(if (monthly) "Show daily report" else "Show monthly report")
        }
        if (monthly) {
            OutlinedTextField(month, { month = it }, label = { Text("Month (YYYY-MM)") }, singleLine = true)
        } else {
            OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
        }
        OutlinedTextField(filter, { filter = it }, label = { Text("Filter by student or class") }, singleLine = true)

        Button(
            onClick = { exportCsv.launch("attendance-${if (monthly) month else date}.csv") },
            enabled = if (monthly) monthlyReportRows != null else reportRows != null
        ) {
            Text("Export CSV")
        }
        Button(
            onClick = { exportPdf.launch("attendance-${if (monthly) month else date}.pdf") },
            enabled = if (monthly) monthlyReportRows != null else reportRows != null
        ) {
            Text("Export PDF")
        }
        if (monthly) {
            when {
                monthlyReportRows == null -> Text("Loading report...")
                monthlyReportRows.isEmpty() -> Text("No students match this report.")
                else -> LazyColumn(modifier = Modifier.weight(1f)) {
                    items(monthlyReportRows) { row ->
                        ListItem(
                            headlineContent = { Text(row.name) },
                            supportingContent = { Text("${row.className} · ${row.daysPresent} days present") }
                        )
                    }
                }
            }
        } else {
            when {
                reportRows == null -> Text("Loading report...")
                reportRows.isEmpty() -> Text("No students match this report.")
                else -> LazyColumn(modifier = Modifier.weight(1f)) {
                    items(reportRows) { row ->
                        val markedAt = row.markedAt
                        ListItem(
                            headlineContent = { Text(row.name) },
                            supportingContent = {
                                val status = markedAt?.let {
                                    "Present · ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))}"
                                } ?: "Not recorded"
                                Text("${row.className} · $status")
                            },
                            trailingContent = {
                                Button(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            val dao = AppDatabase.getInstance(context).attendanceDao()
                                            if (markedAt == null) {
                                                dao.mark(
                                                    AttendanceRecord(
                                                        studentId = row.studentId,
                                                        date = date,
                                                        markedAt = System.currentTimeMillis()
                                                    )
                                                )
                                            } else {
                                                dao.remove(row.studentId, date)
                                            }
                                        }
                                        refresh++
                                    }
                                }) { Text(if (markedAt == null) "Mark" else "Undo") }
                            }
                        )
                    }
                }
            }
        }
        Button(onClick = onBack) { Text("Back") }
    }
}

private fun String.csv() = "\"${replace("\"", "\"\"")}\""

private fun writeAttendancePdf(output: OutputStream, lines: List<String>) {
    val document = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
    try {
        lines.chunked(36).forEachIndexed { index, pageLines ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
            pageLines.forEachIndexed { line, text ->
                page.canvas.drawText(text.take(86), 40f, 50f + line * 20f, paint)
            }
            document.finishPage(page)
        }
        document.writeTo(output)
    } finally {
        document.close()
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
