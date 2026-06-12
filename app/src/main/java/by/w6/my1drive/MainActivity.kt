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
import by.w6.my1drive.ui.RestoreRequest
import by.w6.my1drive.ui.theme.My1DriveTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()
    private var hasPermissions by mutableStateOf(false)
    private var hasPartialAccess by mutableStateOf(false)

    // ─────────────────────────────────────────────────────────────────────────
    // BroadcastReceiver: listens for USB attach/detach to update drive status
    // ─────────────────────────────────────────────────────────────────────────

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED,
                Intent.ACTION_MEDIA_MOUNTED,
                Intent.ACTION_MEDIA_REMOVED,
                Intent.ACTION_MEDIA_EJECT,
                Intent.ACTION_MEDIA_UNMOUNTED -> {
                    // Drive state changed — recompute status immediately
                    viewModel.updateOtgStatus()
                }
            }
        }
    }

    private val deletePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onDeletePermissionGranted()
        } else {
            viewModel.dismissPendingDelete()
        }
    }

    private val otgFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
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

    /** SAF folder picker for restore destination — opened at phone internal storage root. */
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

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        updatePermissionStates()
        if (!hasPermissions && !hasPartialAccess) {
            Toast.makeText(this, getString(R.string.permission_required_toast), Toast.LENGTH_LONG).show()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updatePermissionStates()

        setContent {
            My1DriveTheme {
                LaunchedEffect(hasPermissions, hasPartialAccess) {
                    if (!hasPermissions && !hasPartialAccess) {
                        requestMediaPermissions()
                    }
                }

                val pendingDelete by viewModel.pendingDeleteRequest.collectAsState()
                LaunchedEffect(pendingDelete) {
                    pendingDelete?.let { request ->
                        val intentSenderRequest = IntentSenderRequest.Builder(request.intentSender).build()
                        deletePermissionLauncher.launch(intentSenderRequest)
                    }
                }

                // Handle restore requests that need UI interaction (folder picker)
                val restoreRequest by viewModel.restoreRequest.collectAsState()
                LaunchedEffect(restoreRequest) {
                    when (restoreRequest) {
                        is RestoreRequest.NeedFolderPicker -> {
                            // Launch SAF folder picker at phone internal storage root
                            restoreFolderLauncher.launch(phoneStorageRootUri())
                        }
                        else -> { /* AskOriginalOrCustom is handled in GalleryScreen as a dialog */ }
                    }
                }

                if (hasPermissions || hasPartialAccess) {
                    GalleryScreen(
                        onSelectOtgDirectory = {
                            otgFolderLauncher.launch(null)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Called when app is already running and USB device is attached
        // (because launchMode="singleTask" reuses the existing instance)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            viewModel.updateOtgStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()

        // Register USB + media mount/unmount events
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            // MEDIA_MOUNTED / REMOVED require a data scheme
            addDataScheme("file")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usbReceiver, filter)
        }

        // Force immediate status check
        viewModel.updateOtgStatus()

        if (hasPermissions || hasPartialAccess) {
            viewModel.refresh()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permissions
    // ─────────────────────────────────────────────────────────────────────────

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
