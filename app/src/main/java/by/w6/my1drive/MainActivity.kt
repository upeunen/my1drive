package by.w6.my1drive



import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import by.w6.my1drive.ui.GalleryScreen
import by.w6.my1drive.ui.GalleryViewModel
import by.w6.my1drive.ui.theme.My1DriveTheme
import java.io.File
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.font.FontFamily

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()
    private var hasPermissions by mutableStateOf(false)
    private var hasPartialAccess by mutableStateOf(false)

            private val otgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.updateOtgStatus()
        }
    }

    

    private val deviceDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onDeviceDeleteConfirmed()
        } else {
            viewModel.onDeviceDeleteCancelled()
        }
    }

        /** Returns true if the URI points to a non-primary (removable) storage volume */
    private fun isRemovableStorageUri(uri: Uri): Boolean {
        val path = uri.path ?: return false
        val treeSegment = path.substringAfter("/tree/", "")
        if (treeSegment.isEmpty()) return false
        val rawId = treeSegment.substringBefore(":")
        return !rawId.equals("primary", ignoreCase = true)
    }

    private val otgFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            if (!isRemovableStorageUri(uri)) {
                Toast.makeText(this, "Пожалуйста, выберите USB-флешку, а не внутреннюю память", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                viewModel.setOtgDirectory(uri)
                Toast.makeText(this, getString(R.string.otg_folder_selected_toast), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.otg_folder_error_toast, e.localizedMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** SAF folder picker for restore destination — when originalRelativePath is unknown. */
    private val restoreFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                viewModel.restoreToChosenFolder(uri)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.otg_folder_error_toast, e.localizedMessage), Toast.LENGTH_LONG).show()
                viewModel.dismissRestoreRequest()
                }
            } else {
                viewModel.dismissRestoreRequest()
            }
    }

    /** Returns a URI hint pointing to the phone's internal storage root for OpenDocumentTree */
    private fun phoneStorageRootUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DocumentsContract.buildRootUri(
                "com.android.externalstorage.documents",
                "primary"
            )
        } else {
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")
        }
    }

    private fun selectOtgFolder() {
        val otgUri = otgStorageRootUri(this)
        if (otgUri != null) {
            otgFolderLauncher.launch(otgUri)
        } else {
            Toast.makeText(this, "Внешний носитель не найден. Выберите папку на устройстве.", Toast.LENGTH_LONG).show()
            otgFolderLauncher.launch(phoneStorageRootUri())
        }
    }

    private fun otgStorageRootUri(context: Context): Uri? {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager ?: return null
        for (volume in storageManager.storageVolumes) {
            if (volume.isRemovable && volume.state == android.os.Environment.MEDIA_MOUNTED) {
                val uuid = volume.uuid
                if (uuid != null) {
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        DocumentsContract.buildRootUri("com.android.externalstorage.documents", uuid)
                    } else {
                        Uri.parse("content://com.android.externalstorage.documents/tree/$uuid%3A")
                    }
                }
            }
        }
        return null
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        updatePermissionStates()
        if (!hasPermissions && !hasPartialAccess) {
            Toast.makeText(this, getString(R.string.permission_required_toast), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashLogFile = File(filesDir, "crash_log.txt")

        // Глобальный обработчик необработанных исключений
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                crashLogFile.writeText(
                    """
                    Timestamp: ${java.util.Date()}
                    Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})
                    App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
                    Thread: ${thread.name}
                    
                    Stacktrace:
                    ${throwable.stackTraceToString()}
                    """.trimIndent()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        if (crashLogFile.exists()) {
            val crashContent = try {
                crashLogFile.readText()
            } catch (e: Exception) {
                "Failed to read crash log: ${e.localizedMessage}"
            }

            setContent {
                My1DriveTheme {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Application Crash Detected",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                LazyColumn(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    item {
                                        Text(
                                            text = crashContent,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Crash Log", crashContent)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(this@MainActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Copy Log")
                                }
                                Button(
                                    onClick = {
                                        try {
                                            crashLogFile.delete()
                                            deleteDatabase("my1drive.db")
                                            val dbFile = getDatabasePath("my1drive.db")
                                            try { File(dbFile.path + "-wal").delete() } catch (_: Exception) {}
                                            try { File(dbFile.path + "-shm").delete() } catch (_: Exception) {}
                                            getSharedPreferences("my1drive_prefs", Context.MODE_PRIVATE)
                                                .edit()
                                                .clear()
                                                .commit()
                                            val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                            }
                                            startActivity(intent)
                                            Runtime.getRuntime().exit(0)
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, "Reset failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Reset App")
                                }
                            }
                        }
                    }
                }
            }
            return
        }

        enableEdgeToEdge()
        updatePermissionStates()

        setContent {
            My1DriveTheme {
                                LaunchedEffect(hasPermissions, hasPartialAccess) {
                    if (!hasPermissions && !hasPartialAccess) {
                        requestMediaPermissions()
                    }
                }

                val deviceDeleteSender by viewModel.deviceDeleteSender.collectAsState()
                LaunchedEffect(deviceDeleteSender) {
                    deviceDeleteSender?.let { sender ->
                        val intentSenderRequest = IntentSenderRequest.Builder(sender).build()
                        deviceDeleteLauncher.launch(intentSenderRequest)
                    }
                }

                if (hasPermissions || hasPartialAccess) {
                    GalleryScreen(
                        onSelectOtgDirectory = {
                            selectOtgFolder()
                        },
                        onPickRestoreFolder = {
                            restoreFolderLauncher.launch(phoneStorageRootUri())
                        },
                        viewModel = viewModel,
                        hasPartialAccess = hasPartialAccess,
                        onRequestFullAccess = {
                            requestMediaPermissions()
                        },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(onClick = {
                            updatePermissionStates()
                            if (!hasPermissions && !hasPartialAccess) {
                                requestMediaPermissions()
                            }
                        }) {
                            Text(stringResource(R.string.grant_permission_btn))
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()

        viewModel.updateOtgStatus()

        if (hasPermissions || hasPartialAccess) {
            viewModel.refresh()
        }

                val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_EXPORTED
        } else {
            0
        }

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            this,
            otgReceiver,
            filter,
            receiverFlags
        )

        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(
            this,
            otgReceiver,
            mediaFilter,
            receiverFlags
        )
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(otgReceiver)
        } catch (_: Exception) {}
    }

    // Permissions

    private fun updatePermissionStates() {
        hasPermissions = checkFullMediaAccess()
        hasPartialAccess = if (hasPermissions) false else checkPartialMediaAccess()
    }

    private fun checkFullMediaAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPartialMediaAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    private fun requestMediaPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        permissionsLauncher.launch(permissions)
    }
}
