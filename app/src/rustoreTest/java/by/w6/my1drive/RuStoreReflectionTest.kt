package by.w6.my1drive

import org.junit.Test
import ru.rustore.sdk.pay.model.Product

class RuStoreReflectionTest {
    @Test
    fun dumpProduct() {
        val clazz = ru.rustore.sdk.pay.model.AmountLabel::class.java
        println("=== AmountLabel FIELDS ===")
        clazz.declaredFields.forEach { println("${it.name} : ${it.type.name}") }
        println("=== AmountLabel METHODS ===")
        clazz.declaredMethods.forEach { println("${it.name} : ${it.returnType.name}") }
    }
}
