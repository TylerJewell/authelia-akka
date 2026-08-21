package io.akka.authelia.domain;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Whether an address falls inside a CIDR block (SPEC-001 §2, question-log row A3). */
final class Cidr {

  private record Block(byte[] network, int prefixBits) {}

  private Cidr() {}

  /** Checks the block is usable, and says which rule it came from if it is not. */
  static void parse(String cidr, int position) {
    if (block(cidr) == null) {
      throw new Rule.MalformedRule("rule " + position + " has a network that is not a CIDR block");
    }
  }

  static boolean contains(String cidr, String address) {
    var block = block(cidr);
    if (block == null) {
      return false;
    }

    var bytes = addressBytes(address);
    if (bytes == null || bytes.length != block.network().length) {
      return false;
    }

    var whole = block.prefixBits() / 8;
    for (var i = 0; i < whole; i++) {
      if (bytes[i] != block.network()[i]) {
        return false;
      }
    }

    var remainder = block.prefixBits() % 8;
    if (remainder == 0) {
      return true;
    }

    var mask = (byte) (0xFF << (8 - remainder));
    return (bytes[whole] & mask) == (block.network()[whole] & mask);
  }

  private static Block block(String cidr) {
    var slash = cidr.indexOf('/');
    if (slash < 0) {
      return null;
    }
    var network = addressBytes(cidr.substring(0, slash));
    if (network == null) {
      return null;
    }
    try {
      var bits = Integer.parseInt(cidr.substring(slash + 1));
      return bits < 0 || bits > network.length * 8 ? null : new Block(network, bits);
    } catch (NumberFormatException notANumber) {
      return null;
    }
  }

  /**
   * Parses a literal address and nothing else. {@code InetAddress.getByName} would resolve
   * anything it did not recognise as an address, putting a name lookup inside an
   * authorization decision; a rule and a caller both supply addresses, so nothing here
   * needs resolving.
   */
  private static byte[] addressBytes(String literal) {
    if (literal.indexOf(':') >= 0) {
      try {
        return InetAddress.getByName(literal).getAddress();
      } catch (UnknownHostException | SecurityException notAnAddress) {
        return null;
      }
    }

    var parts = literal.split("\\.", -1);
    if (parts.length != 4) {
      return null;
    }

    var bytes = new byte[4];
    for (var i = 0; i < 4; i++) {
      try {
        var octet = Integer.parseInt(parts[i]);
        if (octet < 0 || octet > 255) {
          return null;
        }
        bytes[i] = (byte) octet;
      } catch (NumberFormatException notANumber) {
        return null;
      }
    }
    return bytes;
  }
}
