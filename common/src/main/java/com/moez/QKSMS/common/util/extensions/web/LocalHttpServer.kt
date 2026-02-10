package moez.QKSMS.common.web

import fi.iki.elonen.NanoHTTPD
import timber.log.Timber

class LocalHttpServer(
    port: Int = 8080
) : NanoHTTPD(port) {

    override fun start(timeout: Int, daemon: Boolean) {
        super.start(timeout, daemon)
        Timber.i("LocalHttpServer started on port $port")
    }

    override fun stop() {
        super.stop()
        Timber.i("LocalHttpServer stopped")
    }

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/health" -> ok("""{"status":"ok"}""")
            else -> notFound()
        }
    }

    private fun ok(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", body)

    private fun notFound(): Response =
        newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            "application/json",
            """{"error":"not_found"}"""
        )
}
