package com.abyss.app.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * Every user-facing string in Abyss. Each language is an object extending this
 * class, so a missing translation is a COMPILE error — completeness is
 * enforced by the compiler, not by hope.
 *
 * Counted phrases use the "Label: N" form on purpose, so languages with
 * complex plural rules (ru, uk) are always grammatical.
 */
abstract class Strings {
    abstract val code: String          // BCP-47 language code
    abstract val nativeName: String    // name of the language in itself
    val locale: Locale get() = Locale.forLanguageTag(code)

    // ---- common
    abstract val copy: String
    abstract val copied: String
    abstract val delete: String
    abstract val save: String
    abstract val cancel: String
    abstract val send: String
    abstract val stop: String
    abstract val close: String
    abstract val back: String
    abstract val use: String
    abstract val importAction: String
    abstract val download: String
    abstract val translate: String
    abstract val grant: String
    abstract val granted: String
    abstract val manage: String
    abstract val error: String
    abstract val active: String
    abstract val done: String
    abstract val connected: String
    abstract val notSet: String
    abstract val latest: String
    abstract val flip: String
    abstract val mb: String

    // ---- navigation
    abstract val navChat: String
    abstract val navTools: String
    abstract val navSettings: String

    // ---- chat
    abstract val chats: String
    abstract val newChat: String
    abstract val themes: String
    abstract val settings: String
    abstract val sugWrite: String
    abstract val sugWritePrompt: String
    abstract val sugLearn: String
    abstract val sugLearnPrompt: String
    abstract val sugCode: String
    abstract val sugCodePrompt: String
    abstract val sugLife: String
    abstract val sugLifePrompt: String
    abstract val sugSurprise: String
    abstract val sugSurprisePrompt: String
    /** [bucket]: 0 morning, 1 afternoon, 2 evening, 3 late night. [name] may be blank. */
    abstract fun greeting(bucket: Int, name: String): String
    abstract val regenerate: String
    abstract val translating: String
    abstract fun translatedFrom(language: String): String
    abstract val showOriginal: String
    abstract val showTranslation: String
    abstract val anotherLanguage: String
    abstract val thinking: String
    abstract val transcribing: String
    abstract val composerHint: String
    abstract val replyHint: String
    abstract val recents: String
    abstract val chatsEmpty: String
    abstract val deleteChat: String
    abstract val chatFallback: String
    abstract val timeNow: String
    abstract fun minutesShort(n: Long): String
    abstract fun hoursShort(n: Long): String
    abstract fun daysShort(n: Long): String
    abstract val describeMediaPrompt: String
    abstract val codeLabel: String

    // ---- agents
    abstract fun agentName(id: String): String
    abstract fun agentTagline(id: String): String

    // ---- tools hub
    abstract val toolsTitle: String
    abstract val toolsSubtitle: String
    abstract val toolCore: String
    abstract val toolCoreBlurb: String
    abstract val toolTranslator: String
    abstract val toolTranslatorBlurb: String
    abstract val toolNotes: String
    abstract val toolNotesBlurb: String
    abstract val toolAlarms: String
    abstract val toolAlarmsBlurb: String
    abstract val toolCalculator: String
    abstract val toolCalculatorBlurb: String
    abstract val toolVpn: String
    abstract val toolVpnBlurb: String

    // ---- settings
    abstract val settingsSubtitle: String
    abstract val secYou: String
    abstract val yourName: String
    abstract val yourNameHint: String
    abstract val theme: String
    abstract val language: String
    abstract fun languageSystem(current: String): String
    abstract val secModel: String
    abstract val anyModelId: String
    abstract val secKeys: String
    abstract fun pasteKey(provider: String): String
    abstract val removeKey: String
    abstract val keysFootnote: String
    abstract val secGeneration: String
    abstract val creativity: String
    abstract val precise: String
    abstract val wild: String
    abstract val customInstructions: String
    abstract val customInstructionsSub: String
    abstract val customInstructionsHint: String
    abstract val secTranslator: String
    abstract val readingLanguage: String
    abstract val autoTranslate: String
    abstract val autoTranslateSub: String
    abstract val engine: String
    abstract val engineOnDevice: String
    abstract val engineMyModel: String
    abstract val secPrivacy: String
    abstract val permissions: String
    abstract val scamGuard: String
    abstract val scamGuardSub: String
    abstract val lookupUrlHint: String
    abstract val backgroundLearning: String
    abstract val backgroundLearningSub: String
    abstract val providerOnDevice: String
    abstract val keyConnected: String
    abstract val noKeyYet: String
    abstract val freeTierReady: String
    abstract val freeTierNote: String
    abstract val paidKeyNote: String
    abstract val getFreeKey: String
    abstract val secDebug: String
    abstract val selfReview: String
    abstract val selfReviewSub: String
    abstract val run: String
    abstract val reviewTitle: String
    abstract val reviewRun: String
    abstract val reviewRunning: String
    abstract val reviewCopy: String
    abstract val reviewIdle: String

