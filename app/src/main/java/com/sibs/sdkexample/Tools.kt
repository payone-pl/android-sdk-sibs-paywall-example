package com.sibs.sdkexample

import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.widget.EditText
import java.io.Serializable
import kotlin.math.roundToInt

fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) =
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(editable: Editable?) = afterTextChanged(editable.toString())
    })

inline fun <reified T : Serializable> Bundle.getSerializableCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { getSerializable(key, T::class.java) }
            .getOrNull()
            ?: getSerializable(key) as? T //Due to SDK issue described here: https://issuetracker.google.com/issues/240585930
    } else {
        getSerializable(key) as? T
    }
}

fun Int.pxAsDp(): Int {
    val displayMetrics = Resources.getSystem().displayMetrics
    return (this * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))
        .roundToInt()
}

