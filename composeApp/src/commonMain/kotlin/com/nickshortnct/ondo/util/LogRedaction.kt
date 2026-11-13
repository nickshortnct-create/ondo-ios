package com.nickshortnct.ondo.util

private val secretQuery = Regex("([?&](?:t|s|p|token|apiKey|api_key|password|access_token)=)[^&#\\s]+", RegexOption.IGNORE_CASE)
private val secretHeader = Regex("((?:Authorization|X-Api-Key|X-Navidrome-Token|X-Navidrome-Salt)\\s*[:=]\\s*)[^\\r\\n,}\\]]+", RegexOption.IGNORE_CASE)

fun redactLogText(value: String): String = secretHeader.replace(secretQuery.replace(value) { it.groupValues[1] + "***" }) {
	it.groupValues[1] + "***"
}
