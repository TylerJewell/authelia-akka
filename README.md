# authelia-akka

Given a list of access rules, a person or program making a request, and the address being
requested, this says how far that requester has to prove who they are before the request is
allowed.

A port of [authelia/authelia](https://github.com/authelia/authelia) onto **Akka**, built with
**Akka Specify**.

---

## Where it came from

Authelia is a sign-in and access server that sits in front of other web applications and
decides who may reach them. It was ported to derive a specification format precise enough to
regenerate a system on a different stack — the port is the vehicle, the specification is the
deliverable.

The part rebuilt here is the piece that reads the list of rules and picks one. It is not the
sign-in page, and it does not check anyone's password: it answers the earlier question of
what proving yourself would have to amount to for this particular request.

The specifications the port was generated from are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness) under
`authelia-port/`.

---

## authelia/authelia → this port

📉 668 Go lines → **543 Java lines**<br>
📁 10 files → **10 files**<br>
⚡ 4,644 nanoseconds → **666** nanoseconds per decision, over 60 rules<br>
⚡ 668 nanoseconds → **666** nanoseconds per decision, over 60 rules, with the original's rule-by-rule logging left out<br>
🎯 92 of 92 answers matched → **92 of 92**<br>
🔀 24 of 24 rule orders matched → **24 of 24**<br>
🧪 not measured → **50 tests**

