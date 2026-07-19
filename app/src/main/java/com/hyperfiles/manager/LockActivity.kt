package com.hyperfiles.manager

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.hyperfiles.manager.databinding.ActivityLockBinding

class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private var setup = false
    private var method = "pin"
    private var firstEntry: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setup = !SecurityPrefs.isSet(this)
        method = if (setup) "pin" else SecurityPrefs.lockType(this)

        binding.patternView.onComplete = { submit(it.joinToString("-")) }
        binding.btnMethodPin.setOnClickListener { method = "pin"; firstEntry = null; renderMethod() }
        binding.btnMethodPattern.setOnClickListener { method = "pattern"; firstEntry = null; renderMethod() }
        binding.btnPrimary.setOnClickListener { submit(binding.pinInput.text.toString()) }
        binding.btnFingerprint.setOnClickListener { showBiometric() }

        renderMode()
        if (!setup && SecurityPrefs.useBiometric(this) && biometricAvailable()) showBiometric()
    }

    private fun renderMode() {
        binding.methodRow.visibility = if (setup) View.VISIBLE else View.GONE
        binding.lockTitle.text = if (setup) "Set up secure folder" else "Unlock secure folder"
        binding.btnFingerprint.visibility =
            if (!setup && SecurityPrefs.useBiometric(this) && biometricAvailable()) View.VISIBLE else View.GONE
        renderMethod()
    }

    private fun renderMethod() {
        val pin = method == "pin"
        binding.pinInput.visibility = if (pin) View.VISIBLE else View.GONE
        binding.patternView.visibility = if (pin) View.GONE else View.VISIBLE
        binding.btnPrimary.visibility = if (pin) View.VISIBLE else View.GONE
        binding.pinInput.setText("")
        binding.patternView.reset()
        binding.lockSubtitle.text = when {
            !setup -> "Enter your $method"
            firstEntry == null -> "Create your $method"
            else -> "Confirm your $method"
        }
        binding.btnPrimary.text = if (setup) "Continue" else "Unlock"
    }

    private fun submit(secret: String) {
        if (secret.isBlank()) return
        if (setup) {
            if (method == "pin" && secret.length < 4) { toast("Use at least 4 digits"); return }
            if (method == "pattern" && secret.split("-").size < 4) { toast("Connect at least 4 dots"); resetInput(); return }
            if (firstEntry == null) {
                firstEntry = secret; resetInput(); renderMethod()
            } else if (secret == firstEntry) {
                SecurityPrefs.setLock(this, method, secret); afterSetup()
            } else {
                toast("Entries don't match"); firstEntry = null; resetInput(); renderMethod()
            }
        } else {
            if (SecurityPrefs.verify(this, secret)) openFolder()
            else { toast("Incorrect"); resetInput() }
        }
    }

    private fun resetInput() {
        binding.pinInput.setText("")
        binding.patternView.reset()
    }

    private fun afterSetup() {
        if (biometricAvailable()) {
            AlertDialog.Builder(this)
                .setTitle("Fingerprint unlock")
                .setMessage("Also unlock the secure folder with your fingerprint?")
                .setPositiveButton("Yes") { _, _ -> SecurityPrefs.setBiometric(this, true); openFolder() }
                .setNegativeButton("No") { _, _ -> SecurityPrefs.setBiometric(this, false); openFolder() }
                .setCancelable(false)
                .show()
        } else openFolder()
    }

    private fun openFolder() {
        startActivity(Intent(this, SecureFolderActivity::class.java))
        finish()
    }

    private fun biometricAvailable(): Boolean =
        BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometric() {
        if (!biometricAvailable()) return
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    openFolder()
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock secure folder")
            .setSubtitle("Use your fingerprint")
            .setNegativeButtonText("Use PIN / pattern")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
