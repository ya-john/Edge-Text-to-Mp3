package com.edgetts.mp3.tts

import org.junit.Assert.assertEquals
import org.junit.Test

/** Vectors generated with rany2/edge-tts 7.3.1 `generate_sec_ms_gec_v2(ts)`. */
class SecMsGecTest {
    @Test
    fun matchesReferenceImplementation() {
        assertEquals(
            "2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2",
            SecMsGec.generate(1_700_000_000L),
        )
        assertEquals(
            "92B7FB55A2CA83ED-71842240C80DE51D944907D8E73F902DC4DADD252E67011C0D831748A0336A73",
            SecMsGec.generate(1_763_900_000L),
        )
        assertEquals(
            "6521DCC55F99AAD7-6D04852628E3DF4D0EF33213239BDC3C48750FE37FE5E4591F28C209CE0A3FA2",
            SecMsGec.generate(1_795_000_000L),
        )
    }
}
