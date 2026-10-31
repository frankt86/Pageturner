package voice.core.audiobookshelf.login

/** Whether [host] is on the local network or a VPN, where servers often only speak http. */
public fun isLocalHost(host: String): Boolean {
  val lower = host.lowercase().removePrefix("[").removeSuffix("]")
  if (lower == "localhost" || '.' !in lower) return true
  if (listOf(".local", ".lan", ".home", ".internal", ".home.arpa").any { lower.endsWith(it) }) return true
  val octets = lower.split('.').mapNotNull { it.toIntOrNull() }
  if (octets.size != 4) return false
  return octets[0] == 10 ||
    octets[0] == 127 ||
    (octets[0] == 192 && octets[1] == 168) ||
    (octets[0] == 172 && octets[1] in 16..31) ||
    (octets[0] == 100 && octets[1] in 64..127)
}
