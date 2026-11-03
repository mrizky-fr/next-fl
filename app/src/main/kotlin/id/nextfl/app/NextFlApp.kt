package id.nextfl.app

import android.app.Application

class NextFlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.session.start()
    }
}
