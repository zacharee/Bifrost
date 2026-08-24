@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "EXPOSED_PARAMETER_TYPE")

package tk.zwander.common.tools

import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.core.toByteArray
import tk.zwander.common.util.BreadcrumbType
import tk.zwander.common.util.BugsnagUtils
import tk.zwander.common.util.globalHttpClient

/**
 * Manage communications with Samsung's server.
 */
object FusClientLegacy : IFusClient<FusClientLegacy.Request> {
    enum class Request(val value: String) : IFusClient.IRequest {
        GENERATE_NONCE("NF_DownloadGenerateNonce.do"),
        BINARY_INFORM("NF_DownloadBinaryInform.do"),
        BINARY_INIT("NF_DownloadBinaryInitForMass.do")
    }

    /**
     * Nonces from the legacy endpoints no longer decrypt with [CryptUtils.Legacy]'s
     * hardcoded key: the plaintext comes back as high-entropy binary rather than 16
     * characters followed by 0x10 padding (verified against OpenSSL, so the AES-CBC
     * implementation itself is correct).
     *
     * When true, sign legacy requests with the current auth_params scheme that
     * [FusClient] uses, while keeping the legacy endpoints and request bodies. Flip to
     * false to go back to the hardcoded-key scheme.
     */
    private const val USE_MODERN_AUTH = true

    private var encNonce = ""
    private var nonce = ""

    private var auth: String = ""

    private var jSessionId: String = ""
    private var session: String = ""

    /**
     * Echo back only the cookies the server actually set. Reconstructing this from a
     * single stored value meant sending `SESSION=<the JSESSIONID value>`, and sending
     * `;SESSION=` unconditionally was a difference from the older builds that still work.
     */
    private fun cookieHeader(): String = listOfNotNull(
        jSessionId.takeIf { it.isNotBlank() }?.let { "JSESSIONID=$it" },
        session.takeIf { it.isNotBlank() }?.let { "SESSION=$it" },
    ).joinToString(";")

    override suspend fun getNonce(): String {
        if (nonce.isBlank()) {
            generateNonce()
        }

        return nonce
    }

    override suspend fun generateNonce() {
        BugsnagUtils.addBreadcrumb(
            message = "Generating nonce.",
            data = mapOf(),
            type = BreadcrumbType.LOG,
        )
        println("Generating nonce.")
        makeReq(Request.GENERATE_NONCE)
        BugsnagUtils.addBreadcrumb(
            message = "Nonce: $nonce, Auth: $auth",
            data = mapOf(),
            type = BreadcrumbType.LOG,
        )
        println("Nonce: $nonce")
        println("Auth: $auth")
    }

    override suspend fun getAuthV(includeNonce: Boolean, signature: String?, cloud: Boolean): String {
        if (USE_MODERN_AUTH) {
            // Match FusClient's header, not just its signature. Two differences that were
            // left in place last time: it always sends the nonce for non-cloud requests
            // (its includeNonce only gates the cloud branch), and it never sends newauth.
            val headerNonce = if (cloud && !includeNonce) "" else nonce

            return "FUS nonce=\"$headerNonce\", signature=\"$auth\", nc=\"\", type=\"\", realm=\"\""
        }

        return "FUS nonce=\"${if (includeNonce) encNonce else ""}\", signature=\"${this.auth}\", nc=\"\", type=\"\", realm=\"\", newauth=\"1\""
    }

    override suspend fun getDownloadUrl(path: String): String {
        return "http://cloud-neofussvr.samsungmobile.com/NF_DownloadBinaryForMass.do?file=${path}"
    }

