package com.lumetime.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val yearProgress = TimeCalculator.computeYearProgress(System.currentTimeMillis())
        binding.tvYearSummary.text = String.format(
            Locale.US,
            "%d: %d days (%d hrs) left • %.1f%% complete",
            yearProgress.year,
            yearProgress.daysLeft,
            yearProgress.hoursLeft,
            yearProgress.percentCompleted
        )
    }
}
