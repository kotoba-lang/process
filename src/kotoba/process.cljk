(ns kotoba.process
  "Assembled from one repo per definition.

  This namespace holds no implementation. It re-exports the definitions
  that each live in their own repo, so a call site can require one name
  and a library can require only the definitions it actually uses.

  AN IMPLEMENTATION BUILT AGAINST kotoba.lang.process IS NOT ACCEPTED HERE.
  kotoba.lang.process still declares IProcess, and a protocol split into its own
  repo is a DIFFERENT protocol from the one the source namespace declares
  (ADR-2609091900). Measured 2026-09-09 on kotoba.lang.fs: a filesystem
  reified against the source protocol answers through the source namespace
  and fails through this one -- No implementation of method: :exists?.
  Build the implementation against the repo that declares the protocol here,
  or call through kotoba.lang.process.

  NOT re-exported here, on purpose: IProcess. A protocol's identity is what extend-type and reify dispatch on,
  and a copy would make an implementation silently extend nothing, so the
  protocol name stays in the one repo that declares it. Requiring that repo
  is a compile error away; a copy would not be.

  Value vars are not re-exported either: error-types, max-arg-bytes, max-argv, max-stdout-bytes, max-timeout-ms. `(def x other/x)` copies, which is harmless for a function and makes
  with-redefs through this namespace a SILENT no-op for a value -- measured
  on kotoba.lang.edn, where three assertions passed against nothing at all.
  Require the repo that defines the value.
"
  (:require [kotoba.process.process :as iprocess-ns]
            [kotoba.process.echo-process :as echo-process-ns]
            [kotoba.process.exec :as exec-ns]
            [kotoba.process.validate-spawn :as validate-spawn-ns]))

(def echo-process "See kotoba.process.echo-process/echo-process." echo-process-ns/echo-process)
(def exec "See kotoba.process.exec/exec." exec-ns/exec)
(def spawn! "See kotoba.process.process/spawn!." iprocess-ns/spawn!)
(def validate-spawn "See kotoba.process.validate-spawn/validate-spawn." validate-spawn-ns/validate-spawn)
