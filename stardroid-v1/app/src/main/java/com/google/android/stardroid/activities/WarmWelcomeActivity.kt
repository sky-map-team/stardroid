package com.google.android.stardroid.activities

import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.R
import com.google.android.stardroid.StardroidApplication
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WarmWelcomeActivity : AppCompatActivity() {

    @Inject lateinit var sharedPreferences: SharedPreferences
    @Inject lateinit var app: StardroidApplication
    @Inject @JvmField var sensorManager: SensorManager? = null

    private lateinit var viewPager: ViewPager2
    private lateinit var doNotShowAgainCheckbox: CheckBox
    private lateinit var btnSkip: Button
    private lateinit var btnNextFinish: Button
    private lateinit var indicatorsContainer: LinearLayout
    private var isManualInvocation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warm_welcome)

        isManualInvocation = intent.getBooleanExtra("is_manual_invocation", false)

        viewPager = findViewById(R.id.warm_welcome_viewpager)
        doNotShowAgainCheckbox = findViewById(R.id.do_not_show_again_checkbox)
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
                if (position == adapter.itemCount - 1) {
                    btnNextFinish.setText(R.string.warm_welcome_finish)
                    // Only show checkbox if this is an automatic launch
                    doNotShowAgainCheckbox.visibility = if (isManualInvocation) View.GONE else View.VISIBLE
                } else {
                    btnNextFinish.setText(R.string.warm_welcome_next)
                    doNotShowAgainCheckbox.visibility = View.GONE
                }
            }
        })

        btnSkip.setOnClickListener { finishWelcome() }
        btnNextFinish.setOnClickListener {
            if (viewPager.currentItem < adapter.itemCount - 1) {
                viewPager.currentItem += 1
            } else {
                finishWelcome()
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

    private fun finishWelcome() {
        if (!isManualInvocation) {
            sharedPreferences.edit().apply {
                putLong(ApplicationConstants.READ_WARM_WELCOME_PREF_VERSION, app.version.toLong())
                if (doNotShowAgainCheckbox.isChecked) {
                    putBoolean(ApplicationConstants.NO_WARN_ABOUT_MISSING_SENSORS, true)
                }
                apply()
            }

            val intent = Intent(this, DynamicStarMapActivity::class.java)
            startActivity(intent)
        }
        finish()
    }

    private inner class WelcomePagerAdapter(fa: AppCompatActivity) : FragmentStateAdapter(fa) {
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

    class Slide1Fragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
        ): View = inflater.inflate(R.layout.fragment_welcome_slide_1, container, false)
    }

    class Slide2Fragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
        ): View = inflater.inflate(R.layout.fragment_welcome_slide_2, container, false)
    }

    class Slide3Fragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
        ): View {
            val view = inflater.inflate(R.layout.fragment_welcome_slide_3, container, false)

            val compassIcon = view.findViewById<ImageView>(R.id.compass_status_icon)
            val accelIcon = view.findViewById<ImageView>(R.id.accelerometer_status_icon)
            val messageText = view.findViewById<TextView>(R.id.sensor_message_text)

            val activity = requireActivity() as WarmWelcomeActivity
            val sensorManager = activity.sensorManager

            val hasCompass = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
            val hasAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

            compassIcon.setImageResource(if (hasCompass) R.drawable.ic_check_circle else R.drawable.ic_warning)
            accelIcon.setImageResource(if (hasAccel) R.drawable.ic_check_circle else R.drawable.ic_warning)

            if (!hasCompass || !hasAccel) {
                messageText.setText(R.string.warm_welcome_slide3_no_sensors)
            } else {
                messageText.setText(R.string.warm_welcome_slide3_compass_calib)
            }

            return view
        }
    }
}
