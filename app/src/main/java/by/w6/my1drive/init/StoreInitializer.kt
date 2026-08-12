package by.w6.my1drive.init

import android.app.Application
import android.content.Intent

interface StoreInitializer {
    fun initApplication(app: Application)
    fun onMainActivityNewIntent(intent: Intent?)
}
