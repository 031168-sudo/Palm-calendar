package ru.palmdate.app.ui

import ru.palmdate.app.str
import ru.palmdate.app.CalState
import ru.palmdate.app.ViewMode
import ru.palmdate.app.model.L10n
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.DisabledByDefault
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.icon
import ru.palmdate.app.model.color
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.OutcomeKind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.ui.theme.Palm
import ru.palmdate.app.weekStart
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import ru.palmdate.app.time.*

/* ---------- Заголовок: вкладка + полоса дней недели или стрелки периода ---------- */

/** Вкладка с косым правым краем — фирменный заголовок приложений Palm. */
private val TabShape = GenericShape { size, _ ->
    val slant = size.height * 0.55f
    moveTo(0f, 0f)
    lineTo(size.width - slant, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/** Широкий экран: планшет или разложенная раскладушка-книжка (от 600 dp). */
@Composable
fun isWide(): Boolean = windowWidthDp() >= 600


/**
 * Раскладка экрана: на телефоне — только main; на широком экране справа колонка подробностей (pane)
 * или подсказка. На раскладушке колонки делят экран пополам — ровно по сгибу.
 */
@Composable
fun AdaptiveFrame(pane: (@Composable () -> Unit)?, main: @Composable () -> Unit) {
    if (!isWide()) { main(); return }
    val screen = windowWidthDp()
    val hinge = LocalHinge.current?.takeIf { it.vertical }
    val density = LocalDensity.current
    Row(Modifier.fillMaxSize().background(Palm.paper)) {
        if (hinge != null) {
            // Книжка: колонки ровно по сгибу, на сам сгиб ничего не попадает
            val left = with(density) { hinge.bounds.left.toDp() }
            val gap = with(density) { (hinge.bounds.right - hinge.bounds.left).toDp() }
            Box(Modifier.width(left).fillMaxHeight()) { main() }
            if (gap > 0.dp) Spacer(Modifier.width(gap)) else Box(Modifier.width(1.dp).fillMaxHeight().background(Palm.rule))
            Box(Modifier.weight(1f).fillMaxHeight()) { if (pane != null) pane() else PaneHint() }
        } else {
            val paneWidth = if (screen < 840) (screen / 2).dp else 420.dp
            Box(Modifier.weight(1f).fillMaxHeight()) { main() }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Palm.rule))
            Box(Modifier.width(paneWidth).fillMaxHeight()) { if (pane != null) pane() else PaneHint() }
        }
    }
}

/** Пустая правая колонка: подсказка. */
@Composable
private fun PaneHint() {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.EventNote, null, tint = Palm.rule, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            str("day_pane_hint"),
            style = Palm.body, color = Palm.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Что делают нажатия на главном экране. */
class MainActions(
    val onSelect: (LocalDate) -> Unit = {},
    val onShiftWeek: (Int) -> Unit = {},
    val onShiftPeriod: (Int) -> Unit = {},
    val onTitle: () -> Unit = {},
    val onSlot: (LocalDate, Int) -> Unit = { _, _ -> },
    val onIcon: (PalmEvent) -> Unit = {},
    val onEvent: (PalmEvent) -> Unit = {},
    val onDay: (LocalDate) -> Unit = {},
    val onMonth: (LocalDate) -> Unit = {},
    val onNew: () -> Unit = {},
    val onToday: () -> Unit = {},
    val onGoTo: () -> Unit = {},
    val onStats: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onMode: (ViewMode) -> Unit = {},
)

/**
 * Главный экран без данных и окон: шапка с датой, выбранный вид, нижняя панель.
 * Отдельно — чтобы его можно было нарисовать в тестах со своими событиями.
 */
@Composable
fun MainLayout(state: CalState, actions: MainActions, headerModifier: Modifier = Modifier, showBar: Boolean = true) {
    Column(Modifier.fillMaxSize().background(Palm.paper)) {
        Header(
            modifier = headerModifier,
            state = state,
            onSelect = actions.onSelect,
            onShiftWeek = actions.onShiftWeek,
            onShiftPeriod = actions.onShiftPeriod,
            onTitle = actions.onTitle,
        )

        // Свайп влево — следующий период, вправо — предыдущий. Страница уезжает в сторону свайпа.
        val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
        AnimatedContent(
            targetState = state,
            contentKey = { it.pageKey },
            transitionSpec = {
                if (targetState.mode != initialState.mode) {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                } else {
                    val forward = targetState.range.first > initialState.range.first
                    (slideInHorizontally(tween(220)) { w -> if (forward) w else -w } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(160)))
                }
            },
            label = "page",
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = {
                            when {
                                dx < -swipeThreshold -> actions.onShiftPeriod(1)
                                dx > swipeThreshold -> actions.onShiftPeriod(-1)
                            }
                        },
                        onHorizontalDrag = { change, amount ->
                            dx += amount
                            change.consume()
                        },
                    )
                },
        ) { page ->
            when (page.mode) {
                ViewMode.DAY -> DayBody(
                    date = page.date,
                    events = page.events,
                    modifier = Modifier.fillMaxSize(),
                    onSlot = { hour -> actions.onSlot(page.date, hour) },
                    onIcon = actions.onIcon,
                    onEvent = actions.onEvent,
                )
                ViewMode.AGENDA -> AgendaView(page.date, page.events, onDay = actions.onDay, onIcon = actions.onIcon, onEvent = actions.onEvent)
                ViewMode.WEEK -> WeekView(page.date, page.events, onDay = actions.onDay, onEvent = actions.onEvent)
                ViewMode.MONTH -> MonthView(page.date, page.events, onDay = actions.onDay)
                ViewMode.YEAR -> YearView(page.date, page.yearDays, onDay = actions.onDay, onMonth = actions.onMonth)
            }
        }

        if (showBar) ActionsBar(state, actions)
    }
}

