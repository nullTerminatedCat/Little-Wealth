package com.lincx.licai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lincx.licai.ui.LicaiRoot
import com.lincx.licai.ui.theme.LicaiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LicaiTheme {
                LicaiRoot()
            }
        }
    }
}
