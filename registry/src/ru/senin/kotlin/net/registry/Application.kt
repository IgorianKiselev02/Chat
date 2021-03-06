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
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    thread {
        // TODO: periodically check users and remove unreachable ones
    }
    EngineMain.main(args)
}

interface UserStorage {
    fun containsUser(userName: String) : Boolean
    fun setOrUpdateUser(userName: String, address: UserAddress)
    fun removeUser(userName: String)
    fun clearUsers()
    fun get() : ConcurrentHashMap<String, UserAddress>
    operator fun set(userName: String, value: Any) {

    }
}

class MemoryUsersStorage : UserStorage {
    val storage = ConcurrentHashMap<String, UserAddress>()

    override fun containsUser(userName: String): Boolean {
        return storage.contains(userName)
    }

    override fun setOrUpdateUser(userName: String, address: UserAddress) {
        storage[userName] = address
    }

    override fun removeUser(userName: String) {
        storage.remove(userName)
    }

    override fun clearUsers() {
        storage.clear()
    }

    override fun get(): ConcurrentHashMap<String, UserAddress> {
        return storage
    }
}

class DBUsersStorage : UserStorage {
    override fun containsUser(userName: String): Boolean {
        var flag = false
        transaction(Registry.connection) {
            for (users in userBase.selectAll())
                if (users[userBase.name].contains(userName))
                    flag = true
        }
        return flag
    }

    override fun setOrUpdateUser(userName: String, address: UserAddress) {
        if (containsUser(userName))
            transaction(Registry.connection) {
                userBase.update({ userBase.name eq userName }) {
                    it[protocol] = address.protocol.scheme
                    it[host] = address.host
                    it[port] = address.port
                }
            }
        else
            transaction(Registry.connection) {
                userBase.insert {
                    it[name] = userName
                    it[protocol] = address.protocol.scheme
                    it[host] = address.host
                    it[port] = address.port
                }
            }
    }

    override fun removeUser(userName: String) {
        transaction(Registry.connection) {
            userBase.deleteWhere { userBase.name eq userName }
        }
    }

    override fun clearUsers() {
        transaction(Registry.connection) {
            SchemaUtils.drop(userBase)
        }
    }

    override fun get(): ConcurrentHashMap<String, UserAddress> {
        val storage = ConcurrentHashMap<String, UserAddress>()
        transaction(Registry.connection) {
            val users = userBase.selectAll()
            users.forEach {
                storage[it[userBase.name]] = UserAddress(when (it[userBase.protocol]) {
                    "http" -> Protocol.HTTP
                    "udp" -> Protocol.UDP
                    else -> Protocol.WEBSOCKET
                }, it[userBase.host], it[userBase.port])
            }
        }
        return storage
    }
}

object Registry {
    lateinit var users : UserStorage
    lateinit var connection: Database
}

@Suppress("UNUSED_PARAMETER")
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    val type = environment.config.propertyOrNull("ktor.config.Type")?.getString() ?: ""

    Registry.users = if (type == "db") {
        Registry.connection = Database.connect(environment.config.propertyOrNull("ktor.config.dbUrl")?.getString() ?: "", driver = "org.h2.Driver")
        DBUsersStorage()
    }
    else
         MemoryUsersStorage()

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

    transaction(Registry.connection) {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(userBase)
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
            call.respond(Registry.users.get())
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