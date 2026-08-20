(ns kotoba.lang.process
  "Process spawn policy + host-injected IProcess. Twin of kotoba.lang.fs.

  Pure validation never touches the OS. Effectful spawn lives behind IProcess,
  which a host implements (see kotoba.lang.process-host). An echo-process is
  provided for tests.

  Zero third-party runtime deps; .cljc."
  (:require [clojure.string :as str]))

(def max-argv 64)
(def max-arg-bytes 4096)
(def max-stdout-bytes 65536)
(def max-timeout-ms 600000)

(def error-types
  "Stable `:type` / validate-spawn keywords. Callers branch on these, never on
  host exception class names."
  #{:process/argv-type :process/empty-argv :process/argv-too-long
    :process/bad-arg :process/path-command :process/not-allowed
    :process/bad-max-stdout :process/bad-timeout
    :process/no-binary :process/timeout :process/spawn})

(defn validate-spawn
  "Pure spawn policy. Returns nil when ok, else an error keyword.

  `argv` is a sequential of strings. `allowed` is a set of permitted basenames
  for argv[0], or nil to skip the allowlist (tests only — production hosts
  must pass a set)."
  ([argv max-out timeout] (validate-spawn argv max-out timeout nil))
  ([argv max-out timeout allowed]
   (cond
     (not (sequential? argv)) :process/argv-type
     (empty? argv) :process/empty-argv
     (> (count argv) max-argv) :process/argv-too-long
     (some #(or (not (string? %)) (str/blank? %)
                (> (count %) max-arg-bytes)) argv)
     :process/bad-arg
     (str/includes? (str (first argv)) "/") :process/path-command
     (str/includes? (str (first argv)) "\\") :process/path-command
     (and allowed (not (contains? allowed (first argv)))) :process/not-allowed
     (not (and (integer? max-out) (pos? max-out) (<= max-out max-stdout-bytes)))
     :process/bad-max-stdout
     (not (and (integer? timeout) (pos? timeout) (<= timeout max-timeout-ms)))
     :process/bad-timeout
     :else nil)))

(defprotocol IProcess
  (spawn! [proc request]
    "Run `request` `{:argv :max-stdout-bytes :timeout-ms}`.
     Returns `{:tag :ok :exit :stdout :stderr}` or
     `{:tag :error :code :message}`."))

(defn echo-process
  "Test double: exit 0, stdout = space-joined argv rest, stderr empty.
  Still runs validate-spawn when `:allowed` is set on the handle."
  ([] (echo-process nil))
  ([allowed]
   (reify IProcess
     (spawn! [_ {:keys [argv max-stdout-bytes timeout-ms]
                 :or {max-stdout-bytes 65536
                      timeout-ms 5000}}]
       (if-let [err (validate-spawn argv max-stdout-bytes timeout-ms allowed)]
         {:tag :error :code err :message (name err)}
         {:tag :ok
          :exit 0
          :stdout (str/join " " (rest argv))
          :stderr ""})))))
