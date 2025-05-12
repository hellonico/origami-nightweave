(ns ai-agents.travelv2
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
    (fn [message ollama-helper _]
      (let [{:keys [from to date]} message]
        (let [response (ollama-helper [from to])]
          (if-let [codes (re-seq #"[A-Z]{3}" response)]
            (let [from-code (first codes) to-code (second codes)]
              (println "Converted Cities to Airport Codes:" from "->" from-code ", " to "->" to-code)
              {:from from-code :to to-code :date date})))))))

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
  (pyjama.agents/make-agent
    id broker {}
    (fn [message _]
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
            {:from from :to to :date date :flights flight-data})
          (println "Failed to fetch flight data:" (:body response)))))))

(defn format-duration [duration]
  (let [hours (re-find #"PT(\d+)H" duration)
        minutes (re-find #"(\d+)M" duration)
        hours (if hours (Integer/parseInt (second hours)) 0)
        minutes (if minutes (Integer/parseInt (second minutes)) 0)]
    (str hours "h" (when (pos? minutes) (str minutes "min")))))

(defn format-flights-as-table [flights]
  (let [rows (map (fn [flight]
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
                  flights)]
    (pyjama.utils/to-markdown
      {:rows    rows
       :headers [:flight-id :carrier :price :duration :stops :departure :departure-time :arrival :arrival-time]})))

(defn create-flight-analyzer-agent [id broker config]
  (agents/make-ollama-agent
    id broker config
    (fn [message ollama-helper broker]
      (let [{:keys [flights n]} message
            top-flights (take (or n 2) (sort-by #(read-string (get-in % [:price :total])) flights))
            formatted-table (format-flights-as-table top-flights)
            response (ollama-helper [(or n 2) formatted-table])]
        (str "Flight Analysis Recommendation:\n" formatted-table "\n" response)))))
;
;(defn create-notifier-agent
;  [id broker]
;  (pyjama.agents/make-simple-agent
;    id broker nil
;    (fn [message _]
;      (println message))))

(defn setup-system! [broker running]
  (let [trip-request-agent
        (create-trip-request-agent
          :trip-request
          broker
          {:model "llama3.2"
           :pre   "What are the airport code names for the main airports in %s and %s? Just answer the 3-letter names."})

        flight-searcher
        (create-flight-searcher-agent :flight-searcher broker)

        flight-analyzer
        (create-flight-analyzer-agent
          :flight-analyzer
          broker
          {:model "llama3.2"
           :pre   "Here are the top %s flight options:\n\n %s \n\nWhich flight do you recommend and why?"})

        flight-notifier
        (pyjama.agents/make-simple-agent
          :flight-searcher
          broker nil
          (fn [message _] (println message) true))

        end-system (pyjama.agents/make-simple-agent
                     :end-system
                     broker nil
                     (fn [message _] (reset! running false)))
        ]

    ((:subscribe broker) :trip-request-in (:input-chan trip-request-agent))
    ((:forward broker) (:output-chan trip-request-agent) :flight-request-out)

    ((:subscribe broker) :flight-request-out (:input-chan flight-searcher))
    ((:forward broker) (:output-chan flight-searcher) :flight-data-in)

    ((:subscribe broker) :flight-data-in (:input-chan flight-analyzer))
    ((:forward broker) (:output-chan flight-analyzer) :trip-notification)

    ((:subscribe broker) :trip-notification (:input-chan flight-notifier))

    ((:forward broker) (:output-chan flight-notifier) :end)
    ((:subscribe broker) :end (:input-chan end-system))

    )

  )

(defn -main [& args]
  (let [broker (agents/create-broker)
        running (atom true)
        params {:from (first args)
                :to   (second args)
                :date (nth args 2)}]
    (println "Received parameters:" params)
    (setup-system! broker running)
    ((:publish broker) :trip-request-in params)
    (while @running)
    (Thread/sleep 1000)))