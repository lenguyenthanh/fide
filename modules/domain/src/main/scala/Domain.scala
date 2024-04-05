package fide
package domain

import cats.syntax.all.*

import java.time.OffsetDateTime

type PlayerId     = Int
type Rating       = Int
type FederationId = String

object FederationId:
  def apply(value: String): FederationId = value

enum Title(val value: String):
  case GM  extends Title("GM")
  case IM  extends Title("IM")
  case FM  extends Title("FM")
  case WGM extends Title("WGM")
  case WIM extends Title("WIM")
  case WFM extends Title("WFM")
  case CM  extends Title("CM")
  case WCM extends Title("WCM")
  case NM  extends Title("NM")
  case WNM extends Title("WNM")

object Title:
  def apply(value: String): Option[Title] =
    Title.values.find(_.value == value)

  private val titleRank: Map[Title, Int] =
    List(GM, IM, WGM, FM, WIM, WFM, NM, CM, WCM, WNM).zipWithIndex.toMap

  def mostValuable(t1: Option[Title], t2: Option[Title]): Option[Title] =
    t1.flatMap(titleRank.get)
      .fold(t2): v1 =>
        t2.flatMap(titleRank.get)
          .fold(t1): v2 =>
            if v1 < v2 then t1 else t2

case class PlayerInfo(
    id: PlayerId,
    name: String,
    title: Option[Title] = None,
    standard: Option[Rating] = None,
    rapid: Option[Rating] = None,
    blitz: Option[Rating] = None,
    year: Option[Int] = None,
    active: Option[Boolean] = None,
    updatedAt: OffsetDateTime,
    createdAt: OffsetDateTime,
    federation: Option[FederationInfo] = None
)

case class NewPlayer(
    id: PlayerId,
    name: String,
    title: Option[Title] = None,
    standard: Option[Rating] = None,
    rapid: Option[Rating] = None,
    blitz: Option[Rating] = None,
    year: Option[Int] = None,
    active: Boolean
)

case class InsertPlayer(
    id: PlayerId,
    name: String,
    title: Option[Title] = None,
    standard: Option[Rating] = None,
    rapid: Option[Rating] = None,
    blitz: Option[Rating] = None,
    year: Option[Int] = None,
    active: Boolean,
    federation: Option[FederationId] = None
)

case class Federation(
    id: FederationId,
    name: String,
    updatedAt: OffsetDateTime,
    createdAt: OffsetDateTime
)

