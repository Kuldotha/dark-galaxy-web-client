package app

/** Display names are a UI concern — the packets carry indices; both clients can name star N
 *  identically from the same catalogue. */
fun starName(index: Int): String {
    val base = STAR_NAMES[index % STAR_NAMES.size]
    val round = index / STAR_NAMES.size
    return if (round == 0) base else "$base $round"
}

fun carrierName(slot: Int): String {
    val base = CARRIER_NAMES[slot % CARRIER_NAMES.size]
    val round = slot / CARRIER_NAMES.size
    return if (round == 0) base else "$base ${round + 1}"
}

private val CARRIER_NAMES = listOf("Lance", "Specter", "Aurora", "Phantom", "Vesper", "Crest", "Nimbus", "Talon")

private val STAR_NAMES = listOf(
    "Sol", "Nyx", "Vega", "Altair", "Rigel", "Deneb", "Auriga", "Lyra",
    "Cygnus", "Draco", "Orion", "Hydra", "Corvus", "Pyxis", "Ara", "Crux",
    "Lupus", "Norma", "Pavo", "Volans", "Mensa", "Fornax", "Caelum", "Dorado",
    "Indus", "Tucana", "Grus", "Phoenix", "Carina", "Vela", "Puppis", "Antlia",
    "Circinus", "Octans", "Apus", "Musca", "Chamaeleon", "Reticulum", "Horologium", "Sculptor",
    "Sirius", "Canopus", "Arcturus", "Capella", "Procyon", "Betelgeuse", "Achernar", "Aldebaran",
    "Antares", "Spica", "Pollux", "Fomalhaut", "Regulus", "Adhara", "Castor", "Bellatrix",
    "Elnath", "Alnilam", "Alnitak", "Alioth", "Dubhe", "Mirfak", "Wezen", "Sargas",
    "Avior", "Alkaid", "Atria", "Alhena", "Mirzam", "Alphard", "Polaris", "Hamal",
    "Algieba", "Diphda", "Mizar", "Nunki", "Menkent", "Mirach", "Alpheratz", "Rasalhague",
    "Kochab", "Saiph", "Denebola", "Algol", "Suhail", "Alphecca", "Mintaka", "Sadr",
    "Eltanin", "Schedar", "Naos", "Almach", "Caph", "Izar", "Dschubba", "Merak",
    "Ankaa", "Enif", "Scheat", "Sabik", "Phecda", "Aludra", "Markab", "Acrab",
    "Gacrux", "Miaplacidus", "Meissa", "Tarazed", "Zaniah", "Zosma", "Chertan", "Vindemiatrix",
    "Heze", "Kraz", "Alkes", "Gienah", "Rastaban", "Thuban", "Edasich", "Sheliak",
    "Helios", "Selene", "Eos", "Boreas", "Zephyrus", "Astraea", "Hyperion", "Theia",
    "Rhea", "Crius", "Iapetus", "Tethys", "Oceanus", "Themis", "Atlas", "Prometheus",
    "Calypso", "Circe", "Elara", "Leda", "Europa", "Ganymede", "Callisto", "Amalthea",
    "Thebe", "Metis", "Adrastea", "Himalia", "Carme", "Ananke", "Sinope", "Pasiphae",
    "Triton", "Nereid", "Proteus", "Larissa", "Galatea", "Despina", "Thalassa", "Naiad",
    "Charon", "Styx", "Kerberos", "Osiris", "Anubis", "Horus", "Seshat", "Sobek",
    "Freya", "Odin", "Baldur", "Heimdall", "Skadi", "Njord", "Sif", "Vali",
)
