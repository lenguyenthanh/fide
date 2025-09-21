package fide
package domain

import cats.syntax.all.*
import fide.types.*

import java.time.OffsetDateTime

enum Title(val value: String):
  case GM  extends Title("GM")
  case IM  extends Title("IM")
  case FM  extends Title("FM")
  case WGM extends Title("WGM")
  case WIM extends Title("WIM")
  case WFM extends Title("WFM")
  case CM  extends Title("CM")
  case WCM extends Title("WCM")

object Title:
  def apply(value: String): Option[Title] =
    Title.values.find(_.value == value)

enum OtherTitle(val value: String):
  case IA  extends OtherTitle("IA")  // International Arbiter
  case FA  extends OtherTitle("FA")  // FIDE Arbiter
  case NA  extends OtherTitle("NA")  // National Arbiter
  case IO  extends OtherTitle("IO")  // International Organizer
  case FST extends OtherTitle("FST") // FIDE Senior Trainer
  case FT  extends OtherTitle("FT")  // FIDE Trainer
  case FI  extends OtherTitle("FI")  // FIDE Instructor
  case DI  extends OtherTitle("DI")  // Developmental Instructor
  case NI  extends OtherTitle("NI")  // National Instructor
  case SI  extends OtherTitle("SI")  // School Instructor
  case LSI extends OtherTitle("LSI") // Lead School Instructor

object OtherTitle:
  def apply(value: String): Option[OtherTitle] =
    OtherTitle.values.find(_.value == value)

  def applyToList(value: String): List[OtherTitle] =
    value.split(",").toList.mapFilter(apply)

enum Gender(val value: String):
  case Female extends Gender("F")
  case Male   extends Gender("M")

object Gender:
  def apply(value: String): Option[Gender] =
    value match
      case "F" => Some(Female)
      case "M" => Some(Male)
      case _   => None

case class PlayerInfo(
    id: PlayerId,
    name: String,
    title: Option[Title] = None,
    womenTitle: Option[Title] = None,
    otherTitles: List[OtherTitle] = Nil,
    standard: Option[Rating] = None,
    standardK: Option[Int] = None,
    rapid: Option[Rating] = None,
    rapidK: Option[Int] = None,
    blitz: Option[Rating] = None,
    blitzK: Option[Int] = None,
    gender: Option[Gender] = None,
    birthYear: Option[Int] = None,
    active: Boolean,
    updatedAt: OffsetDateTime,
    createdAt: OffsetDateTime,
    federation: Option[FederationInfo] = None
)

case class NewPlayer(
    id: PlayerId,
    name: String,
    title: Option[Title] = None,
    womenTitle: Option[Title] = None,
    otherTitles: List[OtherTitle] = Nil,
    standard: Option[Rating] = None,
    standardK: Option[Int] = None,
    rapid: Option[Rating] = None,
    rapidK: Option[Int] = None,
    blitz: Option[Rating] = None,
    blitzK: Option[Int] = None,
    gender: Option[Gender] = None,
    birthYear: Option[Int] = None,
    active: Boolean,
    federationId: Option[FederationId] = None
)

case class NewFederation(
    id: FederationId,
    name: String
)

case class FederationInfo(
    id: FederationId,
    name: String
)

case class FederationSummary(
    id: FederationId,
    name: String,
    nbPlayers: Int,
    standard: Stats,
    rapid: Stats,
    blitz: Stats
)

case class Stats(
    rank: Int,
    nbPlayers: Int,
    top10Rating: Int
)

case class Federation(
    id: FederationId,
    name: String,
    updatedAt: OffsetDateTime,
    createdAt: OffsetDateTime
)

