import eth.likespro.LPFCP
import eth.likespro.commons.models.EncodableResult
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LPFCPTests {

    @Test
    fun processRequest_withValidFunctionAndArgs_returnsSuccess() {
        val processor = object {
            @LPFCP.ExposedFunction
            fun add(a: Int, b: Int): Int = a + b
        }
        val request = JSONObject("""{"functionName": "add", "functionArgs": {"a": "3", "b": "5"}}""")
        val result = LPFCP.processRequest(request, processor)
        assertEquals(EncodableResult.success(8), result as EncodableResult<*>)
    }

    @Test
    fun processRequest_withValidOverloadedFunctionAndArgs_returnsSuccess() {
        val processor = object {
            @LPFCP.ExposedFunction
            fun add(a: Int, b: Int): Int = a + b

            @LPFCP.ExposedFunction
            fun add(a: String, b: String): String = a + b
        }
        val request = JSONObject("""{"functionName": "add", "functionArgs": {"a": "\"3\"", "b": "\"5\""}}""")
        val result = LPFCP.processRequest(request, processor)
        assertEquals(EncodableResult.success("35"), result as EncodableResult<*>)
    }

    @Test
    fun processRequest_withValidOverloadedFunctionWithMoreArgsAndArgs_returnsSuccess() {
        val processor = object {
            @LPFCP.ExposedFunction
            fun add(a: Int, b: Int): Int = a + b

            @LPFCP.ExposedFunction
            fun add(a: Int, b: Int, c: Int): Int = a + b + c
        }
        val request = JSONObject("""{"functionName": "add", "functionArgs": {"a": "3", "b": "5", "c": "7"}}""")
        val result = LPFCP.processRequest(request, processor)
        assertEquals(EncodableResult.success(15), result as EncodableResult<*>)
    }

    @Test
    fun processRequest_withMissingFunctionName_throwsIncorrectFunctionNameException() {
        val processor = object {}
        val request = JSONObject("""{"functionArgs": {"a": "3", "b": "5"}}""")
        val result = LPFCP.processRequest(request, processor)
        assertEquals(result.failure?.exceptionClass, LPFCP.IncorrectFunctionNameException::class.java)
    }

    @Test
    fun processRequest_withInvalidFunctionName_throwsNoMatchingFunctionFound() {
        val processor = object {
            @LPFCP.ExposedFunction
            fun multiply(a: Int, b: Int): Int = a * b
        }
        val request = JSONObject("""{"functionName": "divide", "functionArgs": {"a": "3", "b": "5"}}""")
        val result = LPFCP.processRequest(request, processor)
        assertEquals(result.failure?.exceptionClass, LPFCP.NoMatchingFunctionFound::class.java)
    }

    @Test
    fun processRequest_withMissingFunctionArgs_throwsIncorrectFunctionArgsException() {
        val processor = object {
            @LPFCP.ExposedFunction
            fun subtract(a: Int, b: Int): Int = a - b
        }
        val request = JSONObject("""{"functionName": "subtract"}""")
        val result = LPFCP.processRequest(request, processor)
        assertEquals(result.failure?.exceptionClass, LPFCP.IncorrectFunctionArgsException::class.java)
    }

    @Test
    fun processRequest_withExtraArgs_skipsFunctionAndThrowsNoMatchingFunctionFound() {
        val processor = object {
            @LPFCP.ExposedFunction
            fun divide(a: Int, b: Int): Int = a / b
        }
        val request = JSONObject("""{"functionName": "divide", "functionArgs": {"a": "10", "b": "2", "c": "1"}}""")
        val result = LPFCP.processRequest(request, processor)
        assertEquals(result.failure?.exceptionClass, LPFCP.NoMatchingFunctionFound::class.java)
    }

    @Test
    fun processRequest_withFunctionThrowingException_returnsExecutedFunctionThrowException() {
        val processor = object {
            @LPFCP.ExposedFunction
            fun throwError(): Nothing = throw IllegalStateException("Error occurred")
        }
        val request = JSONObject("""{"functionName": "throwError", "functionArgs": {}}""")
        val result = LPFCP.processRequest(request, processor)
        assertEquals(result.failure?.exceptionClass, LPFCP.ExecutedFunctionThrowException::class.java)
    }
}