    /**
     * Make a request to Samsung, automatically inserting authorization data.
     * @param request the request to make.
     * @param data any body data that needs to go into the request.
     * @return the response body data, as text. Usually XML.
     */
    override suspend fun makeReq(
        request: Request,
        data: String,
        signature: String?,
        includeNonce: Boolean,
    ): String {
        if (nonce.isBlank() && request != Request.GENERATE_NONCE) {
            generateNonce()
        }

        val authV = getAuthV(includeNonce)

        val response =
            globalHttpClient.request("https://neofussvr.sslcs.cdngc.net/${request.value}") {
                method = HttpMethod.Post
                headers {
                    append("Authorization", authV)
                    append("User-Agent", "Kiss2.0_FUS")
                    append("Cookie", cookieHeader())
                    append("Set-Cookie", cookieHeader())
                    append(HttpHeaders.ContentLength, "${data.toByteArray().size}")
                }
                setBody(data)
            }

        val body = response.bodyAsText()

        println("$request -> HTTP ${response.status.value}")
        println("  sent Authorization: $authV")
        println("  sent Cookie: ${cookieHeader()}")
        response.headers.entries().forEach { (name, values) ->
            println("  <- $name: ${values.joinToString(", ")}")
        }

        if (USE_MODERN_AUTH && request == Request.GENERATE_NONCE) {
            // The auth_params blob that authenticateBlock() reads is deleted on launch and
            // only re-extracted by a nonce request, so it has to happen before the nonce
            // below is signed. FusClient does the same thing at the same point.
            AuthParamsHandler.extractFile()
        }

        // Samsung hands back a fresh nonce and session with *every* response, including
        // failures. Consume them before deciding what to do with this response, so that
        // anything retrying afterwards starts from what the server just gave us instead
        // of discarding it and burning another request on GENERATE_NONCE.
        if (response.headers["NONCE"] != null || response.headers["nonce"] != null) {
            val newEncNonce = response.headers["NONCE"] ?: response.headers["nonce"] ?: ""

            // Decrypt and sign into locals before storing anything. getAuth() throws if the
            // decrypted nonce is shorter than 16 characters, and assigning as we go would
            // leave a live nonce paired with a stale or blank auth, which then goes out as
            // signature="" on every subsequent request and 401s forever.
            try {
                val newNonce: String
                val newAuth: String

                if (USE_MODERN_AUTH) {
                    // The modern scheme treats the header as a raw 16-byte block: the
                    // signature is that block run through the auth_params transform. The
                    // same 16 characters feed LOGIC_CHECK, which legacy expects to be 16
                    // characters wide.
                    newNonce = newEncNonce.take(16)
                    newAuth = CryptUtils.decryptNonce(newNonce)
                } else {
                    newNonce = CryptUtils.Legacy.decryptNonce(newEncNonce)
                    newAuth = CryptUtils.Legacy.getAuth(newNonce)
                }

                encNonce = newEncNonce
                nonce = newNonce
                auth = newAuth
            } catch (e: Throwable) {
                BugsnagUtils.addBreadcrumb(
                    message = "Error generating nonce from '$newEncNonce' (${newEncNonce.length} chars).",
                    data = mapOf("error" to e),
                    type = BreadcrumbType.ERROR,
                )
                println("Error generating nonce from '$newEncNonce' (${newEncNonce.length} chars): $e")
            }
        }

        // Match on the cookie *name*, keep the two cookies distinct, and ignore anything
        // the server didn't set. getAll() is case-insensitive, so it covers set-cookie too.
        response.headers.getAll("Set-Cookie").orEmpty().forEach { cookie ->
            val pair = cookie.substringBefore(';').trim()

            when {
                pair.startsWith("JSESSIONID=") -> jSessionId = pair.removePrefix("JSESSIONID=")
                pair.startsWith("SESSION=") -> session = pair.removePrefix("SESSION=")
            }
        }

        if (request != Request.GENERATE_NONCE && response.is401(body)) {
            // Retrying in place can't work: every legacy request body embeds a
            // LOGIC_CHECK derived from the nonce that was current when the caller built
            // it, and that nonce has just been rotated by the response above. Only the
            // caller can rebuild the body against the new nonce, so surface the 401
            // instead of hammering the server until it answers 500.
            BugsnagUtils.addBreadcrumb(
                message = "401 for $request. Sent: $authV",
                data = mapOf("body" to body),
                type = BreadcrumbType.ERROR,
            )
            println("401 for $request.")
            println("Sent auth: $authV")
            println("New nonce: '$nonce' (${nonce.length} chars), new auth: $auth")
            println("Response: $body")
        }

        return body
    }

    override suspend fun createHeaders(authV: String): Map<String, String> {
        return mapOf(
            "Authorization" to authV,
            "User-Agent" to "Kiss2.0_FUS",
        )
    }
}
