package com.apurebase.kgraphql

import com.apurebase.kgraphql.model.UFOSighting
import java.time.LocalDate

fun CreateUFOSightingInput.toUFOSighting(): UFOSighting {
    return UFOSighting(
        dateSighting = this.date,
        city = this.country.city,
        state = this.country.state,
        country = this.country.country,
        shape = this.shape,
        duration = this.duration,
        comments = this.country.comments,
        latitude = this.latitude,
        longitude = this.longitude
    )
}

data class CreateUFOSightingInput(
    var date: LocalDate = LocalDate.now(),
    var country: CountryInput = CountryInput(),
    var shape: String = "",
    var duration: Double = 0.0,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0
)


data class CountryInput(
    var city: String = "",
    var state: String = "",
    var country: String = "",
    var comments: String = ""
)
