package fide
package db

import com.comcast.ip4s.*
import fide.types.PositiveInt

case class PostgresConfig(
    host: Host,
    port: Port,
    user: String,
    password: String,
    database: String,
    max: PositiveInt,
    schema: String,
    debug: Boolean,
    ssl: Boolean
)