@Composable
private fun ActionsBar(state: CalState, actions: MainActions) = ButtonBar(
    mode = state.mode,
    onNew = actions.onNew,
    onToday = actions.onToday,
    onGoTo = actions.onGoTo,
    onStats = actions.onStats,
    onSettings = actions.onSettings,
    onMode = actions.onMode,
)

/** Где события показываются колонкой/половиной, а не панелью снизу: широкий экран или «ноутбук». */
@Composable
fun usesPane(): Boolean = isWide() || LocalHinge.current?.tabletop == true

/**
 * Весь экран календаря с учётом устройства:
 * «ноутбук» (полусложен, стоит на столе) — сверху календарь, снизу кнопки и карточка события;
 * широкий экран — календарь слева, карточка справа (у книжки — ровно по сгибу);
 * телефон — только календарь.
 */
@Composable
fun CalendarFrame(
    state: CalState,
    actions: MainActions,
    pane: (@Composable () -> Unit)?,
    headerModifier: Modifier = Modifier,
) {
    val hinge = LocalHinge.current
    if (hinge?.tabletop == true) {
        val density = LocalDensity.current
        val top = with(density) { hinge.bounds.top.toDp() }
        val gap = with(density) { (hinge.bounds.bottom - hinge.bounds.top).toDp() }
        Column(Modifier.fillMaxSize().background(Palm.paper)) {
            // Верхняя половина — смотреть: шапка и выбранный вид
            Box(Modifier.fillMaxWidth().height(top)) {
                MainLayout(state, actions, headerModifier, showBar = false)
            }
            Spacer(Modifier.height(gap))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palm.rule))
            // Нижняя половина — нажимать: карточка события и кнопки
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (pane != null) pane() else TabletopHint()
            }
            ActionsBar(state, actions)
        }
        return
    }
    AdaptiveFrame(pane = pane) { MainLayout(state, actions, headerModifier) }
}

@Composable
private fun TabletopHint() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            str("day_tabletop_hint"),
            style = Palm.body, color = Palm.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun tabTitle(s: CalState, compact: Boolean = false): String = tabTitle(s.mode, s.date, compact)

/** Дата цифрами: «26.10» / «10/26». */
private fun LocalDate.numeric(): String =
    if (L10n.language == "ru") "$dayOfMonth.${monthNumber.toString().padStart(2, '0')}" else "$monthNumber/$dayOfMonth"

/**
 * Название вкладки. [compact] — короткий вариант для узкого экрана (сложенная раскладушка, крупный шрифт),
 * когда полное название пришлось бы сильно уменьшать.
 */
