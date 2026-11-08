package nl.enadeag.carchargeplanner.android

import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import org.slf4j.LoggerFactory

/** An HttpGet that breaks `AppArchitectureTest`'s rule on request addresses on purpose. */
internal class GetThatLogsItsAddress(private val inner: HttpGet) : HttpGet {

    private val log = LoggerFactory.getLogger(GetThatLogsItsAddress::class.java)

    override fun get(request: HttpGet.Request): HttpGet.Reply {
        log.info("asking {}", request.uri())
        return inner.get(request)
    }
}
