package io.github.shaonialife.cardeck.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** 网易车机版与手机版分包发布。仅使用发行方签名、公开声明的桌面入口。 */
internal object NetEaseCarEdition {
    const val MOBILE = "com.netease.cloudmusic"
    const val CAR = "com.netease.cloudmusic.iot"

    fun owns(pkg: String?) = pkg == MOBILE || pkg == CAR

    private val publisherDigest = "54254d2be09daef48dedc2b4a4f497d153e14ed9d70814fc9c360ee9240827f7"
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun launcher(context: Context): ComponentName? {
        val pm = context.packageManager
        if (!pm.hasSigningCertificate(CAR, publisherDigest, PackageManager.CERT_INPUT_SHA256)) return null
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(CAR)
        val info = pm.resolveActivity(intent, 0)?.activityInfo ?: return null
        if (!info.exported || !info.enabled || !info.applicationInfo.enabled || info.packageName != CAR) return null
        if (info.permission != null && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED) return null
        return ComponentName(info.packageName, info.name)
    }
}
