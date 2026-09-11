package by.w6.my1drive

import android.app.Application
import android.database.CursorWindow
import android.util.Log

class My1DriveApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 50 * 1024 * 1024) // 50 MB
            Log.d("My1DriveApplication", "Successfully expanded CursorWindow buffer to 50MB")
        } catch (e: Throwable) {
            Log.w("My1DriveApplication", "Could not set sCursorWindowSize via reflection: ${e.message}")
        }

        by.w6.my1drive.init.StoreAppInitializer.initApplication(this)
    }

}