private fun tabTitle(mode: ViewMode, d: LocalDate, compact: Boolean = false): String {
    if (compact) return when (mode) {
        ViewMode.DAY -> d.dayMonth()
        ViewMode.AGENDA -> str("view_agenda_from", d.numeric())
        ViewMode.WEEK -> { val a = weekStart(d); "${a.numeric()}–${a.plusDays(6).numeric()}" }
        ViewMode.MONTH -> "${d.shortMonth().replaceFirstChar { it.uppercase() }} ${d.year}"
        ViewMode.YEAR -> "${d.year}"
    }
    return when (mode) {
        ViewMode.DAY -> d.pretty()
        ViewMode.AGENDA -> str("view_agenda_from", d.dayMonth())
        ViewMode.WEEK -> {
            val a = weekStart(d)
            val b = a.plusDays(6)
            if (a.month != b.month) "${a.dayMonth()} – ${b.dayMonth()}"
            else if (L10n.language == "ru") "${a.dayOfMonth}–${b.dayOfMonth} ${b.shortMonth()}"
            else "${a.shortMonth()} ${a.dayOfMonth}–${b.dayOfMonth}"
        }
        ViewMode.MONTH -> "${d.monthTitle()} ${d.year}"
        ViewMode.YEAR -> "${d.year}"
    }
}

@Composable
private fun Header(
    modifier: Modifier = Modifier,
    state: CalState,
    onSelect: (LocalDate) -> Unit,
    onShiftWeek: (Int) -> Unit,
    onShiftPeriod: (Int) -> Unit,
    onTitle: () -> Unit,
) {
    val date = state.date
    val today = LocalDate.now()
    // Полоска дней — только в «Дне» и «Повестке»; в «Неделе» видна вся неделя, там только стрелки
    val showStrip = state.mode == ViewMode.DAY || state.mode == ViewMode.AGENDA
    val byWeek = showStrip || state.mode == ViewMode.WEEK

    // Ширина вкладки постоянная для каждого вида: по самому длинному названию этого вида (+ запас),
    // чтобы вкладка не «прыгала» при листании дат. Блок справа (стрелки + дни недели) — одной ширины во всех видах.
    val fullWidth = rememberTabTextWidth(state.mode, compact = false)
    val compactWidth = rememberTabTextWidth(state.mode, compact = true)
    // Высота вкладки одна во всех видах (по полному размеру шрифта), чтобы она не ездила вверх-вниз
    val tabHeight = rememberTabHeight()
    // День и Список: стрелки по краям дней недели (блок постоянной ширины); Неделя/Месяц/Год: стрелки рядом справа
    val rightBlock = if (showStrip) RIGHT_BLOCK else ARROWS_BLOCK
    Column(modifier.fillMaxWidth().background(Palm.paper).statusBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tabPad = TAB_PAD_START + TAB_PAD_END
            val roomForTab = maxWidth - rightBlock - 4.dp
            // Полное название, если его не придётся уменьшать сильнее чем до 75%; иначе — короткое
            val compact = (roomForTab - tabPad) < fullWidth * 0.75f
            val titleWidth = if (compact) compactWidth else fullWidth
            val tabWidth = minOf(titleWidth + tabPad, roomForTab).coerceAtLeast(tabPad + 24.dp)
            // Не помещается (узкий телефон, крупный шрифт) — чуть уменьшаем шрифт, но не обрезаем название
            val fit = ((tabWidth - tabPad) / titleWidth).coerceIn(0.6f, 1f)
            val style = if (fit < 1f) Palm.title.copy(fontSize = Palm.title.fontSize * fit) else Palm.title
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            // Тап по вкладке — str("go_to") к дате (там же кнопка str("today_cap"))
            Box(
                Modifier
                    .width(tabWidth)
                    .height(tabHeight)
                    .clip(TabShape)
                    .background(Palm.navy)
                    .clickable(onClick = onTitle)
                    .padding(start = TAB_PAD_START, end = TAB_PAD_END),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(tabTitle(state, compact), style = style, color = Color.White, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.width(rightBlock), verticalAlignment = Alignment.CenterVertically) {
            Arrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, str("nav_back")) {
                if (byWeek) onShiftWeek(-1) else onShiftPeriod(-1)
            }
            if (!showStrip) Spacer(Modifier.weight(1f))
            if (showStrip) {
                val monday = weekStart(date)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (i in 0L..6L) {
                        val d = monday.plusDays(i)
                        // В виде "Неделя" подсвечена вся неделя, в остальных — выбранный день
                        val selected = if (state.mode == ViewMode.WEEK) false else d == date
                        val letter = dayOfWeekName(d.dayOfWeek, NameStyle.NARROW).uppercase()
                        Box(
                            Modifier
                                .size(width = 24.dp, height = 28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        selected -> Palm.navy
                                        state.mode == ViewMode.WEEK -> Palm.navyLight
                                        else -> Color.Transparent
                                    },
                                )
                                .clickable { onSelect(d) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                letter,
                                style = Palm.time.copy(
                                    textDecoration = if (d == today && !selected) TextDecoration.Underline else null,
                                ),
                                color = when {
                                    selected -> Color.White
                                    d.dayOfWeek.value >= 6 -> Palm.nowLine
                                    else -> Palm.ink
                                },
                            )
                        }
                    }
                }
            }
            Arrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, str("nav_forward")) {
                if (byWeek) onShiftWeek(1) else onShiftPeriod(1)
            }
            }
            Spacer(Modifier.width(4.dp))
        }
        }
        // Синяя линия под вкладкой — как граница заголовка в Palm OS
        Box(Modifier.fillMaxWidth().height(2.dp).background(Palm.navy))
    }
}

