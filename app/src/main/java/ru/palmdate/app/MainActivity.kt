package ru.palmdate.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ru.palmdate.app.ui.DayScreen
import ru.palmdate.app.ui.PalmButton
import ru.palmdate.app.ui.theme.Palm
import ru.palmdate.app.ui.theme.PalmTheme

private val PERMISSIONS = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
    Manifest.permission.READ_CONTACTS,
)

class MainActivity : ComponentActivity() {
    private val vm: DayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Прозрачные системные панели с правильным цветом значков:
        // светлая тема — тёмные значки, тёмная — светлые (как у Google Календаря).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            PalmTheme {
                var granted by remember { mutableStateOf(hasAll()) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { granted = hasAll() }

                if (granted) {
                    LaunchedEffect(Unit) { vm.start() }
                    DayScreen(vm)
                } else {
                    PermissionScreen { launcher.launch(PERMISSIONS) }
                }
            }
        }
    }

    private fun hasAll() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palm.paper).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("DateBook", style = Palm.title, color = Palm.navy)
        Spacer(Modifier.height(12.dp))
        Text(
            "Нужен доступ к календарю — события хранятся в вашем Google-календаре — " +
                "и к контактам, чтобы назначать звонки и встречи людям из телефонной книги.",
            style = Palm.body, color = Palm.inkSoft, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PalmButton("Разрешить", filled = true, onClick = onRequest)
    }
}
