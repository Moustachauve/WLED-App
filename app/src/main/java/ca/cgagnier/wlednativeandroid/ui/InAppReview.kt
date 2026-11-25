package ca.cgagnier.wlednativeandroid.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory
import java.util.concurrent.TimeUnit

/**
 * Native Google Play "Rate this app" sheet (In-App Review API).
 *
 * Rules (same as iOS):
 *  - never on the first launches: only from the 5th launch,
 *  - at least 3 days after the first launch,
 *  - the user has at least 1 controller in the list,
 *  - at most once every 120 days, and at most 3 times in total.
 *
 * Google decides in the end whether the sheet is really shown (quota), and it
 * never says if the user rated, so there is no "thank you" or follow-up.
 */
object InAppReview {
    private const val TAG = "InAppReview"
    private const val PREFS = "illumidel_review"
    private const val KEY_LAUNCHES = "launches"
    private const val KEY_FIRST_LAUNCH = "first_launch"
    private const val KEY_LAST_ASKED = "last_asked"
    private const val KEY_TIMES_ASKED = "times_asked"

    private const val MIN_LAUNCHES = 5
    private const val MIN_DAYS_INSTALLED = 3L
    private const val DAYS_BETWEEN_ASKS = 120L
    private const val MAX_ASKS = 3

    private var askedThisSession = false

    fun recordLaunch(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putInt(KEY_LAUNCHES, prefs.getInt(KEY_LAUNCHES, 0) + 1)
            if (!prefs.contains(KEY_FIRST_LAUNCH)) {
                putLong(KEY_FIRST_LAUNCH, System.currentTimeMillis())
            }
        }
    }

    fun maybeAsk(activity: Activity, deviceCount: Int) {
        if (askedThisSession || deviceCount < 1) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val launches = prefs.getInt(KEY_LAUNCHES, 0)
        val firstLaunch = prefs.getLong(KEY_FIRST_LAUNCH, now)
        val lastAsked = prefs.getLong(KEY_LAST_ASKED, 0L)
        val timesAsked = prefs.getInt(KEY_TIMES_ASKED, 0)

        val hasEnoughLaunches = launches >= MIN_LAUNCHES
        val isOldEnough = now - firstLaunch >= TimeUnit.DAYS.toMillis(MIN_DAYS_INSTALLED)
        if (!hasEnoughLaunches || !isOldEnough) return

        val cooldownElapsed = now - lastAsked >= TimeUnit.DAYS.toMillis(DAYS_BETWEEN_ASKS)
        val underLimit = timesAsked < MAX_ASKS
        if (!cooldownElapsed || !underLimit) return

        askedThisSession = true
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                Log.w(TAG, "requestReviewFlow failed", request.exception)
                return@addOnCompleteListener
            }
            if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
            manager.launchReviewFlow(activity, request.result).addOnCompleteListener {
                prefs.edit {
                    putLong(KEY_LAST_ASKED, System.currentTimeMillis())
                    putInt(KEY_TIMES_ASKED, timesAsked + 1)
                }
            }
        }
    }
}
