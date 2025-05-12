(ns ai-agents.travelv0
  (:require [clj-http.client :as http]
            [pyjama.agents :as agents]
            [pyjama.core]
            [pyjama.utils]))

(def amadeus-client-id (System/getenv "AMADEUS_KEY"))
(def amadeus-client-secret (System/getenv "AMADEUS_SECRET"))

(def amadeus-auth-url "https://test.api.amadeus.com/v1/security/oauth2/token")
(def amadeus-flight-search-url "https://test.api.amadeus.com/v2/shopping/flight-offers")

(defn create-trip-request-agent
  [id broker config]
  (agents/make-ollama-agent
    id broker config
    (fn [message ollama-helper broker]
      (let [{:keys [from to date]} message]
        (println "Looking for a trip from" from "to" to "on" date)
        (let [response (ollama-helper [from to])]
          (if-let [codes (re-seq #"[A-Z]{3}" response)]
            (let [from-code (first codes) to-code (second codes)]
              (println "Converted Cities to Airport Codes:" from "->" from-code ", " to "->" to-code)
              ((:publish broker) :flight-request {:from from-code :to to-code :date date}))
            (println "Failed to retrieve airport codes for" from "and" to)))))))

(defn get-amadeus-access-token []
  (let [response (http/post amadeus-auth-url
                            {:form-params {:grant_type    "client_credentials"
                                           :client_id     amadeus-client-id
                                           :client_secret amadeus-client-secret}
                             :as          :json})
        body (:body response)]
    (if (= 200 (:status response))
      (:access_token body)
      (throw (Exception. (str "Failed to authenticate with Amadeus API: " (:error_description body)))))))

(defn create-flight-searcher-agent
  [id broker]
  (pyjama.agents/make-simple-agent
    id broker nil
    (fn [message]
      (let [access-token (get-amadeus-access-token)
            {:keys [from to date]} message
            response (http/get amadeus-flight-search-url
                               {:headers      {"Authorization" (str "Bearer " access-token)}
                                :query-params {:originLocationCode      from
                                               :destinationLocationCode to
                                               :departureDate           date
                                               :adults                  1}
                                :as           :json})]
        (if (= 200 (:status response))
          (let [flight-data (:data (:body response))]
            (println "Found " (count flight-data) " matching flights on amadeus.")
            ((:publish broker) :flight-data {:from from :to to :date date :flights flight-data}))
          (println "Failed to fetch flight data:" (:body response)))))))

(defn format-duration [duration]
  (let [hours (re-find #"PT(\d+)H" duration)
        minutes (re-find #"(\d+)M" duration)
        hours (if hours (Integer/parseInt (second hours)) 0)
        minutes (if minutes (Integer/parseInt (second minutes)) 0)]
    (str hours "h" (when (pos? minutes) (str minutes "min")))))

(defn format-flights-as-table [flights]
  (pyjama.utils/to-markdown
    {:rows    (map (fn [flight]
                     (let [segments (get-in flight [:itineraries 0 :segments])
                           last-segment (last segments)]
                       {:flight-id      (:id flight)
                        :carrier        (get-in flight [:itineraries 0 :segments 0 :carrierCode])
                        :price          (str "$" (-> flight :price :total))
                        :duration       (format-duration (get-in flight [:itineraries 0 :duration]))
                        :stops          (dec (count segments))
                        :departure      (get-in segments [0 :departure :iataCode])
                        :departure-time (get-in segments [0 :departure :at])
                        :arrival        (get-in last-segment [:arrival :iataCode])
                        :arrival-time   (get-in last-segment [:arrival :at])}))
                   flights)
     :headers [:flight-id :carrier :price :duration :stops :departure :departure-time :arrival :arrival-time]}))

(defn create-flight-analyzer-agent [id broker config]
  (agents/make-ollama-agent
    id broker config
    (fn [message ollama-helper broker]
      (let [{:keys [flights n]} message
            top-flights (take (or n 2) (sort-by #(read-string (get-in % [:price :total])) flights))
            formatted-table (format-flights-as-table top-flights)
            _ (println formatted-table)
            response (ollama-helper [(or n 2) formatted-table])]
        (println "Flight Analysis Recommendation:\n" response)))))

(defn setup-system! [broker]
  (let [trip-request-agent (create-trip-request-agent "trip-request"
                                                      broker
                                                      {:url   "http://localhost:11434"
                                                       :model "llama3.2"
                                                       :pre   "What are the airport code names for the main airports in %s and %s? Just answer the 3-letter names."
                                                       })
        flight-searcher (create-flight-searcher-agent "flight-searcher" broker)
        flight-analyzer (create-flight-analyzer-agent "flight-analyzer"
                                                      broker
                                                      {:model "phi4"
                                                       :pre   "Here are the top %s flight options:\n\n %s \n\nWhich flight do you recommend and why?"})]

    ((:subscribe broker) :trip-request (:input-chan trip-request-agent))
    ((:subscribe broker) :flight-request (:input-chan flight-searcher))
    ((:subscribe broker) :flight-data (:input-chan flight-analyzer))))

(defn -main [& _]
  (let [broker (agents/create-broker)]
    (setup-system! broker)
    ((:publish broker) :trip-request {:from "Paris" :to "Tokyo" :date "2025-01-30"}))
  (Thread/sleep 60000))