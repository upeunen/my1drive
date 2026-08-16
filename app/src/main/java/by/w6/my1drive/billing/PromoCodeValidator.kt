package by.w6.my1drive.billing

import org.json.JSONObject

data class PromoCodeEntry(
    val code: String,
    val days: Int,
    val message: String? = null
)

/**
 * Валидатор промокодов.
 * Парсит JSON из Remote Config и проверяет введённый пользователем код.
 * Формат JSON: {"codes":[{"code":"BETA2025","days":30,"message":"Приветственный текст..."}]}
 */
object PromoCodeValidator {

    /** Парсит список промокодов из JSON-строки Remote Config */
    fun parsePromoCodes(json: String): List<PromoCodeEntry> {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return emptyList()
        return try {
            val array = when {
                trimmed.startsWith("[") -> org.json.JSONArray(trimmed)
                else -> {
                    val root = JSONObject(trimmed)
                    when {
                        root.has("promo_codes") -> root.getJSONArray("promo_codes")
                        root.has("codes") -> root.getJSONArray("codes")
                        else -> org.json.JSONArray()
                    }
                }
            }
            (0 until array.length()).map {
                val obj = array.getJSONObject(it)
                PromoCodeEntry(
                    code = obj.getString("code").trim().uppercase(),
                    days = obj.getInt("days"),
                    message = obj.optString("message", "").takeIf { msg -> msg.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Проверяет промокод.
     * @return количество дней если код верный, null если не найден
     */
    fun validate(inputCode: String, json: String): Int? {
        return validateWithDetails(inputCode, json)?.days
    }

    fun validateWithDetails(inputCode: String, json: String): PromoCodeEntry? {
        if (inputCode.isBlank()) return null
        val codes = parsePromoCodes(json)
        return codes.find { it.code == inputCode.trim().uppercase() }
    }
}
