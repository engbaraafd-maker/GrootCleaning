package com.groot.cleaning.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.groot.cleaning.automation.*
import com.groot.cleaning.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.ar = Prefs(this).arabic
        setContent { GcTheme { Root() } }
    }
}

private fun toast(c: Context, m: String) = Toast.makeText(c, m, Toast.LENGTH_SHORT).show()

@Composable
fun Root() {
    var tab by remember { mutableIntStateOf(0) }
    val items: List<Triple<ImageVector, String, String>> = listOf(
        Triple(Icons.Default.Home, "Home", "الرئيسية"),
        Triple(Icons.Default.Build, "Apps", "التطبيقات"),
        Triple(Icons.Default.AccountCircle, "Accounts", "الحسابات"),
        Triple(Icons.Default.List, "Logs", "السجل"),
        Triple(Icons.Default.Settings, "Settings", "الإعدادات")
    )
    CompositionLocalProvider(LocalLayoutDirection provides if (L.ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Scaffold(
            containerColor = GC.backgroundPrimary,
            bottomBar = {
                NavigationBar(containerColor = GC.surface) {
                    items.forEachIndexed { i, it ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(it.first, null) },
                            label = { Text(tr(it.second, it.third), fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GC.textPrimary, selectedTextColor = GC.textPrimary,
                                indicatorColor = GC.primary,
                                unselectedIconColor = GC.textSecondary, unselectedTextColor = GC.textSecondary
                            )
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen()
                    1 -> AppsScreen()
                    2 -> AccountsScreen()
                    3 -> LogsScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GC.surface, contentColor = GC.textPrimary),
        border = BorderStroke(1.dp, GC.divider)
    ) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
fun Sub(text: String) = Text(text, color = GC.textSecondary, fontSize = 13.sp)

// ---------------------------------------------------------------- HOME

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val dao = remember { GcDb.get(ctx).dao() }
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val ui by Bridge.ui.collectAsState()
    val a11y by Bridge.a11yEnabled.collectAsState()
    val failed by Bridge.failed.collectAsState()
    val apps by dao.apps().collectAsState(emptyList())
    val accs by dao.accounts().collectAsState(emptyList())
    val pendingKeys = prefs.pending.lines().filter { it.isNotBlank() }

    fun begin(tasks: List<Task>) {
        val s = Bridge.service
        when {
            s == null -> toast(ctx, tr("Enable the Accessibility service first", "فعّل خدمة إمكانية الوصول أولًا"))
            tasks.isEmpty() -> toast(ctx, tr("Nothing selected", "لم يتم اختيار شيء"))
            else -> s.start(tasks)
        }
    }

    val sum = ui.summary
    val (status, statusColor) = when {
        !a11y -> tr("ACCESSIBILITY DISABLED", "الأتمتة غير مفعلة") to GC.error
        ui.running -> tr("RUNNING", "قيد التنفيذ") to GC.primary
        sum != null && (sum.failed + sum.user + sum.uncertain) > 0 -> tr("COMPLETED WITH ERRORS", "اكتمل مع أخطاء") to GC.warning
        else -> tr("READY", "جاهز") to GC.success
    }
    val lastRun = if (prefs.lastRun == 0L) "-" else SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(prefs.lastRun))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("GrootCleaning", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Sub("App & Account Automation")
        Panel {
            Sub("SYSTEM STATUS")
            Text(status, color = statusColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (prefs.simulation) Text(tr("Simulation mode is ON: nothing will be deleted", "وضع المحاكاة مفعّل: لن يُحذف شيء"), color = GC.warning, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { Panel { Sub(tr("Applications", "التطبيقات")); Text("${apps.size}", fontSize = 22.sp) } }
            Box(Modifier.weight(1f)) { Panel { Sub(tr("Accounts", "الحسابات")); Text("${accs.size}", fontSize = 22.sp) } }
        }
        Panel {
            Sub(tr("Last Run", "آخر تشغيل")); Text("$lastRun  ·  ${prefs.lastResult}")
        }
        Panel {
            Sub("PREFLIGHT")
            fun line(ok: Boolean, t: String) = "${if (ok) "✓" else "✕"} $t"
            Text(line(a11y, tr("Accessibility Service", "خدمة إمكانية الوصول")), color = if (a11y) GC.success else GC.error)
            Text(line(apps.isNotEmpty() || accs.isNotEmpty(), tr("Selected apps/accounts", "التطبيقات/الحسابات المحددة")),
                color = if (apps.isNotEmpty() || accs.isNotEmpty()) GC.success else GC.error)
        }
        if (ui.running) {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GC.primary)) { Text("CLEANING...", fontSize = 18.sp) }
            OutlinedButton(onClick = { Bridge.service?.stop() }, modifier = Modifier.fillMaxWidth()) { Text("STOP") }
        } else {
            Button(
                onClick = { scope.launch { begin(TaskBuilder.all(dao)) } },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GC.primary)
            ) { Text(tr("START CLEANING", "ابدأ التنظيف"), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            if (pendingKeys.isNotEmpty()) {
                OutlinedButton(onClick = { scope.launch { begin(TaskBuilder.fromKeys(dao, pendingKeys)) } }, modifier = Modifier.fillMaxWidth()) {
                    Text("${tr("Resume", "استكمال")} (${pendingKeys.size})")
                }
            }
        }
        if (ui.running || ui.steps.isNotEmpty()) {
            Panel {
                val done = ui.steps.count { it.status != StepStatus.PENDING && it.status != StepStatus.RUNNING }
                val frac = if (ui.steps.isEmpty()) 0f else done / ui.steps.size.toFloat()
                val prog = ((ui.index - 1).coerceAtLeast(0) + frac) / ui.total.coerceAtLeast(1)
                Text(ui.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Sub("${ui.index} / ${ui.total}")
                LinearProgressIndicator(progress = prog.coerceIn(0f, 1f), color = GC.primary, trackColor = GC.divider, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                ui.steps.forEach { s ->
                    val (sym, col) = when (s.status) {
                        StepStatus.SUCCESS -> "✓" to GC.success
                        StepStatus.RUNNING -> "⟳" to GC.primary
                        StepStatus.PENDING -> "○" to GC.textSecondary
                        StepStatus.FAILED -> "✕" to GC.error
                        StepStatus.UNCERTAIN -> "?" to GC.warning
                        StepStatus.REQUIRES_USER_ACTION -> "⚠" to GC.warning
                        StepStatus.SKIPPED -> "⏭" to GC.textSecondary
                    }
                    Text("$sym ${s.name}${if (s.detail.isNotBlank()) "  — " + s.detail else ""}", color = col, fontSize = 14.sp)
                }
            }
        }
        if (!ui.running && sum != null) {
            Panel {
                Text(if (ui.stopped) tr("Stopped", "تم الإيقاف") else "Cleaning Complete", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("✓ Successful: ${sum.success}", color = GC.success)
                Text("✕ Failed: ${sum.failed}", color = GC.error)
                Text("⚠ User Action: ${sum.user}", color = GC.warning)
                Text("? Unverified: ${sum.uncertain}", color = GC.warning)
                Text("⏭ Skipped: ${sum.skipped}", color = GC.textSecondary)
                if (failed.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { begin(failed) }, colors = ButtonDefaults.buttonColors(containerColor = GC.primary)) { Text("Retry Failed (${failed.size})") }
                }
            }
        }
        ui.userAction?.let { msg ->
            Panel {
                Text(tr("This step needs your action", "تحتاج هذه الخطوة إلى تدخل المستخدم"), color = GC.warning, fontWeight = FontWeight.Bold)
                Sub(msg)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) }) { Text("OPEN SETTINGS") }
            }
        }
    }
}

// ---------------------------------------------------------------- APPS

private class AppRow(val pkg: String, val label: String, val icon: ImageBitmap)

private fun loadApps(ctx: Context): List<AppRow> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { it.activityInfo.packageName }.distinct()
        .filter { it != ctx.packageName }
        .mapNotNull { p ->
            runCatching {
                val info = pm.getApplicationInfo(p, 0)
                val bmp: Bitmap = pm.getApplicationIcon(p).toBitmap(72, 72)
                AppRow(p, pm.getApplicationLabel(info).toString(), bmp.asImageBitmap())
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
}

private fun nextOp(op: String) = when (op) { "CACHE" -> "DATA"; "DATA" -> "BOTH"; else -> "CACHE" }
private fun opLabel(op: String) = when (op) { "CACHE" -> "Clear Cache"; "DATA" -> "Clear Data"; else -> "Clear Data + Cache" }

@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    val dao = remember { GcDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<AppRow>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    val sel by dao.apps().collectAsState(emptyList())
    LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { loadApps(ctx) } }
    val selMap = sel.associateBy { it.packageName }
    val list = all.filter { q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(tr("Applications", "التطبيقات"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(tr("Search", "بحث")) })
        Row {
            TextButton(onClick = { scope.launch { list.forEach { if (it.pkg !in selMap) dao.upsertApp(SelectedApp(it.pkg, it.label, "CACHE")) } } }) { Text(tr("Select All (Cache)", "تحديد الكل (كاش)")) }
            TextButton(onClick = { scope.launch { if (q.isBlank()) dao.clearApps() else list.forEach { dao.deleteApp(it.pkg) } } }) { Text(tr("Unselect All", "إلغاء الكل")) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.pkg }) { a ->
                val s = selMap[a.pkg]
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(a.icon, null, Modifier.size(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, fontWeight = FontWeight.SemiBold)
                            Sub(a.pkg)
                            if (s != null) Text(opLabel(s.operation) + "  ⇄", color = GC.success, fontSize = 13.sp,
                                modifier = Modifier.clickable { scope.launch { dao.upsertApp(s.copy(operation = nextOp(s.operation))) } })
                        }
                        Checkbox(checked = s != null, onCheckedChange = { on ->
                            scope.launch { if (on) dao.upsertApp(SelectedApp(a.pkg, a.label, "CACHE")) else dao.deleteApp(a.pkg) }
                        }, colors = CheckboxDefaults.colors(checkedColor = GC.primary))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- ACCOUNTS

@Composable
fun AccountsScreen() {
    val ctx = LocalContext.current
    val dao = remember { GcDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    val accs by dao.accounts().collectAsState(emptyList())
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Accounts", "الحسابات"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Panel {
            Text(tr("Removing an account may affect linked apps and services.", "إزالة الحساب من الجهاز قد تؤثر على التطبيقات والخدمات المرتبطة به."), color = GC.warning, fontSize = 13.sp)
            Sub(tr("Android 8+ does not let apps list other accounts. Type the account text exactly as shown in Settings (e.g. the email).", "لا يسمح Android بقراءة الحسابات، اكتب نص الحساب كما يظهر في الإعدادات (مثل البريد)."))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text("name@gmail.com") })
            IconButton(onClick = { val v = input.trim(); if (v.isNotEmpty()) { scope.launch { dao.upsertAccount(SelectedAccount(v)) }; input = "" } }) {
                Icon(Icons.Default.Add, null)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(accs, key = { it.label }) { a ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.label, Modifier.weight(1f))
                        IconButton(onClick = { scope.launch { dao.deleteAccount(a.label) } }) { Icon(Icons.Default.Delete, null, tint = GC.error) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- LOGS

@Composable
fun LogsScreen() {
    val ctx = LocalContext.current
    val dao = remember { GcDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    val logs by dao.logs().collectAsState(emptyList())
    var open by remember { mutableStateOf<Long?>(null) }
    val fmt = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activity Log", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { scope.launch { dao.clearLogs() } }) { Text(tr("Clear", "مسح")) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(logs, key = { it.id }) { l ->
                val col = when (l.status) {
                    "SUCCESS", "SIMULATED" -> GC.success
                    "FAILED" -> GC.error
                    else -> GC.warning
                }
                Box(Modifier.clickable { open = if (open == l.id) null else l.id }) {
                    Panel {
                        Text(l.target, fontWeight = FontWeight.SemiBold)
                        Text("${l.operation} · ${l.status}", color = col, fontSize = 13.sp)
                        Sub("${fmt.format(Date(l.time))} · ${"%.1f".format(l.durationMs / 1000.0)}s")
                        if (l.message.isNotBlank()) Sub(l.message)
                        if (open == l.id) { Spacer(Modifier.height(6.dp)); Text(l.steps, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- SETTINGS

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val a11y by Bridge.a11yEnabled.collectAsState()
    var sim by remember { mutableStateOf(prefs.simulation) }
    var cont by remember { mutableStateOf(prefs.continueAfterFailure) }
    var timeout by remember { mutableIntStateOf(prefs.timeoutSec) }
    var retries by remember { mutableIntStateOf(prefs.retries) }
    var delayS by remember { mutableIntStateOf((prefs.delayMs / 100).toInt()) }
    var verif by remember { mutableStateOf(prefs.verification) }

    @Composable
    fun SwitchRow(label: String, v: Boolean, on: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f))
            Switch(checked = v, onCheckedChange = on, colors = SwitchDefaults.colors(checkedTrackColor = GC.primary))
        }
    }
    @Composable
    fun Stepper(label: String, v: Int, unit: String, dec: () -> Unit, inc: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f))
            TextButton(onClick = dec) { Text("−") }
            Text("$v$unit")
            TextButton(onClick = inc) { Text("+") }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Settings", "الإعدادات"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Panel {
            Text("Accessibility Service", fontWeight = FontWeight.SemiBold)
            Text(if (a11y) "✓ Enabled" else "✕ Disabled", color = if (a11y) GC.success else GC.error)
            Sub(tr("GrootCleaning needs the Accessibility service to navigate Android Settings and run the steps automatically.",
                "GrootCleaning يحتاج إلى خدمة إمكانية الوصول حتى يتمكن من التنقل داخل إعدادات Android وتنفيذ الخطوات تلقائيًا."))
            Spacer(Modifier.height(8.dp))
            Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                colors = ButtonDefaults.buttonColors(containerColor = GC.primary)) { Text("Open Accessibility Settings") }
        }
        Panel {
            SwitchRow(tr("Arabic UI", "الواجهة بالعربية"), L.ar) { L.ar = it; prefs.arabic = it }
            SwitchRow("Simulation Mode", sim) { sim = it; prefs.simulation = it }
            SwitchRow("Continue after failure", cont) { cont = it; prefs.continueAfterFailure = it }
            Stepper("Timeout", timeout, "s", { timeout = (timeout - 1).coerceAtLeast(5); prefs.timeoutSec = timeout }, { timeout = (timeout + 1).coerceAtMost(60); prefs.timeoutSec = timeout })
            Stepper("Retry count", retries, "", { retries = (retries - 1).coerceAtLeast(1); prefs.retries = retries }, { retries = (retries + 1).coerceAtMost(8); prefs.retries = retries })
            Stepper("Delay between tasks", delayS * 100, "ms", { delayS = (delayS - 2).coerceAtLeast(2); prefs.delayMs = delayS * 100L }, { delayS = (delayS + 2).coerceAtMost(50); prefs.delayMs = delayS * 100L })
        }
        Panel {
            Text("Verification Level", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("BASIC", "STANDARD", "STRICT").forEach { lv ->
                    if (verif == lv) Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = GC.primary), contentPadding = PaddingValues(horizontal = 10.dp)) { Text(lv, fontSize = 12.sp) }
                    else OutlinedButton(onClick = { verif = lv; prefs.verification = lv }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(lv, fontSize = 12.sp) }
                }
            }
        }
    }
}
