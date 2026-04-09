package com.google.ai.sample.util

internal object AppMappings {
    private data class AppDefinition(
        val canonicalName: String,
        val packageName: String,
        val variations: List<String> = emptyList(),
        val aliasesForPackageLookup: List<String> = emptyList()
    )

    private val appDefinitions: List<AppDefinition> = listOf(
        AppDefinition("whatsapp", "com.whatsapp", listOf("whats app", "whats", "wa")),
        AppDefinition("facebook", "com.facebook.katana", listOf("fb", "face book")),
        AppDefinition("messenger", "com.facebook.orca"),
        AppDefinition("instagram", "com.instagram.android", listOf("insta", "ig")),
        AppDefinition("youtube", "com.google.android.youtube", listOf("yt", "you tube")),
        AppDefinition("twitter", "com.twitter.android", listOf("x", "tweet"), aliasesForPackageLookup = listOf("x")),
        AppDefinition("telegram", "org.telegram.messenger", listOf("tg")),
        AppDefinition("tiktok", "com.zhiliaoapp.musically", listOf("tik tok")),
        AppDefinition("snapchat", "com.snapchat.android", listOf("snap")),
        AppDefinition("netflix", "com.netflix.mediaclient", listOf("nflx")),
        AppDefinition("spotify", "com.spotify.music", listOf("music")),
        AppDefinition("chrome", "com.android.chrome", listOf("google chrome", "browser")),
        AppDefinition("gmail", "com.google.android.gm", listOf("google mail", "email", "mail")),
        AppDefinition("maps", "com.google.android.apps.maps", listOf("google maps", "navigation")),
        AppDefinition("calendar", "com.google.android.calendar", listOf("google calendar")),
        AppDefinition("photos", "com.google.android.apps.photos", listOf("google photos", "gallery")),
        AppDefinition("drive", "com.google.android.apps.docs", listOf("google drive")),
        AppDefinition("docs", "com.google.android.apps.docs.editors.docs", listOf("google docs", "documents")),
        AppDefinition("sheets", "com.google.android.apps.docs.editors.sheets", listOf("google sheets", "spreadsheets")),
        AppDefinition("slides", "com.google.android.apps.docs.editors.slides", listOf("google slides", "presentations")),
        AppDefinition("keep", "com.google.android.keep", listOf("google keep", "notes")),
        AppDefinition("amazon", "com.amazon.mShop.android.shopping", listOf("amazon shopping", "shop")),
        AppDefinition("uber", "com.ubercab", listOf("uber driver")),
        AppDefinition("lyft", "me.lyft.android", listOf("ride")),
        AppDefinition("doordash", "com.dd.doordash", listOf("food delivery")),
        AppDefinition("ubereats", "com.ubercab.eats", listOf("uber eats", "food")),
        AppDefinition("grubhub", "com.grubhub.android", listOf("food delivery")),
        AppDefinition("instacart", "com.instacart.client", listOf("grocery")),
        AppDefinition("zoom", "us.zoom.videomeetings", listOf("zoom meeting")),
        AppDefinition("teams", "com.microsoft.teams", listOf("microsoft teams")),
        AppDefinition("skype", "com.skype.raider", listOf("microsoft skype")),
        AppDefinition("outlook", "com.microsoft.office.outlook", listOf("microsoft outlook", "email")),
        AppDefinition("word", "com.microsoft.office.word", listOf("microsoft word", "documents")),
        AppDefinition("excel", "com.microsoft.office.excel", listOf("microsoft excel", "spreadsheets")),
        AppDefinition("powerpoint", "com.microsoft.office.powerpoint", listOf("microsoft powerpoint", "presentations")),
        AppDefinition("onedrive", "com.microsoft.skydrive", listOf("microsoft onedrive", "cloud")),
        AppDefinition("onenote", "com.microsoft.office.onenote", listOf("microsoft onenote", "notes")),
        AppDefinition("linkedin", "com.linkedin.android", listOf("linked in")),
        AppDefinition("pinterest", "com.pinterest", listOf("pin")),
        AppDefinition("reddit", "com.reddit.frontpage", listOf("reddit app")),
        AppDefinition("discord", "com.discord", listOf("chat")),
        AppDefinition("slack", "com.Slack", listOf("work chat")),
        AppDefinition("twitch", "tv.twitch.android.app", listOf("streaming")),
        AppDefinition("venmo", "com.venmo", listOf("payment")),
        AppDefinition("paypal", "com.paypal.android.p2pmobile", listOf("payment")),
        AppDefinition("cashapp", "com.squareup.cash", listOf("cash app", "payment")),
        AppDefinition("zelle", "com.zellepay.zelle", listOf("payment")),
        AppDefinition("robinhood", "com.robinhood.android", listOf("stocks")),
        AppDefinition("coinbase", "com.coinbase.android", listOf("crypto")),
        AppDefinition("binance", "com.binance.dev", listOf("crypto")),
        AppDefinition("wechat", "com.tencent.mm", listOf("we chat")),
        AppDefinition("line", "jp.naver.line.android", listOf("line messenger")),
        AppDefinition("viber", "com.viber.voip", listOf("viber messenger")),
        AppDefinition("signal", "org.thoughtcrime.securesms", listOf("signal messenger")),
        AppDefinition("threema", "ch.threema.app", listOf("threema messenger")),
        AppDefinition("settings", "com.android.settings", listOf("system settings", "preferences"))
    )

    val appNameVariations: Map<String, List<String>> = appDefinitions
        .filter { it.variations.isNotEmpty() }
        .associate { it.canonicalName to it.variations }

    val manualMappings: Map<String, String> = buildMap {
        appDefinitions.forEach { definition ->
            put(definition.canonicalName, definition.packageName)
            definition.aliasesForPackageLookup.forEach { alias ->
                put(alias, definition.packageName)
            }
        }
    }
}
