(ns htmls.v3
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [org.httpkit.server :as http]
            [pyjama.games.philosophersv3 :as v3]
            [ring.middleware.file :refer [wrap-file]]
            [ring.util.response :refer [response]]))

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
  (atom {:chatting false}))

(defn thread-with-speakers [question]
  (async/thread
    (swap! app-state assoc :chatting true)
    (v3/chat-simulation
      app-state
      (io/resource "personalitiesv3.csv")
      ##Inf
      question
      broadcast!)
    ))

(defn handle-question [req]
  (let [body (slurp (:body req))
        json (json/parse-string body true)
        question (:question json)
        ]
    (println "Received question:" question)
    (thread-with-speakers question)
    (response (json/generate-string {:answer question}))))  ; Respond with JSON


(defn app []
  (-> (fn [req]
        (cond
          (= (:uri req) "/ws") (ws-handler req)
          (= (:uri req) "/question") (handle-question req)  ; New route to handle the POST request
          :else (do
                  (swap! app-state assoc :chatting false)
                  {:status  200
                 :headers {"Content-Type" "text/html"}
                 :body    (slurp "public/v3.html")}
                  )))
      (wrap-file "public")))

(defn -main []
  (println "Starting server on http://localhost:3001")
  (http/run-server (app) {:port 3001}))
