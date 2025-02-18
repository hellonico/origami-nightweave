(ns v1
  (:require [clojure.core.async :as async]
            [org.httpkit.server :as http]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.adapter.jetty :refer [run-jetty]]))

(defonce clients (atom #{}))

(defn ws-handler [req]
  (http/with-channel req channel
                     (swap! clients conj channel)
                     (http/on-close channel (fn [_] (swap! clients disj channel)))))


(defn broadcast! [message]
  (doseq [client @clients]
    (http/send! client message)))

(defn app []
  (wrap-resource
    (fn [req]
      (if (= (:uri req) "/ws")
        (ws-handler req)
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (slurp "public/v1.html")}))
    "public"))

(defn thread-with-test-broadcast[]
  (async/thread
    (loop[]
      (broadcast! "hello philosophers")
      (Thread/sleep 1000)
      (recur))))

(defn -main []
  (println "Starting server on http://localhost:3001")
  (thread-with-test-broadcast)
  (http/run-server (app) {:port 3001}))
