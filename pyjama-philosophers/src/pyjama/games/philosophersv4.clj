(ns pyjama.games.philosophersv4
  (:gen-class)
  (:require
    [clojure.data.csv :as csv]
    [clojure.java.io :as io]
    [clojure.pprint]
    [clojure.tools.cli]
    [pyjama.state]))

(defn read-personalities [file]
  (with-open [reader (io/reader file)]
    (doall (rest (csv/read-csv reader)))))

(defn random-personalities [file]
  (let [all-personalities (read-personalities file)]
    (shuffle all-personalities)))

(defn load-states [personalities]
  (mapv (fn [[name system url model temperature avatar]]
          (atom {:name     name
                 :url      (if (empty? url)
                             (or (System/getenv "OLLAMA_URL") "http://localhost:11434")
                             url)
                 :model    (or model "tinyllama")
                 :avatar   (or avatar (str "/images/" name ".png"))
                 ;:options  {:temperature (or temperature 0.9)}
                 :system   system
                 :stream   false
                 :messages []}))
        personalities))

(defn chat-simulation [app-state turns original-question broadcast-fn]
  (let [states (:people @app-state)]

    (doseq [state states]
      (swap! state assoc
             :messages [{:role :system :content
                         (str (:battle-message @app-state)
                              original-question)}]))

    (loop [remaining-turns turns
           last-speaker-idx nil]

      (when (and (pos? remaining-turns) (:chatting @app-state))
        (let [available-indices (remove #(= % last-speaker-idx) (range (count states)))
              speaker-idx (nth available-indices (rand-int (count available-indices)))
              speaker-atom (nth states speaker-idx)
              speaker (:name @speaker-atom)
              ]

          (println speaker " is talking...")
          (pyjama.state/handle-chat speaker-atom)

          ; wait for input
          (while (:processing @speaker-atom)
            (Thread/sleep 500))

          (let [last-response (last (:messages @speaker-atom))

                formatted-response (assoc last-response
                                     :role :user
                                     :content (str (:name @speaker-atom) "> " (:content last-response)))]

            (swap! app-state update :messages conj
                   {:name speaker :role :assistant :content (:content last-response)})

            (if (:chatting @app-state)
              (do
                (broadcast-fn {:image (:avatar @speaker-atom) :name speaker :text (:content last-response)})

                (Thread/sleep ^long (:lag @app-state))

                ; TODO: delete? but maybe if a new member joins in, we dont want them to read all the messages
                (doseq [state states]
                  (when (not= state speaker-atom)
                    (swap! state update :messages conj formatted-response)))

                ))

            (recur (dec remaining-turns) speaker-idx)))))

    (broadcast-fn {:image "/images/end.png"
                   :name  ""
                   :text  "This battle has finished"})

    ))