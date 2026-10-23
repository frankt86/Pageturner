package voice.features.reader

import java.util.Locale

/** A voice of the natural voice model. [language] is the language tag its words are pronounced in. */
internal data class NaturalSpeaker(
  val id: Int,
  val name: String,
  val language: String,
) {
  // espeak calls British English plain "en" and has no "en-gb"
  val espeakLanguage: String get() = if (language == "en-gb") "en" else language
}

// only the voices that sound good, the model has more
private val american = listOf(
  NaturalSpeaker(3, "Heart", "en-us"),
  NaturalSpeaker(2, "Bella", "en-us"),
  NaturalSpeaker(6, "Nicole", "en-us"),
  NaturalSpeaker(1, "Aoede", "en-us"),
  NaturalSpeaker(5, "Kore", "en-us"),
  NaturalSpeaker(9, "Sarah", "en-us"),
  NaturalSpeaker(16, "Michael", "en-us"),
  NaturalSpeaker(14, "Fenrir", "en-us"),
  NaturalSpeaker(18, "Puck", "en-us"),
)

private val british = listOf(
  NaturalSpeaker(21, "Emma", "en-gb"),
  NaturalSpeaker(22, "Isabella", "en-gb"),
  NaturalSpeaker(26, "George", "en-gb"),
  NaturalSpeaker(25, "Fable", "en-gb"),
)

private val otherLanguages = mapOf(
  "es" to listOf(NaturalSpeaker(28, "Dora", "es"), NaturalSpeaker(29, "Alex", "es")),
  "fr" to listOf(NaturalSpeaker(30, "Siwis", "fr")),
  "hi" to listOf(
    NaturalSpeaker(31, "Alpha", "hi"),
    NaturalSpeaker(32, "Beta", "hi"),
    NaturalSpeaker(33, "Omega", "hi"),
    NaturalSpeaker(34, "Psi", "hi"),
  ),
  "it" to listOf(NaturalSpeaker(35, "Sara", "it"), NaturalSpeaker(36, "Nicola", "it")),
  "ja" to listOf(
    NaturalSpeaker(37, "Alpha", "ja"),
    NaturalSpeaker(38, "Gongitsune", "ja"),
    NaturalSpeaker(39, "Nezumi", "ja"),
    NaturalSpeaker(40, "Tebukuro", "ja"),
    NaturalSpeaker(41, "Kumo", "ja"),
  ),
  "pt" to listOf(NaturalSpeaker(42, "Dora", "pt-br"), NaturalSpeaker(43, "Alex", "pt-br")),
  "zh" to listOf(
    NaturalSpeaker(45, "Xiaobei", "zh"),
    NaturalSpeaker(46, "Xiaoni", "zh"),
    NaturalSpeaker(47, "Xiaoxiao", "zh"),
    NaturalSpeaker(48, "Xiaoyi", "zh"),
    NaturalSpeaker(49, "Yunjian", "zh"),
    NaturalSpeaker(50, "Yunxi", "zh"),
    NaturalSpeaker(51, "Yunxia", "zh"),
    NaturalSpeaker(52, "Yunyang", "zh"),
  ),
)

private val britishCountries = setOf("GB", "IE", "AU", "NZ")

internal fun Locale.prefersBritishEnglish(): Boolean = country in britishCountries

/** The voices that speak the language of [locale], the default one first. Empty if the model doesn't speak it. */
internal fun naturalSpeakers(locale: Locale): List<NaturalSpeaker> {
  return when (locale.language) {
    "en" -> if (locale.prefersBritishEnglish()) british + american else american + british
    else -> otherLanguages[locale.language].orEmpty()
  }
}
