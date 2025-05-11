package eth.likespro.lpfcp

import eth.likespro.commons.encoding.ObjectEncoding.Companion.decodeObject
import eth.likespro.commons.encoding.ObjectEncoding.Companion.encodeObject
import eth.likespro.commons.models.EncodableResult
import eth.likespro.commons.network.NetworkUtils.Companion.post
import eth.likespro.commons.reflection.ReflectionUtils.Companion.boxed
import eth.likespro.commons.reflection.ReflectionUtils.Companion.getParametrizedType
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.lang.reflect.Type
import java.net.URI
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.javaType

object LPFCP {
    /**
     * Annotation to mark functions as exposed for processing requests.
     * Only functions annotated with `@ExposedFunction` can be invoked by `processRequest`.
     */
    @Target(AnnotationTarget.FUNCTION)
    annotation class ExposedFunction

    /**
     * Exception thrown when the `functionName` key is missing or invalid in the request.
     * @param msg The error message describing the issue.
     */
    class IncorrectFunctionNameException(msg: String) : RuntimeException(msg)
    
    /**
     * Exception thrown when the `functionArgs` key is missing or invalid in the request.
     * @param msg The error message describing the issue.
     */
    class IncorrectFunctionArgsException(msg: String) : RuntimeException(msg)

    /**
     * Exception thrown when no function with the specified name and parameters is found.
     * @param msg The error message describing the issue.
     */
    class NoMatchingFunctionFound(msg: String) : RuntimeException(msg)

    /**
     * Exception thrown when the invoked function throws an exception during execution.
     * @param e The original exception thrown by the function.
     */
    class ExecutedFunctionThrowException(e: Throwable) : RuntimeException(e)



    /*
     * --------------------------------------------------------------
     *                         PROCESSOR SIDE
     * --------------------------------------------------------------
     */



    /**
     * Processes a request to invoke a function on the given processor object safely.
     *
     * @param request A `JSONObject` containing the function name and arguments.
     *                - `functionName`: The name of the function to invoke (String).
     *                - `functionArgs`: The arguments for the function (JSONObject).
     * @param processor The object containing the functions to be invoked.
     * @return An `EncodableResult` containing the result of the function call or an error.
     */
    fun processRequest(request: JSONObject, processor: Any): EncodableResult<Any?> {
        return try {
            EncodableResult.success(processRequestUnsafely(request, processor))
        } catch (e: Exception) {
            EncodableResult.failure(e)
        }
    }

    /**
     * Processes a request to invoke a function on the given processor object.
     *
     * @param request A `JSONObject` containing the function name and arguments.
     *                - `functionName`: The name of the function to invoke (String).
     *                - `functionArgs`: The arguments for the function (JSONObject).
     * @param processor The object containing the functions to be invoked.
     * @return A function's return value.
     *
     * @throws IncorrectFunctionNameException If the `functionName` key is missing or invalid.
     * @throws IncorrectFunctionArgsException If the `functionArgs` key is missing or invalid.
     * @throws NoMatchingFunctionFound If no matching function is found.
     * @throws ExecutedFunctionThrowException If the invoked function throws an exception.
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun processRequestUnsafely(request: JSONObject, processor: Any): Any? {
        val kClass = processor::class

        // Extract the function name from the request
        val functionName = try { request["functionName"] as String } catch (e: Exception) { throw IncorrectFunctionNameException("`functionName` key not found or is not String.") }

        // Extract the function arguments from the request
        val functionArgs = try { request["functionArgs"] as JSONObject } catch (e: Exception) { throw IncorrectFunctionArgsException("`functionArgs` key not found or is not JSON Object.") }

        // Filter functions in the processor class that match the name and are annotated with @ExposedFunction
        val functions = kClass.functions.filter { it.name == functionName && it.findAnnotation<ExposedFunction>() != null }
        functions.forEach { function ->
            val args = mutableMapOf<KParameter, Any?>()

            // Map the provided arguments to the function's parameters
            functionArgs.toMap().forEach { (argName, argValue) ->
                function.parameters.find { it.name == argName || it.index.toString() == argName }?.let { param ->
                    args[param] = try {
                        (argValue as String).decodeObject(param.type.javaType)
                    } catch (e: Exception) { argValue }
                }
            }
            
            // Skip the function if not all passed arguments are used
            if(args.size != functionArgs.length()) {
                return@forEach
            }
            
            // Add the instance parameter if required
            function.instanceParameter!!.let { args[it] = processor }
            
            try {
                // Invoke the function with the mapped arguments
                val result = function.callBy(args)
                return result
            } catch (e: InvocationTargetException) {
                // Handle exceptions thrown by the invoked function
                throw ExecutedFunctionThrowException(e.cause!!)
            } catch (e: IllegalArgumentException) {
                // Handle argument mismatch errors
                // Skip this function and continue searching for another one
            }
        }

        // Throw an exception if no matching function is found
        throw NoMatchingFunctionFound("Function with specified params not found. Ensure the function has @ExposedFunction annotation.")
    }



    /*
     * --------------------------------------------------------------
     *                          INVOKER SIDE
     * --------------------------------------------------------------
     */



    /**
     * Creates a proxy instance of the specified interface that forwards method calls to the provided processor LPFCP URI.
     *
     * @param processorLPFCPURI A string representing the processor LPFCP endpoint URI.
     * @return A proxy instance of the specified interface.
     */
    inline fun <reified Interface> getProcessor(processorLPFCPURI: String) = getProcessor<Interface> (URI(processorLPFCPURI))

