package org.agpeya.niv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BASE_URL = "https://agpeya.org"
private const val PREFS = "agpeya_niv_prefs"
private const val PREF_DARK = "dark"
private const val PREF_LANGUAGE = "language"
private const val PREF_FONT = "font"
private const val PREF_LINE = "line"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AgpeyaApp() }
    }
}

enum class ReaderLanguage { ENGLISH, ARABIC, BILINGUAL }

enum class PageKind { HOME, PRAYER, ABOUT, CONTACT }

data class PrayerRoute(
    val slug: String,
    val titleEn: String,
    val titleAr: String,
    val time: String? = null,
    val special: Boolean = false,
)

data class ContentBlock(
    val kind: Kind,
    val text: String,
    val isArabic: Boolean = false,
    val scripture: ScriptureRef? = null,
) {
    enum class Kind { HEADING, PARAGRAPH, LIST, SCRIPTURE }
}

data class ScriptureRef(
    val label: String,
    val book: String,
    val chapter: Int,
    val startVerse: Int,
    val endVerse: Int?,
)

data class PageContent(
    val titleEn: String,
    val titleAr: String,
    val subtitleEn: String?,
    val blocks: List<ContentBlock>,
)

private val prayerRoutes = listOf(
    PrayerRoute("prime", "Prime", "صلاة باكر", "6:00 AM"),
    PrayerRoute("terce", "Terce", "صلاة الساعة الثالثة", "9:00 AM"),
    PrayerRoute("sext", "Sext", "صلاة الساعة السادسة", "12:00 PM"),
    PrayerRoute("none", "None", "صلاة الساعة التاسعة", "3:00 PM"),
    PrayerRoute("vespers", "Vespers", "صلاة الغروب", "6:00 PM"),
    PrayerRoute("compline", "Compline", "صلاة النوم", "9:00 PM"),
    PrayerRoute("midnight", "Midnight", "صلاة نصف الليل", "12:00 AM"),
    PrayerRoute("veil", "Prayer of the Veil", "صلاة الستار", special = true),
)

private val otherRoutes = listOf(
    PrayerRoute("special", "Special Prayer", "صلاة خاصة", special = true),
    PrayerRoute("other", "Other Prayers", "صلوات أخرى", special = true),
    PrayerRoute("about", "About the Agpeya", "حول الأجبية", special = true),
    PrayerRoute("contact", "Contact", "اتصل بنا", special = true),
)

