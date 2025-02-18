(ns htmls.v4
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [org.httpkit.server :as http]
            [pyjama.games.philosophersv4 :as v4]
            [pyjama.utils]
            [ring.middleware.file :refer [wrap-file]]
            [ring.util.response :refer [content-type response]]
            [ring.util.response :refer [response]])
  (:import (clojure.lang Atom)))

(defonce clients (atom #{}))

(defn ws-handler [req]
  (http/with-channel req channel
                     (swap! clients conj channel)
                     (http/on-close channel (fn [_] (swap! clients disj channel)))))

(defn broadcast! [message]
  (let [json-msg (json/generate-string message)]
    (doseq [client @clients]
      (http/send! client json-msg))))


(def app-state
  (atom {
         :battle-message
         "This is a conversation battle. Everyone should chat, with simple, very very short, witty answers.
                              May the most intelligent win. "
         :chatting false}))

(defn load-people []
  (let [personality-file (io/resource "personalitiesv3.csv")
        personalities (v4/random-personalities personality-file)
        people (v4/load-states personalities)
        ]
    (swap! app-state assoc :people people)))

(defn thread-with-speakers [question]
  (async/thread
    (swap! app-state assoc
           :messages []
           :current-question question
           :chatting true)
    (v4/chat-simulation
      app-state
      ##Inf
      question
      broadcast!)))

(defn handle-question [req]
  (let [body (slurp (:body req))
        json (json/parse-string body true)
        question (:question json)]
    (println "Received question:" question)
    (spit "questions.log" (str question "\n") :append true)
    (thread-with-speakers question)
    (response (json/generate-string {:answer question}))))


(defn deep-deref [x]
  (cond
    (instance? Atom x) (deep-deref @x)
    (map? x) (into {} (map (fn [[k v]] [k (deep-deref v)]) x))
    (coll? x) (into (empty x) (map deep-deref x))
    :else x))

(defn handle-state [req]
  (-> (response (json/generate-string (deep-deref app-state)))
      (content-type "application/json")))

(defn handle-summary [req]
  (let [body (slurp (:body req))
        json (json/parse-string body true)
        question (or (:question json) "Make a five point summary of the conversation.")
        summary
        {:url      (or (System/getenv "OLLAMA_URL") "http://localhost:11434")
         :model    "tinydolphin"
         :options  {:temperature 0.9}
         :stream   false
         :messages (reverse (conj (:messages @app-state)
                                  {:role :user :content question}))}
        ]
    (->
      (pyjama.core/ollama (:url summary) :chat (dissoc summary :url) identity)
      (json/generate-string)
      (response)
      (content-type "application/json"))
    )
  )

(defn handle-questions [req]
  (->
    "questions.log"
    pyjama.utils/load-lines-of-file
    set
    reverse
    json/generate-string
    response
    (content-type "application/json")
  ))

(defn app []
  (-> (fn [req]
        (cond
          (= (:uri req) "/ws") (ws-handler req)
          (= (:uri req) "/state") (handle-state req)
          (= (:uri req) "/ask") {:status  200
                                 :headers {"Content-Type" "text/html"}
                                 :body    (slurp "public/v4/ask.html")}
          (= (:uri req) "/human") {:status  200
                                   :headers {"Content-Type" "text/html"}
                                   :body    (slurp "public/v4/human.html")}
          (= (:uri req) "/summary") (handle-summary req)
          (= (:uri req) "/questions") (handle-questions req)
          (= (:uri req) "/question") (handle-question req)  ; New route to handle the POST request
          :else (do
                  (swap! app-state assoc :chatting false)
                  {:status  200
                   :headers {"Content-Type" "text/html"}
                   :body    (slurp "public/v4/index.html")}
                  )))
      (wrap-file "public")))

(defn -main []
  (println "Starting server on http://localhost:3001")
  (load-people)
  (http/run-server (app) {:port 3001}))
