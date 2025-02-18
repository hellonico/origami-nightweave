(ns pyjama.games.philosophersv4
  (:gen-class)
  (:require
    [clojure.pprint]
    [clojure.tools.cli]
    [pyjama.state]))

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
        (let [
              states (:people @app-state)
              available-indices (remove #(= % last-speaker-idx) (range (count states)))
              speaker-idx (nth available-indices (rand-int (count available-indices)))
              speaker-atom (nth states speaker-idx)
              speaker (:name @speaker-atom)
              position (rand-nth [:left :right])
              ]

          (println speaker " is talking...")
          (pyjama.state/handle-chat speaker-atom)

          ; who is speaking ...
          (broadcast-fn {:image (:avatar @speaker-atom) :name speaker :position position :text "..."})

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
                (broadcast-fn {:image (:avatar @speaker-atom) :position position :name speaker :text (:content last-response)})

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