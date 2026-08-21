package dev.nullcode.powerflash

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Neutralizes power-button long-press (which normally triggers whatever
 * LONG_PRESS_POWER behavior is configured — almost always the global
 * actions / power menu dialog) and replaces it with a flashlight toggle.
 *
 * Targets com.android.server.policy.PhoneWindowManager inside system_server
 * (package "android"). powerLongPress() is the terminal handler PWM calls
 * once it has decided the key event qualifies as a long press — hooking it
 * with a full result override means the power menu never gets constructed,
 * regardless of Settings.Secure long-press config.
 */
class MainHook : IXposedHookLoadPackage {

    private var torchOn = false
    private var cameraId: String? = null
    private var callbackRegistered = false

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android") return

        val pwmClass = try {
            XposedHelpers.findClass(
                "com.android.server.policy.PhoneWindowManager",
                lpparam.classLoader
            )
        } catch (t: Throwable) {
            return
        }

        XposedBridge.hookAllMethods(
            pwmClass,
            "powerLongPress",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as? Context
                    if (context != null) toggleFlashlight(context)
                    param.result = null
                }
            }
        )
    }

    private fun getCameraId(cm: CameraManager): String? {
        cameraId?.let { return it }
        for (id in cm.cameraIdList) {
            val chars = cm.getCameraCharacteristics(id)
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (hasFlash == true && facing == CameraCharacteristics.LENS_FACING_BACK) {
                cameraId = id
                return id
            }
        }
        return null
    }

    private fun toggleFlashlight(context: Context) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = getCameraId(cm) ?: return

            if (!callbackRegistered) {
                cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(camId: String, enabled: Boolean) {
                        if (camId == id) torchOn = enabled
                    }
                }, null)
                callbackRegistered = true
            }

            cm.setTorchMode(id, !torchOn)
        } catch (t: Throwable) {
            // no torch
        }
    }
}