    // ---- notes
    abstract val searchHint: String
    abstract val newNote: String
    abstract val untitled: String
    abstract val pin: String
    abstract val noteTitle: String
    abstract val noteBody: String

    // ---- alarms
    /** Mon..Sun, short. */
    abstract val weekdaysShort: List<String>
    abstract val once: String
    abstract val smart: String
    abstract val newAlarm: String
    abstract val alarmLabel: String
    abstract val vibrate: String
    abstract val skipSundays: String
    abstract val smartVolume: String
    abstract fun baseVolume(percent: Int): String
    abstract val up: String
    abstract val down: String
    abstract val alarm: String
    abstract val alarmsChannel: String
    abstract val snooze: String
    abstract val dismiss: String

    // ---- translator
    abstract fun translatorSubtitle(usingModel: Boolean): String
    abstract val enterText: String
    abstract fun detected(language: String): String
    abstract fun toLanguage(language: String): String
    abstract val translatedByModel: String
    abstract val translatedOnDevice: String

    // ---- core
    abstract val coreSubtitle: String
    abstract val knowledge: String
    abstract val improvements: String
    abstract val selfImprovement: String
    abstract val enableBgLearning: String
    abstract val bgLearningDesc: String
    abstract val working: String
    abstract val runCycle: String
    /** [reflection]: 0 skipped, 1 local, 2 online. */
    abstract fun cycleResult(harvested: Int, needKeys: Boolean, reflection: Int): String
    abstract val recentKnowledge: String
    abstract val reflectionTag: String
    abstract val localModelTitle: String
    abstract val nativePresent: String
    abstract val nativeMissing: String
    abstract val ggufHelp: String
    abstract val saveLink: String
    abstract val downloadedModels: String
    abstract fun loadingModel(name: String): String
    abstract fun loadedModel(name: String): String
    abstract val loadFailed: String

    // ---- vpn
    abstract val vpnReady: String
    abstract val vpnMissing: String
    abstract val subscriptionUrl: String
    abstract val fetching: String
    abstract fun importedServers(n: Int): String
    abstract val orPasteLinks: String
    abstract val onePerLine: String
    abstract val addPasted: String
    abstract fun addedServers(n: Int): String
    abstract fun serversCount(n: Int): String
    abstract fun vpnConnected(label: String): String
    abstract val vpnConnecting: String
    abstract fun vpnError(message: String): String
    abstract val vpnDisconnected: String
    abstract val disconnect: String
    abstract val vpnErrNoEngine: String
    abstract val vpnErrTunnel: String
    abstract val vpnErrStart: String

    // ---- permissions
    abstract val permsIntro: String
    abstract val requestAll: String
    abstract val permNotifications: String
    abstract val permNotificationsWhy: String
    abstract val permMic: String
    abstract val permMicWhy: String
    abstract val permCamera: String
    abstract val permCameraWhy: String
    abstract val permContacts: String
    abstract val permContactsWhy: String
    abstract val permPhone: String
    abstract val permPhoneWhy: String
    abstract val permUsage: String
    abstract val permUsageWhy: String
    abstract val permScreening: String
    abstract val permScreeningWhy: String
    abstract val permExactAlarms: String
    abstract val permExactAlarmsWhy: String
    abstract val permBattery: String
    abstract val permBatteryWhy: String

    // ---- camera
    abstract val camPhoto: String
    abstract val camVideo: String
    abstract val camCircle: String
    abstract val recordingTapToStop: String

    // ---- call guard
    abstract val guardChannel: String
    abstract val likelyScam: String
    abstract val unknownCaution: String
    abstract val riskHigh: String
    abstract val riskMedium: String
    abstract val riskLow: String
    abstract val privateNumber: String
    abstract val reasonHidden: String
    abstract val reasonShort: String
    abstract val reasonInternational: String
    abstract val reasonPremium: String
    abstract val reasonRepetitive: String
    abstract val reasonReported: String
    abstract val reasonNone: String

    // ---- errors / statuses from the chat engine
    abstract fun errNoKey(provider: String): String
    abstract val errRequestFailed: String
    abstract val errMic: String
    abstract val errEmptyTranscript: String
    abstract val errTranscription: String
    abstract val errCaptureMissing: String
    abstract fun analyzing(kind: String): String
    abstract val errNeedTranscribeKey: String
    abstract val errNeedGeminiKey: String
    abstract val errAnalysis: String
    abstract val errNoAnalysis: String
    abstract fun unsupportedLanguage(language: String): String

