package com.treasurenova.bep20manager.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.treasurenova.bep20manager.automation.SessionController
import com.treasurenova.bep20manager.automation.WebViewBridge
import com.treasurenova.bep20manager.logic.AccountImport
import com.treasurenova.bep20manager.logic.AccountInput
import com.treasurenova.bep20manager.logic.AddressRow
import com.treasurenova.bep20manager.logic.ExportFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

val Navy = Color(0xFF0B1220)
val CardNavy = Color(0xFF152033)
val Gold = Color(0xFFD4AF37)
val ActionBlue = Color(0xFF3B82F6)
val SuccessGreen = Color(0xFF22C55E)
val DangerRed = Color(0xFFEF4444)
val Muted = Color(0xFFB7C0D0)
const val Bep20Warning = "Only send USDT on BEP20 / BNB Smart Chain to this address."

object UiCatalog {
    val homeActions = listOf(
        "Single Account",
        "Multi Account",
        "Import Accounts",
        "Results",
        "History",
        "Settings",
    )
    val stepNames = listOf("Login", "Wallet", "USDT", "BEP20", "Address")
}

@Composable
fun HomeScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize().background(Navy).padding(20.dp)) {
        Text("TreasureNova", color = Gold, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("BEP20 Address Manager", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text("Read-only USDT deposit addresses. No withdrawals.", color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        UiCatalog.homeActions.forEach { label ->
            Button(
                onClick = { nav.navigate(label.route()) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
            ) { Text(label) }
        }
    }
}

private fun String.route(): String = when (this) {
    "Single Account" -> "single"
    "Multi Account" -> "multi"
    "Import Accounts" -> "import"
    "Results" -> "results"
    "History" -> "history"
    "Settings" -> "settings"
    else -> "home"
}

@Composable
fun SingleScreen(nav: NavHostController, model: AppViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ask by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Navy).padding(20.dp).verticalScroll(rememberScrollState())) {
        Title("Single Account")
        Field("Username", username) { username = it }
        Field("Password", password, password = true) { password = it }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { ask = true },
            enabled = username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
        ) { Text("Start") }
        Back(nav)
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("Process this account?") },
            text = { Text("The app will log in, read the USDT BEP20 address, and log out. The password is not saved.") },
            confirmButton = {
                TextButton(onClick = {
                    ask = false
                    val account = AccountInput(UUID.randomUUID().toString(), username.trim(), password)
                    model.begin(1)
                    nav.navigate("process") {
                        launchSingleTop = true
                    }
                    ProcessHolder.accounts = listOf(account)
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Cancel") } },
        )
    }
}

object ProcessHolder {
    var accounts: List<AccountInput> = emptyList()
}

@Composable
fun MultiScreen(nav: NavHostController, model: AppViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Navy).padding(20.dp).verticalScroll(rememberScrollState())) {
        Title("Multi Account")
        Field("Username", username) { username = it }
        Field("Password", password, password = true) { password = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (username.isNotBlank() && password.isNotBlank()) {
                    if (editing == null) model.addAccount(username, password) else model.updateAccount(editing!!, username, password)
                    username = ""
                    password = ""
                    editing = null
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)) {
                Text(if (editing == null) "Add account" else "Save edit")
            }
        }
        Spacer(Modifier.height(12.dp))
        model.accounts.forEach { account ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = CardNavy)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(account.username, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        editing = account.id
                        username = account.username
                        password = ""
                    }) { Text("Edit") }
                    TextButton(onClick = { model.deleteAccount(account.id) }) { Text("Delete", color = DangerRed) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { ask = true },
            enabled = model.accounts.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
        ) { Text("Start Processing") }
        Back(nav)
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("Process ${model.accounts.size} accounts?") },
            text = { Text("Accounts run one at a time. Passwords stay on this device and are not exported.") },
            confirmButton = {
                TextButton(onClick = {
                    ask = false
                    ProcessHolder.accounts = model.accounts
                    model.begin(model.accounts.size)
                    nav.navigate("process")
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ImportScreen(nav: NavHostController, model: AppViewModel) {
    val context = LocalContext.current
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var drafts by remember { mutableStateOf<List<AccountImport.Draft>>(emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment ?: "accounts.csv"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val parsed = runCatching { AccountImport.parse(name, bytes) }.getOrElse {
            Toast.makeText(context, it.message ?: "Could not read file", Toast.LENGTH_LONG).show()
            emptyList()
        }
        drafts = parsed
        names = AccountImport.previewUsernames(parsed)
    }
    Column(Modifier.fillMaxSize().background(Navy).padding(20.dp).verticalScroll(rememberScrollState())) {
        Title("Import Accounts")
        Text("CSV, XLSX, or TXT with Username and Password. The preview shows usernames only.", color = Muted)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { picker.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)) {
            Text("Choose file")
        }
        names.forEach { Text(it, color = Color.White, modifier = Modifier.padding(top = 6.dp)) }
        if (names.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                drafts.forEach { model.addAccount(it.username, it.password) }
                drafts = emptyList()
                names = emptyList()
                Toast.makeText(context, "Imported into encrypted storage", Toast.LENGTH_SHORT).show()
            }, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)) { Text("Confirm import") }
        }
        Back(nav)
    }
}

