package by.w6.my1drive

import android.app.Application

class My1DriveApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        by.w6.my1drive.init.StoreAppInitializer.initApplication(this)
    }

}
