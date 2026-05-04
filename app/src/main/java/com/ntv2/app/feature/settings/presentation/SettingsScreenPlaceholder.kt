package com.ntv2.app.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text

@Composable
fun SettingsScreenPlaceholder(
    onBackToLibrary: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Configurações (placeholder)", color = Color.White)
        Text("Filtro mínimo e logout serão ligados aos repositórios na próxima fase.", color = Color.White)
        Button(onClick = onBackToLibrary) {
            Text("Voltar para Biblioteca")
        }
    }
}
