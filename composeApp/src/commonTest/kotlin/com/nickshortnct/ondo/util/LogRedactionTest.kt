package com.nickshortnct.ondo.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LogRedactionTest {
	@Test fun redactsUrlsAndHeadersIncludingStackTraceMessages() {
		val text = "GET /stream?id=123&t=test-secret&s=salt&p=pass&token=token-value&api_key=key\nAuthorization: Bearer header-secret\nX-Api-Key=api-secret, Accept=audio"
		val safe = redactLogText(text)
		for (secret in listOf("test-secret", "salt", "pass", "token-value", "header-secret", "api-secret")) assertFalse(secret in safe)
		assertEquals("GET /stream?id=123&t=***&s=***&p=***&token=***&api_key=***\nAuthorization: ***\nX-Api-Key=***, Accept=audio", safe)
	}
}