private val TAB_PAD_START = 12.dp
private val TAB_PAD_END = 26.dp // место под косой край вкладки
/** Стрелки (2 × 32) + 7 дней (24) с промежутками — одинаково во всех видах. */
private val RIGHT_BLOCK = 32.dp * 2 + 24.dp * 7 + 2.dp * 6
/** Неделя/Месяц/Год: две стрелки рядом справа с зазором, чтобы не промахнуться пальцем. */
private val ARROWS_BLOCK = 32.dp * 2 + 16.dp

/** Высота вкладки: строка заголовка полного размера + поля сверху и снизу. Одна для всех видов. */
@Composable
private fun rememberTabHeight(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(density.density, density.fontScale) {
        val px = measurer.measure("Ag", Palm.title, maxLines = 1).size.height
        with(density) { px.toDp() } + 11.dp
    }
}

/**
 * Ширина текста самого длинного названия вкладки для вида [mode] (по всем дням года), с небольшим запасом.
 * Считается один раз на вид / язык / размер шрифта.
 */
@Composable
private fun rememberTabTextWidth(mode: ViewMode, compact: Boolean): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val lang = L10n.language
    return remember(mode, compact, lang, density.density, density.fontScale) {
        val year = LocalDate.now().year
        val titles: Set<String> = if (mode == ViewMode.YEAR) {
            (year - 10..year + 30).map { "$it" }.toSet()
        } else {
            val first = LocalDate(year, 1, 1)
            (0L..370L).map { tabTitle(mode, first.plusDays(it), compact) }.toSet()
        }
        val px = titles.maxOf { t -> measurer.measure(t, Palm.title, maxLines = 1, softWrap = false).size.width }
        with(density) { px.toDp() } + 4.dp
    }
}

@Composable
private fun Arrow(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Icon(
        icon, desc, tint = Palm.navy,
        modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick).padding(4.dp),
    )
}

/* ---------- Вид "День": часовые строки с пунктиром ---------- */

private sealed interface DayRow {
    data class AllDay(val e: PalmEvent) : DayRow
    data class Slot(val hour: Int, val events: List<PalmEvent>) : DayRow
}