    /**
     * Creates a proxy instance of the specified interface that forwards method calls to the provided processor LPFCP URI.
     *
     * @param processorLPFCP A URI representing the processor LPFCP endpoint.
     * @return A proxy instance of the specified interface.
     */
    inline fun <reified Interface> getProcessor(processorLPFCP: URI) = getProcessor<Interface> { request, type ->
        (processorLPFCP.post(request.toString()).decodeObject(EncodableResult::class.java.getParametrizedType(type.boxed())) as EncodableResult<*>).getOrThrow()
    }

    /**
     * Creates a proxy instance of the specified interface that forwards method calls to the provided processor function.
     *
     * @param processorLPFCP A lambda function that takes a `JSONObject` and a `Type` and returns an `Any?`.
     * @return A proxy instance of the specified interface.
     */
    inline fun <reified Interface> getProcessor(noinline processorLPFCP: (JSONObject, Type) -> Any?): Interface {
        return Proxy.newProxyInstance(
            Interface::class.java.classLoader,
            arrayOf(Interface::class.java),
            FunctionCallHandler(processorLPFCP)
        ) as Interface
    }



    /*
     * --------------------------------------------------------------
     *                        KTOR SERVER SIDE
     * --------------------------------------------------------------
     */



    object Ktor {
        /**
         * Creates an embedded Ktor server to handle LPFCP requests.
         *
         * @param processor The object containing the functions to be invoked with [ExposedFunction] annotation.
         * @param port The port on which the server will listen to (default is `8080`).
         */
        fun lpfcpServer(processor: Any, port: Int = 8080) = embeddedServer(Netty, port) {
            routing {
                lpfcp(processor)
            }
        }

        /**
         * Ktor route to handle LPFCP requests.
         *
         * @param processor The object containing the functions to be invoked with [ExposedFunction] annotation.
         * @param path The path for the LPFCP endpoint (default is "/lpfcp").
         */
        fun Route.lpfcp(processor: Any, path: String = "/lpfcp") {
            post(path) {
                val request = JSONObject(call.receiveText())
                call.respond(processRequest(request, processor).encodeObject())
            }
        }
    }

//    fun generateOpenAPISchema(forProcessor: Any, LPFCPPath: String = "/lpfcp"): JSONObject {
//        val kClass = forProcessor::class
//        val functions = kClass.functions.filter { it.findAnnotation<ExposedFunction>() != null }
//        val openAPISchema = JSONObject()
//        openAPISchema.put("openapi", "3.0.0")
//        openAPISchema.put("info", JSONObject()
//            .put("title", "LPFCP API")
//            .put("version", "1.0.0"))
//        openAPISchema.put("paths", JSONObject()
//            .put(LPFCPPath, JSONObject().apply {
//                functions.forEach { function ->
//                    println("Processing ${function.name} with ${function.parameters.joinToString(", ") { (it.name ?: it.index.toString()) + ":${it.type.toString()}" }}")
//                    put("post", JSONObject()
//                        .put("requestBody", JSONObject()
//                            .put("required", true)
//                            .put("content", JSONObject()
//                                .put("application/json", JSONObject()
//                                    .put("schema", JSONObject()
//                                        .put("type", "object")
//                                        .put("properties", JSONObject()
//                                            .put("functionName", JSONObject().put("type", "string"))
//                                            .put("functionArgs", generateOpenAPISchemaForParameters(function.parameters.drop(1))) // drop the first parameter (instance parameter)
//                                        )
//                                    )
//                                )
//                            )
//                        )
//                    )
//                }
//            })
//        )
//        return openAPISchema
//    }
//
//    /**
//     * Generates an OpenAPI schema for the given function parameters.
//     *
//     * @param parameters The list of function parameters.
//     * @return A `JSONObject` representing the OpenAPI schema for the parameters.
//     */
//    private fun generateOpenAPISchemaForParameters(parameters: List<KParameter>): JSONObject {
//        return JSONObject()
//            .put("type", "object")
//            .put("properties", JSONObject().apply {
//                parameters.forEach { param ->
//                    put(param.name ?: param.index.toString(), generateOpenAPISchemaForClass(param.type.classifier as KClass<*>))
//                }
//            })
//    }
//
//    /**
//     * Generates an OpenAPI schema for the given class.
//     *
//     * @param clazz The class to generate the schema for.
//     * @return A `JSONObject` representing the OpenAPI schema for the class.
//     */
//    private fun generateOpenAPISchemaForClass(clazz: KClass<*>): JSONObject {
//        val type = if(clazz.isSubclassOf(Number::class)) "number"
//        else if(clazz == String::class) "string"
//        else if(clazz == Boolean::class) "boolean"
//        else if(clazz.java.isArray) "array"
//        else "object"
//        return JSONObject()
//            .put("type", type).apply {
//                if (type == "array") {
//                    put("items", JSONObject().put("type", "string"))
//                } else if (type == "object") {
//                    put("properties", JSONObject().apply {
//                        clazz.members.forEach { member ->
//                            println(member.name + " " +  member.returnType + " " + (member is KProperty<*>))
//                            if (member is KProperty<*>) {
//                                put(member.name, generateOpenAPISchemaForClass(member.returnType.classifier as KClass<*>))
//                            }
//                        }
//                    })
//                }
//            }
//    }
}