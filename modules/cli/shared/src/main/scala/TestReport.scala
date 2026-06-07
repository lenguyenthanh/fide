package fide.cli

import fide.types.*

case class FieldDiff(field: String, expected: String, actual: String)

enum TestResult:
  case Pass(endpoint: String, playerId: Option[PlayerId], month: Option[YearMonth])
  case Mismatch(
      endpoint: String,
      playerId: Option[PlayerId],
      month: Option[YearMonth],
      diffs: List[FieldDiff]
  )
  case HttpError(endpoint: String, playerId: Option[PlayerId], month: Option[YearMonth], error: String)

case class TestReport(
    passes: Int,
    mismatches: List[TestResult.Mismatch],
    errors: List[TestResult.HttpError]
):

  def add(result: TestResult): TestReport =
    result match
      case _: TestResult.Pass      => copy(passes = passes + 1)
      case m: TestResult.Mismatch  => copy(mismatches = mismatches :+ m)
      case e: TestResult.HttpError => copy(errors = errors :+ e)

  def combine(other: TestReport): TestReport =
    TestReport(
      passes = passes + other.passes,
      mismatches = mismatches ++ other.mismatches,
      errors = errors ++ other.errors
    )

  def isSuccess: Boolean = mismatches.isEmpty && errors.isEmpty

  def total: Int = passes + mismatches.size + errors.size

  def summary: String =
    val sep = "=" * 50
    val sb  = StringBuilder()
    sb.append(s"\n$sep\nTest Summary\n$sep\n")
    sb.append(s"  Total:      $total\n")
    sb.append(s"  Passed:     $passes\n")
    sb.append(s"  Mismatches: ${mismatches.size}\n")
    sb.append(s"  Errors:     ${errors.size}\n")

    if mismatches.nonEmpty then
      sb.append("\nMismatches:\n")
      mismatches.foreach: m =>
        val monthStr  = m.month.fold("")(ym => s"[${ym.format}] ")
        val playerStr = m.playerId.fold("")(id => s"/$id")
        sb.append(s"  $monthStr${m.endpoint}$playerStr\n")
        m.diffs.foreach: d =>
          sb.append(s"    ${d.field}: expected=${d.expected}, actual=${d.actual}\n")

    if errors.nonEmpty then
      sb.append("\nErrors:\n")
      errors.foreach: e =>
        val monthStr  = e.month.fold("")(ym => s"[${ym.format}] ")
        val playerStr = e.playerId.fold("")(id => s"/$id")
        sb.append(s"  $monthStr${e.endpoint}$playerStr\n")
        sb.append(s"    ${e.error}\n")

    sb.append(s"$sep\n")
    if isSuccess then sb.append("PASS\n")
    else sb.append(s"FAIL (${mismatches.size} mismatches, ${errors.size} errors)\n")

    sb.toString

object TestReport:
  val empty: TestReport = TestReport(0, Nil, Nil)
