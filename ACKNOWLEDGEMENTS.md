# Acknowledgements

This project is a port of **[authelia/authelia](https://github.com/authelia/authelia)**.

## Licence and copyright

authelia/authelia is under the **Apache License 2.0**, copyright held by the Authelia
contributors. Read from the repository's own `LICENSE` file at commit `01fa1df8`, not from a
badge. A copy of that licence ships beside this port's code as `LICENSE-authelia`.

## What was copied

**No source file was copied, in whole or in part.** The port is Java; the original is Go, and
nothing was translated line by line.

Two things were read closely and are reflected in this port's structure, which is what makes
it a derived work regardless of no text being copied:

- The decision procedure in `internal/authorization` — the order rules are read in, what each
  criterion means, how a policy name maps to a tier, and how a request with no identity is
  matched. `specs/SPEC-001-authelia.md` states each of these as a rule and cites the evidence.
- The names of things a caller writes: `bypass`, `one_factor`, `two_factor`, `deny`, the
  `user:` / `group:` / `oauth2:client:` prefixes, the `{user}` and `{group}` domain
  placeholders, the six query operators, and the `User` and `Group` capture-group names. These
  are the port's wire format as well, because a rule set that has to be rewritten to move
  between the two would make the comparison in `bench/REPORT.md` meaningless.

`probes/oracle/` is a program of this port's own that *links against* authelia/authelia as a
Go module in order to run it. It contains no Authelia source; the module is fetched from
upstream and is not redistributed here.

## What licence this forces

Apache-2.0 conditions apply to this port as a derived work: the licence and the attribution
above travel with it, and any modified files carry a notice. That is satisfied by
`LICENSE-authelia` and by this file.

## Also used

- Akka, and the Akka Java SDK
