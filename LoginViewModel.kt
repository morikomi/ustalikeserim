package com.ersinozdogan.ustalikeserimv.ui.login

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LoginViewModel : ViewModel() {
    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _loginSuccessful = MutableStateFlow(false)
    val loginSuccessful: StateFlow<Boolean> = _loginSuccessful

    fun onUsernameChange(input: String) {
        _username.value = input
    }

    fun onPasswordChange(input: String) {
        _password.value = input
    }

    fun login() {
        _loginSuccessful.value =
            (_username.value == "admin" && _password.value == "1234")
    }
}