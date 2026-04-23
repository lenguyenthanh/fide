$version: "2.0"

namespace fide.spec

use smithy4s.meta#unwrap
use smithy4s.meta#refinement

/// Returned when request input fails validation (e.g. out-of-range numbers, malformed ids).
@error("client")
@httpError(400)
structure ValidationError {
  @required
  message: String
}

/// Returned when no player exists for the given FIDE id.
@error("client")
@httpError(404)
structure PlayerFideIdNotFound {
  @required
  fideId: FideId
}

/// Non-empty set of FIDE ids. Length 1 to 100 (bounds batch lookup cost).
@uniqueItems
@length(min: 1, max: 100)
@nonEmptySetFormat
@unwrap
list SetFideIds {
  member: FideId
}

/// Returned for unexpected server-side failures; message is intentionally generic.
@error("server")
@httpError(500)
structure InternalServerError {
  @required
  message: String
}

/// Refinement trait marking an integer as a validated PlayerId.
@trait(selector: "integer")
@refinement(
   targetType: "fide.types.PlayerId"
   providerImport: "fide.spec.providers.given"
)
structure PlayerIdFormat {}

/// Internal player id assigned by this service (distinct from the FIDE id). Positive integer (>= 1).
@PlayerIdFormat
@unwrap
integer PlayerId

/// Refinement trait marking a string as a validated FideId.
@trait(selector: "string")
@refinement(
   targetType: "fide.types.FideId"
   providerImport: "fide.spec.providers.given"
)
structure FideIdFormat {}

/// FIDE-assigned player id, as published on ratings.fide.com. Non-empty string.
@FideIdFormat
@unwrap
string FideId

/// Player birth year. Positive integer (>= 1); in practice a four-digit year.
@BirthYearFormat
@unwrap
integer BirthYear

/// Refinement trait marking a string as a validated FederationId.
@trait(selector: "string")
@refinement(
   targetType: "fide.types.FederationId"
   providerImport: "fide.spec.providers.given"
)
structure FederationIdFormat {}

/// FIDE federation code (e.g. "NOR", "USA"). Non-empty string; the sentinel "NON" denotes "no federation".
@unwrap
@FederationIdFormat
string FederationId

/// Refinement trait marking an integer as a validated Rating.
@trait(selector: "integer")
@refinement(
   targetType: "fide.types.Rating"
   providerImport: "fide.spec.providers.given"
)
structure RatingFormat {}

/// FIDE Elo rating. Integer in the range 1400 to 4000 (inclusive).
@RatingFormat
@unwrap
integer Rating

/// Refinement trait marking a string as a validated PageNumber.
/// Page numbers are carried as strings in query params to match the pagination convention.
@trait(selector: "string")
@refinement(
   targetType: "fide.types.PageNumber"
   providerImport: "fide.spec.providers.given"
)
structure PageFormat {}

/// Refinement trait marking an integer as a validated PageSize (positive integer).
@trait(selector: "integer")
@refinement(
   targetType: "fide.types.PageSize"
   providerImport: "fide.spec.providers.given"
)
structure PageSizeFormat { }

/// Refinement trait marking an integer as a validated BirthYear.
@trait(selector: "integer")
@refinement(
   targetType: "fide.types.BirthYear"
   providerImport: "fide.spec.providers.given"
)
structure BirthYearFormat { }

/// Refinement trait marking a string as a validated YearMonth (format "YYYY-MM").
@trait(selector: "string")
@refinement(
   targetType: "fide.types.YearMonth"
   providerImport: "fide.spec.providers.given"
)
structure YearMonthFormat {}

/// Parameterised refinement lifting a list into a non-empty set.
@trait(selector: "list")
@refinement(
  targetType: "fide.types.NonEmptySet",
  providerImport: "fide.spec.providers.given"
  parameterised: true
)
structure nonEmptySetFormat {}

/// 1-based page number used by paginated endpoints. Positive integer (>= 1), carried as a string in query params.
@PageFormat
@unwrap
string PageNumber

/// Number of items per page. Positive integer (>= 1); list operations additionally cap the accepted values via @range(min:1, max:100).
@PageSizeFormat
@unwrap
integer PageSize

/// FIDE chess titles (Grandmaster, International Master, etc.) and their women-specific variants.
enum Title {
  GM = "GM"
  IM = "IM"
  FM = "FM"
  CM = "CM"
  NM = "NM"
  WGM = "WGM"
  WIM = "WIM"
  WFM = "WFM"
  WCM = "WCM"
  WNM = "WNM"
}

/// Non-playing FIDE titles (arbiters, organisers, trainers, etc.).
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

/// Player gender as recorded by FIDE.
enum Gender {
  Female = "F"
  Male = "M"
}

/// A FIDE federation (national chess body) identified by its three-letter code.
structure Federation {
  @required
  id: FederationId

  @required
  name: String

}

/// Full player record returned by player-lookup endpoints.
structure GetPlayerByIdOutput {
  @required
  id: PlayerId

  fideId: FideId

  @required
  name: String

  title: Title
  womenTitle: Title
  otherTitles: OtherTitles

  standard: Rating
  rapid: Rating
  blitz: Rating

  gender: Gender
  birthYear: Integer
  @required
  active: Boolean
  @required
  updatedAt: Timestamp

  federation: Federation
}

/// A page of player records.
list Players {
  member: GetPlayerByIdOutput
}

/// Secondary (non-playing) titles held by a player.
list OtherTitles {
  member: OtherTitle
}

/// Filter list of player titles.
list Titles {
  member: Title
}

/// Sort direction applied to list endpoints.
enum Order {
  Asc = "asc"
  Desc = "desc"
}

/// Field to sort list endpoints by.
enum SortBy {
  Name = "name"
  Standard = "standard"
  Rapid = "rapid"
  Blitz = "blitz"
  BirthYear = "birth_year"
}

/// Mixin adding sort-by-field and sort-order query parameters to list operations.
@mixin
structure SortingMixin {
  @httpQuery("sort_by")
  sortBy: SortBy
  @httpQuery("order_by")
  order: Order
}

/// Mixin adding player filtering (ratings, name, titles, gender, birth year, presence flags) to list operations.
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
  @httpQuery("title")
  titles: Titles
  @httpQuery("other_title")
  otherTitles: OtherTitles
  @httpQuery("gender")
  gender: Gender
  @httpQuery("birth_year[gte]")
  birthYearMin: BirthYear
  @httpQuery("birth_year[lte]")
  birthYearMax: BirthYear
  /// Presence filter: when true, only players that hold any playing Title.
  @httpQuery("has_title")
  hasTitle: Boolean
  /// Presence filter: when true, only players that hold a women-specific title.
  @httpQuery("has_women_title")
  hasWomenTitle: Boolean
  /// Presence filter: when true, only players that hold any non-playing OtherTitle.
  @httpQuery("has_other_title")
  hasOtherTitle: Boolean
}
