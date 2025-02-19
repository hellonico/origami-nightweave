(ns pyjama.games.handlers.core
  (:require [cheshire.core :as json]
            [compojure.core :refer [POST defroutes]]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :as response]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.tools.cli :refer [parse-opts]])
  (:import (java.time Instant)))

(def cli-options
  [["-h" "--handler HANDLER" "Handler function name" :default "default-handler"]
   ["-p" "--port PORT" "Port number" :parse-fn #(Integer. %) :default 3000]])

(defn get-handler-fn [handler-path]
  (let [[ns-name fn-name] (clojure.string/split handler-path #"/")]
    (let [handler-ns (symbol ns-name)]
      (require handler-ns)
      (ns-resolve handler-ns (symbol fn-name)))))

(defn handle-request [handle-messages request]
  (let [body (slurp (:body request))
        data (json/parse-string body true)]
    (-> data
        handle-messages
        (assoc :created_at (str (Instant/now)) :done true)
        json/generate-string
        response/response)))

(defn create-app [handle-messages]
  (defroutes app-routes
             (POST "/api/chat" request (handle-request handle-messages request))
             (route/not-found "Not Found"))
  app-routes)

(defn -main [& args]
  (let [{:keys [options]} (clojure.tools.cli/parse-opts args cli-options)
        handle-messages (get-handler-fn (:handler options))]
    (run-jetty (create-app handle-messages) {:port (:port options) :join? false})))
