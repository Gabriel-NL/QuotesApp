package com.nog.quotesapp

import com.nog.quotesapp.model.*
import org.junit.Assert.*
import org.junit.Test

class NotificationPolicyTest {
    @Test fun dismissedQuoteStaysHiddenAcrossRepeatedRefreshes() {
        repeat(5) { assertEquals(NotificationDelivery.SKIP,notificationDelivery("2026-10-22","2026-10-22",false,false)) }
    }
    @Test fun nextDayAndHomeRerollCanShowItAgain() {
        assertEquals(NotificationDelivery.POST,notificationDelivery("2026-10-22","2026-10-23",false,false))
        assertEquals(NotificationDelivery.POST,notificationDelivery("2026-10-22","2026-10-22",false,true))
    }
    @Test fun visibleQuoteCanUpdateSilentlyAndFirstDeliveryPosts() {
        assertEquals(NotificationDelivery.UPDATE_SILENTLY,notificationDelivery("2026-10-22","2026-10-22",true,false))
        assertEquals(NotificationDelivery.POST,notificationDelivery(null,"2026-10-22",false,false))
    }
}
