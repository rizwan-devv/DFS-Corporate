package com.example.kycapp

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

class KycApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val loaded = OpenCVLoader.initLocal()
        Log.i("KycApplication", if (loaded) "OpenCV loaded" else "OpenCV FAILED to load")

        // net.zetetic:sqlcipher-android never loads its own native lib -- unlike
        // the old net.zetetic:android-database-sqlcipher's SQLiteDatabase.loadLibs(),
        // this artifact has no such helper, so the app must load it explicitly
        // before the first SupportOpenHelperFactory/Room database open.
        System.loadLibrary("sqlcipher")
        Log.i("KycApplication", "libsqlcipher loaded")
    }
}