@Composable
private fun DayBody(
    date: LocalDate,
    events: List<PalmEvent>,
    modifier: Modifier,
    onSlot: (Int) -> Unit,
    onIcon: (PalmEvent) -> Unit,
    onEvent: (PalmEvent) -> Unit,
) {
    val timed = events.filter { !it.allDay }
    // Адаптивный «День»: мало событий — название показываем целиком (до 3 строк), много — одной строкой
    val titleLines = when {
        timed.size <= 3 -> 3
        timed.size <= 6 -> 2
        else -> 1
    }
    val byHour = timed.groupBy { if (it.start.toLocalDate() < date) 0 else it.start.hour }
    // Показываем все 24 часа; рабочие часы (из настроек) — обычным пунктиром, нерабочие — бледно-зелёным
    val settings = ru.palmdate.app.data.CurrentSettings.current
    val first = 0
    val last = 23
    val allDayEvents = events.filter { it.allDay }
    val rows: List<DayRow> = (first..last).map { DayRow.Slot(it, byHour[it].orEmpty()) }

    val isToday = date == LocalDate.now()
    val nowHour = LocalTime.now().hour
    val listState = rememberLazyListState()
    LaunchedEffect(date) {
        val target = rows.indexOfFirst { it is DayRow.Slot && it.hour == (if (isToday) nowHour else settings.dayFrom) }
        if (target > 0) listState.scrollToItem((target - 1).coerceAtLeast(0))
    }

    // Длительность событий: дорожки слева от плашек и бледная заливка (как скобка длительности в Palm)
    val band = settings.durationBand
    val dayStart = date.atStartOfDay()
    fun minutesOf(dt: LocalDateTime) = minutesBetween(dayStart, dt).toInt().coerceIn(0, 1440)
    class Ev(val e: PalmEvent, val s: Int, val en: Int, val lane: Int)
    val laneEnds = ArrayList<Int>()
    val evs = timed.sortedBy { it.start }.map { e ->
        val st = minutesOf(e.start)
        val en = minutesOf(e.end)
        var lane = laneEnds.indexOfFirst { it <= st }
        if (lane < 0) { laneEnds += 0; lane = laneEnds.size - 1 }
        laneEnds[lane] = maxOf(en, st + 1)
        Ev(e, st, en, minOf(lane, 3))
    }.filter { it.en > it.s }
    val laneCount = if (evs.isEmpty()) 0 else evs.maxOf { it.lane } + 1
    fun decorFor(t: Int, tNext: Int): LaneDecor {
        val spans = evs.mapNotNull { v ->
            if (tNext <= t) {
                if (v.s <= t && v.en > t) LaneSpan(v.lane, Color(v.e.color), 0f, 1f, false, false) else null
            } else {
                val a = maxOf(v.s, t)
                val b = minOf(v.en, tNext)
                if (b > a) LaneSpan(
                    v.lane, Color(v.e.color), (a - t).toFloat() / (tNext - t), (b - t).toFloat() / (tNext - t),
                    startsHere = v.s in t until tNext, endsHere = v.en in (t + 1)..tNext,
                ) else null
            }
        }
        return LaneDecor(spans, laneCount, band)
    }

    Column(modifier.fillMaxWidth()) {
    // События на весь день — закреплены сверху, не прокручиваются вместе с часами
    if (allDayEvents.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().heightIn(max = 132.dp).verticalScroll(rememberScrollState())) {
            allDayEvents.forEach { e ->
                EventLine(e, timeLabel = "•", highlight = false, onIcon, onEvent, decor = LaneDecor(emptyList(), laneCount, band))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palm.rule))
    }
    LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
        items(rows) { row ->
            when (row) {
                is DayRow.AllDay -> {}
                is DayRow.Slot -> {
                    val label = LocalTime.of(row.hour, 0).format(HM)
                    val hl = isToday && row.hour == nowHour
                    val work = row.hour in settings.dayFrom..settings.dayTo
                    val rule = if (work) Palm.rule else Palm.offRule
                    val hourStart = row.hour * 60
                    val hourEnd = hourStart + 60
                    // Время начала каждой строки часа: сама «пустая» строка часа (если нужна) и события
                    val startsOnHour = row.events.firstOrNull()?.start?.minute == 0
                    val needEmpty = row.events.isEmpty() || !startsOnHour
                    val evTimes = row.events.map { if (it.start.toLocalDate() < date) hourStart else it.start.hour * 60 + it.start.minute }
                    val times = (if (needEmpty) listOf(hourStart) else emptyList()) + evTimes
                    fun next(i: Int) = if (i + 1 < times.size) times[i + 1] else hourEnd
                    if (needEmpty) EmptyLine(label, hl, rule, decorFor(times[0], next(0))) { onSlot(row.hour) }
                    row.events.forEachIndexed { i, e ->
                        val ti = i + (if (needEmpty) 1 else 0)
                        val t = if (e.start.minute == 0 && i == 0) label else e.start.format(HM)
                        EventLine(e, t, hl && i == 0 && startsOnHour, onIcon, onEvent, ruleColor = rule, decor = decorFor(times[ti], next(ti)), titleLines = titleLines)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun EmptyLine(label: String, highlight: Boolean, rule: Color, decor: LaneDecor? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .laneDraw(decor)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeLabel(label, highlight)
        decor?.let { Spacer(Modifier.width(it.gutter)) }
        Box(Modifier.weight(1f).height(44.dp).padding(end = 12.dp).dottedRule(rule))
    }
}

/* ---------- Нижняя панель: Новое · иконки видов ---------- */

private val ViewMode.icon: ImageVector
    get() = when (this) {
        ViewMode.AGENDA -> Icons.Outlined.ViewAgenda
        ViewMode.DAY -> Icons.Outlined.ViewDay
        ViewMode.WEEK -> Icons.Outlined.ViewWeek
        ViewMode.MONTH -> Icons.Outlined.CalendarViewMonth
        ViewMode.YEAR -> Icons.Outlined.GridView
    }

@Composable
private fun ButtonBar(
    mode: ViewMode,
    onNew: () -> Unit,
    onToday: () -> Unit,
    onGoTo: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onMode: (ViewMode) -> Unit,
) {
    @Composable
    fun ViewTab(m: ViewMode, modifier: Modifier) {
        val sel = m == mode
        Column(
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (sel) Palm.navy else Color.Transparent)
                .clickable { onMode(m) }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(m.icon, null, tint = if (sel) Color.White else Palm.navy, modifier = Modifier.size(20.dp))
            Text(m.label, style = Palm.small.copy(fontSize = Palm.small.fontSize * 0.85f),
                color = if (sel) Color.White else Palm.navy, maxLines = 1)
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().background(Palm.paper).navigationBarsPadding(),
    ) {
      val wideBar = maxWidth >= 800.dp
      Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palm.rule))
        if (wideBar) {
            // Широкий экран: виды и кнопки — одной строкой
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewMode.entries.forEach { m -> ViewTab(m, Modifier.width(64.dp)) }
                Spacer(Modifier.weight(1f))
                BarTextButton(str("new_event"), filled = true, onClick = onNew, modifier = Modifier.width(104.dp))
                BarTextButton(str("today_cap"), onClick = onToday, modifier = Modifier.width(104.dp))
                BarTextButton(str("go_to"), onClick = onGoTo, modifier = Modifier.width(104.dp))
                Spacer(Modifier.width(10.dp))
                BarIcon(Icons.Outlined.BarChart, str("stats_title"), onStats)
                BarIcon(Icons.Outlined.Settings, str("settings_title"), onSettings)
            }
        } else Column(Modifier.fillMaxWidth()) {
            // Виды: иконка + подпись, на всю ширину
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                ViewMode.entries.forEach { m -> ViewTab(m, Modifier.weight(1f)) }
            }
            // Кнопки, как на Palm
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Все кнопки одной высоты: три с текстом делят ширину поровну, две квадратные — справа
                BarTextButton(str("new_event"), filled = true, onClick = onNew, modifier = Modifier.weight(1f))
                BarTextButton(str("today_cap"), onClick = onToday, modifier = Modifier.weight(1f))
                BarTextButton(str("go_to"), onClick = onGoTo, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp)) // промежуток побольше перед значками
                BarIcon(Icons.Outlined.BarChart, str("stats_title"), onStats)
                BarIcon(Icons.Outlined.Settings, str("settings_title"), onSettings)
            }
        }
      }
    }
}

private val BAR_H = 40.dp

@Composable
private fun BarTextButton(text: String, modifier: Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .height(BAR_H)
            .clip(shape)
            .background(if (filled) Palm.navy else Color.Transparent)
            .border(1.dp, Palm.navy, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Palm.button, color = if (filled) Color.White else Palm.navy, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(BAR_H).clip(RoundedCornerShape(10.dp))
            .border(1.dp, Palm.navy, RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Palm.navy, modifier = Modifier.size(22.dp)) }
}
