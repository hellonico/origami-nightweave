(ns htmls.v2
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [org.httpkit.server :as http]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.adapter.jetty :refer [run-jetty]]))

(defonce clients (atom #{}))

(defn ws-handler [req]
  (http/with-channel req channel
                     (swap! clients conj channel)
                     (http/on-close channel (fn [_] (swap! clients disj channel)))))


(defn broadcast! [message]
  (let [json-msg (json/generate-string message)]
    (doseq [client @clients]
      (http/send! client json-msg))))

(defn app []
  (-> (fn [req]
        (if (= (:uri req) "/ws")
          (ws-handler req)
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body (slurp "public/v2.html")}))
      (wrap-file "public"))) ;; Ensures static files like images are served


(def stoic-quotes
  ["We suffer more in imagination than in reality." ; Seneca
   "The best revenge is not to be like your enemy." ; Marcus Aurelius
   "He who fears death will never do anything worth of a man who is alive." ; Seneca
   "Dwell on the beauty of life. Watch the stars, and see yourself running with them." ; Marcus Aurelius
   "Man conquers the world by conquering himself." ; Zeno of Citium
   "No man is free who is not master of himself." ; Epictetus
   "Don’t explain your philosophy. Embody it." ; Epictetus
   "If it is not right, do not do it; if it is not true, do not say it." ; Marcus Aurelius
   "He who has a why to live can bear almost any how." ; Nietzsche (not Stoic, but relevant)
   "Difficulties strengthen the mind, as labor does the body." ; Seneca
   "Happiness and freedom begin with a clear understanding of one principle: Some things are within our control, and some things are not." ; Epictetus
   "The whole future lies in uncertainty: live immediately." ; Seneca
   "Wealth consists not in having great possessions, but in having few wants." ; Epictetus
   "It is not death that a man should fear, but he should fear never beginning to live." ; Marcus Aurelius
   ])
(def names ["confucius" "rene" "socrates" "seneca" "friedrich" "epictetus" "zeno"])

(defn thread-with-test-broadcast[]
  (async/thread
    (loop[]
      (let [speaker (rand-nth names)]

      ;(broadcast! "hello philosophers")
      (broadcast! {:name speaker :text (rand-nth stoic-quotes) :image (str "/images/" speaker ".png")})
      (Thread/sleep 5000)
      (recur)))))

(defn -main []
  (println "Starting server on http://localhost:3001")
  (thread-with-test-broadcast)
  (http/run-server (app) {:port 3001}))
