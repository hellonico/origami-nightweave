(ns pyjama.games.rag
  (:require [cheshire.core :as json]
            [compojure.core :refer [POST defroutes]]
            [compojure.route :as route]
            [pyjama.core]
            [pyjama.io.readers]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :as response])
  (:import (java.time Instant)))

(def knowledge
  (pyjama.io.readers/extract-text "knowledge/Yuval.epub"))

(def url
  "http://localhost:11434")

(defn handle-chat [request]
  (let [body (slurp (:body request))
        data (json/parse-string body true)
        messages (:messages data)
        last-message (:content (last messages))
        first-message (:content (first messages))
        _ (println "First message: " first-message)
        _ (println "Last message: " last-message)
        model (or (:model data) "llama3.1")
        response-text (pyjama.core/ollama
                        url
                        :generate
                        {:model  model
                         :prompt [knowledge first-message last-message]
                         :stream false
                         :pre    "This is your knowledge: %s\n. This is the first question: %s\n. This is the last comment: %s. Make a statement."}
                        :response
                        )
        _ (println "Response: " response-text)
        response-map {:model      model
                      :created_at (str (Instant/now))
                      :message    {:role "user" :content response-text}
                      :done       true}
        json (json/generate-string response-map)
        ]
    (response/response json)))

(defroutes app-routes
           (POST "/api/chat" request (handle-chat request))
           (route/not-found "Not Found"))

(defn -main []
  (run-jetty app-routes {:port 3100 :join? false}))