Full method and the numbers that did *not* make this list:
[`bench/REPORT.md`](https://github.com/TylerJewell/akka-specify-harness/blob/main/authelia-port/bench/REPORT.md).

---

## What it took to build

⏱️ **0.9 hours** from the first command to the published repository, **0.9** of them active<br>
💬 **257** exchanges with the model<br>
✍️ **229,686** tokens written by the model, **45,906,531** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **50** tests

```bash
python toolkit/tokens.py --port authelia    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/akka-specify-harness/tree/main/port-log).

---

## What it does

From the specification:

- **Rules are read in the order they were written, and the first one that fits decides.**
  Nothing after it is looked at, so moving a rule up or down changes the answer.
- **A rule fits only when all eight of its conditions fit.** Address, path, query, request
  type, network, and who is asking — a condition left blank fits everything.
- **Within one condition, any one entry is enough.** Two addresses on the same rule means
  either address; two named groups inside one entry means both at once.
- **A requester who has not said who they are matches a rule that names people.** The rule
  applies, its answer is given, and the answer is marked as one that may change once the
  requester is known.
- **If no rule fits, the list's own fallback answer is given**, and that answer is never
  marked as changeable.
- **A word that is not one of the four known answers means refuse.** A blank one does too.

---

## Design decisions

**Rules kept in the order they arrived.** The whole answer depends on which rule is read
first, so the list has to remember the order it was written in and never quietly reorder it.
Adding a rule always puts it at the end, and it gets the next number in the sequence.

**One list, one queue.** Adding a rule and asking a question both go to the same place, and
that place does one thing at a time. A question never sees a half-added rule.

**Asking never writes anything down.** Working out an answer only reads the list, so a
million questions leave the list exactly as it was and can be answered anywhere a copy of it
lives.

**Patterns worked out when the rule is written, not when it is read.** Turning the text of a
pattern into something matchable takes real time, and rules are written far less often than
they are read. Doing it once when the rule arrives means a rule with an unusable pattern is
refused on the spot instead of failing later in the middle of somebody's request.

**An explanation that names the rule that actually answered.** Asking why an answer came out
that way returns the number of the rule the answer came from, and shows separately, for every
rule, whether it would still have fitted had the requester said who they are.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/authelia-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9036.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9036**.

### Ask it something

```bash
# The fallback answer, for requests no rule covers
curl -X POST localhost:9036/acl/demo/default \
  -H 'content-type: application/json' -d '{"policy":"deny"}'

# Rule 1: two-step sign-in, but only for alice
curl -X POST localhost:9036/acl/demo/rules \
  -H 'content-type: application/json' \
  -d '{"policy":"two_factor","domains":["app.example.com"],"subjects":[["user:alice"]]}'

# Rule 2: everyone else on that address walks straight in
curl -X POST localhost:9036/acl/demo/rules \
  -H 'content-type: application/json' \
  -d '{"policy":"bypass","domains":["app.example.com"]}'

# Someone who has not said who they are
curl -X POST localhost:9036/acl/demo/evaluate \
  -H 'content-type: application/json' \
  -d '{"url":"https://app.example.com/","method":"GET","ip":"10.0.0.1"}'
# {"tier":"TWO_FACTOR","provisional":true,"position":1}

# The same request, from bob
curl -X POST localhost:9036/acl/demo/evaluate \
  -H 'content-type: application/json' \
  -d '{"url":"https://app.example.com/","method":"GET","username":"bob","ip":"10.0.0.1"}'
# {"tier":"BYPASS","provisional":false,"position":2}

# Why
curl -X POST localhost:9036/acl/demo/trace \
  -H 'content-type: application/json' \
  -d '{"url":"https://app.example.com/","method":"GET","ip":"10.0.0.1"}'
```

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| none | — | The service reads no environment variables. Rules arrive over the interface above and are stored; nothing is read from a file. |

The port it listens on is set in `src/main/resources/application.conf`.

---

## Where it differs from authelia/authelia

Everything not listed here behaves the same way on purpose, including the parts that look
like mistakes.

- **Which rule the explanation names.** Authelia answers this twice and the two answers
  disagree: the call that produces the answer stops at the first rule that fits, while the
  call that explains the answer reports that same rule as only a possible fit and names a
  later one as the real fit. Both are offered to callers and nothing in Authelia says which
  is intended. This port keeps the answer exactly as Authelia produces it and makes the
  explanation name that same rule, because an explanation that points at a different rule
  than the answer came from explains nothing; whether each rule would still have fitted had
  the requester been known is shown as its own column on every line instead.
- **What happens when the rules change while a question is being answered.** Authelia builds
  its rule list once when it starts and never changes it, so the situation cannot arise
  there. This port lets rules be added while it runs, so it needed an answer: a question sees
  the list exactly as it stood when the question was accepted, and never a partly-added rule.
- **A condition that cannot be understood.** Authelia drops a query condition whose operator
  it does not recognise, which silently makes the rule apply to more requests than it was
  written for; a pattern that will not compile is caught earlier, when the configuration file
  is checked. This port refuses the whole rule the moment it is written and says which
  condition was at fault, and an unrecognised operator matches nothing rather than being
  dropped, because a rule that quietly widened is harder to notice than one that was
  rejected.
- **How patterns are written.** Both accept regular expressions for addresses, paths and
  query values, but Go and Java do not accept exactly the same ones. Java has no `(?P<name>)`
  form and Go has no lookahead or backreferences, so a pattern using either will work on one
  side and not the other. Patterns that stay within what both accept — which includes the
  `(?<User>...)` and `(?<Group>...)` forms this port and Authelia both give meaning to —
  behave identically; this was checked across every scenario in the comparison.
- **Where rules come from.** Authelia reads them from its configuration file at startup, and
  runs a validator over that file which fills in defaults, resolves named network groups and
  rejects malformed entries. This port takes rules one at a time over its own interface and
  has no configuration file, so none of that validator's behaviour was ported and none of it
  was compared. Named network groups in particular are not supported: a rule states its
  networks directly.
- **How addresses in rules are read.** Authelia's network conditions come from a
  configuration file that has already turned them into addresses. This port reads them
  itself, and accepts only literal addresses — a name that would have to be looked up is
  refused, so that answering an access question can never wait on a network lookup.
- **Rules can only be added.** Authelia's list is replaced wholesale whenever its
  configuration is reloaded. This port appends and never removes, because the behaviour being
  rebuilt is the order rules are read in rather than how they are edited. Replacing a list
  means starting a new one under a different name.
- **Speed under many requests at once.** Not checked. Every measurement in
  `bench/REPORT.md` is one request at a time. This port answers every question about one rule
  list through a single queue, which Authelia does not, so the two may behave differently
  when many requests arrive together.
- **Everything Authelia does after the answer.** Redirecting a visitor to a sign-in page,
  setting a session, and checking a password are not in this port at all, so nothing about
  them is compared.

---

## Licence

authelia/authelia is under the Apache License 2.0, © the Authelia contributors. This port
reimplements the behaviour without copied source and is a derived work of it; see
[`ACKNOWLEDGEMENTS.md`](ACKNOWLEDGEMENTS.md) and `LICENSE-authelia`.
