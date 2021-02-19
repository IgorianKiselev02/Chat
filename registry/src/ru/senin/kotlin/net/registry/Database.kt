package ru.senin.kotlin.net.registry
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object userBase : Table() {
    val name = varchar("name", length = 20)
    val protocol = varchar("protocol", length = 10)
    val host = varchar("host", length = 20)
    val port = integer("port")

    override val primaryKey = PrimaryKey(name)
}