(ns ascii.core
  (:require [clj-figlet.core :refer :all])
  )

; http://www.figlet.org/fontdb.cgi

(defn -main [& args]
  (let [flf (load-flf "src/ascii/cybermedium.flf")]
    (println
      (clojure.string/join
        \newline
        (render flf "Skywalker Two")))))