    // ---- sky radar
    abstract val toolRadar: String
    abstract val toolRadarBlurb: String
    abstract val radarSearchHint: String
    abstract val radarFilterAll: String
    abstract val radarFilterMilitary: String
    abstract val radarFilterEmergency: String
    abstract val radarFilterAirborne: String
    abstract fun radarCount(n: Int): String
    abstract fun radarUpdatedAgo(sec: Int): String
    abstract val radarLoading: String
    abstract val radarOffline: String
    abstract val radarZoomIn: String
    abstract val radarNoResults: String
    abstract val radarSearching: String
    abstract val radarAltitude: String
    abstract val radarSpeed: String
    abstract val radarVSpeed: String
    abstract val radarHeading: String
    abstract val radarSquawk: String
    abstract val radarType: String
    abstract val radarReg: String
    abstract val radarHex: String
    abstract val radarSeen: String
    abstract val radarRouteUnknown: String
    abstract val radarFollow: String
    abstract val radarFollowing: String
    abstract val radarOnGround: String
    abstract val radarMyLocation: String
    abstract val radarLocationDenied: String
    abstract val radarUnitsMetric: String
    abstract val radarUnitsImperial: String
    abstract val radarSq7500: String
    abstract val radarSq7600: String
    abstract val radarSq7700: String
    abstract val radarEmergencyGeneric: String
    abstract fun radarProgress(flown: String, left: String): String
    abstract val radarCredit: String
    abstract val radarMilitaryTag: String
    abstract val unitM: String
    abstract val unitFt: String
    abstract val unitKmh: String
    abstract val unitKt: String
    abstract val unitMs: String
    abstract val unitFpm: String
    abstract val unitKm: String
    abstract val unitNm: String
    abstract val unitSec: String

    // ---- radar: maps, layers, OSINT geo tools
    abstract val radarLayers: String
    abstract val radarLayerAbyss: String
    abstract val radarLayerStreets: String
    abstract val radarLayerSatellite: String
    abstract val radarLayerTerrain: String
    abstract val radarLayerTransport: String
    abstract val radarOverlays: String
    abstract val radarOverlayWeather: String
    abstract val radarOverlayWiki: String
    abstract val radarOverlayPlanes: String
    abstract val radarSearchPlanes: String
    abstract val radarSearchPlaces: String
    abstract val radarPlacesHint: String
    abstract val radarPoint: String
    abstract val radarCoordinates: String
    abstract val radarShare: String
    abstract fun radarFromMe(d: String): String
    abstract fun radarNearbyWiki(n: Int): String
    abstract val radarOpenArticle: String
    abstract val radarReverseFail: String
    abstract val radarDropPinHint: String
    abstract val radarMeasure: String
    abstract val radarMeasureHint: String
    abstract fun radarTotal(d: String): String
    abstract val radarClear: String
    abstract val radarResetNorth: String
    abstract val radarLocating: String
    abstract val radarWikiTitle: String

    // ---- radar: events overlay
    abstract val radarOverlayEvents: String
    abstract val radarEventQuake: String
    abstract val radarEventNews: String

    // ---- radar: day/night terminator
    abstract val radarOverlayTerminator: String

    // ---- radar: data sources
    abstract val radarSources: String
    abstract fun radarSourcesActive(ok: Int, total: Int): String
    abstract val radarSeenBy: String
    abstract val radarOwner: String

    // ---- radar: roads (traffic, incidents, cameras)
    abstract val roadSection: String
    abstract val roadTraffic: String
    abstract val roadIncidents: String
    abstract val roadCameras: String
    abstract val roadKeys: String
    abstract val roadKeysHint: String
    abstract val roadGetKey: String
    abstract fun roadNeedKey(name: String): String
    abstract val roadZoomForCams: String
    abstract val roadKindAccident: String
    abstract val roadKindJam: String
    abstract val roadKindClosure: String
    abstract val roadKindLaneClosed: String
    abstract val roadKindRoadworks: String
    abstract val roadKindHazard: String
    abstract val roadKindWeather: String
    abstract val roadKindBreakdown: String
    abstract val roadKindEvent: String
    abstract val roadKindOther: String
    abstract val roadSevMinor: String
    abstract val roadSevModerate: String
    abstract val roadSevMajor: String
    abstract val roadSevClosed: String
    abstract fun roadDelay(min: Int): String
    abstract val roadLengthLabel: String
    abstract val roadDelayLabel: String
    abstract val roadStart: String
    abstract val roadEnd: String
    abstract fun roadReports(n: Int): String
    abstract val roadUnconfirmed: String
    abstract val roadSourceLabel: String
    abstract fun roadList(n: Int): String
    abstract val roadNone: String
    abstract val camSpeed: String
    abstract val camRedLight: String
    abstract val camAvgSpeed: String
    abstract val camTraffic: String
    abstract val camLive: String
    abstract val camLimit: String
    abstract val camDirection: String
    abstract val camRefresh: String
    abstract val camOpen: String
    abstract val roadSaveKey: String
    abstract val roadKeySaved: String

