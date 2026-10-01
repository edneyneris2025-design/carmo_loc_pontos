package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    val c = androidx.compose.ui.graphics.Color((0xFFFF5252).toInt())
    assertEquals(1.0f, c.alpha, 0.01f)
    assertEquals(1.0f, c.red, 0.01f)
    assertEquals(4, 2 + 2)
  }
}
