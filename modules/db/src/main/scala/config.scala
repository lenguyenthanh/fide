package fide
package db

import cats.syntax.all.*

case class PostgresConfig(
    host: String,
    port: Int,
    user: String,
    password: String,
    database: String,
    max: Int,
    schema: String = "fide",
    debug: Boolean = true
):

  def toFlywayConfig: FlywayConfig = FlywayConfig(
    url = s"jdbc:postgresql://$host:$port/$database",
    user = user.toString.some,
    password = password.toString.toCharArray.nn.some,
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