@Composable
fun ProcessingScreen(nav: NavHostController, model: AppViewModel) {
    val scope = rememberCoroutineScope()
    val webView = remember { WebView(nav.context) }
    var message by remember { mutableStateOf("Ready") }
    var twoFactor by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val accounts = ProcessHolder.accounts
        if (accounts.isEmpty()) {
            message = "No accounts queued"
            return@LaunchedEffect
        }
        val bridge = WebViewBridge(webView) { model.loginUrl }
        val controller = SessionController(
            bridge = bridge,
            awaitTwoFactor = { username ->
                twoFactor = true
                message = "2FA is showing for $username. Finish it on the page, then continue."
                val gate = CompletableDeferred<Unit>()
                model.twoFactorGate = gate
                gate.await()
                twoFactor = false
            },
            shouldStop = { model.flags.stopped },
            waitIfPaused = {
                while (model.flags.paused && !model.flags.stopped) delay(200)
            },
            onStep = { username, step, detail ->
                val index = accounts.indexOfFirst { it.username == username }.coerceAtLeast(0)
                model.note(index, accounts.size, username, step, detail)
                message = "$step: $detail"
            },
        )
        val rows = runCatching { controller.runBatch(accounts, confirmed = true) }.getOrElse { error ->
            listOf(AddressRow("", "", "Failed", error.message ?: "Processing failed"))
        }
        model.finish(rows)
        message = "Finished ${rows.count { it.status == "Success" }} of ${rows.size}"
    }
    Column(Modifier.fillMaxSize().background(Navy).padding(12.dp)) {
        Text("Processing", color = Gold, style = MaterialTheme.typography.titleLarge)
        Text(model.progress, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text(model.currentUsername, color = Muted)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = ActionBlue)
        UiCatalog.stepNames.forEach { step ->
            val detail = model.steps[step] ?: "Waiting"
            val color = when {
                detail.contains("Success", true) || detail == "Success" -> SuccessGreen
                detail.contains("Fail", true) || detail.contains("Blocked", true) || detail.contains("mismatch", true) -> DangerRed
                else -> Color.White
            }
            Text("$step: $detail", color = color)
        }
        Text(message, color = Gold, modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { model.flags.paused = !model.flags.paused }) {
                Text(if (model.flags.paused) "Resume" else "Pause", color = Color.White)
            }
            OutlinedButton(onClick = { model.flags.stopped = true }) { Text("Stop", color = DangerRed) }
            if (twoFactor) {
                Button(onClick = { model.twoFactorGate?.complete(Unit) }, colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)) {
                    Text("Continue")
                }
            }
        }
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp))
        TextButton(onClick = { nav.navigate("results") }) { Text("Results") }
    }
}

@Composable
fun ResultsScreen(nav: NavHostController, model: AppViewModel) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val kind = pending
        pending = null
        if (uri == null || kind == null) return@rememberLauncherForActivityResult
        val bytes = when (kind) {
            "csv" -> ExportFormatter.csv(model.results).toByteArray()
            "txt" -> ExportFormatter.txt(model.results).toByteArray()
            else -> ExportFormatter.xlsx(model.results)
        }
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
    }
    Column(Modifier.fillMaxSize().background(Navy).padding(16.dp).verticalScroll(rememberScrollState())) {
        Title("Results")
        Text("Total ${model.results.size}   Successful ${model.results.count { it.status == "Success" }}   Failed ${model.results.count { it.status != "Success" }}", color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { pending = "csv"; exporter.launch("bep20-addresses.csv") }, colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)) { Text("CSV") }
            Button(onClick = { pending = "xlsx"; exporter.launch("bep20-addresses.xlsx") }, colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)) { Text("XLSX") }
            Button(onClick = { pending = "txt"; exporter.launch("bep20-addresses.txt") }, colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)) { Text("TXT") }
        }
        model.results.forEach { row ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { model.selected = row; nav.navigate("detail") },
                colors = CardDefaults.cardColors(containerColor = CardNavy),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(row.username, color = Gold)
                    Text(row.bep20Address.ifBlank { "No address" }, color = Color.White)
                    Text(row.status, color = if (row.status == "Success") SuccessGreen else DangerRed)
                    if (row.error.isNotBlank()) Text(row.error, color = Muted)
                }
            }
        }
        Back(nav)
    }
}

