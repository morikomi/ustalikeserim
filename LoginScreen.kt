package com.ersinozdogan.ustalikeserimv.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val loginSuccessful by viewModel.loginSuccessful.collectAsState()

    LaunchedEffect(loginSuccessful) {
        if (loginSuccessful) onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Kullanıcı Adı") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Şifre") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::login,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Giriş Yap")
        }
        if (!loginSuccessful && (username.isNotEmpty() || password.isNotEmpty())) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Hatalı kullanıcı adı veya şifre",
                color = androidx.compose.ui.graphics.Color.Red
            )
        }
    }
}