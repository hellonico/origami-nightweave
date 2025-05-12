(ns ai-agents.quotes
  (:require [clj-http.client :as http]
            [clojure.core.async :as async]
            [pyjama.agents]
            [pyjama.core]))

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

(defn create-ollama-agent
  [id broker config]
  (let [input-chan (async/chan)]
    {:id         id
     :input-chan input-chan
     :process    (fn [{:keys [symbol avg max min]}]
                   (println (str id " performing advanced analysis for " symbol))
                   (try
                     (let [

                           response (pyjama.core/ollama (or (:url config) (System/getenv "OLLAMA_URL")) :generate {:stream false
                                                                              :model  (or (:model config) "llama3.2")
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

(defn run-system []
  (let [broker (pyjama.agents/create-broker)
        fetcher-agent (create-fetcher-agent "FetcherAgent" broker)
        analyzer-agent (create-analyzer-agent "AnalyzerAgent" broker)
        ollama-agent (create-ollama-agent "OllamaAgent" broker {:url "http://localhost:11434" :model "tinyllama"})]

    ((:subscribe broker) "quote-requests" (:input-chan fetcher-agent))
    ((:subscribe broker) "quotes-fetched" (:input-chan analyzer-agent))
    ((:subscribe broker) "advanced-analysis" (:input-chan ollama-agent))

    ((:run fetcher-agent) fetcher-agent)
    ((:run analyzer-agent) analyzer-agent)
    ((:run ollama-agent) ollama-agent)

    ;; Send a test message to fetcher agent
    ;(async/put! (:input-chan fetcher-agent) {:symbol "AAPL"})
    ;(async/put! (:input-chan fetcher-agent) {:symbol "GOOG"})
    (async/put! (:input-chan fetcher-agent) {:symbol "TSLA"})

    ))

(defn -main [& _]
  (run-system)
  (Thread/sleep 10000))
