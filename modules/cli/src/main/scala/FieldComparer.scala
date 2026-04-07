package fide.cli

import fide.domain.NewPlayer
import fide.spec.GetHistoricalPlayerByIdOutput

object FieldComparer:

  def compareHistorical(csv: NewPlayer, api: GetHistoricalPlayerByIdOutput): List[FieldDiff] =
    List(
      compareName(csv.name, api.name),
      compareField("title", csv.title.map(_.value), api.title.map(_.value)),
      compareField("womenTitle", csv.womenTitle.map(_.value), api.womenTitle.map(_.value)),
      compareField(
        "otherTitles",
        csv.otherTitles.map(_.value).sorted,
        api.otherTitles.getOrElse(Nil).map(_.value).sorted
      ),
      compareField("standard", csv.standard, api.standard),
      compareField("standardK", csv.standardK, api.standardK),
      compareField("rapid", csv.rapid, api.rapid),
      compareField("rapidK", csv.rapidK, api.rapidK),
      compareField("blitz", csv.blitz, api.blitz),
      compareField("blitzK", csv.blitzK, api.blitzK),
      compareField("gender", csv.gender.map(_.value), api.gender.map(_.value)),
      compareField("birthYear", csv.birthYear, api.birthYear),
      compareField("active", csv.active, api.active),
      compareField("federationId", csv.federationId, api.federation.map(_.id))
    ).flatten

  private def compareField[A](name: String, expected: A, actual: A): Option[FieldDiff] =
    if expected == actual then None
    else Some(FieldDiff(name, expected.toString, actual.toString))

  private def compareName(expected: String, actual: String): Option[FieldDiff] =
    if normalize(expected) == normalize(actual) then None
    else Some(FieldDiff("name", expected, actual))

  private def normalize(s: String): String =
    s.trim.toLowerCase.replaceAll("\\s+", " ")
