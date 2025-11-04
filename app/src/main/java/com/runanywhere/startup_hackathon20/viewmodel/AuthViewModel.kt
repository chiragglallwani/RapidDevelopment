package com.runanywhere.startup_hackathon20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.startup_hackathon20.data.repository.AuthRepository
import com.runanywhere.startup_hackathon20.data.repository.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    private val _isLoggedIn = MutableStateFlow(authRepository.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    
    private val _userName = MutableStateFlow(authRepository.getUserName() ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()
    
    private val _userEmail = MutableStateFlow(authRepository.getUserEmail() ?: "")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()
    
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            when (val result = authRepository.login(email, password)) {
                is AuthResult.Success -> {
                    _isLoggedIn.value = true
                    _userName.value = authRepository.getUserName() ?: ""
                    _userEmail.value = authRepository.getUserEmail() ?: ""
                    _authState.value = AuthState.Success("Login successful")
                }
                is AuthResult.Error -> {
                    _authState.value = AuthState.Error(result.message)
                }
            }
        }
    }
    
    fun register(name: String, email: String, password: String, role: String = "user") {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            when (val result = authRepository.register(name, email, password, role)) {
                is AuthResult.Success -> {
                    _isLoggedIn.value = true
                    _userName.value = authRepository.getUserName() ?: ""
                    _userEmail.value = authRepository.getUserEmail() ?: ""
                    _authState.value = AuthState.Success("Registration successful")
                }
                is AuthResult.Error -> {
                    _authState.value = AuthState.Error(result.message)
                }
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            when (val result = authRepository.logout()) {
                is AuthResult.Success -> {
                    _isLoggedIn.value = false
                    _userName.value = ""
                    _userEmail.value = ""
                    _authState.value = AuthState.Success("Logged out successfully")
                }
                is AuthResult.Error -> {
                    _authState.value = AuthState.Error(result.message)
                }
            }
        }
    }
    
    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }
    
    fun checkLoginStatus() {
        _isLoggedIn.value = authRepository.isLoggedIn()
        _userName.value = authRepository.getUserName() ?: ""
        _userEmail.value = authRepository.getUserEmail() ?: ""
    }
}

