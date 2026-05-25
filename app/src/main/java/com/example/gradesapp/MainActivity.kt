package com.example.gradesapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import com.example.gradesapp.ui.theme.GradesAppTheme
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

// ─── COLORS ───────────────────────────────────────────────
val Primary = Color(0xFF3F51B5)
val PrimaryLight = Color(0xFFE8EAF6)
val Success = Color(0xFF4CAF50)
val Danger = Color(0xFFE53935)
val Background = Color(0xFFF5F6FA)
val CardWhite = Color.White
val TextGray = Color(0xFF757575)

// ─── MODULES ──────────────────────────────────────────────
val MODULES = listOf(
    "Entrepreneurial Culture",
    "Oracle Database Administration",
    "NoSQL Databases",
    "Mobile Development",
    "XML & Data Formats",
    "English",
    "Jakarta EE"
)

// ─── DATA CLASSES ─────────────────────────────────────────
data class Student(
    val firestoreId: String = "",
    val name: String = ""
)

data class Grade(
    val firestoreId: String = "",
    val studentName: String = "",
    val module: String = "",
    val value: Float = 0f
)

// ─── NAVIGATION ───────────────────────────────────────────
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Students : Screen("students", "Students", Icons.Default.Person)
    object Grades : Screen("grades", "Grades", Icons.Default.Edit)
    object Results : Screen("results", "Results", Icons.Default.Star)
}

// ─── MAIN ACTIVITY ────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GradesAppTheme {
                GradesApp()
            }
        }
    }
}

// ─── GRADES APP ───────────────────────────────────────────
@Composable
fun GradesApp() {
    val navController = rememberNavController()
    val screens = listOf(Screen.Students, Screen.Grades, Screen.Results)
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var students by remember { mutableStateOf(listOf<Student>()) }
    var grades by remember { mutableStateOf(listOf<Grade>()) }
    val db = Firebase.firestore

    LaunchedEffect(Unit) {
        db.collection("students").get()
            .addOnSuccessListener { result ->
                students = result.documents.map { doc ->
                    Student(
                        firestoreId = doc.id,
                        name = doc.getString("name") ?: ""
                    )
                }
            }
        db.collection("grades").get()
            .addOnSuccessListener { result ->
                grades = result.documents.map { doc ->
                    Grade(
                        firestoreId = doc.id,
                        studentName = doc.getString("studentName") ?: "",
                        module = doc.getString("module") ?: "",
                        value = doc.getDouble("value")?.toFloat() ?: 0f
                    )
                }
            }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = CardWhite) {
                screens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = { navController.navigate(screen.route) },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Primary,
                            selectedTextColor = Primary,
                            indicatorColor = PrimaryLight
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Students.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Students.route) {
                StudentsScreen(
                    students = students,
                    onAddStudent = { name ->
                        if (students.any { it.name.lowercase() == name.lowercase() }) return@StudentsScreen
                        db.collection("students")
                            .add(mapOf("name" to name))
                            .addOnSuccessListener { docRef ->
                                students = students + Student(firestoreId = docRef.id, name = name)
                            }
                    },
                    onDeleteStudent = { student ->
                        db.collection("students").document(student.firestoreId).delete()
                            .addOnSuccessListener {
                                students = students.filter { it.firestoreId != student.firestoreId }
                                grades.filter { it.studentName == student.name }.forEach { grade ->
                                    db.collection("grades").document(grade.firestoreId).delete()
                                }
                                grades = grades.filter { it.studentName != student.name }
                            }
                    },
                    onEditStudent = { student, newName ->
                        if (students.any { it.name.lowercase() == newName.lowercase() && it.firestoreId != student.firestoreId }) return@StudentsScreen
                        db.collection("students").document(student.firestoreId)
                            .update("name", newName)
                            .addOnSuccessListener {
                                students = students.map {
                                    if (it.firestoreId == student.firestoreId) it.copy(name = newName) else it
                                }
                                grades = grades.map {
                                    if (it.studentName == student.name) it.copy(studentName = newName) else it
                                }
                            }
                    }
                )
            }
            composable(Screen.Grades.route) {
                GradesScreen(
                    students = students,
                    grades = grades,
                    onAddGrade = { studentName, module, value ->
                        db.collection("grades")
                            .add(mapOf("studentName" to studentName, "module" to module, "value" to value))
                            .addOnSuccessListener { docRef ->
                                grades = grades + Grade(
                                    firestoreId = docRef.id,
                                    studentName = studentName,
                                    module = module,
                                    value = value
                                )
                            }
                    },
                    onDeleteGrade = { grade ->
                        db.collection("grades").document(grade.firestoreId).delete()
                            .addOnSuccessListener {
                                grades = grades.filter { it.firestoreId != grade.firestoreId }
                            }
                    },
                    onEditGrade = { grade, newValue ->
                        db.collection("grades").document(grade.firestoreId)
                            .update("value", newValue)
                            .addOnSuccessListener {
                                grades = grades.map {
                                    if (it.firestoreId == grade.firestoreId) it.copy(value = newValue) else it
                                }
                            }
                    }
                )
            }
            composable(Screen.Results.route) {
                ResultsScreen(students = students, grades = grades)
            }
        }
    }
}

