package com.borhji.mushaf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.borhji.mushaf.ui.MushafRoot
import com.borhji.mushaf.ui.theme.BorhjiMushafTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BorhjiMushafTheme {
                MushafRoot()
            }
        }
    }
}
