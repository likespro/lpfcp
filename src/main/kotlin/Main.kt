package eth.likespro

import eth.likespro.commons.encoding.ObjectEncoding.Companion.encodeObject
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONObject

class Wr(val a: Int)
interface Calculator {
    class AddParams(val p1: Int, val p2: Int, val sm: Something) {
        class Something(val str: String){
            val floa: Float = 1.0f
        }
    }

    fun add(a: Int, b: Int): Int
    fun add(a: String = "1", b: String = "2"): String
    fun sum(vararg args: Int): Int
    fun some(something: AddParams.Something): String
}
fun main() {
    println("Hello World!")
    val calculator = object : Calculator {
        @LPFCP.ExposedFunction
        fun add(addParams: Calculator.AddParams): Int {
            return addParams.p1 + addParams.p2
        }

        @LPFCP.ExposedFunction
        override fun add(a: Int, b: Int): Int {
            TODO("Not yet implemented")
        }

        @LPFCP.ExposedFunction
        override fun add(a: String, b: String): String {
            return a + b
        }

        @LPFCP.ExposedFunction
        override fun sum(vararg args: Int): Int {
            return args.sum()
        }
        @LPFCP.ExposedFunction
        override fun some(something: Calculator.AddParams.Something): String {
            return something.str + something.floa
        }
    }

//    val remoteProcessor = LPFCP.getProcessor<Calculator> { request, type ->
//        println("Request: ${request.toString(2)}")
//        (LPFCP.processRequest(request, calculator).encodeObject().decodeObject(EncodableResult::class.java.getParametrizedType(type.boxed())) as EncodableResult<*>).getOrThrow()
//    }
    val remoteProcessor = LPFCP.getProcessor<Calculator>("http://localhost:8080/lpfcp")

    embeddedServer(Netty, 8080) {
        routing {
            post("/lpfcp") {
                val request = JSONObject(call.receiveText())
                call.respond(LPFCP.processRequest(request, calculator).encodeObject())
            }
        }
    }.start(wait = false)
    Thread.sleep(3000)
    println(remoteProcessor.some(Calculator.AddParams.Something("Hello")))
}