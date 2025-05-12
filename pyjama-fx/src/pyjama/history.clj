(ns pyjama.history
  (:require
    [clojure.java.io :as io]
    [pyjama.config :as c]))

(defn read-history [app-name history-file-path]
  (let [path (c/get-config-file app-name history-file-path)]
    (if (.exists path)
      (with-open [reader (io/reader path)]
        (doall (line-seq reader)))
      [])))

(defn append-to-history [app-name history-file-path file-path]
  (let [history (set (read-history app-name history-file-path))]
    (when-not (history file-path)
      (spit (c/get-config-file app-name history-file-path)
            (str file-path "\n") :append true))))