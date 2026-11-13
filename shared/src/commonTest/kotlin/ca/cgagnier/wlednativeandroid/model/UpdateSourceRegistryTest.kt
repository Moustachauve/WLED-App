package ca.cgagnier.wlednativeandroid.model

import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UpdateSourceRegistryTest {

    private fun createInfo(brand: String?, product: String? = null): Info = Info(
        name = "Test Device",
        leds = Leds(),
        wifi = Wifi(),
        brand = brand,
        product = product,
    )

    @Test
    fun getSource_withIllumidelBrand_returnsIllumidelSource() {
        listOf("FR", "EN", null).forEach { product ->
            val source = UpdateSourceRegistry.getSource(createInfo(brand = "ILLUMIDEL", product = product))

            assertNotNull(source)
            assertEquals(UpdateSourceType.ILLUMIDEL, source.type)
            assertEquals("Illumidel", source.githubOwner)
            assertEquals("App-Releases", source.githubRepo)
        }
    }

    @Test
    fun getSource_withNonIllumidelBrand_returnsNull() {
        listOf("WLED", "UnknownBrand", null).forEach { brand ->
            assertNull(UpdateSourceRegistry.getSource(createInfo(brand = brand)))
        }
    }
}
