package ru.senin.kotlin.net

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table

object messages : IntIdTable() {
    var directionUser = messages.varchar("name", length = 20)
    var message = messages.varchar("name", length = 140)
}