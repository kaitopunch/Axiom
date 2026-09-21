package com.duylt.demo.axiom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.duylt.demo.axiom.ui.DemoAppScreen
import com.duylt.demo.axiom.ui.theme.DemoAxiomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoAxiomTheme {
                DemoAppScreen()
            }
        }
    }
}
