package eth.likespro

import eth.likespro.commons.encoding.ObjectEncoding.Companion.encodeObject
import org.json.JSONObject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

class FunctionCallHandler(val processor: (JSONObject, Type) -> Any?) : InvocationHandler {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        return processor(JSONObject().apply {
            put("functionName", method.name)
            put("functionArgs", JSONObject().apply {
                args?.forEachIndexed { index, arg ->
                    put("${index + 1}", arg.encodeObject())
                }
            })
        }, method.kotlinFunction!!.returnType.javaType)
    }
}