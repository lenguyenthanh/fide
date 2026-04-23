$version: "2"

namespace fide.spec

use alloy#simpleRestJson
use smithy4s.meta#scalaImports

/// Federation-related lookups: aggregated summaries across federations, per-federation details, and federation rosters.
@simpleRestJson
service FederationService {
  version: "0.0.1"
  operations: [GetFederationsSummary, GetFederationSummaryById, GetFederationPlayersById],
}

/// Paginated summaries (player counts and top-10 stats) for all federations.
///
/// Defaults when omitted:
/// - page = "1", page_size = 30
@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/federations/summary", code: 200)
operation GetFederationsSummary {
  input :=
    @scalaImports(["fide.spec.providers.given"]) {

    @httpQuery("page")
    @default("1")
    page: PageNumber
    @httpQuery("page_size")
    @range(min: 1, max: 100)
    @default(30)
    pageSize: PageSize
  }

  output:= {
    @required
    items: FederationsSummary
    @required
    totalResults: Long
    nextPage: PageNumber
  }

  errors: [InternalServerError]
}

/// Get a federation summary by its id
@readonly
@http(method: "GET", uri: "/api/federations/summary/{id}", code: 200)
operation GetFederationSummaryById {
  input := {
    @httpLabel
    @required
    id: FederationId
  }

  output: GetFederationSummaryByIdOutput
  errors: [FederationNotFound, InternalServerError]
}

/// Paginated list of players belonging to a federation, with the same filters/sorting as GetPlayers.
///
/// Defaults when omitted:
/// - page = "1", page_size = 30
/// - sort_by = "name"; order_by = "asc" if sort_by is "name", otherwise "desc"
/// - is_active, has_title, has_women_title, has_other_title: unset => no filter
/// - name, title, other_title, gender: unset => no filter
/// - std[gte]/std[lte], rapid[gte]/rapid[lte], blitz[gte]/blitz[lte]: unset => no bound
/// - birth_year[gte], birth_year[lte]: unset => no bound
@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/federations/summary/{id}/players", code: 200)
operation GetFederationPlayersById {
  input :=
    @scalaImports(["fide.spec.providers.given"])
    with [SortingMixin, FilterMixin] {

    @httpLabel
    @required
    id: FederationId
    @httpQuery("page")
    @default("1")
    page: PageNumber
    @httpQuery("page_size")
    @range(min: 1, max: 100)
    @default(30)
    pageSize: PageSize
  }

  output := {
    @required
    items: Players
    @required
    totalResults: Long
    nextPage: PageNumber
  }

  errors: [FederationNotFound, InternalServerError]
}

/// A page of federation summaries.
list FederationsSummary {
  member: GetFederationSummaryByIdOutput
}

/// Federation with aggregated rating stats across classical/rapid/blitz.
structure GetFederationSummaryByIdOutput {
  @required
  id: FederationId

  @required
  name: String

  @required
  nbPlayers: Integer

  @required
  standard: Stats

  @required
  rapid: Stats

  @required
  blitz: Stats
}


/// A plain list of federations (id + name).
list Federations {
  member: Federation
}

/// Aggregated rating stats for one rating category (classical, rapid, or blitz).
structure Stats {
  /// Federation's rank by this category (1 = best).
  @required
  rank: Integer

  /// Number of rated players in this category.
  @required
  nbPlayers: Integer

  /// Sum of the top-10 players' ratings in this category.
  @required
  top10Rating: Integer
}


/// Returned when no federation exists for the given id.
@error("client")
@httpError(404)
structure FederationNotFound {
  @required
  id: FederationId
}