    // ---- radar: civil defence (alerts + shelters)
    abstract val safetySection: String
    abstract val safetyHint: String
    abstract val safetyAlerts: String
    abstract val safetyShelters: String
    abstract val safetyNeedToken: String
    abstract val safetyFindShelter: String
    abstract val safetyToShelter: String
    abstract fun safetyCalm(region: String): String
    abstract fun safetyActiveCount(n: Int): String
    abstract val safetyZoomForShelters: String
    abstract val safetyNearestTitle: String
    abstract val safetyNoShelters: String
    abstract val safetyRoute: String
    abstract val alertAirRaid: String
    abstract val alertArtillery: String
    abstract val alertUrbanFights: String
    abstract val alertChemical: String
    abstract val alertNuclear: String
    abstract val alertOther: String
    abstract val shelterBomb: String
    abstract val shelterMetro: String
    abstract val shelterUnderground: String
    abstract val shelterParking: String
    abstract val shelterGeneral: String
    abstract val shelterOperator: String
    abstract val shelterCapacity: String

    // ---- radar: civil defence background + history
    abstract val safetyWatching: String
    abstract val safetyStopWatch: String
    abstract val safetyAllClear: String
    abstract val safetyNoAlert: String
    abstract val safetyPossible: String
    abstract val safetyActive: String
    abstract val safetyBackground: String
    abstract val safetyBackgroundHint: String
    abstract val safetyHistory: String
    abstract val safetyNoHistory: String
    abstract val safetyOngoing: String

    abstract val safetyMinShort: String

    abstract val safetyNotifBlocked: String

    // ---- vpn: speed test + sort
    abstract val vpnTestAll: String
    abstract val vpnTesting: String
    abstract val vpnSortSpeed: String
    abstract val vpnSortAdded: String
    abstract fun vpnSubFailed(reason: String): String

    // ---- radar: saved places
    abstract val radarBookmarks: String
    abstract val radarNoBookmarks: String
    abstract val radarSave: String

    // ---- radar: aircraft in view
    abstract val radarInView: String
    abstract fun radarInViewCount(n: Int): String

    abstract val radarMagAny: String

    abstract val radarCoordFormats: String

    // ---- radar: sun & shadow (chronolocation)
    abstract val radarSun: String
    abstract val radarSunNow: String
    abstract val radarShadow: String
    abstract val radarSunBelow: String
    abstract val radarSunrise: String
    abstract val radarSunset: String
    abstract val radarSolarNoon: String
    abstract val radarPolarDay: String
    abstract val radarPolarNight: String

    abstract val radarOpenIn: String

    // ---- power saving
    abstract val powerSave: String
    abstract val powerSaveSub: String

    /** Localised label for a captured media kind ("photo" | "video" | "circle"). */
    fun mediaKind(kind: String): String = when (kind) {
        "photo" -> camPhoto
        "video" -> camVideo
        "circle" -> camCircle
        else -> kind
    }

    /** Language name ([tag]) shown in THIS UI language, via the system locale database. */
    fun languageName(tag: String): String {
        val name = Locale.forLanguageTag(tag).getDisplayLanguage(locale)
        return if (name.isBlank() || name == tag) tag.uppercase()
        else name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /**
     * Language name for use INSIDE a sentence ("Traducido del inglés"). Keeps the
     * locale database's own casing: lowercase in es/fr/pt/ru/uk, capitalised in de/en/tr.
     */
    fun languageNameInline(tag: String): String {
        val name = Locale.forLanguageTag(tag).getDisplayLanguage(locale)
        return if (name.isBlank() || name == tag) tag.uppercase() else name
    }
}

/** Language selection + a process-wide current value for non-Compose code. */
object I18n {
    val all: List<Strings> = listOf(StringsEn, StringsRu, StringsUk, StringsEs, StringsDe,
        StringsFr, StringsPt, StringsTr, StringsZh, StringsJa)

    /** Used by services, notifications and the view model (outside Compose). */
    @Volatile var current: Strings = fromLocale(Locale.getDefault())

    fun fromLocale(locale: Locale): Strings =
        all.firstOrNull { it.code == locale.language } ?: StringsEn

    /** [setting] is "system" or a language code. */
    fun resolve(setting: String): Strings =
        if (setting == "system" || setting.isBlank()) fromLocale(Locale.getDefault())
        else all.firstOrNull { it.code == setting } ?: fromLocale(Locale.getDefault())
}

val LocalStrings = staticCompositionLocalOf<Strings> { StringsEn }
