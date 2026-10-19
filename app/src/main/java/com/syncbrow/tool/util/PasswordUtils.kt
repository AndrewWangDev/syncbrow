package com.syncbrow.tool.util

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.syncbrow.tool.R
import java.security.SecureRandom

object PasswordUtils {
    private val charPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+"
    private val secureRandom = SecureRandom()

    fun generateStrongPassword(length: Int = 16): String {
        return (1..length)
            .map { charPool[secureRandom.nextInt(charPool.length)] }
            .joinToString("")
    }

    enum class Strength {
        VERY_WEAK, WEAK, NORMAL, STRONG, VERY_STRONG
    }

    data class StrengthResult(
        val strength: Strength,
        @StringRes val labelResId: Int,
        val color: Color
    )

    fun evaluateStrength(password: String): StrengthResult {
        if (password.isEmpty()) return StrengthResult(Strength.VERY_WEAK, R.string.password_strength_none, Color.Gray)
        
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { "!@#$%^&*()-_=+".contains(it) }) score++

        return when {
            score <= 2 -> StrengthResult(Strength.WEAK, R.string.password_strength_weak, Color(0xFFE57373))
            score <= 4 -> StrengthResult(Strength.NORMAL, R.string.password_strength_normal, Color(0xFFFFB74D))
            score <= 5 -> StrengthResult(Strength.STRONG, R.string.password_strength_strong, Color(0xFF81C784))
            else -> StrengthResult(Strength.VERY_STRONG, R.string.password_strength_very_strong, Color(0xFF4CAF50))
        }
    }
}
