package by.w6.my1drive.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import by.w6.my1drive.R
import by.w6.my1drive.utils.VpsConnectionManager
import kotlinx.coroutines.launch

@Composable
fun VpsSettingsSection(vpsManager: VpsConnectionManager?) {
    if (vpsManager == null) return

    val context = LocalContext.current
    var vpsEnabled by remember { mutableStateOf(vpsManager.isVpsEnabled()) }
    var host by remember { mutableStateOf(vpsManager.getHost()) }
    var portStr by remember { mutableStateOf(vpsManager.getPort().toString()) }
    var username by remember { mutableStateOf(vpsManager.getUsername()) }
    var password by remember { mutableStateOf(vpsManager.getPassword()) }
    var remotePath by remember { mutableStateOf(vpsManager.getRemotePath()) }
    var vpsLimitGbStr by remember { mutableStateOf(vpsManager.getVpsLimitGb().toString()) }
    val coroutineScope = rememberCoroutineScope()
    var testingConnection by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.vps_server_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.vps_server_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
                Switch(
                    checked = vpsEnabled,
                    onCheckedChange = { checked ->
                        vpsEnabled = checked
                        vpsManager.setVpsEnabled(checked)
                        val msg = if (checked) context.getString(R.string.toast_vps_enabled) else context.getString(R.string.toast_vps_disabled)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (vpsEnabled) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.vps_host)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = portStr,
                        onValueChange = { portStr = it },
                        label = { Text(stringResource(R.string.vps_port)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.vps_username)) },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.vps_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = remotePath,
                    onValueChange = { remotePath = it },
                    label = { Text(stringResource(R.string.vps_path)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vpsLimitGbStr,
                    onValueChange = { vpsLimitGbStr = it },
                    label = { Text(stringResource(R.string.vps_limit)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val port = portStr.toIntOrNull() ?: 22
                        val limit = vpsLimitGbStr.toIntOrNull() ?: 10
                        testingConnection = true
                        coroutineScope.launch {
                            val result = vpsManager.testConnection(host, port, username, password, remotePath)
                            testingConnection = false
                            if (result.isSuccess) {
                                vpsManager.saveConfig(host, port, username, password, remotePath)
                                vpsManager.setVpsLimitGb(limit)
                                Toast.makeText(context, context.getString(R.string.toast_vps_success), Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_vps_error, result.exceptionOrNull()?.localizedMessage), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !testingConnection && host.isNotEmpty() && username.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (testingConnection) stringResource(R.string.btn_vps_testing) else stringResource(R.string.btn_vps_test))
                }
            }
        }
    }
}
