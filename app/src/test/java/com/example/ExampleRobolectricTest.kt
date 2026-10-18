package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.CloudUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CloudBox", appName)
  }

  @Test
  fun `test cloud url parser with public link`() {
    val url = "https://cloud.mail.ru/public/KVy7/S1hRRPVUE"
    val weblink = CloudUrlParser.extractWeblink(url)
    assertEquals("KVy7/S1hRRPVUE", weblink)
    assertTrue(CloudUrlParser.isValidMailRuUrl(url))
  }
}