// ─── STUDENTS SCREEN ──────────────────────────────────────
@Composable
fun StudentsScreen(
    students: List<Student>,
    onAddStudent: (String) -> Unit,
    onDeleteStudent: (Student) -> Unit,
    onEditStudent: (Student, String) -> Unit
) {
    var inputName by remember { mutableStateOf("") }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var editedName by remember { mutableStateOf("") }
    var showDuplicateError by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Students",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add New Student", fontWeight = FontWeight.SemiBold, color = TextGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it; showDuplicateError = false },
                            placeholder = { Text("Student name...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            isError = showDuplicateError
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputName.isNotBlank()) {
                                    if (students.any { it.name.lowercase() == inputName.lowercase() }) {
                                        showDuplicateError = true
                                    } else {
                                        onAddStudent(inputName.trim())
                                        inputName = ""
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    }
                    if (showDuplicateError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("⚠️ This name already exists!", color = Danger, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (students.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("👨‍🎓", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No students yet", fontSize = 18.sp, color = TextGray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(students, key = { it.firestoreId }) { student ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            if (editingStudent?.firestoreId == student.firestoreId) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = editedName,
                                        onValueChange = { editedName = it },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    IconButton(onClick = {
                                        if (editedName.isNotBlank()) {
                                            onEditStudent(student, editedName.trim())
                                            editingStudent = null
                                        }
                                    }) {
                                        Icon(Icons.Default.Done, contentDescription = "Save", tint = Success)
                                    }
                                    IconButton(onClick = { editingStudent = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextGray)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(student.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { editingStudent = student; editedName = student.name }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Primary)
                                    }
                                    IconButton(onClick = { onDeleteStudent(student) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── GRADES SCREEN ────────────────────────────────────────
@Composable
fun GradesScreen(
    students: List<Student>,
    grades: List<Grade>,
    onAddGrade: (String, String, Float) -> Unit,
    onDeleteGrade: (Grade) -> Unit,
    onEditGrade: (Grade, Float) -> Unit
) {
    var editingGrade by remember { mutableStateOf<Grade?>(null) }
    var editedValue by remember { mutableStateOf("") }
    var addingGradeFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var inputGrade by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Grades",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            if (students.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📝", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No students yet", fontSize = 18.sp, color = TextGray)
                    Text("Add students first", color = TextGray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(students, key = { it.firestoreId }) { student ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

                                // Student header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = student.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = Background)
                                Spacer(modifier = Modifier.height(8.dp))

                                // Modules list
                                MODULES.forEach { module ->
                                    val existingGrade = grades.find {
                                        it.studentName == student.name && it.module == module
                                    }
                                    val isAddingThis = addingGradeFor == Pair(student.name, module)
                                    val isEditingThis = editingGrade?.studentName == student.name &&
                                            editingGrade?.module == module

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = module,
                                            fontSize = 13.sp,
                                            color = TextGray,
                                            modifier = Modifier.weight(1f)
                                        )

                                        when {
                                            // Edit mode
                                            isEditingThis -> {
                                                OutlinedTextField(
                                                    value = editedValue,
                                                    onValueChange = { editedValue = it },
                                                    modifier = Modifier.width(80.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    singleLine = true
                                                )
                                                IconButton(onClick = {
                                                    val value = editedValue.toFloatOrNull()
                                                    if (value != null && value in 0f..20f) {
                                                        onEditGrade(existingGrade!!, value)
                                                        editingGrade = null
                                                        editedValue = ""
                                                    }
                                                }) {
                                                    Icon(Icons.Default.Done, contentDescription = "Save", tint = Success)
                                                }
                                                IconButton(onClick = {
                                                    editingGrade = null
                                                    editedValue = ""
                                                }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextGray)
                                                }
                                            }

                                            // Add mode
                                            isAddingThis -> {
                                                OutlinedTextField(
                                                    value = inputGrade,
                                                    onValueChange = { inputGrade = it; showError = "" },
                                                    modifier = Modifier.width(80.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    singleLine = true
                                                )
                                                IconButton(onClick = {
                                                    val value = inputGrade.toFloatOrNull()
                                                    if (value != null && value in 0f..20f) {
                                                        onAddGrade(student.name, module, value)
                                                        addingGradeFor = null
                                                        inputGrade = ""
                                                        showError = ""
                                                    } else {
                                                        showError = "Enter a valid grade (0-20)"
                                                    }
                                                }) {
                                                    Icon(Icons.Default.Done, contentDescription = "Save", tint = Success)
                                                }
                                                IconButton(onClick = {
                                                    addingGradeFor = null
                                                    inputGrade = ""
                                                }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextGray)
                                                }
                                            }

                                            // Grade exists
                                            existingGrade != null -> {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (existingGrade.value >= 10f)
                                                        Success.copy(alpha = 0.15f)
                                                    else
                                                        Danger.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "%.1f/20".format(existingGrade.value),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = if (existingGrade.value >= 10f) Success else Danger
                                                    )
                                                }
                                                IconButton(onClick = {
                                                    editingGrade = existingGrade
                                                    editedValue = existingGrade.value.toString()
                                                }) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Primary, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(onClick = { onDeleteGrade(existingGrade) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Danger, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            // No grade yet
                                            else -> {
                                                TextButton(onClick = {
                                                    addingGradeFor = Pair(student.name, module)
                                                    inputGrade = ""
                                                }) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add", fontSize = 13.sp)
                                                }
                                            }
                                        }
                                    }

                                    if (showError.isNotBlank() && isAddingThis) {
                                        Text("⚠️ $showError", color = Danger, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── RESULTS SCREEN ───────────────────────────────────────
@Composable
fun ResultsScreen(
    students: List<Student>,
    grades: List<Grade>
) {
    var selectedModule by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Results",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Module filter
            Text("Filter by module:", fontSize = 13.sp, color = TextGray)
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 160.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedModule == null,
                        onClick = { selectedModule = null },
                        label = { Text("All Modules") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                items(MODULES) { module ->
                    FilterChip(
                        selected = selectedModule == module,
                        onClick = { selectedModule = module },
                        label = { Text(module, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Class average for selected module
            val filteredGrades = if (selectedModule == null) grades
            else grades.filter { it.module == selectedModule }

            if (filteredGrades.isNotEmpty()) {
                val classAvg = filteredGrades.map { it.value }.average().toFloat()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (classAvg >= 10f) Success.copy(alpha = 0.1f) else Danger.copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📊", fontSize = 32.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (selectedModule == null) "Overall Class Average" else "Average: $selectedModule",
                                fontSize = 13.sp,
                                color = TextGray
                            )
                            Text(
                                text = "%.2f / 20".format(classAvg),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (classAvg >= 10f) Success else Danger
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Students results
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(students, key = { it.firestoreId }) { student ->
                    val studentGrades = if (selectedModule == null)
                        grades.filter { it.studentName == student.name }
                    else
                        grades.filter { it.studentName == student.name && it.module == selectedModule }

                    val studentAvg = if (studentGrades.isEmpty()) null
                    else studentGrades.map { it.value }.average().toFloat()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(student.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (studentAvg != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (studentAvg >= 10f) Success.copy(alpha = 0.15f) else Danger.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "avg: %.1f".format(studentAvg),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            fontWeight = FontWeight.Bold,
                                            color = if (studentAvg >= 10f) Success else Danger
                                        )
                                    }
                                }
                            }
                            if (studentGrades.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                studentGrades.forEach { grade ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(grade.module, fontSize = 12.sp, color = TextGray, modifier = Modifier.weight(1f))
                                        Text(
                                            text = "%.1f/20".format(grade.value),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (grade.value >= 10f) Success else Danger
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("No grades yet", fontSize = 12.sp, color = TextGray)
                            }
                        }
                    }
                }
            }
        }
    }
}