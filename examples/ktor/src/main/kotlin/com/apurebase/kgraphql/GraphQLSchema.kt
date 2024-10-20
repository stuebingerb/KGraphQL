package com.apurebase.kgraphql

import com.apurebase.kgraphql.dao.DatabaseFactory
import com.apurebase.kgraphql.exception.NotAuthenticatedException
import com.apurebase.kgraphql.exception.NotFoundException
import com.apurebase.kgraphql.model.CountrySightings
import com.apurebase.kgraphql.model.UFOSighting
import com.apurebase.kgraphql.model.User
import com.apurebase.kgraphql.model.users
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.apurebase.kgraphql.service.UFOSightingService
import java.time.LocalDate

fun SchemaBuilder.ufoSchema() {
    DatabaseFactory.init()
    val service = UFOSightingService()

    stringScalar<LocalDate> {
        serialize = { date -> date.toString() }
        deserialize = { dateString -> LocalDate.parse(dateString) }
    }

    query("sightings") {
        description = "Returns a subset of the UFO Sighting records"

        resolver { size: Int? -> service.findAll(size ?: 10).toMutableList() }.withArgs {
            arg<Int> { name = "size"; defaultValue = 10; description = "The number of records to return" }
        }
    }

    query("sighting") {
        description = "Returns a single UFO Sighting record based on the id"

        resolver { id: Int ->
            service.findById(id) ?: throw NotFoundException("Sighting with id: $id does not exist")
        }
    }

    query("user") {
        description = "Returns a single User based on the id or the authenticated user."

        resolver { ctx: Context, id: Int? ->
            if (id == null) {
                User(4, ctx.get<User>()?.name ?: throw NotAuthenticatedException())
            } else {
                users.getOrNull(id - 1) ?: throw NotFoundException("User with id: $id does not exist")
            }
        }
    }

    type<User> {
        description = "A User who has reported a UFO sighting"

        property<UFOSighting?>("sighting") {
            resolver { user -> service.findById(user.id) }
        }
    }

    mutation("createUFOSighting") {
        description = "Adds a new UFO Sighting to the database"

        resolver { input: CreateUFOSightingInput ->
            service.create(input.toUFOSighting())
        }

    }

    query("topSightings") {
        description = "Returns a list of the top state,country based on the number of sightings"

        resolver { -> service.getTopSightings() }
    }

    query("topCountrySightings") {
        description = "Returns a list of the top countries based on the number of sightings"

        resolver { -> service.getTopCountrySightings() }
    }

    type<CountrySightings> {
        description = "A country sighting; contains total number of occurrences"

        property(CountrySightings::numOccurrences) {
            description = "The number of occurrences of the sighting"
        }
    }

    inputType<CreateUFOSightingInput>()

    type<UFOSighting> {
        description = "A UFO sighting"

        property(UFOSighting::dateSighting) {
            description = "The date of the sighting"
        }

        property<User>("user") {
            resolver {
                users[(0..2).shuffled().last()]
            }
        }
    }
}
