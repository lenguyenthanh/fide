package fide
package domain

import cats.syntax.all.*

import java.time.OffsetDateTime

type PlayerId = Int
type Rating   = Int

enum Title(val value: String):
  case GM   extends Title("GM")
  case IM   extends Title("IM")
  case FM   extends Title("FM")
  case WGM  extends Title("WGM")
  case WIM  extends Title("WIM")
  case WFM  extends Title("WFM")
  case CM   extends Title("CM")
  case WCM  extends Title("WCM")
  case NM   extends Title("NM")
  case WHCM extends Title("WHCM")

object Title:
  def apply(value: String): Option[Title] =
    Title.values.find(_.value == value)

case class Player(
    id: PlayerId,
    name: String,
    title: Option[Title] = None,
    standard: Option[Rating] = None,
    rapid: Option[Rating] = None,
    blitz: Option[Rating] = None,
    year: Option[Int] = None,
    active: Option[Boolean] = None,
    fetchedAt: OffsetDateTime,
    createdAt: OffsetDateTime
)

case class NewPlayer(
    id: PlayerId,
    name: String,
    title: Option[Title] = None,
    standard: Option[Rating] = None,
    rapid: Option[Rating] = None,
    blitz: Option[Rating] = None,
    year: Option[Int] = None,
    active: Option[Boolean] = None
)

type FederationId = String

case class Federation(
    id: FederationId,
    name: String,
    updatedAt: OffsetDateTime,
    createdAt: OffsetDateTime
)

case class NewFederation(
    id: FederationId,
    name: String
)