@Composable
fun DetailScreen(nav: NavHostController, model: AppViewModel) {
    val row = model.selected
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().background(Navy).padding(20.dp).verticalScroll(rememberScrollState())) {
        Title("Address")
        if (row == null) {
            Text("No address selected", color = Color.White)
        } else {
            Text(row.username, color = Muted)
            Text(row.bep20Address.ifBlank { "No address" }, color = Color.White)
            Text("Network: BNB Smart Chain / BEP20", color = Gold, modifier = Modifier.padding(vertical = 8.dp))
            Text(Bep20Warning, color = DangerRed, fontWeight = FontWeight.Bold)
            if (row.bep20Address.startsWith("0x")) {
                Spacer(Modifier.height(12.dp))
                QrImage(row.bep20Address)
            }
            Button(onClick = {
                clipboard.setText(AnnotatedString(row.bep20Address))
            }, colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)) {
                androidx.compose.material3.Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
                Text("Copy Address")
            }
            if (row.error.isNotBlank()) Text(row.error, color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
        Back(nav)
    }
}

@Composable
fun HistoryScreen(nav: NavHostController, model: AppViewModel) {
    Column(Modifier.fillMaxSize().background(Navy).padding(16.dp).verticalScroll(rememberScrollState())) {
        Title("History")
        if (model.history.isEmpty()) Text("No previous batches", color = Muted)
        model.history.forEach { batch ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                model.openHistory(batch)
                nav.navigate("results")
            }, colors = CardDefaults.cardColors(containerColor = CardNavy)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${batch.successful} successful / ${batch.total} accounts", color = Color.White)
                    Text(batch.id, color = Muted)
                }
            }
        }
        Back(nav)
    }
}

@Composable
fun SettingsScreen(nav: NavHostController, model: AppViewModel) {
    var url by remember { mutableStateOf(model.loginUrl) }
    var confirmClearAccounts by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Navy).padding(20.dp).verticalScroll(rememberScrollState())) {
        Title("Settings")
        Text("This app only reads a USDT BEP20 deposit address. It stops if the site shows a security, legal, or regional block. 2FA pauses for you. There is no withdraw, transfer, or trade action.", color = Muted)
        Spacer(Modifier.height(12.dp))
        Field("Login URL", url) { url = it }
        Button(onClick = { model.setLoginUrl(url) }, colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)) { Text("Save URL") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { confirmClearAccounts = true }) { Text("Clear saved accounts", color = DangerRed) }
        OutlinedButton(onClick = { confirmClearHistory = true }) { Text("Clear history", color = DangerRed) }
        Back(nav)
    }
    if (confirmClearAccounts) {
        AlertDialog(
            onDismissRequest = { confirmClearAccounts = false },
            title = { Text("Clear accounts?") },
            text = { Text("Encrypted usernames and passwords on this device will be deleted.") },
            confirmButton = { TextButton(onClick = { model.clearAccounts(); confirmClearAccounts = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClearAccounts = false }) { Text("Cancel") } },
        )
    }
    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear history?") },
            text = { Text("Saved address batches will be deleted from this device.") },
            confirmButton = { TextButton(onClick = { model.clearHistory(); confirmClearHistory = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Title(text: String) {
    Text(text, color = Gold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Field(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
    )
}

@Composable
private fun Back(nav: NavHostController) {
    TextButton(onClick = { nav.popBackStack() }) { Text("Back") }
}

@Composable
private fun QrImage(content: String) {
    val image = remember(content) {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        for (x in 0 until 512) {
            for (y in 0 until 512) {
                bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap.asImageBitmap()
    }
    androidx.compose.foundation.Image(bitmap = image, contentDescription = "QR code", modifier = Modifier.height(180.dp).fillMaxWidth())
}
