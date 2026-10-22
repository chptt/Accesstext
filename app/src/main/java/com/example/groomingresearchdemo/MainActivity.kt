package com.example.groomingresearchdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.groomingresearchdemo.ui.RootScreen
import com.example.groomingresearchdemo.ui.theme.AppTheme

/**
 * Single-activity entry point. All six spec screens are rendered by [RootScreen] through a
 * small manual navigation graph (see ui/RootScreen.kt), which keeps the Compose dependency
 * surface minimal and makes the state flow between screens explicit for the write-up.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                RootScreen()
            }
        }
    }
}
