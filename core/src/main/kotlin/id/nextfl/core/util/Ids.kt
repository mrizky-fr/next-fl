package id.nextfl.core.util

import java.util.UUID

object Ids {
    fun create(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"
}
