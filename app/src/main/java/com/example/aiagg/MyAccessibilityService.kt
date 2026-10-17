package com.example.aiagg

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class MyAccessibilityService : AccessibilityService() {

    private val tag = "MyAccessibilityService"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(tag, "onServiceConnected")
        val info = AccessibilityServiceInfo()
        info.eventTypes = (AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                or AccessibilityEvent.TYPE_VIEW_CLICKED
                or AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                or AccessibilityEvent.TYPE_VIEW_FOCUSED)
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100L
        serviceInfo = info
        Log.d(tag, "service info configured")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            return
        }
        val et = event.eventType
        val pkg = event.packageName
        when (et) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(tag, "window state changed pkg=" + pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                Log.d(tag, "window content changed pkg=" + pkg)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                Log.d(tag, "view clicked pkg=" + pkg)
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                Log.d(tag, "view long clicked pkg=" + pkg)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                Log.d(tag, "view focused pkg=" + pkg)
            }
            else -> {
                Log.d(tag, "event type=" + et + " pkg=" + pkg)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(tag, "onInterrupt")
    }
}
