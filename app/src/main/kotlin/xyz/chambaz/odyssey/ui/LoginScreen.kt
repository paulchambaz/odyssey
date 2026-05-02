package xyz.chambaz.odyssey.ui

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

@Composable
fun LoginScreen(
    onLogin: suspend (serverUrl: String, username: String, password: String) -> Unit,
    onRegister: suspend (serverUrl: String, username: String, password: String) -> Unit,
) {
    var serverUrl by remember { mutableStateOf("https://") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun attempt(block: suspend () -> Unit) {
        focusManager.clearFocus()
        scope.launch {
            loading = true
            error = null
            try {
                block()
            } catch (e: ConnectException) {
                error = "Server unreachable"
            } catch (e: UnknownHostException) {
                error = "Server unreachable"
            } catch (e: IOException) {
                error = when {
                    e.message?.contains("401") == true -> "Wrong password"
                    e.message?.contains("username taken") == true -> "Username taken"
                    else -> "Login failed"
                }
            } catch (e: Exception) {
                Log.e("LoginScreen", "unexpected auth error", e)
                error = "Unexpected error: ${e.javaClass.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Please login to\nan Iliad instance", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(72.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { attempt { onLogin(serverUrl, username, password) } }),
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(72.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = { attempt { onRegister(serverUrl, username, password) } },
                modifier = Modifier.weight(1f),
                enabled = !loading,
                border = BorderStroke(2.dp, Accent),
            ) { Text("Register", color = Accent) }
            Button(
                onClick = { attempt { onLogin(serverUrl, username, password) } },
                modifier = Modifier.weight(1f),
                enabled = !loading,
            ) { Text("Login") }
        }
        if (loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}
