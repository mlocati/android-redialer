package it.locati.michele.redialer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import it.locati.michele.redialer.ui.theme.RedialerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: RedialViewModel by viewModels()
    private lateinit var telephonyManager: TelephonyManager

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        handleContactSelection(contactUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        registerCallStateListener()

        setContent {
            RedialerTheme {
                val uiState by viewModel.uiState.collectAsState()
                PermissionWrapper {
                    RedialerScreen(
                        uiState = uiState,
                        onPickContact = { pickContactLauncher.launch(null) },
                        onStartRedial = { viewModel.startRedialing() },
                        onStopRedial = { viewModel.stopRedialing() }
                    )
                }

                LaunchedEffect(Unit) {
                    viewModel.callRequest.collect { number ->
                        makeCall(number)
                    }
                }
            }
        }
    }

    private fun registerCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(mainExecutor, object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    viewModel.onCallStateChanged(state)
                }
            })
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.listen(object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    viewModel.onCallStateChanged(state)
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun makeCall(number: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$number")
        }
        try {
            startActivity(intent)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied for calling", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleContactSelection(uri: Uri?) {
        uri?.let { contactUri ->
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )
            contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                    val hasPhone = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0

                    if (hasPhone) {
                        val phoneCursor: Cursor? = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(id),
                            null
                        )
                        phoneCursor?.use { pCursor ->
                            if (pCursor.moveToFirst()) {
                                val number = pCursor.getString(pCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                viewModel.updateContact(name, number)
                            }
                        }
                    } else {
                        viewModel.updateContact(name, null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionWrapper(content: @Composable () -> Unit) {
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS
        )
    )

    if (permissionState.allPermissionsGranted) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This app needs permissions to call, read phone state, and access contacts to function correctly.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                Text("Grant Permissions")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedialerScreen(
    uiState: RedialUiState,
    onPickContact: () -> Unit,
    onStartRedial: () -> Unit,
    onStopRedial: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContactPage,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (uiState.contactName != null) {
                        Text(
                            text = stringResource(R.string.contact_selected, uiState.contactName),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        uiState.phoneNumber?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } ?: Text(
                            text = "No phone number found",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "No contact selected",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Text(
                text = stringResource(uiState.statusMessageResId),
                style = MaterialTheme.typography.headlineSmall,
                color = if (uiState.isRedialing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )

            Button(
                onClick = onPickContact,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isRedialing
            ) {
                Text(stringResource(R.string.select_contact))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onStartRedial,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isRedialing && uiState.phoneNumber != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.start_redialing))
                }

                Button(
                    onClick = onStopRedial,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isRedialing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.stop))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RedialerScreenPreview() {
    RedialerTheme {
        RedialerScreen(
            uiState = RedialUiState(
                contactName = "John Doe",
                phoneNumber = "+123456789",
                isRedialing = false,
                statusMessageResId = R.string.idle_status
            ),
            onPickContact = {},
            onStartRedial = {},
            onStopRedial = {}
        )
    }
}
