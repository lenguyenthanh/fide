package fide
package db

import cats.syntax.all.*
import com.comcast.ip4s.*

case class PostgresConfig(
    host: Host,
    port: Port,
    user: String,
    password: String,
    database: String,
    max: Int,
    schema: String,
    debug: Boolean
):

  def toFlywayConfig: FlywayConfig = FlywayConfig(
    url = s"jdbc:postgresql://$host:$port/$database",
    user = user.some,
    password = password.toCharArray.nn.some,
    migrationsTable = "flyway",
    migrationsLocations = List("/db"),
    schema = schema
  )

case class FlywayConfig(
    url: String,
    user: Option[String],
    password: Option[Array[Char]],
    migrationsTable: String,
    migrationsLocations: List[String],
    schema: String
)
