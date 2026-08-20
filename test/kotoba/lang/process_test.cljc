(ns kotoba.lang.process-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.process :as proc]
            [kotoba.lang.process-host :as host]))

(deftest validate-spawn-refuses-path-commands
  (is (= :process/path-command
         (proc/validate-spawn ["/bin/echo" "x"] 100 1000 #{"echo"})))
  (is (= :process/not-allowed
         (proc/validate-spawn ["rm" "-rf"] 100 1000 #{"echo"})))
  (is (nil? (proc/validate-spawn ["echo" "hi"] 100 1000 #{"echo"}))))

(deftest echo-process-ok
  (let [p (proc/echo-process #{"echo"})
        r (proc/spawn! p {:argv ["echo" "a" "b"] :timeout-ms 1000
                          :max-stdout-bytes 1000})]
    (is (= :ok (:tag r)))
    (is (= 0 (:exit r)))
    (is (= "a b" (:stdout r)))))

(deftest echo-process-deny
  (let [p (proc/echo-process #{"echo"})
        r (proc/spawn! p {:argv ["curl" "http://x"] :timeout-ms 1000
                          :max-stdout-bytes 1000})]
    (is (= :error (:tag r)))
    (is (= :process/not-allowed (:code r)))))

#?(:clj
   (deftest os-spawn-echo-on-jvm
     (let [echo (let [c (java.io.File. "/bin/echo")]
                  (when (.isFile c) (.getAbsolutePath c)))
           _ (is (string? echo) "need /bin/echo for this host test")
           p (host/os-spawn {:binaries {"echo" echo}})
           r (proc/spawn! p {:argv ["echo" "itonami"]
                             :timeout-ms 5000
                             :max-stdout-bytes 4096})]
       (is (= :ok (:tag r)))
       (is (= 0 (:exit r)))
       (is (str/starts-with? (str/trim (:stdout r)) "itonami")))))

#?(:cljs
   (deftest os-spawn-echo-on-node
     (let [echo "/bin/echo"
           p (host/os-spawn {:binaries {"echo" echo}})
           r (proc/spawn! p {:argv ["echo" "itonami"]
                             :timeout-ms 5000
                             :max-stdout-bytes 4096})]
       (is (= :ok (:tag r)))
       (is (= 0 (:exit r)))
       (is (str/starts-with?
            (str/trim (:stdout r)) "itonami")))))
