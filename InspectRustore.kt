import ru.rustore.sdk.pay.model.ApplicationPurchaseResult
import ru.rustore.sdk.pay.model.Purchase

fun main() {
    val successClass = ApplicationPurchaseResult.Success::class.java
    successClass.declaredMethods.forEach { println("Success method: ${it.name} -> ${it.returnType.name}") }
    
    val purchaseClass = Purchase::class.java
    purchaseClass.declaredMethods.forEach { println("Purchase method: ${it.name} -> ${it.returnType.name}") }
}
