$version: "2.0"

namespace fide.spec

use smithy4s.meta#unwrap
use smithy4s.meta#refinement

@error("client")
@httpError(400)
structure ValidationError {
  @required
  message: String
}

@error("server")
@httpError(501)
structure NotImplementedYetError {
  @required
  message: String
}

@error("server")
@httpError(500)
structure InternalServerError {
  @required
  message: String
}

integer PlayerId
string FederationId

@range(min: 0, max: 4000)
integer Rating

@trait(selector: "string")
@refinement(
   targetType: "fide.types.PositiveInt"
   providerImport: "fide.spec.providers.given"
)
structure PageFormat {}

@trait(selector: "integer")
@refinement(
   targetType: "fide.types.PositiveInt"
   providerImport: "fide.spec.providers.given"
)
structure PageSizeFormat { }

@trait(selector: "list")
@refinement(
  targetType: "fide.types.NonEmptySet",
  providerImport: "fide.spec.providers.given"
  parameterised: true
)
structure nonEmptySetFormat {}

@PageFormat
@unwrap
string PageNumber

@PageSizeFormat
@unwrap
integer PageSize

enum Title {
  GM = "GM"
  WGM = "WGM"
  IM = "IM"
  WIM = "WIM"
  FM = "FM"
  WFM = "WFM"
  CM = "CM"
  WCM = "WCM"
}

enum OtherTitle {
  IA = "IA"
  FA = "FA"
  NA = "NA"
  IO = "IO"
  FT = "FT"
  FI = "FI"
  FST = "FST"
  DI = "DI"
  NI = "NI"
  SI = "SI"
  LSI = "LSI"
}

enum Sex {
  Female = "F"
  Male = "M"
}

structure Federation {
  @required
  id: FederationId

  @required
  name: String

}

structure GetPlayerByIdOutput {
  @required
  id: PlayerId

  @required
  name: String

  title: Title
  womenTitle: Title
  otherTitles: OtherTitles

  standard: Rating
  rapid: Rating
  blitz: Rating

  sex: Sex
  birthYear: Integer
  @required
  active: Boolean
  @required
  updatedAt: Timestamp

  federation: Federation
}

list Players {
  member: GetPlayerByIdOutput
}

list OtherTitles {
  member: OtherTitle
}

enum Order {
  Asc = "asc"
  Desc = "desc"
}

enum SortBy {
  Name = "name"
  Standard = "standard"
  Rapid = "rapid"
  Blitz = "blitz"
  BirthYear = "birth_year"
}

@mixin
structure SortingMixin {
  @httpQuery("sort_by")
  sortBy: SortBy
  @httpQuery("order_by")
  order: Order
}

@mixin
structure FilterMixin {
  @httpQuery("is_active")
  isActive: Boolean
  @httpQuery("std[gte]")
  standardMin: Rating
  @httpQuery("std[lte]")
  standardMax: Rating
  @httpQuery("rapid[gte]")
  rapidMin: Rating
  @httpQuery("rapid[lte]")
  rapidMax: Rating
  @httpQuery("blitz[gte]")
  blitzMin: Rating
  @httpQuery("blitz[lte]")
  blitzMax: Rating
  @httpQuery("name")
  name: String
}
