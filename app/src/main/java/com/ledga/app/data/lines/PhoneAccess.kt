package com.ledga.app.data.lines

/** Whether Ledga may read the phone's SIMs (READ_PHONE_STATE, R33): it names lines and files single-SIM messages. */
fun interface PhoneAccess {
    fun granted(): Boolean
}
