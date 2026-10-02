package com.example

import com.example.ui.screens.settings.SettingsCategory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun testSettingsCategoryDoesNotCrash() {
    val values = SettingsCategory.values()
    assert(values.isNotEmpty())
  }
}
