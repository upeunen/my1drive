package by.w6.my1drive.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
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
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R
import by.w6.my1drive.utils.VpsConnectionManager
import kotlinx.coroutines.launch

@Composable
fun VpsSettingsSection(
    vpsManager: VpsConnectionManager?,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Cloud Icon Badge + Title + Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_category_cloud),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.badge_beta),
                                    fontSize = 8.5.sp,
                                    lineHeight = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.vps_server_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

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
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.vps_host)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = portStr,
                        onValueChange = { portStr = it },
                        label = { Text(stringResource(R.string.vps_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.vps_username)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.vps_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = remotePath,
                    onValueChange = { remotePath = it },
                    label = { Text(stringResource(R.string.vps_path)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = vpsLimitGbStr,
                    onValueChange = { vpsLimitGbStr = it },
                    label = { Text(stringResource(R.string.vps_limit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val port = portStr.toIntOrNull() ?: 22
                        val limitGb = vpsLimitGbStr.toIntOrNull() ?: 100
                        coroutineScope.launch {
                            testingConnection = true
                            val result = vpsManager.testConnection(
                                host = host,
                                port = port,
                                username = username,
                                password = password,
                                remotePath = remotePath
                            )
                            testingConnection = false
                            if (result.isSuccess) {
                                vpsManager.saveConfig(
                                    host = host,
                                    port = port,
                                    username = username,
                                    password = password,
                                    remotePath = remotePath
                                )
                                vpsManager.setVpsLimitGb(limitGb)
                                Toast.makeText(context, context.getString(R.string.toast_vps_success), Toast.LENGTH_LONG).show()
                            } else {
                                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                                Toast.makeText(context, context.getString(R.string.toast_vps_error, errorMsg), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !testingConnection,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (testingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.btn_vps_testing))
                    } else {
                        Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_vps_test))
                    }
                }
            }
        }
    }
}
