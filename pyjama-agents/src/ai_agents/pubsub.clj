(ns ai-agents.pubsub
  (:require [clojure.core.async :as async]))

(defn create-broker
  "Creates a message broker for managing topics and subscriptions."
  []
  (let [topics (atom {})]                                   ;; Map of topic -> set of subscribers
    {:topics      topics
     :subscribe   (fn [topic subscriber-chan]
                    (swap! topics update topic #(conj (or % #{}) subscriber-chan)))
     :unsubscribe (fn [topic subscriber-chan]
                    (swap! topics update topic #(disj (or % #{}) subscriber-chan)))
     :publish     (fn [topic message]
                    (doseq [subscriber (get @topics topic)]
                      (async/put! subscriber message)))}))

(defn create-agent
  "Creates an agent with an ID and input channel."
  [id]
  (let [input-chan (async/chan)]
    {:id         id
     :input-chan input-chan
     :process    (fn [message]
                   (println (str "Agent " id " processing: " message)))}))

(defn run-agent
  "Runs an agent, listening for messages on its input channel."
  [{:keys [id input-chan process]}]
  (async/go-loop []
    (when-let [message (async/<! input-chan)]
      (process message))
    (recur)))

(defn filtered-subscribe
  [broker topic subscriber-chan filter-fn]
  (async/go-loop []
    (when-let [message (async/<! subscriber-chan)]
      (when (filter-fn message)
        (println (str "Filtered message for topic: " topic " -> " message))))
    (recur)))

(defn -main []
  (let [broker (create-broker)
        agent1 (create-agent "Agent1")
        agent2 (create-agent "Agent2")
        agent3 (create-agent "Agent3")]

    ;; Run agents
    (run-agent agent1)
    (run-agent agent2)
    (run-agent agent3)

    ;; Subscribe agents to topics
    ((:subscribe broker) "topic1" (:input-chan agent1))
    ((:subscribe broker) "topic1" (:input-chan agent2))
    ((:subscribe broker) "topic2" (:input-chan agent3))

    (filtered-subscribe broker "topic1" (:input-chan agent1) #(clojure.string/includes? % "important"))

    ;; Publish messages to topics
    ((:publish broker) "topic1" "Message for topic1 subscribers")
    ((:publish broker) "topic1" "important message")
    ;((:publish broker) "topic2" "Message for topic2 subscribers")


  (Thread/sleep 10000)
  ))