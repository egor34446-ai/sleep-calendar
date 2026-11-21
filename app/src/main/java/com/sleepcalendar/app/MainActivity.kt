package com.sleepcalendar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Moon
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val bg = Color(0xFF101522)
private val panel = Color(0xFF1B2333)
private val muted = Color(0xFF8D9AB3)
private val accent = Color(0xFF8F9CFF)
private val fmt = DateTimeFormatter.ofPattern("HH:mm")
private val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))
private val dateFmt = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))

private data class SleepEntry(val start: String, val end: String) {
    fun minutes(): Int = try {
        val a = LocalTime.parse(start, fmt)
        val b = LocalTime.parse(end, fmt)
        var m = Duration.between(a, b).toMinutes().toInt()
        if (m <= 0) m += 24 * 60
        m
    } catch (_: Exception) { 0 }
    fun hours(): Double = minutes() / 60.0
    fun durationText(): String {
        val m = minutes()
        return "${m / 60} ч ${m % 60} мин"
    }
}

private fun formatMinutes(total: Int): String = "${total / 60} ч ${total % 60} мин"
private fun sleepColor(hours: Double): Color = when {
    hours < 6 -> Color(0xFFC96C75)
    hours < 7 -> Color(0xFFD99A54)
    hours < 8 -> Color(0xFFB9AA63)
    hours < 9 -> Color(0xFF3E9C82)
    else -> Color(0xFF6686D6)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SleepCalendarApp() }
    }
}


