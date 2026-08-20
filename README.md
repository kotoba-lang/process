# kotoba-lang/process

**Foundational process spawn for Kotoba** — pure argv/timeout/allowlist policy
plus a host-injected spawn transport. Twin of [`kotoba-lang/fs`](https://github.com/kotoba-lang/fs):
the cell never sees ambient PATH or a shell; it only sees a transport the host
granted. Zero third-party runtime deps; `.cljc` (JVM / SCI / ClojureScript / nbb).

Catalog identity: `:process/spawn` (compiler wire id **20**, ADR-t83).

## Why a protocol, not `ProcessBuilder`

Ambient `ProcessBuilder` / `child_process` is the same class of authority as
ambient `java.nio.file`. This library splits:

- **Pure policy** (`validate-spawn`, basename allowlist) — no OS
- **Transport** (`IProcess` / `os-spawn`) — host injects absolute binaries only

There is **no default PATH scan**. The host must pass
`:binaries {"git" "/usr/bin/git", …}`. Basename validation refuses path
separators in argv[0]; the transport only runs mapped absolute paths.

## Surface

`kotoba.lang.process`:

- `validate-spawn` — pure; returns `nil` or an error keyword
- `IProcess` protocol — `(spawn! proc request)`
- `echo-process` — test double (exit 0, stdout = joined argv rest)
- bounds: `max-argv`, `max-arg-bytes`, `max-stdout-bytes`, `max-timeout-ms`

`kotoba.lang.process-host` (separate namespace — keeps `process` free of OS):

- `os-spawn` — `#?(:clj ProcessBuilder, :cljs child_process.spawnSync)`
- `resolve-binary`, `absolute-path?`

```clojure
(require '[kotoba.lang.process :as proc]
         '[kotoba.lang.process-host :as host])

(def p (host/os-spawn {:binaries {"echo" "/bin/echo" "git" "/usr/bin/git"}}))
(proc/spawn! p {:argv ["echo" "hi"] :timeout-ms 5000 :max-stdout-bytes 65536})
;; => {:tag :ok :exit 0 :stdout "hi\n" :stderr ""}
```

## Relation to `provider.process`

`kotoba-lang/provider` owns the **typed kit** (`provider.process` +
`provider.process-transport`) that amu/kototama bind as capability id 20.
This repo is the **foundational stdlib** the same way `fs` sits under
`provider.scoped-fs`: apps that only need spawn without KIR value codecs
depend here. The policy vocabulary (`:process/not-allowed`, …) is kept aligned.

## Install

```clojure
io.github.kotoba-lang/process {:git/sha "<sha>"}
```
