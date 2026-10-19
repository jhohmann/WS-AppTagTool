package com.example.tagtool

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import android.os.Bundle
import android.os.SystemClock

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import java.util.concurrent.atomic.AtomicBoolean

// ---------------------------------------------------------
// Markenfarben
// ---------------------------------------------------------

private val BrandGreen = Color(0xFF50AF47)
private val BrandDarkGreen = Color(0xFF004F38)
private val BrandBlue = Color(0xFF004D70)
private val BrandLightGreen = Color(0xFFB5D8AF)
private val BrandOrange = Color(0xFFC54409)

private val PageBackground = Color(0xFFF7F9F7)
private val CardBackground = Color.White
private val TextPrimary = Color(0xFF203128)
private val TextSecondary = Color(0xFF64736A)
private val BorderColor = Color(0xFFE1E9E2)

private val AppColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandLightGreen,
    onPrimaryContainer = BrandDarkGreen,
    secondary = BrandBlue,
    onSecondary = Color.White,
    background = PageBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    outline = BorderColor,
    error = BrandOrange
)

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private lateinit var repository: TagRepository
    private var nfcAdapter: NfcAdapter? = null

    // ---------------------------------------------------------
    // App-Zustand
    // ---------------------------------------------------------

    private var mode by mutableStateOf("read")

    private var selectedList by mutableStateOf(TagListType.OLD)
    private var selectedNumber by mutableIntStateOf(1)
    private var numberInput by mutableStateOf("1")

    private var autoMode by mutableStateOf(false)
    private var overwriteWithoutConfirmation by mutableStateOf(false)

    private var writeEnabled by mutableStateOf(false)
    private var writeInProgress by mutableStateOf(false)

    private var tagInfo by mutableStateOf("NFC-Tag annähern")

    private var displayedNumber by mutableStateOf<String?>(null)
    private var displayedCode by mutableStateOf("")
    private var displayedList by mutableStateOf("")

    private var success by mutableStateOf(false)
    private var hasResult by mutableStateOf(false)

    // ---------------------------------------------------------
    // Bestätigungsdialog
    // ---------------------------------------------------------

    private var overwriteDialogVisible by mutableStateOf(false)
    private var overwriteMessage by mutableStateOf("")

    private var pendingUid: String? = null
    private var pendingRecord: TagRecord? = null

    private var approvedUid: String? = null
    private var approvedRecord: TagRecord? = null

    // ---------------------------------------------------------
    // NFC
    // ---------------------------------------------------------

    private val writeArmed = AtomicBoolean(false)
    private val processing = AtomicBoolean(false)

    private val nfcLock = Any()

    private var lastUid: String? = null
    private var lastSeen = 0L

    // ---------------------------------------------------------
    // Initialisierung
    // ---------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = TagRepository(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        repository.oldRecords.firstOrNull()?.let {
            selectedNumber = it.number
            numberInput = it.number.toString()
        }

        setContent {
            MaterialTheme(
                colorScheme = AppColorScheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PageBackground
                ) {
                    AppContent()
                }

                if (overwriteDialogVisible) {
                    OverwriteDialog()
                }
            }
        }
    }

    // ---------------------------------------------------------
    // Hauptlayout
    // ---------------------------------------------------------

    @Composable
    private fun AppContent() {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            AppHeader()

            Spacer(Modifier.height(24.dp))

            ModeSelector()

            Spacer(Modifier.height(20.dp))

            if (mode == "read") {
                ReadView()
            } else {
                WriteView()
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "V0.1 | Hohmann | 10.2026",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }

    // ---------------------------------------------------------
    // Kopfzeile mit Logo
    // ---------------------------------------------------------

    @Composable
    private fun AppHeader() {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Image(
                painter = painterResource(R.drawable.ws_logo),
                contentDescription = "Firmenlogo",
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    "Tag Tool",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandDarkGreen
                )

                Text(
                    "NFC Tag Management",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
    }

    // ---------------------------------------------------------
    // Modusauswahl
    // ---------------------------------------------------------

    @Composable
    private fun ModeSelector() {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFEAF0EA))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            ModeButton(
                title = "Lesen",
                selected = mode == "read",
                modifier = Modifier.weight(1f)
            ) {
                changeMode("read")
            }

            ModeButton(
                title = "Schreiben",
                selected = mode == "write",
                modifier = Modifier.weight(1f)
            ) {
                changeMode("write")
            }
        }
    }

    @Composable
    private fun ModeButton(
        title: String,
        selected: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {

        Button(
            onClick = onClick,
            modifier = modifier.height(46.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor =
                    if (selected) BrandGreen
                    else Color.Transparent,
                contentColor =
                    if (selected) Color.White
                    else TextSecondary
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp
            )
        ) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    // ---------------------------------------------------------
    // Wiederverwendbare Karte
    // ---------------------------------------------------------

    @Composable
    private fun AppCard(
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {

        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = CardBackground,
            shadowElevation = 2.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                content = content
            )
        }
    }

    // ---------------------------------------------------------
    // Statusanzeige
    // ---------------------------------------------------------

    @Composable
    private fun StatusIndicator(
        message: String,
        positive: Boolean = false,
        warning: Boolean = false
    ) {
        val foreground = when {
            positive -> BrandDarkGreen
            warning -> BrandOrange
            else -> TextSecondary
        }

        val symbol = when {
            positive -> "✓"
            warning -> "!"
            else -> "ⓘ"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = symbol,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = foreground
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = message,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = foreground,
                textAlign = TextAlign.Start
            )
        }
    }

    // ---------------------------------------------------------
    // Lesemodus
    // ---------------------------------------------------------

    @Composable
    private fun ReadView() {

        AppCard {

            Text(
                "ERKANNTER TAG",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(18.dp))

            Text(
                displayedNumber ?: "–",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 92.sp,
                lineHeight = 100.sp,
                fontWeight = FontWeight.Bold,
                color =
                    if (hasResult && displayedNumber != "?")
                        BrandDarkGreen
                    else
                        TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                displayedList.ifBlank { "Noch kein Tag erkannt" },
                modifier = Modifier.fillMaxWidth(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            HorizontalDivider(color = BorderColor)

            Spacer(Modifier.height(18.dp))

            Text(
                "TAG-CODE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                displayedCode.ifBlank { "– – – –" },
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandBlue
            )
        }

        Spacer(Modifier.height(16.dp))

        StatusIndicator(
            message = tagInfo,
            positive = hasResult && displayedNumber != "?",
            warning = hasResult && displayedNumber == "?"
        )
    }

    // ---------------------------------------------------------
    // Schreibmodus
    // ---------------------------------------------------------

    @Composable
    private fun WriteView() {

        val records = repository.getRecords(selectedList)

        val selectedRecord = repository.findByNumber(
            selectedList,
            selectedNumber
        )

        AppCard {

            Text(
                "CODELISTE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                ListButton(
                    text = "Alte Liste",
                    selected = selectedList == TagListType.OLD,
                    modifier = Modifier.weight(1f)
                ) {
                    selectList(TagListType.OLD)
                }

                ListButton(
                    text = "Neue Liste",
                    selected = selectedList == TagListType.NEW,
                    modifier = Modifier.weight(1f)
                ) {
                    selectList(TagListType.NEW)
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "TAG-NUMMER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                FilledTonalButton(
                    onClick = {
                        records.lastOrNull {
                            it.number < selectedNumber
                        }?.let {
                            selectNumber(it.number)
                        }
                    },
                    enabled = records.any {
                        it.number < selectedNumber
                    },
                    modifier = Modifier.size(54.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("−", fontSize = 26.sp)
                }

                OutlinedTextField(
                    value = numberInput,
                    onValueChange = { value ->

                        if (value.all { it.isDigit() }) {

                            numberInput = value

                            val number = value.toIntOrNull()

                            if (
                                number != null &&
                                repository.findByNumber(
                                    selectedList,
                                    number
                                ) != null
                            ) {
                                selectNumber(number)
                            } else {
                                disarm()
                                clearApproval()
                                success = false
                                hasResult = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = BrandDarkGreen
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                FilledTonalButton(
                    onClick = {
                        records.firstOrNull {
                            it.number > selectedNumber
                        }?.let {
                            selectNumber(it.number)
                        }
                    },
                    enabled = records.any {
                        it.number > selectedNumber
                    },
                    modifier = Modifier.size(54.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+", fontSize = 26.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Ausgewählter Code",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(Modifier.height(5.dp))

            Text(
                selectedRecord?.hexCode()
                    ?.chunked(2)
                    ?.joinToString(" ")
                    ?: "Ungültige Tag-Nummer",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandBlue
            )
        }

        Spacer(Modifier.height(16.dp))

        // Ergebnisanzeige
        AppCard {

            Text(
                "SCHREIBSTATUS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                displayedNumber ?: selectedNumber.toString(),
                modifier = Modifier.fillMaxWidth(),
                fontSize = 76.sp,
                lineHeight = 88.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color =
                    if (success)
                        BrandGreen
                    else
                        TextPrimary
            )

            Spacer(Modifier.height(12.dp))

            StatusIndicator(
                message = tagInfo,
                positive = success,
                warning = overwriteDialogVisible ||
                        tagInfo.contains("Fehler", ignoreCase = true) ||
                        tagInfo.contains("fehlgeschlagen", ignoreCase = true)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Optionen
        AppCard {

            Text(
                "OPTIONEN",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(14.dp))

            OptionSwitch(
                title = "Automatikmodus",
                description = "Nach erfolgreichem Schreiben zur nächsten Nummer wechseln",
                checked = autoMode,
                onCheckedChange = {
                    autoMode = it
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = BorderColor
            )

            OptionSwitch(
                title = "Ohne Nachfrage überschreiben",
                description = "Bereits beschriebene Tags automatisch überschreiben",
                checked = overwriteWithoutConfirmation,
                onCheckedChange = {
                    overwriteWithoutConfirmation = it
                    clearApproval()
                }
            )
        }

        Spacer(Modifier.height(18.dp))

        // Schreibbutton
        Button(
            onClick = {

                if (writeEnabled) {

                    disarm()
                    clearApproval()
                    tagInfo = "Schreiben abgebrochen"

                } else {

                    arm()
                    tagInfo = "Bereit – Tag annähern"
                }
            },
            enabled =
                selectedRecord != null &&
                        numberInput.toIntOrNull() == selectedNumber &&
                        !overwriteDialogVisible &&
                        !writeInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor =
                    if (writeEnabled)
                        BrandOrange
                    else
                        BrandGreen,
                contentColor = Color.White
            )
        ) {
            Text(
                when {
                    writeInProgress -> "Schreibvorgang läuft ..."
                    writeEnabled -> "Schreiben abbrechen"
                    else -> "Schreiben aktivieren"
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // ---------------------------------------------------------
    // Codelisten-Auswahl
    // ---------------------------------------------------------

    @Composable
    private fun ListButton(
        text: String,
        selected: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {

        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor =
                    if (selected)
                        Color(0xFFE8F5E6)
                    else
                        Color.White,
                contentColor =
                    if (selected)
                        BrandDarkGreen
                    else
                        TextSecondary
            ),
            border = androidx.compose.foundation.BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) BrandGreen else BorderColor
            )
        ) {
            Text(
                text,
                fontWeight =
                    if (selected)
                        FontWeight.Bold
                    else
                        FontWeight.Normal
            )
        }
    }

    // ---------------------------------------------------------
    // Optionsschalter
    // ---------------------------------------------------------

    @Composable
    private fun OptionSwitch(
        title: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    description,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BrandGreen,
                    uncheckedTrackColor = Color(0xFFDDE5DD)
                )
            )
        }
    }

    // ---------------------------------------------------------
    // Überschreibdialog
    // ---------------------------------------------------------

    @Composable
    private fun OverwriteDialog() {

        AlertDialog(
            onDismissRequest = {
                cancelOverwrite()
            },
            icon = {
                Text(
                    "!",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandOrange
                )
            },
            title = {
                Text(
                    "Tag bereits beschrieben",
                    fontWeight = FontWeight.Bold,
                    color = BrandDarkGreen
                )
            },
            text = {
                Text(
                    overwriteMessage,
                    color = TextPrimary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uid: String?
                        val record: TagRecord?

                        synchronized(nfcLock) {
                            uid = pendingUid
                            record = pendingRecord

                            approvedUid = uid
                            approvedRecord = record

                            pendingUid = null
                            pendingRecord = null
                        }

                        overwriteDialogVisible = false

                        if (uid != null && record != null) {

                            resetDetectionLock()
                            arm()

                            tagInfo =
                                "Bestätigt – denselben Tag erneut annähern"

                        } else {

                            disarm()
                            tagInfo = "Keine gültige Schreibfreigabe"
                        }
                    }
                ) {
                    Text(
                        "Trotzdem schreiben",
                        color = BrandGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        cancelOverwrite()
                    }
                ) {
                    Text(
                        "Abbrechen",
                        color = TextSecondary
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ---------------------------------------------------------
    // Zustandsverwaltung
    // ---------------------------------------------------------

    private fun changeMode(newMode: String) {

        disarm()
        clearApproval()

        overwriteDialogVisible = false

        mode = newMode

        success = false
        hasResult = false

        displayedNumber = null
        displayedCode = ""
        displayedList = ""

        tagInfo = "NFC-Tag annähern"
    }

    private fun selectList(type: TagListType) {

        disarm()
        clearApproval()

        selectedList = type

        repository.getRecords(type).firstOrNull()?.let {
            selectedNumber = it.number
            numberInput = it.number.toString()
        }

        success = false
        hasResult = false
        displayedNumber = null

        tagInfo = "Datensatz auswählen"
    }

    private fun selectNumber(number: Int) {

        disarm()
        clearApproval()

        selectedNumber = number
        numberInput = number.toString()

        success = false
        hasResult = false
        displayedNumber = null

        tagInfo = "Bereit"
    }

    private fun arm() {

        success = false

        writeArmed.set(true)
        writeEnabled = true
    }

    private fun disarm() {

        writeArmed.set(false)
        writeEnabled = false
    }

    private fun clearApproval() {

        synchronized(nfcLock) {
            pendingUid = null
            pendingRecord = null

            approvedUid = null
            approvedRecord = null
        }
    }

    private fun cancelOverwrite() {

        overwriteDialogVisible = false

        clearApproval()
        disarm()

        tagInfo = "Schreibvorgang abgebrochen"
    }

    private fun resetDetectionLock() {

        synchronized(nfcLock) {
            lastUid = null
            lastSeen = 0L
        }
    }

    // ---------------------------------------------------------
    // NFC Lifecycle
    // ---------------------------------------------------------

    override fun onResume() {
        super.onResume()

        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )
    }

    override fun onPause() {
        super.onPause()

        disarm()
        clearApproval()

        overwriteDialogVisible = false

        nfcAdapter?.disableReaderMode(this)
    }

    // ---------------------------------------------------------
    // NFC-Erkennung
    // ---------------------------------------------------------

    override fun onTagDiscovered(tag: Tag) {

        if (!processing.compareAndSet(false, true)) {
            return
        }

        try {

            val uid = tag.id.joinToString("") {
                "%02X".format(it.toInt() and 0xFF)
            }

            val now = SystemClock.elapsedRealtime()

            // Sperrzeit 1 Sekunde für dieselbe UID
            val blocked = synchronized(nfcLock) {

                if (
                    uid == lastUid &&
                    now - lastSeen < 1000L
                ) {
                    true
                } else {
                    lastUid = uid
                    lastSeen = now
                    false
                }
            }

            if (blocked) return

            if (overwriteDialogVisible) return

            val currentMode = mode

            val shouldWrite =
                currentMode == "write" &&
                        writeArmed.compareAndSet(true, false)

            val record = if (shouldWrite) {
                repository.findByNumber(
                    selectedList,
                    selectedNumber
                )
            } else {
                null
            }

            if (shouldWrite) {
                runOnUiThread {
                    writeEnabled = false
                    writeInProgress = true
                    tagInfo = "Prüfe Tag ..."
                }
            }

            val nfc = NfcV.get(tag)
                ?: error("Kein NFC-V-Tag")

            nfc.connect()

            try {

                if (shouldWrite && record != null) {

                    processWrite(nfc, uid, record)

                } else if (currentMode == "read") {

                    processRead(nfc)
                }

            } finally {
                nfc.close()
            }

        } catch (e: Exception) {

            runOnUiThread {
                success = false
                tagInfo = "NFC-Fehler: ${e.message}"
            }

        } finally {

            runOnUiThread {
                writeInProgress = false
            }

            processing.set(false)
        }
    }

    // ---------------------------------------------------------
    // Lesen
    // ---------------------------------------------------------

    private fun processRead(nfc: NfcV) {

        val data = readBlock(nfc, 0)
        val found = repository.findByBytes(data)

        runOnUiThread {

            displayedNumber =
                found?.number?.toString() ?: "?"

            displayedList = when (found?.listType) {
                TagListType.OLD -> "Alte Codeliste"
                TagListType.NEW -> "Neue Codeliste"
                null -> "Unbekannter Code"
            }

            displayedCode = formatBytes(data)

            hasResult = true
            success = false

            tagInfo =
                if (found != null)
                    "Tag erfolgreich erkannt"
                else
                    "Code nicht in den Codelisten"
        }
    }

    // ---------------------------------------------------------
    // Schreiben mit Inhaltsprüfung
    // ---------------------------------------------------------

    private fun processWrite(
        nfc: NfcV,
        uid: String,
        record: TagRecord
    ) {

        // Vorhandenen Inhalt lesen
        val existingData = readBlock(nfc, 0)

        val existingRecord =
            repository.findByBytes(existingData)

        // Leerer Tag: Block 0 enthält vier Nullbytes
        val isEmpty = existingData.all {
            it == 0.toByte()
        }

        val isApproved = synchronized(nfcLock) {
            approvedUid == uid &&
                    approvedRecord == record
        }

        // Bereits beschriebene Tags bestätigen lassen
        if (
            !isEmpty &&
            !overwriteWithoutConfirmation &&
            !isApproved
        ) {

            val existingDescription =
                when (existingRecord?.listType) {

                    TagListType.OLD ->
                        "Tag-Nr. ${existingRecord.number} " +
                                "(alte Codeliste)"

                    TagListType.NEW ->
                        "Tag-Nr. ${existingRecord.number} " +
                                "(neue Codeliste)"

                    null ->
                        "einen unbekannten Code: " +
                                formatBytes(existingData)
                }

            synchronized(nfcLock) {
                pendingUid = uid
                pendingRecord = record
            }

            runOnUiThread {

                overwriteMessage =
                    "Dieser Tag enthält bereits " +
                            existingDescription +
                            ".\n\n" +
                            "Soll er mit Tag-Nr. " +
                            "${record.number} überschrieben werden?"

                overwriteDialogVisible = true
                writeEnabled = false
                writeInProgress = false

                tagInfo = "Bestätigung erforderlich"
            }

            return
        }

        // Freigabe verbrauchen
        synchronized(nfcLock) {
            approvedUid = null
            approvedRecord = null
        }

        // Schreiben
        val expected = record.bytes()

        writeBlock(nfc, 0, expected)

        // Rücklesen und verifizieren
        val actual = readBlock(nfc, 0)
        val verified = actual.contentEquals(expected)

        runOnUiThread {

            success = verified
            hasResult = true

            displayedNumber = record.number.toString()
            displayedCode = formatBytes(actual)

            displayedList = when (record.listType) {
                TagListType.OLD -> "Alte Codeliste"
                TagListType.NEW -> "Neue Codeliste"
            }

            if (verified) {

                tagInfo = "Erfolgreich geschrieben und geprüft"

                if (autoMode) {

                    val next = repository
                        .getRecords(record.listType)
                        .firstOrNull {
                            it.number > record.number
                        }

                    if (next != null) {

                        selectedNumber = next.number
                        numberInput = next.number.toString()

                        writeArmed.set(true)
                        writeEnabled = true

                    } else {

                        disarm()
                        tagInfo = "Letzter Tag geschrieben"
                    }
                }

            } else {

                disarm()
                tagInfo = "Verifikation fehlgeschlagen"
            }
        }
    }

    // ---------------------------------------------------------
    // ISO15693: Block lesen
    // ---------------------------------------------------------

    private fun readBlock(
        nfc: NfcV,
        block: Int
    ): ByteArray {

        val response = nfc.transceive(
            byteArrayOf(
                0x02,
                0x20,
                block.toByte()
            )
        )

        checkResponse(response)

        require(response.size == 5) {
            "Unerwartete Antwortlänge: ${response.size}"
        }

        return response.copyOfRange(1, 5)
    }

    // ---------------------------------------------------------
    // ISO15693: Block schreiben
    // ---------------------------------------------------------

    private fun writeBlock(
        nfc: NfcV,
        block: Int,
        data: ByteArray
    ) {

        require(data.size == 4) {
            "Es müssen genau 4 Byte sein"
        }

        val response = nfc.transceive(
            byteArrayOf(
                0x02,
                0x21,
                block.toByte()
            ) + data
        )

        checkResponse(response)
    }

    // ---------------------------------------------------------
    // ISO15693: Antwort prüfen
    // ---------------------------------------------------------

    private fun checkResponse(response: ByteArray) {

        require(response.isNotEmpty()) {
            "Keine Antwort vom Tag"
        }

        if ((response[0].toInt() and 0x01) != 0) {

            val code = response.getOrNull(1)
                ?.let {
                    "%02X".format(it.toInt() and 0xFF)
                }
                ?: "unbekannt"

            error("ISO15693 Fehler: $code")
        }
    }

    // ---------------------------------------------------------
    // Byte-Darstellung
    // ---------------------------------------------------------

    private fun formatBytes(data: ByteArray): String {

        return data.reversedArray()
            .joinToString(" ") {
                "%02X".format(it.toInt() and 0xFF)
            }
    }
}