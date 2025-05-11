import eth.likespro.lpfcp.LPFCP
import eth.likespro.commons.models.EncodableResult
import eth.likespro.commons.wrapping.WrappedException
import eth.likespro.lpfcp.LPFCP.Ktor.lpfcpServer
import org.json.JSONObject
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LPFCPTests {



    /*
     * --------------------------------------------------------------
     *                         PROCESSOR SIDE
     * --------------------------------------------------------------
     */



    @Test
    fun processRequest_withValidFunctionWithNoArgsAndNoArgs_returnsSuccess() {
        val processor = object {
            @LPFCP.ExposedFunction
            fun hello(): String = "Hello, World!"
        }
        val request = JSONObject("""{"functionName": "hello", "functionArgs": {}}""")
        val result = LPFCP.processRequest(request, processor)
        assertEquals(EncodableResult.success("Hello, World!"), result as EncodableResult<*>)
    }

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
    fun processRequest_withValidFunctionReturningNullAndArgs_returnsSuccess() {
        val processor = object {
            @LPFCP.ExposedFunction
            fun divideSafely(a: Int, b: Int): Int? = if (b == 0) null else a / b
        }
        val request = JSONObject("""{"functionName": "divideSafely", "functionArgs": {"a": "3", "b": "0"}}""")
        val result = LPFCP.processRequest(request, processor)
        assertEquals(EncodableResult.success(null), result as EncodableResult<*>)
    }

    @Test
    fun processRequest_withValidFunctionWithDefaultArgsAndArgs_returnsSuccess() {
        val processor = object {
            @LPFCP.ExposedFunction
            fun add(a: Int = 1, b: Int = 2): Int = a + b
        }
        val request = JSONObject("""{"functionName": "add", "functionArgs": {"b": "10"}}""")
        val result = LPFCP.processRequest(request, processor)
        assertEquals(EncodableResult.success(11), result as EncodableResult<*>)
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

    class StaticProcessor {
        companion object {
            @LPFCP.ExposedFunction
            @JvmStatic
            fun add(a: Int, b: Int): Int = a + b
        }
    }
    @Test
    fun processRequest_withValidStaticFunctionAndArgs_returnsSuccess() {
        val request = JSONObject("""{"functionName": "add", "functionArgs": {"a": "3", "b": "5"}}""")
        val result = LPFCP.processRequest(request, StaticProcessor)
        assertEquals(EncodableResult.success(8), result as EncodableResult<*>)
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
    fun processRequest_withNotAnnotatedFunction_throwsNoMatchingFunctionFound() {
        val processor = object {
            fun multiply(a: Int, b: Int): Int = a * b
        }
        val request = JSONObject("""{"functionName": "multiply", "functionArgs": {"a": "3", "b": "5"}}""")
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



    /*
     * --------------------------------------------------------------
     *                          INVOKER SIDE
     * --------------------------------------------------------------
     */



    interface Calculator {
        fun hello(): String
        fun add(a: Int, b: Int): Int
        fun add(a: String = "1", b: String = "2"): String
        fun multiply(a: Int, b: Int): Int
        fun divide(a: Int, b: Int): Int
        fun divideSafely(a: Int, b: Int): Int?
    }

    companion object {
        val calculator = object : Calculator {
            @LPFCP.ExposedFunction
            override fun hello(): String = "Hello, World!"

            @LPFCP.ExposedFunction
            override fun add(a: Int, b: Int): Int = a + b

            @LPFCP.ExposedFunction
            override fun add(a: String, b: String): String = a + b

            override fun multiply(a: Int, b: Int) = a * b

            @LPFCP.ExposedFunction
            override fun divide(a: Int, b: Int) = a / b

            @LPFCP.ExposedFunction
            override fun divideSafely(a: Int, b: Int): Int? = if (b == 0) null else a / b
        }
    }

    @Test fun getProcessor_withLambda_returnsProxyInstance() {
        val processor = LPFCP.getProcessor<Calculator> { request, _ ->
            LPFCP.processRequest(request, calculator).getOrThrow()
        }
        assertIs<Calculator>(processor)
    }

    @Test fun getProcessor_withUriString_returnsProxyInstance() {
        val processor = LPFCP.getProcessor<Calculator>("http://localhost:8080/lpfcp")
        assertIs<Calculator>(processor)
    }

    @Test fun getProcessor_withUri_returnsProxyInstance() {
        val processor = LPFCP.getProcessor<Calculator>(URI("http://localhost:8080/lpfcp"))
        assertIs<Calculator>(processor)
    }

    @Test fun getProcessor_withLambda_proceedsRequest_withValidFunctionWithNoArgsAndNoArgs_returnsSuccess() {
        val processor = LPFCP.getProcessor<Calculator> { request, _ ->
            LPFCP.processRequest(request, calculator).getOrThrow()
        }
        val result = processor.hello()
        assertEquals("Hello, World!", result)
    }

    @Test fun getProcessor_withLambda_proceedsRequest_withValidFunctionAndArgs_returnsSuccess() {
        val processor = LPFCP.getProcessor<Calculator> { request, _ ->
            LPFCP.processRequest(request, calculator).getOrThrow()
        }
        val result = processor.add(3, 5)
        assertEquals(8, result)
    }

    @Test fun getProcessor_withLambda_proceedsRequest_withValidFunctionReturningNullAndArgs_returnsSuccess() {
        val processor = LPFCP.getProcessor<Calculator> { request, _ ->
            LPFCP.processRequest(request, calculator).getOrThrow()
        }
        val result = processor.divideSafely(3, 0)
        assertEquals(null, result)
    }

    @Test fun getProcessor_withLambda_proceedsRequest_withValidOverloadedFunctionAndArgs_returnsSuccess() {
        val processor = LPFCP.getProcessor<Calculator> { request, _ ->
            LPFCP.processRequest(request, calculator).getOrThrow()
        }
        val result = processor.add("3", "5")
        assertEquals("35", result)
    }

    @Test fun getProcessor_withLambda_proceedsRequest_withValidFunctionWithDefaultArgsAndArgs_returnsSuccess() {
        val processor = LPFCP.getProcessor<Calculator> { request, _ ->
            LPFCP.processRequest(request, calculator).getOrThrow()
        }
        val result = processor.add(b = "5")
        assertEquals("15", result)
    }

    @Test fun getProcessor_withLambda_proceedsRequest_withValidFunctionWithDefaultArgsAndArgsInReverseOrder_returnsSuccess() {
        val processor = LPFCP.getProcessor<Calculator> { request, _ ->
            LPFCP.processRequest(request, calculator).getOrThrow()
        }
        val result = processor.add(b = "5", a = "4")
        assertEquals("45", result)
    }

    @Test fun getProcessor_withLambda_proceedsRequest_withFunctionThrowingException_returnsExecutedFunctionThrowException() {
        val processor = LPFCP.getProcessor<Calculator> { request, _ ->
            LPFCP.processRequest(request, calculator).getOrThrow()
        }
        assertEquals(
            LPFCP.ExecutedFunctionThrowException::class.java,
            assertFailsWith<WrappedException.Exception> { processor.divide(5, 0) }.wrappedException.exceptionClass
        )
    }
}