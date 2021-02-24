package ru.senin.kotlin.net.registry
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object userBase : Table() {
    var name = varchar("name", length = 20)
    var protocol = varchar("protocol", length = 10)
    var host = varchar("host", length = 20)
    var port = integer("port")

    override val primaryKey = PrimaryKey(name)
}