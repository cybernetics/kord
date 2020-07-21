package gateway

import com.gitlab.kordlib.gateway.*
import io.ktor.http.cio.websocket.CloseReason
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultGatewayTest {

    private inline fun <reified T : Throwable> assertThrows(
            noinline block: suspend () -> Unit
    ) = org.junit.jupiter.api.assertThrows<T> {
        runBlocking {
            block()
        }
    }

    lateinit var mock: MockConnectionSupplier
    lateinit var gateway: DefaultGateway

    private suspend fun CoroutineScope.startGateway(): Job {
        //the gateway starts relatively quickly (< 1ms), but the launch method can take up to a 100ms or so
        //to combat this, we'll have a lock wait until the launch has... launched.
        val lock = Mutex(locked = true)

        val gatewayRunning = launch {
            lock.unlock()
            gateway.start("token")
        }

        lock.lock()
        return gatewayRunning
    }

    @BeforeEach
    fun setUp() {
        mock = MockConnectionSupplier()
        gateway = DefaultGateway {
            connectionProvider = mock
            url = "wss://gateway.discord.gg/?v=6&encoding=json"
        }
    }

    @Test
    fun `gateway cannot send data before opening`(): Unit = runBlocking {
        assertThrows<IllegalStateException> {
            gateway.send(Command.Heartbeat(5))
        }
        Unit
    }

    @Test
    fun `gateway cannot send data after detaching`(): Unit = runBlocking {
        gateway.detach()
        assertThrows<IllegalStateException> {
            gateway.send(Command.Heartbeat(5))
        }
        Unit
    }

    @Test
    fun `stopped gateway is allowed to stop again`(): Unit = runBlocking {
        val gatewayRunning = startGateway()
        gateway.stop()

        withTimeout(1500) { //1500 is way more than we need, but we'll play it safe
            gatewayRunning.join()
        }

        gateway.stop()
        Unit
    }

    @Test
    fun `stopped gateway is allowed to start again`(): Unit = runBlocking {
        run {
            val gatewayRunning = startGateway()
            gateway.stop()

            withTimeout(1500) { //1500 is way more than we need, but we'll play it safe
                gatewayRunning.join()
            }
        }

        run {
            val gatewayRunning = startGateway()
            gateway.stop()

            withTimeout(1500) { //1500 is way more than we need, but we'll play it safe
                gatewayRunning.join()
            }
        }
    }

    @Test
    fun `stopped gateway is allowed to detach`(): Unit = runBlocking {
        val gatewayRunning = startGateway()
        gateway.stop()

        withTimeout(1500) { //1500 is way more than we need, but we'll play it safe
            gatewayRunning.join()
        }

        gateway.detach()
        Unit
    }

    @Test
    fun `running gateway is allowed to detach`(): Unit = runBlocking {
        val gatewayRunning = startGateway()
        gateway.detach()

        withTimeout(1500) { //1500 is way more than we need, but we'll play it safe
            gatewayRunning.join()
        }

        Unit
    }

    @Test
    fun `running gateway is allowed to stop`(): Unit = runBlocking {
        val gatewayRunning = startGateway()
        gateway.stop()

        withTimeout(1500) { //1500 is way more than we need, but we'll play it safe
            gatewayRunning.join()
        }

        Unit
    }


    @Test
    fun `gateway will close on stop`(): Unit = runBlocking {
        val gatewayRunning = startGateway()
        gateway.stop()

        withTimeout(1500) { //1500 is way more than we need, but we'll play it safe
            gatewayRunning.join()
        }
        Unit
    }

    @Test
    fun `gateway will reconnect on reconnectable codes`(): Unit = runBlocking {
        val gatewayRunning = startGateway()

        mock.connection.close(CloseReason(4000, "this is a test"))

        assert(gatewayRunning.isActive)

        gateway.detach()
        Unit
    }

    @Test
    fun `gateway will not reconnect on non-reconnectable codes`(): Unit = runBlocking {
        val gatewayRunning = startGateway()
        mock.connection.close(CloseReason(4004, "this is a test"))

        withTimeout(1500) { //1500 is way more than we need, but we'll play it safe
            gatewayRunning.join() //this job should have stopped by now. If it hasn't, the gateway didn't attempt to stop.
        }
        Unit
    }

    @Test
    fun `gateway cannot be reopened after detaching`(): Unit = runBlocking {
        gateway.detach()

        assertThrows<IllegalStateException> {
            gateway.start("token")
        }

        Unit
    }

    @Test
    fun `gateway identifies on hello`(): Unit = runBlocking {
        startGateway()

        mock.connection.sendEvent(OpCode.Hello, Hello.serializer(), Hello(1000, emptyList()))
        val command = mock.connection.outgoing.receiveAsFlow().take(1).single()

        gateway.stop()

        Assertions.assertTrue(command is Identify)
        Unit
    }

    @Test
    fun `gateway heartbeats on heartbeat`(): Unit = runBlocking {
        startGateway()

        mock.connection.sendEvent(OpCode.Heartbeat, Long.serializer(), 9000)
        val command = mock.connection.outgoing.receiveAsFlow().take(1).single()

        gateway.stop()

        Assertions.assertTrue(command is Command.Heartbeat)
        Unit
    }

    @Test
    fun `gateway sends heartbeat at interval`(): Unit = runBlocking {
        startGateway()

        mock.connection.sendEvent(OpCode.Hello, Hello.serializer(), Hello(1, emptyList()))
        val command = mock.connection.outgoing.receiveAsFlow().drop(1).take(1).single()

        gateway.stop()

        Assertions.assertTrue(command is Command.Heartbeat)
        Unit
    }

}