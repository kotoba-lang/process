(ns kotoba.lang.process-host
  "Real OS spawn for kotoba.lang.process. Separate namespace so requiring
  `kotoba.lang.process` pulls in no ProcessBuilder / child_process.

  Twin of kotoba.lang.fs-host. No ambient PATH: `:binaries` map required.
  `#?(:clj ProcessBuilder, :cljs child_process.spawnSync)`."
  (:require [clojure.string :as str]
            [kotoba.lang.process :as process])
  #?(:clj
     (:import (java.io ByteArrayOutputStream InputStream)
              (java.nio.charset StandardCharsets)
              (java.util.concurrent TimeUnit))))

(defn resolve-binary
  "Look up basename in host `:binaries`. Pure map lookup — never PATH."
  [binaries basename]
  (when (and (map? binaries) (string? basename))
    (let [p (get binaries basename)]
      (when (and (string? p) (not (str/blank? p)))
        p))))

(defn absolute-path?
  [p]
  (and (string? p)
       (not (str/blank? p))
       #?(:clj (.isAbsolute (java.io.File. ^String p))
          :cljs (try
                  (.isAbsolute (js/require "path") p)
                  (catch :default _ false)))))

(defn- truncate-utf8 [s max-bytes]
  (let [s (str s)]
    #?(:clj
       (let [bytes (.getBytes s StandardCharsets/UTF_8)]
         (if (<= (alength bytes) (long max-bytes))
           s
           (String. bytes 0 (int max-bytes) StandardCharsets/UTF_8)))
       :cljs
       (let [buf (.from js/Buffer s "utf8")
             n (long max-bytes)]
         (if (<= (.-length buf) n)
           s
           (.toString (.slice buf 0 n) "utf8"))))))

(defn- validate-binaries! [binaries]
  (when-not (and (map? binaries) (seq binaries)
                 (every? string? (keys binaries))
                 (every? string? (vals binaries)))
    (throw (ex-info "process-host requires non-empty :binaries map"
                    {:type :process/spawn :phase :process-host})))
  (doseq [[_ p] binaries]
    (when-not (absolute-path? p)
      (throw (ex-info "process-host binary paths must be absolute"
                      {:type :process/spawn :phase :process-host :path p})))))

#?(:clj
   (defn- read-bounded
     [^InputStream in max-bytes]
     (let [buf (byte-array 4096)
           out (ByteArrayOutputStream.)]
       (loop [total 0]
         (let [n (.read in buf)]
           (cond
             (neg? n) (.toString out StandardCharsets/UTF_8)
             (>= total max-bytes) (.toString out StandardCharsets/UTF_8)
             :else
             (let [take (min n (- max-bytes total))]
               (.write out buf 0 take)
               (recur (+ total take)))))))))

(defn os-spawn
  "Build an IProcess backed by the OS.

  opts:
    :binaries   required {basename absolute-path}
    :allowed    optional set of basenames (defaults to keys of :binaries)
    :spawn-sync optional cljs test double (bin args-js opts-js) -> result-js"
  [{:keys [binaries allowed spawn-sync] :as opts}]
  (validate-binaries! binaries)
  (let [allowed (or allowed (set (keys binaries)))]
    (reify process/IProcess
      (spawn! [_ {:keys [argv max-stdout-bytes timeout-ms]
                  :or {max-stdout-bytes 65536
                       timeout-ms 5000}}]
        (if-let [err (process/validate-spawn argv max-stdout-bytes timeout-ms allowed)]
          {:tag :error :code err :message (name err)}
          (let [cmd (first argv)
                bin (resolve-binary binaries cmd)
                max-out (long max-stdout-bytes)
                t-ms (long timeout-ms)]
            (cond
              (nil? bin)
              {:tag :error
               :code :process/no-binary
               :message (str "no host binary for " cmd)}

              :else
              #?(:clj
                 (try
                   (let [pb (doto (ProcessBuilder. ^java.util.List
                                                   (vec (cons bin (rest argv))))
                              (.redirectErrorStream false))
                         proc (.start pb)
                         finished (.waitFor proc t-ms TimeUnit/MILLISECONDS)]
                     (if-not finished
                       (do
                         (.destroyForcibly proc)
                         {:tag :error
                          :code :process/timeout
                          :message (str "timeout after " t-ms "ms")})
                       (let [out (read-bounded (.getInputStream proc) max-out)
                             err (read-bounded (.getErrorStream proc) max-out)
                             exit (.exitValue proc)]
                         {:tag :ok
                          :exit (long exit)
                          :stdout (str out)
                          :stderr (str err)})))
                   (catch Exception e
                     {:tag :error
                      :code :process/spawn
                      :message (or (.getMessage e) "spawn failed")}))
                 :cljs
                 (try
                   (let [spawn-sync
                         (or spawn-sync
                             (fn [b a o]
                               (.spawnSync (js/require "child_process") b a o)))
                         args (clj->js (vec (rest argv)))
                         result (spawn-sync bin args
                                            #js {:encoding "utf8"
                                                 :timeout t-ms
                                                 :maxBuffer max-out
                                                 :shell false
                                                 :windowsHide true})
                         err (.-error result)]
                     (if err
                       (let [code (.-code err)
                             msg (or (.-message err) "spawn failed")]
                         (if (or (= code "ETIMEDOUT")
                                 (str/includes? (str msg) "TIMEDOUT")
                                 (str/includes? (str msg) "timeout"))
                           {:tag :error
                            :code :process/timeout
                            :message (str "timeout after " t-ms "ms")}
                           {:tag :error
                            :code :process/spawn
                            :message (str msg)}))
                       {:tag :ok
                        :exit (long (or (.-status result) 1))
                        :stdout (truncate-utf8 (or (.-stdout result) "") max-out)
                        :stderr (truncate-utf8 (or (.-stderr result) "") max-out)}))
                   (catch :default e
                     {:tag :error
                      :code :process/spawn
                      :message (or (.-message e) "spawn failed")}))))))))))