@Composable
fun SleepCalendarApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("sleep_entries", 0) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var entries by remember {
        mutableStateOf(prefs.all.mapNotNull { (k, v) -> if (v is String) k to v else null }.toMap().mapValues { (_, v) ->
            val p = v.split("|"); SleepEntry(p.getOrElse(0) { "22:30" }, p.getOrElse(1) { "07:00" })
        })
    }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    val today = LocalDate.now()
    val todayEntry = entries[today.toString()]

    MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = accent)) {
        Column(Modifier.fillMaxSize().background(bg)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp)) {
                AppHeader()
                Spacer(Modifier.height(18.dp))
                when (tab) {
                    0 -> CalendarScreen(month, { month = it }, entries, { date -> selectedDate = date; showDialog = true }, todayEntry)
                    1 -> StatisticsScreen(entries, month)
                    else -> HistoryScreen(entries, { date -> selectedDate = date; showDialog = true })
                }
            }
            NavigationBar(containerColor = panel, contentColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("Календарь") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text("Статистика") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.History, null) }, label = { Text("История") })
            }
        }
    }
    if (showDialog && selectedDate != null) {
        val date = selectedDate!!
        val existing = entries[date.toString()]
        var start by remember(date) { mutableStateOf(existing?.start ?: "22:30") }
        var end by remember(date) { mutableStateOf(existing?.end ?: "07:00") }
        var invalid by remember(date) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { showDialog = false }, containerColor = panel,
            title = { Text("Сон · ${date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Время в формате 24 часа, например 22:45", color = muted, fontSize = 12.sp)
                    OutlinedTextField(start, { start = it; invalid = false }, label = { Text("Лёг спать (HH:mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), singleLine = true)
                    OutlinedTextField(end, { end = it; invalid = false }, label = { Text("Проснулся (HH:mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), singleLine = true)
                    val preview = runCatching { SleepEntry(start, end).durationText() }.getOrNull()
                    if (preview != null) Text("Продолжительность: $preview", color = Color.White)
                    if (invalid) Text("Проверь время: нужно указать часы и минуты, например 07:30", color = Color(0xFFFF8B8B), fontSize = 12.sp)
                }
            }, confirmButton = {
                TextButton(onClick = {
                    val valid = runCatching { LocalTime.parse(start, fmt); LocalTime.parse(end, fmt) }.isSuccess
                    if (valid) {
                        val value = SleepEntry(start, end)
                        entries = entries + (date.toString() to value)
                        prefs.edit().putString(date.toString(), "$start|$end").apply()
                        showDialog = false
                    } else invalid = true
                }) { Text("Сохранить") }
            }, dismissButton = {
                Row {
                    if (existing != null) TextButton(onClick = {
                        entries = entries - date.toString(); prefs.edit().remove(date.toString()).apply(); showDialog = false
                    }) { Text("Удалить") }
                    TextButton(onClick = { showDialog = false }) { Text("Отмена") }
                }
            })
    }
}


@Composable
private fun AppHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(44.dp).background(panel, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Moon, null, tint = accent) }
        Column {
            Text("КАЛЕНДАРЬ СНА", color = muted, fontSize = 11.sp, letterSpacing = 1.sp)
            Text("Мой сон", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
    }
}


@Composable
private fun CalendarScreen(month: YearMonth, onMonthChange: (YearMonth) -> Unit, entries: Map<String, SleepEntry>, onSelectDate: (LocalDate) -> Unit, todayEntry: SleepEntry?) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("Сегодня", color = muted, fontSize = 13.sp)
            Text(todayEntry?.durationText() ?: "Нет записи", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(if (todayEntry != null) "${todayEntry.start} → ${todayEntry.end}" else "Добавь сон, чтобы начать вести календарь", color = muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onSelectDate(LocalDate.now()) }, colors = ButtonDefaults.buttonColors(containerColor = accent), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Добавить сон")
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Icon(Icons.Default.ChevronLeft, null, tint = Color.White) }
        Text(month.format(monthFmt).replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Icon(Icons.Default.ChevronRight, null, tint = Color.White) }
    }
    val weekdays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    Row(Modifier.fillMaxWidth()) { weekdays.forEach { Text(it, Modifier.weight(1f), color = muted, fontSize = 12.sp, textAlign = TextAlign.Center) } }
    Spacer(Modifier.height(8.dp))
    val offset = month.atDay(1).dayOfWeek.value - 1
    val totalCells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp), userScrollEnabled = false, verticalArrangement = Arrangement.spacedBy(7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items((0 until totalCells).toList()) { cell ->
            val day = cell - offset + 1
            if (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                val entry = entries[date.toString()]
                val color = entry?.let { sleepColor(it.hours()) } ?: Color(0xFF252D3E)
                Box(Modifier.aspectRatio(1f).background(color, RoundedCornerShape(10.dp)).clickable { onSelectDate(date) }, contentAlignment = Alignment.Center) {
                    Text(day.toString(), color = Color.White, fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                }
            } else Box(Modifier.aspectRatio(1f))
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(Color(0xFFC96C75), "Мало"); LegendDot(Color(0xFFD99A54), "Недостаточно"); LegendDot(Color(0xFF3E9C82), "Хорошо"); LegendDot(Color(0xFF6686D6), "Долго")
    }
    Spacer(Modifier.height(18.dp))
    val monthEntries = entries.filterKeys { it.startsWith(month.toString()) }.values
    val average = monthEntries.map { it.minutes() }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
    SummaryCard("Статистика месяца", if (monthEntries.isEmpty()) "Пока нет данных" else "Среднее: ${formatMinutes(average)}", "Записей: ${monthEntries.size}")
}


@Composable
private fun StatisticsScreen(entries: Map<String, SleepEntry>, month: YearMonth) {
    val monthEntries = entries.filterKeys { it.startsWith(month.toString()) }.values
    val monthAverage = monthEntries.map { it.minutes() }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
    val weekDates = (0..6).map { LocalDate.now().minusDays((6 - it).toLong()) }
    val weekEntries = weekDates.mapNotNull { entries[it.toString()] }
    val weekAverage = weekEntries.map { it.minutes() }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
    SummaryCard("Среднее за 7 дней", if (weekEntries.isEmpty()) "Пока нет данных" else formatMinutes(weekAverage), "Записей за неделю: ${weekEntries.size}")
    Spacer(Modifier.height(12.dp))
    SummaryCard("Среднее за месяц", if (monthEntries.isEmpty()) "Пока нет данных" else formatMinutes(monthAverage), "Записей в ${month.format(monthFmt)}: ${monthEntries.size}")
    Spacer(Modifier.height(12.dp))
    SummaryCard("Всего записей", entries.size.toString(), "Данные сохраняются на этом телефоне")
    Spacer(Modifier.height(18.dp))
    Text("Последние 7 дней", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Spacer(Modifier.height(10.dp))
    weekDates.forEach { date ->
        val entry = entries[date.toString()]
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("ru")), color = muted, modifier = Modifier.width(36.dp))
            Box(Modifier.weight(1f).height(18.dp).background(Color(0xFF252D3E), RoundedCornerShape(6.dp))) {
                if (entry != null) Box(Modifier.fillMaxWidth((entry.hours() / 12.0).coerceIn(0.02, 1.0)).fillMaxHeight().background(sleepColor(entry.hours()), RoundedCornerShape(6.dp)))
            }
            Text(entry?.durationText() ?: "—", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(76.dp), textAlign = TextAlign.End)
        }
    }
}


@Composable
private fun HistoryScreen(entries: Map<String, SleepEntry>, onSelectDate: (LocalDate) -> Unit) {
    Text("История сна", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    if (entries.isEmpty()) {
        SummaryCard("Записей пока нет", "Добавь время сна в календаре", "Здесь появится история")
    } else {
        entries.toList().sortedByDescending { it.first }.forEach { (key, entry) ->
            val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@forEach
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onSelectDate(date) }, colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(sleepColor(entry.hours()), RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(date.format(dateFmt).replaceFirstChar { it.uppercase() }, color = Color.White, fontWeight = FontWeight.Medium)
                        Text("${entry.start} → ${entry.end}", color = muted, fontSize = 12.sp)
                    }
                    Text(entry.durationText(), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


@Composable
private fun SummaryCard(title: String, value: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = muted, fontSize = 13.sp)
            Text(value, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = muted, fontSize = 13.sp)
        }
    }
}


@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(text, color = muted, fontSize = 10.sp)
    }
}