object Federation:

  def nameById(id: FederationId): String = names.getOrElse(id, id)

  val names: Map[FederationId, String] = Map(
    FederationId("FID") -> "FIDE",
    FederationId("USA") -> "United States of America",
    FederationId("IND") -> "India",
    FederationId("CHN") -> "China",
    FederationId("RUS") -> "Russia",
    FederationId("AZE") -> "Azerbaijan",
    FederationId("FRA") -> "France",
    FederationId("UKR") -> "Ukraine",
    FederationId("ARM") -> "Armenia",
    FederationId("GER") -> "Germany",
    FederationId("ESP") -> "Spain",
    FederationId("NED") -> "Netherlands",
    FederationId("HUN") -> "Hungary",
    FederationId("POL") -> "Poland",
    FederationId("ENG") -> "England",
    FederationId("ROU") -> "Romania",
    FederationId("NOR") -> "Norway",
    FederationId("UZB") -> "Uzbekistan",
    FederationId("ISR") -> "Israel",
    FederationId("CZE") -> "Czech Republic",
    FederationId("SRB") -> "Serbia",
    FederationId("CRO") -> "Croatia",
    FederationId("GRE") -> "Greece",
    FederationId("IRI") -> "Iran",
    FederationId("TUR") -> "Turkiye",
    FederationId("SLO") -> "Slovenia",
    FederationId("ARG") -> "Argentina",
    FederationId("SWE") -> "Sweden",
    FederationId("GEO") -> "Georgia",
    FederationId("ITA") -> "Italy",
    FederationId("CUB") -> "Cuba",
    FederationId("AUT") -> "Austria",
    FederationId("PER") -> "Peru",
    FederationId("BUL") -> "Bulgaria",
    FederationId("BRA") -> "Brazil",
    FederationId("DEN") -> "Denmark",
    FederationId("SUI") -> "Switzerland",
    FederationId("CAN") -> "Canada",
    FederationId("SVK") -> "Slovakia",
    FederationId("LTU") -> "Lithuania",
    FederationId("VIE") -> "Vietnam",
    FederationId("AUS") -> "Australia",
    FederationId("BEL") -> "Belgium",
    FederationId("MNE") -> "Montenegro",
    FederationId("MDA") -> "Moldova",
    FederationId("KAZ") -> "Kazakhstan",
    FederationId("ISL") -> "Iceland",
    FederationId("COL") -> "Colombia",
    FederationId("BIH") -> "Bosnia & Herzegovina",
    FederationId("EGY") -> "Egypt",
    FederationId("FIN") -> "Finland",
    FederationId("MGL") -> "Mongolia",
    FederationId("PHI") -> "Philippines",
    FederationId("BLR") -> "Belarus",
    FederationId("LAT") -> "Latvia",
    FederationId("POR") -> "Portugal",
    FederationId("CHI") -> "Chile",
    FederationId("MEX") -> "Mexico",
    FederationId("MKD") -> "North Macedonia",
    FederationId("INA") -> "Indonesia",
    FederationId("PAR") -> "Paraguay",
    FederationId("EST") -> "Estonia",
    FederationId("SGP") -> "Singapore",
    FederationId("SCO") -> "Scotland",
    FederationId("VEN") -> "Venezuela",
    FederationId("IRL") -> "Ireland",
    FederationId("URU") -> "Uruguay",
    FederationId("TKM") -> "Turkmenistan",
    FederationId("MAR") -> "Morocco",
    FederationId("MAS") -> "Malaysia",
    FederationId("BAN") -> "Bangladesh",
    FederationId("ALG") -> "Algeria",
    FederationId("RSA") -> "South Africa",
    FederationId("AND") -> "Andorra",
    FederationId("ALB") -> "Albania",
    FederationId("KGZ") -> "Kyrgyzstan",
    FederationId("KOS") -> "Kosovo *",
    FederationId("FAI") -> "Faroe Islands",
    FederationId("ZAM") -> "Zambia",
    FederationId("MYA") -> "Myanmar",
    FederationId("NZL") -> "New Zealand",
    FederationId("ECU") -> "Ecuador",
    FederationId("CRC") -> "Costa Rica",
    FederationId("NGR") -> "Nigeria",
    FederationId("JPN") -> "Japan",
    FederationId("SYR") -> "Syria",
    FederationId("DOM") -> "Dominican Republic",
    FederationId("LUX") -> "Luxembourg",
    FederationId("WLS") -> "Wales",
    FederationId("BOL") -> "Bolivia",
    FederationId("TUN") -> "Tunisia",
    FederationId("UAE") -> "United Arab Emirates",
    FederationId("MNC") -> "Monaco",
    FederationId("TJK") -> "Tajikistan",
    FederationId("PAN") -> "Panama",
    FederationId("LBN") -> "Lebanon",
    FederationId("NCA") -> "Nicaragua",
    FederationId("ESA") -> "El Salvador",
    FederationId("ANG") -> "Angola",
    FederationId("TTO") -> "Trinidad & Tobago",
    FederationId("SRI") -> "Sri Lanka",
    FederationId("IRQ") -> "Iraq",
    FederationId("JOR") -> "Jordan",
    FederationId("UGA") -> "Uganda",
    FederationId("MAD") -> "Madagascar",
    FederationId("ZIM") -> "Zimbabwe",
    FederationId("MLT") -> "Malta",
    FederationId("SUD") -> "Sudan",
    FederationId("KOR") -> "South Korea",
    FederationId("PUR") -> "Puerto Rico",
    FederationId("HON") -> "Honduras",
    FederationId("GUA") -> "Guatemala",
    FederationId("PAK") -> "Pakistan",
    FederationId("JAM") -> "Jamaica",
    FederationId("THA") -> "Thailand",
    FederationId("YEM") -> "Yemen",
    FederationId("LBA") -> "Libya",
    FederationId("CYP") -> "Cyprus",
    FederationId("NEP") -> "Nepal",
    FederationId("HKG") -> "Hong Kong, China",
    FederationId("SSD") -> "South Sudan",
    FederationId("BOT") -> "Botswana",
    FederationId("PLE") -> "Palestine",
    FederationId("KEN") -> "Kenya",
    FederationId("AHO") -> "Netherlands Antilles",
    FederationId("MAW") -> "Malawi",
    FederationId("LIE") -> "Liechtenstein",
    FederationId("TPE") -> "Chinese Taipei",
    FederationId("AFG") -> "Afghanistan",
    FederationId("MOZ") -> "Mozambique",
    FederationId("KSA") -> "Saudi Arabia",
    FederationId("BAR") -> "Barbados",
    FederationId("NAM") -> "Namibia",
    FederationId("HAI") -> "Haiti",
    FederationId("ARU") -> "Aruba",
    FederationId("CIV") -> "Cote d’Ivoire",
    FederationId("CPV") -> "Cape Verde",
    FederationId("SUR") -> "Suriname",
    FederationId("LBR") -> "Liberia",
    FederationId("IOM") -> "Isle of Man",
    FederationId("MTN") -> "Mauritania",
    FederationId("BRN") -> "Bahrain",
    FederationId("GHA") -> "Ghana",
    FederationId("OMA") -> "Oman",
    FederationId("BRU") -> "Brunei Darussalam",
    FederationId("GCI") -> "Guernsey",
    FederationId("GUM") -> "Guam",
    FederationId("KUW") -> "Kuwait",
    FederationId("JCI") -> "Jersey",
    FederationId("MRI") -> "Mauritius",
    FederationId("SEN") -> "Senegal",
    FederationId("BAH") -> "Bahamas",
    FederationId("MDV") -> "Maldives",
    FederationId("NRU") -> "Nauru",
    FederationId("TOG") -> "Togo",
    FederationId("FIJ") -> "Fiji",
    FederationId("PLW") -> "Palau",
    FederationId("GUY") -> "Guyana",
    FederationId("LES") -> "Lesotho",
    FederationId("CAY") -> "Cayman Islands",
    FederationId("SOM") -> "Somalia",
    FederationId("SWZ") -> "Eswatini",
    FederationId("TAN") -> "Tanzania",
    FederationId("LCA") -> "Saint Lucia",
    FederationId("ISV") -> "US Virgin Islands",
    FederationId("SLE") -> "Sierra Leone",
    FederationId("BER") -> "Bermuda",
    FederationId("SMR") -> "San Marino",
    FederationId("BDI") -> "Burundi",
    FederationId("QAT") -> "Qatar",
    FederationId("ETH") -> "Ethiopia",
    FederationId("DJI") -> "Djibouti",
    FederationId("SEY") -> "Seychelles",
    FederationId("PNG") -> "Papua New Guinea",
    FederationId("DMA") -> "Dominica",
    FederationId("STP") -> "Sao Tome and Principe",
    FederationId("MAC") -> "Macau",
    FederationId("CAM") -> "Cambodia",
    FederationId("VIN") -> "Saint Vincent and the Grenadines",
    FederationId("BUR") -> "Burkina Faso",
    FederationId("COM") -> "Comoros Islands",
    FederationId("GAB") -> "Gabon",
    FederationId("RWA") -> "Rwanda",
    FederationId("CMR") -> "Cameroon",
    FederationId("MLI") -> "Mali",
    FederationId("ANT") -> "Antigua and Barbuda",
    FederationId("CHA") -> "Chad",
    FederationId("GAM") -> "Gambia",
    FederationId("COD") -> "Democratic Republic of the Congo",
    FederationId("SKN") -> "Saint Kitts and Nevis",
    FederationId("BHU") -> "Bhutan",
    FederationId("NIG") -> "Niger",
    FederationId("GRN") -> "Grenada",
    FederationId("BIZ") -> "Belize",
    FederationId("CAF") -> "Central African Republic",
    FederationId("ERI") -> "Eritrea",
    FederationId("GEQ") -> "Equatorial Guinea",
    FederationId("IVB") -> "British Virgin Islands",
    FederationId("LAO") -> "Laos",
    FederationId("SOL") -> "Solomon Islands",
    FederationId("TGA") -> "Tonga",
    FederationId("TLS") -> "Timor-Leste",
    FederationId("VAN") -> "Vanuatu"
  )

case class NewFederation(
    id: FederationId,
    name: String
)

case class FederationInfo(
    id: FederationId,
    name: String
)
