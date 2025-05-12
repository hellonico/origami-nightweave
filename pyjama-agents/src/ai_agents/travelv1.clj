(ns ai-agents.travelv1
  (:require [clj-http.client :as http]
            [clojure.core.async :as async]
            [pyjama.core]))

(def amadeus-client-id (System/getenv "AMADEUS_KEY"))
(def amadeus-client-secret (System/getenv "AMADEUS_SECRET"))

(def amadeus-auth-url "https://test.api.amadeus.com/v1/security/oauth2/token")
(def amadeus-flight-search-url "https://test.api.amadeus.com/v2/shopping/flight-offers")

(defn create-broker
  []
  (let [topics (atom {})]
    {:topics      topics
     :subscribe   (fn [topic subscriber-chan]
                    (swap! topics update topic #(conj (or % #{}) subscriber-chan)))
     :unsubscribe (fn [topic subscriber-chan]
                    (swap! topics update topic #(disj (or % #{}) subscriber-chan)))
     :publish     (fn [topic message]
                    (doseq [subscriber (get @topics topic)]
                      (async/put! subscriber message)))}))

(defn create-trip-request-agent
  [id broker]
  (let [input-chan (async/chan)]
    (async/go-loop []
      (let [message (async/<! input-chan)]
        (when message
          (let [{:keys [from to date]} message
                _ (println "Looking for a trip from " from " to " to " on " date)
                response (pyjama.core/ollama "http://localhost:11434"
                                             :generate
                                             {:stream false
                                              :model  "phi4"
                                              :pre    "What are the airport code names for the main airports in %s and %s? Just answer the 3-letter names."
                                              :prompt [from to]}
                                             :response)]
            (if-let [codes (re-seq #"[A-Z]{3}" response)]
              (let [from-code (first codes)
                    to-code (second codes)]
                (println "Converted Cities to Airport Codes:" from "->" from-code ", " to "->" to-code)
                ((:publish broker) :flight-request {:from from-code :to to-code :date date}))
              (println "Failed to retrieve airport codes for" from "and" to))))
        (recur)))
    {:id         id
     :input-chan input-chan}))

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
  (let [input-chan (async/chan)
        access-token (get-amadeus-access-token)]
    (async/go-loop []
      (let [message (async/<! input-chan)]
        (when message
          (let [{:keys [from to date]} message
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
              (println "Failed to fetch flight data:" (:body response)))))
        (recur)))
    {:id         id
     :input-chan input-chan}))

(defn format-duration [duration]
  (let [hours (re-find #"PT(\d+)H" duration)
        minutes (re-find #"(\d+)M" duration)
        hours (if hours (Integer/parseInt (second hours)) 0)
        minutes (if minutes (Integer/parseInt (second minutes)) 0)]
    (str hours "h" (when (pos? minutes) (str minutes "min")))))

(defn format-flights-as-table [flights]
  (let [header "| Flight ID | Carrier | Price | Duration | Stops | Departure -> Arrival |\n|-----------|---------|-------|----------|-------|---------------------|"
        rows (map (fn [flight]
                    (let [segments (get-in flight [:itineraries 0 :segments])
                          last-segment (last segments)
                          id (:id flight)
                          carrier (get-in flight [:itineraries 0 :segments 0 :carrierCode])
                          price (-> flight :price :total)
                          duration (format-duration (get-in flight [:itineraries 0 :duration]))
                          stops (dec (count segments))
                          dep (get-in segments [0 :departure :iataCode])
                          dep-time (get-in segments [0 :departure :at])
                          arr (get last-segment :arrival :iataCode)
                          arr-time (get last-segment :arrival :at)]
                      (str "| " id " | " carrier " | $" price " | " duration " | " stops " | "
                           dep " (" dep-time ") -> " (:iataCode arr) " (" (:at arr-time) ") |")))
                  flights)]
    (str header "\n" (clojure.string/join "\n" rows))))


(defn create-flight-analyzer-agent
  [id _]
  (let [input-chan (async/chan)]
    (async/go-loop []
      (let [message (async/<! input-chan)]
        (when message
          (let [{:keys [flights n]} message
                top-flights (take (or n 2) (sort-by #(read-string (get-in % [:price :total])) flights))
                formatted-table (format-flights-as-table top-flights)
                _ (println formatted-table)
                ollama-prompt (str "Here are the top " (or n 2) " flight options:\n\n"
                                   formatted-table
                                   "\n\nWhich flight do you recommend and why?")
                response (pyjama.core/ollama "http://localhost:11434"
                                             :generate
                                             {:stream false
                                              :model  "llama3.2"
                                              :prompt ollama-prompt}
                                             :response)]
            (println "Flight Analysis Recommendation:\n" response)))
        (recur)))
    {:id         id
     :input-chan input-chan}))

(defn setup-system! [broker]
  (let [trip-request-agent (create-trip-request-agent "trip-request" broker)
        flight-searcher (create-flight-searcher-agent "flight-searcher" broker)
        flight-analyzer (create-flight-analyzer-agent "flight-analyzer" broker)]

    ((:subscribe broker) :trip-request (:input-chan trip-request-agent))
    ((:subscribe broker) :flight-request (:input-chan flight-searcher))
    ((:subscribe broker) :flight-data (:input-chan flight-analyzer))))

(defn -main [& _]
  (let [broker (create-broker)]
    (setup-system! broker)
    ((:publish broker) :trip-request {:from "Paris" :to "Tokyo" :date "2025-01-30"}))
  (Thread/sleep 60000))