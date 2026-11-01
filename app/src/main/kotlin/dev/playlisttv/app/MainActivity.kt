package dev.playlisttv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import dev.playlisttv.app.ui.theme.PlaylistTVTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      PlaylistTVTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        ) { MainNavigation() }
      }
    }
  }
}
