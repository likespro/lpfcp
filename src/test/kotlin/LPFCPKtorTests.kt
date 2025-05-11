import LPFCPTests.Companion.calculator
import eth.likespro.commons.wrapping.WrappedException
import eth.likespro.lpfcp.LPFCP
import eth.likespro.lpfcp.LPFCP.Ktor.lpfcpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LPFCPKtorTests {



    /*
     * --------------------------------------------------------------
     *                        KTOR SERVER SIDE
     * --------------------------------------------------------------
     */



    companion object {
        @JvmStatic
        @BeforeAll fun setupKtorServer() {
            lpfcpServer(calculator).start(wait = false)
        }

        @JvmStatic
        @AfterAll fun teardownKtorServer() {
            lpfcpServer(calculator).stop(0, 0)
        }
    }

    @Test fun ktorServer_withValidFunctionWithNoArgsAndNoArgs_returnsSuccess() {
        val processor = LPFCP.getProcessor<LPFCPTests.Calculator>(URI("http://localhost:8080/lpfcp"))
        val response = processor.hello()
        assertEquals("Hello, World!", response)
    }

    @Test fun ktorServer_withValidFunctionAndArgs_returnsSuccess() {
        val processor = LPFCP.getProcessor<LPFCPTests.Calculator>("http://localhost:8080/lpfcp")
        val response = processor.add(3, 5)
        assertEquals(8, response)
    }

    @Test fun getProcessor_withLambda_proceedsRequest_withValidFunctionReturningNullAndArgs_returnsSuccess() {
        val processor = LPFCP.getProcessor<LPFCPTests.Calculator>("http://localhost:8080/lpfcp")
        val response = processor.divideSafely(3, 0)
        assertEquals(null, response)
    }

    @Test fun getProcessor_withLambda_proceedsRequest_withValidOverloadedFunctionAndArgs_returnsSuccess() {
        val processor = LPFCP.getProcessor<LPFCPTests.Calculator>("http://localhost:8080/lpfcp")
        val response = processor.add("3", "5")
        assertEquals("35", response)
    }

    @Test fun getProcessor_withLambda_proceedsRequest_withValidFunctionWithDefaultArgsAndArgs_returnsSuccess() {
        val processor = LPFCP.getProcessor<LPFCPTests.Calculator>("http://localhost:8080/lpfcp")
        val response = processor.add(b = "5")
        assertEquals("15", response)
    }

    @Test fun getProcessor_withLambda_proceedsRequest_withValidFunctionWithDefaultArgsAndArgsInReverseOrder_returnsSuccess() {
        val processor = LPFCP.getProcessor<LPFCPTests.Calculator>("http://localhost:8080/lpfcp")
        val response = processor.add(b = "5", a = "4")
        assertEquals("45", response)
    }

    @Test fun getProcessor_withLambda_proceedsRequest_withFunctionThrowingException_returnsExecutedFunctionThrowException() {
        val processor = LPFCP.getProcessor<LPFCPTests.Calculator>("http://localhost:8080/lpfcp")
        assertEquals(
            LPFCP.ExecutedFunctionThrowException::class.java,
            assertFailsWith<WrappedException.Exception> { processor.divide(5, 0) }.wrappedException.exceptionClass
        )
    }
}