object Federation:

  import io.github.iltotore.iron.*
  val all: Map[FederationId, String] = Map(
    FederationId("AFG") -> "Afghanistan",
    FederationId("AHO") -> "Netherlands Antilles",
    FederationId("ALB") -> "Albania",
    FederationId("ALG") -> "Algeria",
    FederationId("AND") -> "Andorra",
    FederationId("ANG") -> "Angola",
    FederationId("ANT") -> "Antigua and Barbuda",
    FederationId("ARG") -> "Argentina",
    FederationId("ARM") -> "Armenia",
    FederationId("ARU") -> "Aruba",
    FederationId("AUS") -> "Australia",
    FederationId("AUT") -> "Austria",
    FederationId("AZE") -> "Azerbaijan",
    FederationId("BAH") -> "Bahamas",
    FederationId("BAN") -> "Bangladesh",
    FederationId("BAR") -> "Barbados",
    FederationId("BDI") -> "Burundi",
    FederationId("BEL") -> "Belgium",
    FederationId("BER") -> "Bermuda",
    FederationId("BHU") -> "Bhutan",
    FederationId("BIH") -> "Bosnia & Herzegovina",
    FederationId("BIZ") -> "Belize",
    FederationId("BLR") -> "Belarus",
    FederationId("BOL") -> "Bolivia",
    FederationId("BOT") -> "Botswana",
    FederationId("BRA") -> "Brazil",
    FederationId("BRN") -> "Bahrain",
    FederationId("BRU") -> "Brunei Darussalam",
    FederationId("BUL") -> "Bulgaria",
    FederationId("BUR") -> "Burkina Faso",
    FederationId("CAF") -> "Central African Republic",
    FederationId("CAM") -> "Cambodia",
    FederationId("CAN") -> "Canada",
    FederationId("CAY") -> "Cayman Islands",
    FederationId("CGO") -> "Congo",
    FederationId("CHA") -> "Chad",
    FederationId("CHI") -> "Chile",
    FederationId("CHN") -> "China",
    FederationId("CIV") -> "Cote d’Ivoire",
    FederationId("CMR") -> "Cameroon",
    FederationId("COD") -> "Democratic Republic of the Congo",
    FederationId("COL") -> "Colombia",
    FederationId("COM") -> "Comoros Islands",
    FederationId("CPV") -> "Cape Verde",
    FederationId("CRC") -> "Costa Rica",
    FederationId("CRO") -> "Croatia",
    FederationId("CUB") -> "Cuba",
    FederationId("CYP") -> "Cyprus",
    FederationId("CZE") -> "Czech Republic",
    FederationId("DEN") -> "Denmark",
    FederationId("DJI") -> "Djibouti",
    FederationId("DMA") -> "Dominica",
    FederationId("DOM") -> "Dominican Republic",
    FederationId("ECU") -> "Ecuador",
    FederationId("EGY") -> "Egypt",
    FederationId("ENG") -> "England",
    FederationId("ERI") -> "Eritrea",
    FederationId("ESA") -> "El Salvador",
    FederationId("ESP") -> "Spain",
    FederationId("EST") -> "Estonia",
    FederationId("ETH") -> "Ethiopia",
    FederationId("FAI") -> "Faroe Islands",
    FederationId("FID") -> "FIDE",
    FederationId("FIJ") -> "Fiji",
    FederationId("FIN") -> "Finland",
    FederationId("FRA") -> "France",
    FederationId("GAB") -> "Gabon",
    FederationId("GAM") -> "Gambia",
    FederationId("GCI") -> "Guernsey",
    FederationId("GEO") -> "Georgia",
    FederationId("GEQ") -> "Equatorial Guinea",
    FederationId("GER") -> "Germany",
    FederationId("GHA") -> "Ghana",
    FederationId("GRE") -> "Greece",
    FederationId("GRL") -> "Greenland",
    FederationId("GRN") -> "Grenada",
    FederationId("GUA") -> "Guatemala",
    FederationId("GUM") -> "Guam",
    FederationId("GUY") -> "Guyana",
    FederationId("HAI") -> "Haiti",
    FederationId("HKG") -> "Hong Kong, China",
    FederationId("HON") -> "Honduras",
    FederationId("HUN") -> "Hungary",
    FederationId("INA") -> "Indonesia",
    FederationId("IND") -> "India",
    FederationId("IOM") -> "Isle of Man",
    FederationId("IRI") -> "Iran",
    FederationId("IRL") -> "Ireland",
    FederationId("IRQ") -> "Iraq",
    FederationId("ISL") -> "Iceland",
    FederationId("ISR") -> "Israel",
    FederationId("ISV") -> "US Virgin Islands",
    FederationId("ITA") -> "Italy",
    FederationId("IVB") -> "British Virgin Islands",
    FederationId("JAM") -> "Jamaica",
    FederationId("JCI") -> "Jersey",
    FederationId("JOR") -> "Jordan",
    FederationId("JPN") -> "Japan",
    FederationId("KAZ") -> "Kazakhstan",
    FederationId("KEN") -> "Kenya",
    FederationId("KGZ") -> "Kyrgyzstan",
    FederationId("KOR") -> "South Korea",
    FederationId("KOS") -> "Kosovo *",
    FederationId("KSA") -> "Saudi Arabia",
    FederationId("KUW") -> "Kuwait",
    FederationId("LAO") -> "Laos",
    FederationId("LAT") -> "Latvia",
    FederationId("LBA") -> "Libya",
    FederationId("LBN") -> "Lebanon",
    FederationId("LBR") -> "Liberia",
    FederationId("LCA") -> "Saint Lucia",
    FederationId("LES") -> "Lesotho",
    FederationId("LIE") -> "Liechtenstein",
    FederationId("LTU") -> "Lithuania",
    FederationId("LUX") -> "Luxembourg",
    FederationId("MAC") -> "Macau",
    FederationId("MAD") -> "Madagascar",
    FederationId("MAR") -> "Morocco",
    FederationId("MAS") -> "Malaysia",
    FederationId("MAW") -> "Malawi",
    FederationId("MDA") -> "Moldova",
    FederationId("MDV") -> "Maldives",
    FederationId("MEX") -> "Mexico",
    FederationId("MGL") -> "Mongolia",
    FederationId("MKD") -> "North Macedonia",
    FederationId("MLI") -> "Mali",
    FederationId("MLT") -> "Malta",
    FederationId("MNC") -> "Monaco",
    FederationId("MNE") -> "Montenegro",
    FederationId("MOZ") -> "Mozambique",
    FederationId("MRI") -> "Mauritius",
    FederationId("MTN") -> "Mauritania",
    FederationId("MYA") -> "Myanmar",
    FederationId("NAM") -> "Namibia",
    FederationId("NCA") -> "Nicaragua",
    FederationId("NCL") -> "New Caledonia",
    FederationId("NED") -> "Netherlands",
    FederationId("NEP") -> "Nepal",
    FederationId("NGR") -> "Nigeria",
    FederationId("NIG") -> "Niger",
    FederationId("NOR") -> "Norway",
    FederationId("NRU") -> "Nauru",
    FederationId("NZL") -> "New Zealand",
    FederationId("OMA") -> "Oman",
    FederationId("PAK") -> "Pakistan",
    FederationId("PAN") -> "Panama",
    FederationId("PAR") -> "Paraguay",
    FederationId("PER") -> "Peru",
    FederationId("PHI") -> "Philippines",
    FederationId("PLE") -> "Palestine",
    FederationId("PLW") -> "Palau",
    FederationId("PNG") -> "Papua New Guinea",
    FederationId("POL") -> "Poland",
    FederationId("POR") -> "Portugal",
    FederationId("PUR") -> "Puerto Rico",
    FederationId("QAT") -> "Qatar",
    FederationId("ROU") -> "Romania",
    FederationId("RSA") -> "South Africa",
    FederationId("RUS") -> "Russia",
    FederationId("RWA") -> "Rwanda",
    FederationId("SCO") -> "Scotland",
    FederationId("SEN") -> "Senegal",
    FederationId("SEY") -> "Seychelles",
    FederationId("SGP") -> "Singapore",
    FederationId("SKN") -> "Saint Kitts and Nevis",
    FederationId("SLE") -> "Sierra Leone",
    FederationId("SLO") -> "Slovenia",
    FederationId("SMR") -> "San Marino",
    FederationId("SOL") -> "Solomon Islands",
    FederationId("SOM") -> "Somalia",
    FederationId("SRB") -> "Serbia",
    FederationId("SRI") -> "Sri Lanka",
    FederationId("SSD") -> "South Sudan",
    FederationId("STP") -> "Sao Tome and Principe",
    FederationId("SUD") -> "Sudan",
    FederationId("SUI") -> "Switzerland",
    FederationId("SUR") -> "Suriname",
    FederationId("SVK") -> "Slovakia",
    FederationId("SWE") -> "Sweden",
    FederationId("SWZ") -> "Eswatini",
    FederationId("SYR") -> "Syria",
    FederationId("TAN") -> "Tanzania",
    FederationId("TGA") -> "Tonga",
    FederationId("THA") -> "Thailand",
    FederationId("TJK") -> "Tajikistan",
    FederationId("TKM") -> "Turkmenistan",
    FederationId("TLS") -> "Timor-Leste",
    FederationId("TOG") -> "Togo",
    FederationId("TPE") -> "Chinese Taipei",
    FederationId("TTO") -> "Trinidad & Tobago",
    FederationId("TUN") -> "Tunisia",
    FederationId("TUR") -> "Turkiye",
    FederationId("UAE") -> "United Arab Emirates",
    FederationId("UGA") -> "Uganda",
    FederationId("UKR") -> "Ukraine",
    FederationId("URU") -> "Uruguay",
    FederationId("USA") -> "United States of America",
    FederationId("UZB") -> "Uzbekistan",
    FederationId("VAN") -> "Vanuatu",
    FederationId("VEN") -> "Venezuela",
    FederationId("VIE") -> "Vietnam",
    FederationId("VIN") -> "Saint Vincent and the Grenadines",
    FederationId("WLS") -> "Wales",
    FederationId("YEM") -> "Yemen",
    FederationId("ZAM") -> "Zambia",
    FederationId("ZIM") -> "Zimbabwe"
  )

