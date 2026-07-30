package com.example.mygallery

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE) }
            var isDarkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", true)) }

            // Material You: on Android 12+, the whole app's palette adapts to
            // the device wallpaper's colors, matching the current system
            // design language. Older versions fall back to a static scheme.
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDarkTheme) darkColorScheme() else lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GalleryPermissionGate(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = {
                            isDarkTheme = !isDarkTheme
                            prefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Requests the correct runtime permissions for the Android version, then
 * shows the gallery once granted. On Android 13+ this requests BOTH
 * READ_MEDIA_IMAGES and READ_MEDIA_VIDEO — requesting only the images
 * permission (as before) silently returns zero videos from MediaStore.
 */
@Composable
fun GalleryPermissionGate(isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var hasPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> hasPermission = results.values.all { it } }

    LaunchedEffect(Unit) {
        launcher.launch(permissions)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (hasPermission) {
            GalleryApp(isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme)
        } else {
            Text("Waiting for media permission…")
        }
    }
}