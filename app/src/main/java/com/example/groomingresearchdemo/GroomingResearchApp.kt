package com.example.groomingresearchdemo

import android.app.Application
import com.example.groomingresearchdemo.state.ResearchStore

/**
 * Initialises the [ResearchStore] so both the Activity and the AccessibilityService (which can
 * be started by the system independently of the Activity) find the same application-scoped
 * singleton ready to use.
 *
 * IMPORTANT: this init loads only the consent decision from preferences. It does NOT restore
 * any armed-inspection state. If the process restarts, the app never auto-begins an inspection.
 */
class GroomingResearchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ResearchStore.init(this)
    }
}