case class RatingHistoryEntry(
    id: Long,
    playerId: PlayerId,
    standard: Option[Rating] = None,
    standardK: Option[Int] = None,
    rapid: Option[Rating] = None,
    rapidK: Option[Int] = None,
    blitz: Option[Rating] = None,
    blitzK: Option[Int] = None,
    month: Int, // Epoch-based month index: (year - 1970) * 12 + (month - 1)
    recordedAt: OffsetDateTime,
    createdAt: OffsetDateTime
):
  // Derived year from epoch-based month index
  def year: Int = 1970 + month / 12
  // Derived calendar month (1-12)
  def calendarMonth: Int = (month % 12) + 1

case class NewRatingHistoryEntry(
    playerId: PlayerId,
    standard: Option[Rating] = None,
    standardK: Option[Int] = None,
    rapid: Option[Rating] = None,
    rapidK: Option[Int] = None,
    blitz: Option[Rating] = None,
    blitzK: Option[Int] = None,
    month: Int, // Epoch-based month index: (year - 1970) * 12 + (month - 1)
    recordedAt: Option[OffsetDateTime] = None
):
  // Derived year from epoch-based month index
  def year: Int = 1970 + month / 12
  // Derived calendar month (1-12)
  def calendarMonth: Int = (month % 12) + 1
