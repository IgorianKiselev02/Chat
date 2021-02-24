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
import ru.senin.kotlin.net.UserAddress
import ru.senin.kotlin.net.UserInfo
import ru.senin.kotlin.net.checkUserName
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    /** TODO("Remake temporary connection and change URL") */

    thread {
        // TODO: periodically check users and remove unreachable ones
    }
    EngineMain.main(args)
}

object Registry {
    val users = ConcurrentHashMap<String, UserAddress>()
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
    val connection = Database.connect("jdbc:h2:file:C:\\Users\\MSI GL75\\IdeaProjects\\talk-chat-database-scream-team\\test", driver = "org.h2.Driver")
    transaction(connection) {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(userBase)
    }
    routing {
        get("/v1/health") {
            call.respondText("OK", contentType = ContentType.Text.Plain)
        }

        post("/v1/users") {
            val user = call.receive<UserInfo>()
            val userAddresses = Registry.users[user.name]
            if (Registry.users.contains(user.name)) {
                throw UserAlreadyRegisteredException()
            }
            checkUserName(user.name) ?: throw IllegalUserNameException()
            if (userAddresses != null) {
                throw UserAlreadyRegisteredException()
            }
            Registry.users[user.name] = user.address
            transaction(connection) {
                //SchemaUtils.create(userBase)
                userBase.insert {
                    it[name] = user.name
                    it[protocol] = user.address.protocol.scheme
                    it[host] = user.address.host
                    it[port] = user.address.port
                }
            }
            call.respond(mapOf("status" to "ok"))
        }

        get("/v1/users") {
            call.respond(Registry.users)
        }

        put("/v1/users/{name}") {
            val userName = call.parameters["name"] ?: throw IllegalArgumentException("User name not provided")
            checkUserName(userName) ?: throw IllegalUserNameException()
            Registry.users[userName] = call.receive()
            transaction(connection) {
                //SchemaUtils.create(userBase)
                userBase.update( { userBase.name eq userName } ) {
                    it[protocol] = Registry.users[userName]!!.protocol.scheme
                    it[host] = Registry.users[userName]!!.host
                    it[port] = Registry.users[userName]!!.port
                }
            }
            call.respond(mapOf("status" to "ok"))
        }

        delete("/v1/users/{name}") {
            val userName = call.parameters["name"] ?: throw IllegalArgumentException("User name not provided")
            Registry.users.remove(userName)
            transaction(connection) {
                //SchemaUtils.create(userBase)
                userBase.deleteWhere { userBase.name eq userName }
            }
            call.respond(mapOf("status" to "ok"))
        }
    }
}

class UserAlreadyRegisteredException: RuntimeException("User already registered")
class IllegalUserNameException: RuntimeException("Illegal user name")