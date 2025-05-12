(ns ai-agents.core
  (:require [clojure.core.async :as async]
            [pyjama.core :as ollama]))

(defn create-agent
  "Creates an AI agent with a unique ID and a processing function."
  [id model]
  (let [input-chan (async/chan)]
    {:id         id
     :model      model
     :input-chan input-chan
     :process    (fn [message]
                   (ollama/ollama
                     "http://localhost:11434"
                     :generate
                     {:stream false
                      :model  model
                      :prompt message}
                     :response))}))

(defn run-agent
  "Runs an agent, listening for messages on its input channel."
  [{:keys [id input-chan process]}]
  (async/go-loop []
    (when-let [message (async/<! input-chan)]
      (println (str "Agent " id " received: " message))
      (let [response (process message)]
        (println (str "Agent " id " response: " response))))
    (recur)))

(defn send-message
  "Sends a message to a target agent."
  [target-agent message]
  (async/put! (:input-chan target-agent) message))

(defn message-router
  "Routes messages between agents."
  [agents message-map]
  (let [{:keys [from to message]} message-map]
    (if-let [target-agent (some #(when (= (:id %) to) %) agents)]
      (send-message target-agent message)
      (println (str "Agent " to " not found")))))

(defn test-agents-simple []
  (let [agent1 (create-agent "Agent1" "tinyllama")
        agent2 (create-agent "Agent2" "tinydolphin")]
    (run-agent agent1)
    (run-agent agent2)
    (send-message agent1 "Hello from Agent2")
    (send-message agent2 "Replying to Agent1")
    ))

(defn test-agents-with-router []
  (let [agent1 (create-agent "Agent1" "tinyllama")
        agent2 (create-agent "Agent2" "tinydolphin")
        agents [agent1 agent2]
        ]
    (run-agent agent1)
    (run-agent agent2)

    (message-router agents {:from "Agent1" :to "Agent2" :message "How are you?"})
    ))

(defn -main []

  ;(test-agents-simple)
  (test-agents-with-router)

  (Thread/sleep 10000)

  )