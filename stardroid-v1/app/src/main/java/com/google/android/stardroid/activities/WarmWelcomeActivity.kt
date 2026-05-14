package com.google.android.stardroid.activities

import android.content.Intent
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.google.android.stardroid.ApplicationConstants.BUNDLE_IS_MANUAL_INVOCATION
import com.google.android.stardroid.R
import com.google.android.stardroid.activities.dialogs.WhatsNewDialogFragment
import com.google.android.stardroid.util.Analytics
import com.google.android.stardroid.util.AnalyticsInterface
import com.google.android.stardroid.util.MiscUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WarmWelcomeActivity : AppCompatActivity(), WhatsNewDialogFragment.CloseListener {

    @Inject lateinit var startupRouter: StartupRouter
    @Inject @JvmField var sensorManager: SensorManager? = null
    @Inject @JvmField var vibrator: Vibrator? = null
    @Inject lateinit var analytics: Analytics
    private var lastLoggedPosition = -1

    private lateinit var viewPager: ViewPager2
    private lateinit var btnSkip: Button
    private lateinit var btnNextFinish: Button
    private lateinit var indicatorsContainer: LinearLayout
    private var isManualInvocation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isManualInvocation = intent.getBooleanExtra(BUNDLE_IS_MANUAL_INVOCATION, false)

        if (savedInstanceState == null) {
            val params = Bundle().apply {
                putBoolean(AnalyticsInterface.WARM_WELCOME_STARTED_MANUAL, isManualInvocation)
            }
            analytics.trackEvent(AnalyticsInterface.WARM_WELCOME_STARTED_EVENT, params)

            // Also log the viewing of the first slide, since onPageSelected isn't called for it.
            val slideParams = Bundle().apply {
                putInt(AnalyticsInterface.WARM_WELCOME_SLIDE_NUMBER, 1)
            }
            analytics.trackEvent(AnalyticsInterface.WARM_WELCOME_SLIDE_VIEWED_EVENT, slideParams)
        }

        setContentView(R.layout.activity_warm_welcome)

        viewPager = findViewById(R.id.warm_welcome_viewpager)
        btnSkip = findViewById(R.id.btn_skip)
        btnNextFinish = findViewById(R.id.btn_next_finish)
        indicatorsContainer = findViewById(R.id.indicators_container)

        val adapter = WelcomePagerAdapter(this)
        viewPager.adapter = adapter

        setupIndicators(adapter.itemCount)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)

                // Just in case we got here due to a screen rotation.
                if (lastLoggedPosition != -1) {
                    val slideParams = Bundle().apply {
                        putInt(AnalyticsInterface.WARM_WELCOME_SLIDE_NUMBER, position + 1)
                    }
                    analytics.trackEvent(
                        AnalyticsInterface.WARM_WELCOME_SLIDE_VIEWED_EVENT,
                        slideParams
                    )
                    lastLoggedPosition = position
                }

                if (position == adapter.itemCount - 1) {
                    btnNextFinish.setText(R.string.warm_welcome_finish)
                } else {
                    btnNextFinish.setText(R.string.warm_welcome_next)
                }
                val fragment = supportFragmentManager.findFragmentByTag("f$position")
                if (fragment is Animatable) {
                    Log.w(TAG, "Reanimating")
                    fragment.onSelected()
                }
            }
        })

        btnSkip.setOnClickListener {
            val skipParams = Bundle().apply {
                putInt(AnalyticsInterface.WARM_WELCOME_SLIDE_NUMBER, viewPager.currentItem + 1)
            }
            analytics.trackEvent(AnalyticsInterface.WARM_WELCOME_SKIPPED_EVENT, skipParams)
            finishWelcome(completed = false)
        }
        btnNextFinish.setOnClickListener {
            if (viewPager.currentItem < adapter.itemCount - 1) {
                viewPager.currentItem += 1
            } else {
                analytics.trackEvent(AnalyticsInterface.WARM_WELCOME_COMPLETED_EVENT, null)
                finishWelcome(completed = true)
            }
        }
    }

    private fun setupIndicators(count: Int) {
        val indicators = arrayOfNulls<ImageView>(count)
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(8, 0, 8, 0) }

        for (i in indicators.indices) {
            indicators[i] = ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(this@WarmWelcomeActivity, R.drawable.star_off))
                this.layoutParams = layoutParams
            }
            indicatorsContainer.addView(indicators[i])
        }
    }

    private fun updateIndicators(position: Int) {
        val count = indicatorsContainer.childCount
        for (i in 0 until count) {
            val imageView = indicatorsContainer.getChildAt(i) as ImageView
            if (i == position) {
                imageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.star_on))
            } else {
                imageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.star_off))
            }
        }
    }

    private fun finishWelcome(completed: Boolean = false) {
        if (!isManualInvocation) {
            // Only update the user property on first invocation to avoid overwriting completion status
            analytics.setUserProperty(AnalyticsInterface.COMPLETED_WARM_WELCOME, completed.toString())
            startupRouter.markWarmWelcomeSeen()
            if (startupRouter.needsWhatsNew()) {
                showDialog(
                    WhatsNewDialogFragment.newInstance(),
                    WhatsNewDialogFragment::class.java.simpleName
                )
            } else {
                launchSkyMap()
            }
        } else {
            finish()
        }
    }

    override fun dialogClosed() {
        startupRouter.markWhatsNewSeen()
        launchSkyMap()
    }

    private fun showDialog(fragment: androidx.fragment.app.DialogFragment, tag: String) {
        if (supportFragmentManager.findFragmentByTag(tag) == null) {
            fragment.show(supportFragmentManager, tag)
        }
    }

    private fun launchSkyMap() {
        val intent = Intent(this, DynamicStarMapActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun buzz(happy: Boolean) {
        val v = vibrator ?: return
    fun buzz(happy: Boolean) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effectId = if (happy) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_DOUBLE_CLICK
            VibrationEffect.createPredefined(effectId)
        } else {
            if (happy) VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            else VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1)
        }
        v.vibrate(effect)
    }

    private class WelcomePagerAdapter(fa: AppCompatActivity) : FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> Slide1Fragment()
                1 -> Slide2Fragment()
                2 -> Slide3Fragment()
                else -> Slide1Fragment()
            }
        }

        override fun getItemCount(): Int = 3
    }

    interface Animatable {
        fun onSelected();
    }
    class Slide1Fragment : Fragment() {
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private var currentIndex = 0
        private val highlightGroups = intArrayOf(
            R.id.highlight_stars,
            R.id.highlight_constellations,
            R.id.highlight_deep_sky,
            R.id.highlight_planets,
            R.id.highlight_meteors,
            R.id.highlight_grid,
            R.id.highlight_horizon,
            R.id.highlight_search,
            R.id.highlight_night_mode,
            R.id.highlight_time_travel,
            R.id.highlight_manual_auto
        )
        private var isAnimating = false

        private lateinit var highlightViews: List<View>

        private val animationRunnable = object : Runnable {
            override fun run() {
                if (!isAdded || !isAnimating) return
                
                // Hide all
                highlightViews.forEach { it.visibility = View.GONE }
                
                // Show current and increment
                if (highlightViews.isNotEmpty()) {
                    highlightViews[currentIndex].visibility = View.VISIBLE
                    currentIndex = (currentIndex + 1) % highlightViews.size
                }
                
                // Post next
                handler.postDelayed(this, 1000)
            }
        }

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
        ): View = inflater.inflate(R.layout.fragment_welcome_slide_1, container, false)

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val views = mutableListOf<View>()
            for (id in highlightGroups) {
                view.findViewById<View>(id)?.let { views.add(it) }
            }
            highlightViews = views
        }

        override fun onResume() {
            super.onResume()
            isAnimating = true
            currentIndex = 0
            handler.post(animationRunnable)
        }

        override fun onPause() {
            super.onPause()
            isAnimating = false
            handler.removeCallbacks(animationRunnable)
        }
    }

    class Slide2Fragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
        ): View = inflater.inflate(R.layout.fragment_welcome_slide_2, container, false)

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            view.findViewById<ImageView>(R.id.slide2_background)
                .load("file:///android_asset/celestial_images/deep_sky_objects/hubble_m1.jpg") {
                    crossfade(true)
                }
        }
    }

    class Slide3Fragment : Fragment(), Animatable {
        private lateinit var messageText: TextView
        private lateinit var compassIcon: ImageView
        private lateinit var accelIcon: ImageView
        private lateinit var gyroIcon: ImageView
        private lateinit var gyroSpinner: View
        private lateinit var accelSpinner: View
        private lateinit var compassSpinner: View
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private var hasCompass = false
        private var hasAccel = false
        private var hasGyro = false

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
        ): View {
            val view = inflater.inflate(R.layout.fragment_welcome_slide_3, container, false)
            view.findViewById<ImageView>(R.id.slide3_background)
                .load("file:///android_asset/celestial_images/planets/cassini_iapetus.webp") {
                    crossfade(true)
                }

            compassSpinner = view.findViewById<View>(R.id.compass_status_spinner)
            accelSpinner = view.findViewById<View>(R.id.accelerometer_status_spinner)
            gyroSpinner = view.findViewById<View>(R.id.gyroscope_status_spinner)

            compassIcon = view.findViewById<ImageView>(R.id.compass_status_icon)
            accelIcon = view.findViewById<ImageView>(R.id.accelerometer_status_icon)
            gyroIcon = view.findViewById<ImageView>(R.id.gyroscope_status_icon)

            messageText = view.findViewById<TextView>(R.id.sensor_message_text)

            val activity = requireActivity() as WarmWelcomeActivity
            val sensorManager = activity.sensorManager

            hasCompass = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
            hasAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
            hasGyro = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

            val goodColor = ContextCompat.getColor(requireContext(), R.color.status_good)
            val badColor = ContextCompat.getColor(requireContext(), R.color.status_bad)

            compassIcon.setImageResource(if (hasCompass) R.drawable.ic_check_circle else R.drawable.ic_warning)
            compassIcon.setColorFilter(if (hasCompass) goodColor else badColor)

            accelIcon.setImageResource(if (hasAccel) R.drawable.ic_check_circle else R.drawable.ic_warning)
            accelIcon.setColorFilter(if (hasAccel) goodColor else badColor)

            gyroIcon.setImageResource(if (hasGyro) R.drawable.ic_check_circle else R.drawable.ic_warning)
            gyroIcon.setColorFilter(if (hasGyro) goodColor else badColor)

            if (!hasCompass || !hasAccel) {
                messageText.setText(R.string.warm_welcome_slide3_no_sensors)
            } else {
                messageText.setText(R.string.warm_welcome_slide3_compass_calib)
            }

            return view
        }

        override fun onDestroyView() {
            super.onDestroyView()
            handler.removeCallbacksAndMessages(null)
        }

        override fun onSelected() {
            listOf(compassSpinner, accelSpinner, gyroSpinner).forEach { it.visibility = View.VISIBLE }
            listOf(compassIcon, accelIcon, gyroIcon, messageText).forEach { it.visibility = View.GONE }

            handler.postDelayed({
                if (isAdded) {
                    compassSpinner.visibility = View.GONE
                    compassIcon.visibility = View.VISIBLE
                    (activity as? WarmWelcomeActivity)?.buzz(hasCompass)
                }
            }, 800)

            handler.postDelayed({
                if (isAdded) {
                    accelSpinner.visibility = View.GONE
                    accelIcon.visibility = View.VISIBLE
                    (activity as? WarmWelcomeActivity)?.buzz(hasAccel)
                }
            }, 1600)

            handler.postDelayed({
                if (isAdded) {
                    gyroSpinner.visibility = View.GONE
                    gyroIcon.visibility = View.VISIBLE
                    messageText.visibility = View.VISIBLE
                    (activity as? WarmWelcomeActivity)?.buzz(hasGyro)
                }
            }, 2400)
        }
    }
    companion object {
        private val TAG = MiscUtil.getTag(WarmWelcomeActivity::class.java)
    }
}
