package com.ersinozdogan.ustalikeserimv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ersinozdogan.ustalikeserimv.navigation.NavGraph
import com.ersinozdogan.ustalikeserimv.ui.theme.UstalikEserimVTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OpenCV native library initialization
        if (!OpenCVLoader.initDebug()) {
            Log.e("MainActivity", "OpenCV initialization failed")
        } else {
            Log.d("MainActivity", "OpenCV initialized successfully")
        }

        setContent {
            UstalikEserimVTheme {
                NavGraph()
            }
        }
    }
}