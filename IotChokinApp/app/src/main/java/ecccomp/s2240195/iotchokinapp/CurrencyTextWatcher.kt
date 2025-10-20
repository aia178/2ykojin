package ecccomp.s2240195.iotchokinapp

import android.text.Editable
import android.text.TextWatcher
import java.text.DecimalFormat

class CurrencyTextWatcher : TextWatcher {
    private var current = ""

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        val cleanString = s.toString().replace(",", "")

        if (cleanString.isEmpty()) {
            current = ""
        } else {
            val parsed = cleanString.toLongOrNull()
            current = if (parsed != null) {
                DecimalFormat("#,###").format(parsed)
            } else {
                cleanString
            }
        }
    }

    override fun afterTextChanged(s: Editable?) {
        if (s.toString() != current) {
            s?.replace(0, s.length, current)
        }
    }

    companion object {
        // カンマを削除して数値を取得
        fun getNumericValue(text: String): Int {
            return text.replace(",", "").toIntOrNull() ?: 0
        }
    }
}
