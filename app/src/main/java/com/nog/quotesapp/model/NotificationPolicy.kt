package com.nog.quotesapp.model

enum class NotificationDelivery { POST, UPDATE_SILENTLY, SKIP }

fun notificationDelivery(previousPeriod:String?,period:String,visible:Boolean,homeReroll:Boolean):NotificationDelivery = when {
    homeReroll || previousPeriod!=period -> NotificationDelivery.POST
    visible -> NotificationDelivery.UPDATE_SILENTLY
    else -> NotificationDelivery.SKIP
}
