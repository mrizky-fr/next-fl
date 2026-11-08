package id.nextfl.app

import android.app.Application
import id.nextfl.app.platform.AndroidPlatform

class NextFlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(AndroidPlatform(this))
        container.session.start()
    }
}
