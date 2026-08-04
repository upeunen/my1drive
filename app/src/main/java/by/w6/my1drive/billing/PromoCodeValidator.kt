package by.w6.my1drive.billing

import org.json.JSONObject

data class PromoCodeEntry(val code: String, val days: Int)

/**
 * Валидатор промокодов.
 * Парсит JSON из Remote Config и проверяет введённый пользователем код.
 * Формат JSON: {"codes":[{"code":"BETA2025","days":30}]}
 */
object PromoCodeValidator {

    /** Парсит список промокодов из JSON-строки Remote Config */
    fun parsePromoCodes(json: String): List<PromoCodeEntry> {
        if (json.isBlank()) return emptyList()
        return try {
            val root = JSONObject(json)
            val array = root.getJSONArray("codes")
            (0 until array.length()).map {
                val obj = array.getJSONObject(it)
                PromoCodeEntry(
                    code = obj.getString("code").trim().uppercase(),
                    days = obj.getInt("days")
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
        if (inputCode.isBlank()) return null
        val codes = parsePromoCodes(json)
        return codes.find { it.code == inputCode.trim().uppercase() }?.days
    }
}
