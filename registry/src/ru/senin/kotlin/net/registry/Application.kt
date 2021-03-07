package ru.senin.kotlin.net.registry

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.event.Level
import ru.senin.kotlin.net.Protocol
import ru.senin.kotlin.net.UserAddress
import ru.senin.kotlin.net.UserInfo
import ru.senin.kotlin.net.checkUserName
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    thread {
        // TODO: periodically check users and remove unreachable ones
    }
    EngineMain.main(args)
}

interface UserStorage {
    fun containsUser(name: String) : Boolean
    fun setOrUpdateUser(name: String, address: UserAddress)
    fun removeUser(name: String)
    fun clearUsers()
    operator fun set(userName: String, value: Any) {

    }
}

class MemoryUsersStorage : UserStorage {
    val storage = ConcurrentHashMap<String, UserAddress>()

    override fun containsUser(name: String): Boolean {
        return storage.contains(name)
    }

    override fun setOrUpdateUser(name: String, address: UserAddress) {
        storage[name] = address
    }

    override fun removeUser(name: String) {
        storage.remove(name)
    }

    override fun clearUsers() {
        storage.clear()
    }
}

class DBUsersStorage : UserStorage {
    val connection = Database.connect("jdbc:h2:file:C:\\Users\\MSI GL75\\IdeaProjects\\talk-chat-database-scream-team\\test", driver = "org.h2.Driver")
    var isConnected = false

    fun createDB() {
        if (!isConnected)
            transaction(connection) {
                addLogger(StdOutSqlLogger)
                SchemaUtils.create(userBase)
                isConnected = true
            }
    }

    override fun containsUser(userName: String): Boolean {
        var flag = false
        createDB()
        transaction(connection) {
            for (users in userBase.selectAll())
                if (users[userBase.name].contains(userName))
                    flag = true
        }
        return flag
    }

    override fun setOrUpdateUser(userName: String, address: UserAddress) {
        createDB()
        if (containsUser(userName))
            transaction(connection) {
                userBase.update({ userBase.name eq userName }) {
                    it[protocol] = address.protocol.scheme
                    it[host] = address.host
                    it[port] = address.port
                }
            }
        else
            transaction(connection) {
                userBase.insert {
                    it[name] = userName
                    it[protocol] = address.protocol.scheme
                    it[host] = address.host
                    it[port] = address.port
                }
            }
    }

    override fun removeUser(userName: String) {
        createDB()
        transaction(connection) {
            userBase.deleteWhere { userBase.name eq userName }
        }
    }

    override fun clearUsers() {
        transaction {
            SchemaUtils.drop(userBase)
        }
    }
}

object Registry {
    val users : UserStorage = DBUsersStorage()
}

@Suppress("UNUSED_PARAMETER")
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "invalid argument")
        }
        exception<UserAlreadyRegisteredException> { cause ->
            call.respond(HttpStatusCode.Conflict, cause.message ?: "user already registered")
        }
        exception<IllegalUserNameException> { cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "illegal user name")
        }
    }

    routing {
        get("/v1/health") {
            call.respondText("OK", contentType = ContentType.Text.Plain)
        }

        post("/v1/users") {
            val user = call.receive<UserInfo>()
            //val userAddresses = Registry.users[user.name]
            if (Registry.users.containsUser(user.name)) {
                throw UserAlreadyRegisteredException()
            }
            checkUserName(user.name) ?: throw IllegalUserNameException()
            /*if (userAddresses != null) {
                throw UserAlreadyRegisteredException()
            }*/
            Registry.users.setOrUpdateUser(user.name, user.address)
            call.respond(mapOf("status" to "ok"))
        }

        get("/v1/users") {
            call.respond(Registry.users)
        }

        put("/v1/users/{name}") {
            val userName = call.parameters["name"] ?: throw IllegalArgumentException("User name not provided")
            checkUserName(userName) ?: throw IllegalUserNameException()
            Registry.users[userName] = call.receive()
            call.respond(mapOf("status" to "ok"))
        }

        delete("/v1/users/{name}") {
            val userName = call.parameters["name"] ?: throw IllegalArgumentException("User name not provided")
            Registry.users.removeUser(userName)
            call.respond(mapOf("status" to "ok"))
        }
    }
}

class UserAlreadyRegisteredException: RuntimeException("User already registered")
class IllegalUserNameException: RuntimeException("Illegal user name")