@Composable
private fun AgpeyaApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var darkTheme by rememberSaveable { mutableStateOf(prefs.getBoolean(PREF_DARK, false)) }
    var language by rememberSaveable {
        mutableStateOf(
            ReaderLanguage.valueOf(
                prefs.getString(PREF_LANGUAGE, ReaderLanguage.BILINGUAL.name)!!
            )
        )
    }
    var fontScale by rememberSaveable { mutableFloatStateOf(prefs.getFloat(PREF_FONT, 1f)) }
    var lineSpacing by rememberSaveable { mutableFloatStateOf(prefs.getFloat(PREF_LINE, 1.25f)) }
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(darkTheme, language, fontScale, lineSpacing) {
        prefs.edit()
            .putBoolean(PREF_DARK, darkTheme)
            .putString(PREF_LANGUAGE, language.name)
            .putFloat(PREF_FONT, fontScale)
            .putFloat(PREF_LINE, lineSpacing)
            .apply()
    }

    AgpeyaTheme(darkTheme = darkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (route == null) "Agpeya" else routeTitle(route!!, language),
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        if (route != null) {
                            IconButton(onClick = { route = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        } else {
                            Icon(Icons.Filled.MenuBook, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                    )
                )
            },
        ) { padding ->
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (route == null) {
                    HomeScreen(
                        language = language,
                        onOpen = { route = it },
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    ReaderScreen(
                        slug = route!!,
                        language = language,
                        fontScale = fontScale,
                        lineSpacing = lineSpacing,
                        onOpenPage = { route = it },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }

        if (settingsOpen) {
            SettingsDialog(
                darkTheme = darkTheme,
                onDarkChanged = { darkTheme = it },
                language = language,
                onLanguageChanged = { language = it },
                fontScale = fontScale,
                onFontChanged = { fontScale = it },
                lineSpacing = lineSpacing,
                onLineChanged = { lineSpacing = it },
                onDismiss = { settingsOpen = false },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    language: ReaderLanguage,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val currentHour = remember { currentPrayerSlug() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (language == ReaderLanguage.ARABIC) "الأجبية" else "The Agpeya", style = MaterialTheme.typography.headlineMedium)
                if (language != ReaderLanguage.ENGLISH) {
                    Text("كتاب الصلوات القبطي", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Start)
                }
                Text(
                    text = if (language == ReaderLanguage.ARABIC)
                        "صلوات الساعات اليومية للكنيسة القبطية الأرثوذكسية"
                    else
                        "The Coptic Book of Hours — a native reader with Bible passages opened in NIV on Bible.com.",
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 25.sp,
                )
            }
        }

        Text(
            if (language == ReaderLanguage.ARABIC) "الساعات اليومية" else "Daily Hours",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        prayerRoutes.forEach { prayer ->
            HourCard(
                route = prayer,
                language = language,
                highlighted = prayer.slug == currentHour,
                onClick = { onOpen(prayer.slug) },
            )
        }

        Text(
            if (language == ReaderLanguage.ARABIC) "صلوات وأقسام أخرى" else "More",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        otherRoutes.forEach { prayer ->
            HourCard(route = prayer, language = language, highlighted = false, onClick = { onOpen(prayer.slug) })
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HourCard(
    route: PrayerRoute,
    language: ReaderLanguage,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✝", fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (language != ReaderLanguage.ARABIC) {
                    Text(route.titleEn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                if (language != ReaderLanguage.ENGLISH) {
                    Text(route.titleAr, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Start)
                }
                route.time?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            }
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun ReaderScreen(
    slug: String,
    language: ReaderLanguage,
    fontScale: Float,
    lineSpacing: Float,
    onOpenPage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember(slug) { mutableStateOf<LoadState>(LoadState.Loading) }
    var retryToken by remember(slug) { mutableStateOf(0) }
    LaunchedEffect(slug, retryToken) {
        state = LoadState.Loading
        state = try {
            LoadState.Success(AgpeyaRepository.load(slug))
        } catch (t: Throwable) {
            LoadState.Error(t.message ?: "Unable to load the prayer.")
        }
    }

    when (val s = state) {
        LoadState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading…")
        }
        is LoadState.Error -> ErrorScreen(s.message, onRetry = { retryToken++ })
        is LoadState.Success -> PrayerPage(
            content = s.content,
            language = language,
            fontScale = fontScale,
            lineSpacing = lineSpacing,
            onOpenPage = onOpenPage,
            modifier = modifier,
        )
    }
}

private sealed interface LoadState {
    data object Loading : LoadState
    data class Success(val content: PageContent) : LoadState
    data class Error(val message: String) : LoadState
}

@Composable
private fun PrayerPage(
    content: PageContent,
    language: ReaderLanguage,
    fontScale: Float,
    lineSpacing: Float,
    onOpenPage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (language != ReaderLanguage.ARABIC) {
            Text(content.titleEn, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        if (language != ReaderLanguage.ENGLISH) {
            Text(content.titleAr, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        content.subtitleEn?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Divider(Modifier.padding(vertical = 6.dp))

        content.blocks.forEachIndexed { index, block ->
            when (block.kind) {
                ContentBlock.Kind.HEADING -> {
                    ScriptureHeadingOrText(
                        block = block,
                        language = language,
                        fontScale = fontScale,
                        context = context,
                    )
                }
                ContentBlock.Kind.SCRIPTURE -> {
                    ScriptureCard(
                        scripture = block.scripture ?: return@forEachIndexed,
                        language = language,
                        context = context,
                    )
                }
                ContentBlock.Kind.PARAGRAPH,
                ContentBlock.Kind.LIST -> {
                    val show = when (language) {
                        ReaderLanguage.ENGLISH -> !block.isArabic
                        ReaderLanguage.ARABIC -> block.isArabic
                        ReaderLanguage.BILINGUAL -> true
                    }
                    if (show) {
                        Text(
                            text = block.text,
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = (17f * fontScale).sp,
                            lineHeight = (17f * fontScale * lineSpacing).sp,
                            textAlign = if (block.isArabic) TextAlign.Start else TextAlign.Start,
                            fontFamily = if (block.isArabic) FontFamily.SansSerif else FontFamily.Serif,
                        )
                    }
                }
            }
            if (index % 9 == 0) Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ScriptureHeadingOrText(
    block: ContentBlock,
    language: ReaderLanguage,
    fontScale: Float,
    context: Context,
) {
    val show = when (language) {
        ReaderLanguage.ENGLISH -> !block.isArabic
        ReaderLanguage.ARABIC -> block.isArabic
        ReaderLanguage.BILINGUAL -> true
    }
    if (!show) return
    val scripture = block.scripture
    if (scripture != null) {
        ScriptureCard(scripture, language, context)
    } else {
        Text(
            text = block.text,
            style = MaterialTheme.typography.titleLarge,
            fontSize = (21f * fontScale).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 5.dp),
        )
    }
}

@Composable
private fun ScriptureCard(scripture: ScriptureRef, language: ReaderLanguage, context: Context) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openBible(context, scripture) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Book, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (language == ReaderLanguage.ARABIC) "افتح النص في الكتاب المقدس" else "Open Scripture in NIV",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(scripture.label, style = MaterialTheme.typography.bodyMedium)
                Text("bible.com · NIV", style = MaterialTheme.typography.labelMedium)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Unable to load", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun SettingsDialog(
    darkTheme: Boolean,
    onDarkChanged: (Boolean) -> Unit,
    language: ReaderLanguage,
    onLanguageChanged: (ReaderLanguage) -> Unit,
    fontScale: Float,
    onFontChanged: (Float) -> Unit,
    lineSpacing: Float,
    onLineChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Agpeya Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DarkMode, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Dark theme")
                    }
                    Switch(checked = darkTheme, onCheckedChange = onDarkChanged)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ReaderLanguage.entries.forEach { option ->
                        FilterChip(
                            selected = language == option,
                            onClick = { onLanguageChanged(option) },
                            label = {
                                Text(
                                    when (option) {
                                        ReaderLanguage.ENGLISH -> "English"
                                        ReaderLanguage.ARABIC -> "العربية"
                                        ReaderLanguage.BILINGUAL -> "Both"
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    when (option) {
                                        ReaderLanguage.ENGLISH -> Icons.Filled.Language
                                        ReaderLanguage.ARABIC -> Icons.Filled.Translate
                                        ReaderLanguage.BILINGUAL -> Icons.Filled.Language
                                    },
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }

                Text("Font size")
                Slider(value = fontScale, onValueChange = onFontChanged, valueRange = .85f..1.35f)
                Text("Line spacing")
                Slider(value = lineSpacing, onValueChange = onLineChanged, valueRange = 1.05f..1.65f)
                Text(
                    "Scripture passages are opened on Bible.com in NIV. The prayer reader itself uses native Android UI and loads prayer text from agpeya.org.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

private object AgpeyaRepository {
    suspend fun load(slug: String): PageContent = withContext(Dispatchers.IO) {
        val url = if (slug.isEmpty()) BASE_URL else "$BASE_URL/$slug/"
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Android) AgpeyaNivNative/1.0")
            .timeout(20_000)
            .get()

        val root = doc.selectFirst("main") ?: doc.selectFirst("article") ?: doc.body()
        root.select("script,style,noscript,header,footer,nav,aside,form,dialog").remove()

        val elements = root.select("h1,h2,h3,p,li,blockquote")
        val firstH1 = elements.indexOfFirst { it.tagName() == "h1" }
        val filtered = if (firstH1 >= 0) elements.drop(firstH1) else elements

        val clean = filtered.mapNotNull { el ->
            val text = el.text().trim()
            if (text.isBlank()) null else text to el.tagName()
        }.filterNot { (text, tag) ->
            tag == "li" && (text.contains("Settings") || text.contains("تسجيل الدخول") || text.contains("Language"))
        }

        val firstTitle = clean.firstOrNull { it.second == "h1" }?.first ?: "Agpeya"
        val subtitle = clean.firstOrNull { it.second == "p" && (it.first.contains("Hour") || it.first.matches(Regex(".*\\d{1,2}:\\d{2}.*"))) }?.first
        val titleAr = inferArabicTitle(firstTitle, slug)

        val blocks = mutableListOf<ContentBlock>()
        for ((text, tag) in clean) {
            val isArabic = text.contains(Regex("[\\u0600-\\u06FF]"))
            if (tag == "h1" && text == firstTitle) {
                continue
            }
            val gospel = parseGospel(text)
            val psalm = parsePsalm(text)
            when {
                gospel != null -> blocks += ContentBlock(ContentBlock.Kind.SCRIPTURE, text, scripture = gospel)
                psalm != null -> blocks += ContentBlock(ContentBlock.Kind.SCRIPTURE, text, scripture = psalm)
                tag.startsWith("h") -> blocks += ContentBlock(ContentBlock.Kind.HEADING, text, isArabic)
                tag == "li" -> blocks += ContentBlock(ContentBlock.Kind.LIST, "• $text", isArabic)
                else -> blocks += ContentBlock(ContentBlock.Kind.PARAGRAPH, text, isArabic)
            }
        }
        PageContent(firstTitle, titleAr, subtitle, blocks)
    }
}

private fun parseGospel(text: String): ScriptureRef? {
    val m = Regex("(?i)Holy Gospel\\s*\\(([^)]+)\\)").find(text) ?: return null
    val ref = m.groupValues[1]
    val parsed = Regex("(?i)(Matthew|Mark|Luke|John)\\s+(\\d+):(\\d+)(?:-(\\d+))?").find(ref) ?: return null
    val book = parsed.groupValues[1].lowercase(Locale.US).replaceFirstChar { it.uppercase() }
    return ScriptureRef(
        label = ref,
        book = book,
        chapter = parsed.groupValues[2].toInt(),
        startVerse = parsed.groupValues[3].toInt(),
        endVerse = parsed.groupValues[4].takeIf { it.isNotBlank() }?.toInt(),
    )
}

private fun parsePsalm(text: String): ScriptureRef? {
    val m = Regex("(?i)^Psalm\\s+(\\d+)(?:\\s|$)").find(text) ?: return null
    val agpeyaNumber = m.groupValues[1].toIntOrNull() ?: return null
    val nivNumber = if (agpeyaNumber in 9..150) agpeyaNumber + 1 else agpeyaNumber
    return ScriptureRef(
        label = "Psalm $agpeyaNumber (NIV Psalm $nivNumber)",
        book = "Psalms",
        chapter = nivNumber,
        startVerse = 1,
        endVerse = null,
    )
}

private fun openBible(context: Context, scripture: ScriptureRef) {
    val code = when (scripture.book.lowercase(Locale.US)) {
        "matthew" -> "MAT"
        "mark" -> "MRK"
        "luke" -> "LUK"
        "john" -> "JHN"
        "psalms" -> "PSA"
        else -> return
    }
    val range = if (scripture.endVerse != null) {
        "$code.${scripture.chapter}.${scripture.startVerse}-${scripture.endVerse}"
    } else {
        "$code.${scripture.chapter}"
    }
    val uri = Uri.parse("https://www.bible.com/bible/111/$range.NIV")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

private fun routeTitle(slug: String, language: ReaderLanguage): String {
    val route = (prayerRoutes + otherRoutes).firstOrNull { it.slug == slug }
    return when {
        route == null -> "Agpeya"
        language == ReaderLanguage.ARABIC -> route.titleAr
        else -> route.titleEn
    }
}

private fun inferArabicTitle(title: String, slug: String): String {
    return (prayerRoutes + otherRoutes).firstOrNull { it.slug == slug }?.titleAr ?: title
}

private fun currentPrayerSlug(): String {
    val hour = SimpleDateFormat("H", Locale.US).format(Date()).toInt()
    return when (hour) {
        in 5..8 -> "prime"
        in 9..11 -> "terce"
        in 12..14 -> "sext"
        in 15..17 -> "none"
        in 18..20 -> "vespers"
        in 21..23 -> "compline"
        else -> "midnight"
    }
}

@Composable
private fun AgpeyaTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val scheme = if (darkTheme) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
