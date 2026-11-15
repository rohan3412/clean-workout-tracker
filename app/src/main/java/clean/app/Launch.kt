package clean.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * What the launcher icons open: every icon alias targets this, and it hands over to [MainActivity] at once. The app's task then
 * starts with MainActivity itself, not with an alias, because Android closes a task whose launching alias is disabled, and a
 * change of look disables the alias of the old icon (that was the "crash" on changing presets). It has a task affinity of its own,
 * so MainActivity's task is found and brought to the front as it was.
 */
class Launch : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
