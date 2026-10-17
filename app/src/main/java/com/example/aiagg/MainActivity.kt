package com.example.aiagg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import android.app.AlertDialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : Activity() {

    companion object {
        const val ACTION_SEND_TO_AI = "com.example.aiagg.ACTION_SEND_TO_AI"
        const val ACTION_AI_RESULT = "com.example.aiagg.ACTION_AI_RESULT"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_RESULT = "extra_result"
        const val EXTRA_ERROR = "extra_error"

        private val APPS = linkedMapOf(
            "com.larus.wormhole" to "豆包",
            "com.aliyun.tongyi" to "通义千问",
            "com.deepseek.chat" to "DeepSeek",
            "com.zhipu.qlib" to "智谱清言",
            "com.openai.chatgpt" to "ChatGPT"
        )

        private const val REQUEST_ENABLE_A11Y = 1001
    }

    private lateinit var spinnerApp: Spinner
    private lateinit var inputText: EditText
    private lateinit var btnSend: Button
    private lateinit var resultText: TextView
    private lateinit var statusText: TextView

    private val installedApps = LinkedHashMap<String, String>()

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val result = intent?.getStringExtra(EXTRA_RESULT).orEmpty()
            val err = intent?.getStringExtra(EXTRA_ERROR)
            if (!err.isNullOrBlank()) {
                resultText.setText("错误：$err")
            } else if (result.isNotEmpty()) {
                resultText.setText(result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerApp = findViewById(R.id.spinnerApp)
        inputText = findViewById(R.id.inputText)
        btnSend = findViewById(R.id.btnSend)
        resultText = findViewById(R.id.resultText)
        statusText = findViewById(R.id.statusText)

        refreshInstalledApps()
        btnSend.setOnClickListener { onSend() }
    }

    private fun refreshInstalledApps() {
        installedApps.clear()
        val pm = packageManager
        for ((pkg, displayName) in APPS) {
            val installed = try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
            if (installed) installedApps[pkg] = displayName
        }

        val names = installedApps.values.toList()
        spinnerApp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        if (names.isNotEmpty()) {
            spinnerApp.setSelection(0)
        }
    }

    private fun onSend() {
        val text = inputText.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "请先输入内容", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
            promptEnableAccessibility()
            return
        }

        val pkg = spinnerApp.selectedItemPosition.let { i ->
            installedApps.keys.toList().getOrNull(i)
        }
        if (pkg == null) {
            Toast.makeText(this, "未检测到已安装的 AI 应用", Toast.LENGTH_LONG).show()
            return
        }

        val sendIntent = Intent(ACTION_SEND_TO_AI).putExtra(EXTRA_TEXT, text)
        LocalBroadcastManager.getInstance(this).sendBroadcast(sendIntent)

        launchApp(pkg)
        resultText.setText("正在等待 AI 回复…")
    }

    private fun launchApp(pkg: String) {
        val pm = packageManager
        val intent = pm.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, "无法打开目标应用：$pkg", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            "enabled_accessibility_services"
        ).orEmpty()
        val serviceName = "$packageName/${MyAccessibilityService::class.java.name}"
        val parts = enabled.split(':')
        return parts.any { it.trim() == serviceName }
    }

    private fun promptEnableAccessibility() {
        AlertDialog.Builder(this)
            .setTitle("无障碍服务未开启")
            .setMessage("该 App 需要无障碍服务来自动操作目标 AI 应用。请在列表中找到「AI聚合外壳」并开启。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityStatus()
        refreshInstalledApps()
    }

    private fun refreshAccessibilityStatus() {
        val enabled = isAccessibilityServiceEnabled()
        statusText.text = if (enabled) "无障碍服务：已开启" else "无障碍服务：未开启"
        statusText.setTextColor(
            if (enabled) 0xFF00897B.toInt() else 0xFFE60000.toInt()
        )
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(resultReceiver, IntentFilter(ACTION_AI_RESULT))
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(resultReceiver)
        super.onStop()
    }
}
