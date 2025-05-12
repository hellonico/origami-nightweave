(ns ai-agents.quotes2
  (:require [clj-http.client :as http]
            [clojure.core.async :as async]
            [pyjama.core]
    ;[clojure.data.json :as json]
            ))

(def alpha-vantage-api-key "IJJFONQHOH1DGC2K")              ;; Replace with your API key
(def alpha-vantage-base-url "https://www.alphavantage.co/query")

;; Broker definition
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
;
;;; Agent 1: Fetch financial quotes
;(defn create-fetcher-agent
;  [id broker]
;  (let [input-chan (async/chan)]
;    {:id         id
;     :input-chan input-chan
;     :process    (fn [{:keys [symbol]}]
;                   (println (str id " fetching data for " symbol))
;                   (try
;                     (let [response (http/get alpha-vantage-base-url
;                                              {:query-params {:function "TIME_SERIES_DAILY"
;                                                              :symbol   symbol
;                                                              :apikey   alpha-vantage-api-key}
;                                               :as           :json})
;                           _ (println response)
;                           data (get-in response [:body (keyword "Time Series (Daily)")])
;                           ;_ (println data)
;                           ]
;                       (if data
;                         ((:publish broker) "quotes-fetched" {:symbol symbol :data data})
;                         (println (str id ": No data found for symbol " symbol))))
;                     (catch Exception e
;                       (println (str id " failed to fetch data for " symbol ": " (.getMessage e))))))
;     :run        (fn [this]
;                   (async/go-loop []
;                     (when-let [message (async/<! input-chan)]
;                       ((:process this) message))
;                     (recur)))}))


;; Agent 2: Analyze financial quotes
;(defn create-analyzer-agent
;  [id broker]
;  (let [input-chan (async/chan)]
;    {:id         id
;     :input-chan input-chan
;     :process    (fn [{:keys [symbol data]}]
;                   (println (str id " analyzing data for " symbol))
;                   (let [prices (map #(Double/parseDouble (% (keyword "4. close"))) (vals data))
;                         avg-price (/ (reduce + prices) (count prices))
;                         max-price (apply max prices)
;                         min-price (apply min prices)]
;                     (println (str "Analysis for " symbol ":"
;                                   "\n  Avg price: " avg-price
;                                   "\n  Max price: " max-price
;                                   "\n  Min price: " min-price))
;                     ((:publish broker) "advanced-analysis" {:symbol symbol
;                                                             :avg    avg-price
;                                                             :max    max-price
;                                                             :min    min-price})))
;     :run        (fn [this]
;                   (async/go-loop []
;                     (when-let [message (async/<! input-chan)]
;                       ((:process this) message))
;                     (recur)))}))

(def twelve-data-api-key "772d8047b4434615b7eaa4ef2c83078d") ;; Replace with your API key
(def twelve-data-base-url "https://api.twelvedata.com/time_series")

(defn create-fetcher-agent
  [id broker]
  (let [input-chan (async/chan)]
    {:id         id
     :input-chan input-chan
     :process    (fn [{:keys [symbol]}]
                   (println (str id " fetching data for " symbol))
                   (try
                     (let [response (http/get twelve-data-base-url
                                              {:query-params {:symbol   symbol
                                                              :interval "1day"
                                                              :apikey   twelve-data-api-key}
                                               :as           :json})
                           data (get-in response [:body :values])]
                       (if data
                         ((:publish broker) "quotes-fetched" {:symbol symbol :data data})
                         (println (str id ": No data found for symbol " symbol))))
                     (catch Exception e
                       (println (str id " failed to fetch data for " symbol ": " (.getMessage e))))))
     :run        (fn [this]
                   (async/go-loop []
                     (when-let [message (async/<! input-chan)]
                       ((:process this) message))
                     (recur)))}))


;; Agent 2: Analyze financial quotes
(defn create-analyzer-agent
  [id broker]
  (let [input-chan (async/chan)]
    {:id         id
     :input-chan input-chan
     :process    (fn [{:keys [symbol data]}]
                   (println (str id " analyzing data for " symbol))
                   (let [prices (map #(Double/parseDouble (% (keyword "close"))) data)
                         avg-price (/ (reduce + prices) (count prices))
                         max-price (apply max prices)
                         min-price (apply min prices)]
                     (println (str "Analysis for " symbol ":"
                                   "\n  Avg price: " avg-price
                                   "\n  Max price: " max-price
                                   "\n  Min price: " min-price))
                     ((:publish broker) "advanced-analysis" {:symbol symbol
                                                             :avg    avg-price
                                                             :max    max-price
                                                             :min    min-price})))
     :run        (fn [this]
                   (async/go-loop []
                     (when-let [message (async/<! input-chan)]
                       ((:process this) message))
                     (recur)))}))

;; Agent 3: Advanced analysis using Ollama
(defn create-ollama-agent
  [id broker ollama-url model]
  (let [input-chan (async/chan)]
    {:id         id
     :input-chan input-chan
     :process    (fn [{:keys [symbol avg max min]}]
                   (println (str id " performing advanced analysis for " symbol))
                   (try
                     (let [

                           response (pyjama.core/ollama ollama-url :generate {:stream false
                                                                              :model  model
                                                                              :prompt (str "Analyze the following financial data for "
                                                                                           symbol ": Avg price = " avg
                                                                                           ", Max price = " max
                                                                                           ", Min price = " min ". Provide insights.")})
                           result (:response response)

                           ]
                       (println (str "Ollama insights for " symbol ": " result)))
                     (catch Exception e
                       (println (str id " failed to analyze data for " symbol ": " (.getMessage e))))))
     :run        (fn [this]
                   (async/go-loop []
                     (when-let [message (async/<! input-chan)]
                       ((:process this) message))
                     (recur)))}))

;; Main function to set up the agents and broker
(defn run-system []
  (let [broker (create-broker)
        fetcher-agent (create-fetcher-agent "FetcherAgent" broker)
        analyzer-agent (create-analyzer-agent "AnalyzerAgent" broker)
        ollama-agent (create-ollama-agent "Tiny Agent" broker "http://localhost:11434" "tinyllama")
        ollama-agent2 (create-ollama-agent "LLama Agent" broker "http://localhost:11434" "llama3.2")
        ]

    ;; Subscribe agents to topics
    ((:subscribe broker) "quote-requests" (:input-chan fetcher-agent))
    ((:subscribe broker) "quotes-fetched" (:input-chan analyzer-agent))
    ((:subscribe broker) "advanced-analysis" (:input-chan ollama-agent))
    ((:subscribe broker) "advanced-analysis" (:input-chan ollama-agent2))

    ;; Run agents
    ((:run fetcher-agent) fetcher-agent)
    ((:run analyzer-agent) analyzer-agent)
    ((:run ollama-agent) ollama-agent)
    ((:run ollama-agent2) ollama-agent2)

    ;; Send a test message to fetcher agent
    ;(async/put! (:input-chan fetcher-agent) {:symbol "AAPL"})
    ;(async/put! (:input-chan fetcher-agent) {:symbol "GOOG"})
    (async/put! (:input-chan fetcher-agent) {:symbol "TSLA"})

    ))

(defn -main [& args]
  (run-system)
  (Thread/sleep 10000))
