package net.slashetc.callinspector

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.slashetc.callinspector.data.model.PhoneNumberType
import net.slashetc.callinspector.util.PhoneNumberFormatter
import org.junit.Assert.assertEquals
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
        val expected = if (BuildConfig.DEBUG) "Info Opérateur (debug)" else "Info Opérateur"
        assertEquals(expected, appName)
    }

    @Test
    fun `test phone number normalization`() {
        assertEquals("0612345678", PhoneNumberFormatter.normalize("+33 6 12 34 56 78"))
        assertEquals("0142680000", PhoneNumberFormatter.normalize("+33142680000"))
        assertEquals("01 62 00 11 22", PhoneNumberFormatter.format("0162001122"))
    }

    @Test
    fun `test demarchage regulatory classification`() {
        val type = PhoneNumberType.classify("0162001122")
        assertEquals(PhoneNumberType.DEMARCHAGE_COMMERCIAL, type)
        assertTrue(type.isDemarchage)

        val mobileType = PhoneNumberType.classify("0612345678")
        assertEquals(PhoneNumberType.MOBILE, mobileType)
    }
}

