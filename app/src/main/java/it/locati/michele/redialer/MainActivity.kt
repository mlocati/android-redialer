@file:Suppress("DEPRECATION")
package it.locati.michele.redialer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import it.locati.michele.redialer.ui.theme.RedialerTheme
import kotlin.math.roundToInt

private const val TAG = "RedialerMainActivity"

class MainActivity : ComponentActivity() {
    private val viewModel: RedialViewModel by viewModels()
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: Any? = null

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        handleContactSelection(contactUri)
    }

    private val requestContactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickContactLauncher.launch(null)
        } else {
            Toast.makeText(this, getString(R.string.contacts_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPhonePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val callPhoneGranted = permissions[Manifest.permission.CALL_PHONE] ?: false
        val readPhoneStateGranted = permissions[Manifest.permission.READ_PHONE_STATE] ?: false
        
        if (callPhoneGranted && readPhoneStateGranted) {
            registerCallStateListener()
            viewModel.startRedialing()
        } else {
            Toast.makeText(this, getString(R.string.phone_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        
        setContent {
            RedialerTheme {
                val uiState by viewModel.uiState.collectAsState()
                var showAboutDialog by remember { mutableStateOf(false) }
                
                RedialerScreen(
                    uiState = uiState,
                    onPhoneNumberChange = { viewModel.onPhoneNumberChange(it) },
                    onDelayChange = { viewModel.onDelayChange(it) },
                    onStopThresholdChange = { viewModel.onStopThresholdChange(it) },
                    onPickContact = { attemptPickContact() },
                    onStartRedial = { attemptStartRedial() },
                    onStopRedial = { viewModel.stopRedialing() },
                    onNumberChosen = { viewModel.onNumberChosen(it) },
                    onDismissNumberSelection = { viewModel.dismissNumberSelection() },
                    onShowAbout = { showAboutDialog = true }
                )

                if (showAboutDialog) {
                    AboutDialog(
                        onDismiss = { showAboutDialog = false }
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterCallStateListener()
    }

    private fun attemptPickContact() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            pickContactLauncher.launch(null)
        } else {
            requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun attemptStartRedial() {
        val callPhoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val readPhoneStateGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        
        if (callPhoneGranted && readPhoneStateGranted) {
            registerCallStateListener()
            viewModel.startRedialing()
        } else {
            requestPhonePermissionsLauncher.launch(
                arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
            )
        }
    }

    private fun registerCallStateListener() {
        if (telephonyCallback != null) return // Already registered

        val tm = telephonyManager ?: return
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        viewModel.onCallStateChanged(state)
                    }
                }
                tm.registerTelephonyCallback(ContextCompat.getMainExecutor(this), callback)
                telephonyCallback = callback
            } else {
                val listener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        viewModel.onCallStateChanged(state)
                    }
                }
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                telephonyCallback = listener
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while registering call state listener", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering call state listener", e)
        }
    }

    private fun unregisterCallStateListener() {
        val tm = telephonyManager ?: return
        val callback = telephonyCallback ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (callback is TelephonyCallback) {
                    tm.unregisterTelephonyCallback(callback)
                }
            } else {
                if (callback is PhoneStateListener) {
                    tm.listen(callback, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering call state listener", e)
        } finally {
            telephonyCallback = null
        }
    }

    private fun makeCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "CALL_PHONE permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${Uri.encode(number)}")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating call", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleContactSelection(uri: Uri?) {
        if (uri == null) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.contacts_permission_denied), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                    val hasPhone = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0

                    if (hasPhone) {
                        val numbers = mutableListOf<ContactNumber>()
                        val phoneCursor: Cursor? = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(
                                ContactsContract.CommonDataKinds.Phone.NUMBER,
                                ContactsContract.CommonDataKinds.Phone.TYPE,
                                ContactsContract.CommonDataKinds.Phone.LABEL,
                                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
                            ),
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(id),
                            null
                        )
                        phoneCursor?.use { pCursor ->
                            val numberIdx = pCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            val typeIdx = pCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                            val labelIdx = pCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
                            val primaryIdx = pCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
                            
                            while (pCursor.moveToNext()) {
                                val number = pCursor.getString(numberIdx)
                                val type = pCursor.getInt(typeIdx)
                                val label = pCursor.getString(labelIdx)
                                val isPrimary = pCursor.getInt(primaryIdx) != 0
                                
                                val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(resources, type, label).toString()
                                numbers.add(ContactNumber(number, typeLabel, isPrimary))
                            }
                        }
                        
                        if (numbers.isEmpty()) {
                            viewModel.updateContact(name, null)
                            Toast.makeText(this, getString(R.string.contact_no_phone), Toast.LENGTH_SHORT).show()
                        } else {
                            // Sort numbers to put primary one first
                            numbers.sortByDescending { it.isPrimary }
                            viewModel.onContactSelected(name, numbers)
                        }
                    } else {
                        viewModel.updateContact(name, null)
                        Toast.makeText(this, getString(R.string.contact_no_phone), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading contact", e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedialerScreen(
    uiState: RedialUiState,
    onPhoneNumberChange: (String) -> Unit,
    onDelayChange: (Int) -> Unit,
    onStopThresholdChange: (Int) -> Unit,
    onPickContact: () -> Unit,
    onStartRedial: () -> Unit,
    onStopRedial: () -> Unit,
    onNumberChosen: (ContactNumber) -> Unit,
    onDismissNumberSelection: () -> Unit,
    onShowAbout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onShowAbout) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = stringResource(R.string.about_title)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !uiState.isRedialing) { onPickContact() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContactPage,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.contactName ?: stringResource(R.string.select_contact),
                        style = if (uiState.contactName != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (uiState.contactName != null) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                    if (!uiState.phoneType.isNullOrBlank()) {
                        Text(
                            text = uiState.phoneType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.phoneNumber ?: "",
                onValueChange = onPhoneNumberChange,
                label = { Text(stringResource(R.string.manual_number_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                enabled = !uiState.isRedialing
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.delay_label, uiState.delaySeconds),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = uiState.delaySeconds.toFloat(),
                    onValueChange = { onDelayChange(it.roundToInt()) },
                    valueRange = RedialViewModel.MIN_DELAY_SECONDS.toFloat()..RedialViewModel.MAX_DELAY_SECONDS.toFloat(),
                    enabled = !uiState.isRedialing
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.stop_threshold_label, uiState.stopThresholdSeconds),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = uiState.stopThresholdSeconds.toFloat(),
                    onValueChange = { onStopThresholdChange(it.roundToInt()) },
                    valueRange = RedialViewModel.MIN_STOP_THRESHOLD_SECONDS.toFloat()..RedialViewModel.MAX_STOP_THRESHOLD_SECONDS.toFloat(),
                    enabled = !uiState.isRedialing
                )
            }

            Text(
                text = stringResource(uiState.statusMessageResId),
                style = MaterialTheme.typography.headlineSmall,
                color = if (uiState.isRedialing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onStartRedial,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isRedialing && !uiState.phoneNumber.isNullOrBlank(),
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

    if (uiState.numbersToSelect != null) {
        NumberSelectionDialog(
            contactName = uiState.contactName ?: "",
            numbers = uiState.numbersToSelect,
            onNumberChosen = onNumberChosen,
            onDismiss = onDismissNumberSelection
        )
    }
}

@Composable
fun NumberSelectionDialog(
    contactName: String,
    numbers: List<ContactNumber>,
    onNumberChosen: (ContactNumber) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = contactName) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.select_number_title),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(numbers) { contactNumber ->
                        ListItem(
                            headlineContent = { Text(contactNumber.number) },
                            supportingContent = { Text(contactNumber.typeLabel) },
                            leadingContent = {
                                RadioButton(
                                    selected = contactNumber.isPrimary,
                                    onClick = null
                                )
                            },
                            trailingContent = {
                                if (contactNumber.isPrimary) {
                                    Text(
                                        text = stringResource(R.string.default_number_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNumberChosen(contactNumber) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName ?: "N/A"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.version_label, versionName),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.build_label, versionCode),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.author_label),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.license_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mlocati/android-redialer/blob/main/LICENSE"))
                        context.startActivity(intent)
                    },
                    color = MaterialTheme.colorScheme.primary
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mlocati/android-redialer"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.source_code))
                }
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mlocati/android-redialer/issues"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.report_issue))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun RedialerScreenPreview() {
    RedialerTheme {
        RedialerScreen(
            uiState = RedialUiState(
                contactName = "John Doe",
                phoneNumber = "+123456789",
                phoneType = "Mobile",
                isRedialing = false,
                statusMessageResId = R.string.idle_status,
                delaySeconds = 5,
                stopThresholdSeconds = 2
            ),
            onPhoneNumberChange = {},
            onDelayChange = {},
            onStopThresholdChange = {},
            onPickContact = {},
            onStartRedial = {},
            onStopRedial = {},
            onNumberChosen = {},
            onDismissNumberSelection = {},
            onShowAbout = {}
        )
    }
}
