(ns hello
  (:require [telegrambot-lib.core :as tg]
;            [telegrambot-lib.polling :as polling]
            [clojure.string :as str]
            ;[clojure.data.json :as json]
            [pyjama.core]
            )
  (:require [telegrambot-lib.core :as tbot]))


(def token "7174428470:AAGXCiEl2XpG61Qr_6zsd1e8yckY7CppFRE")
;
;(defn -main [& args]
;  (def mybot (tbot/create))
;  ;(def mybot (tbot/create my-token))
;  (def mybot (tbot/create {:bot-token token}))
;  (prn (tbot/get-me mybot)))


;; 1. State for URL, Model, and Chat History
(def state (atom {:url "http://localhost:11434" :model "llama3.2"}))
(def chat-history (atom []))

;; 2. Function to Call Ollama API
(defn chat-to-ollama [user-messages]
  (let [{:keys [url model]} @state]
    ;; Replace this mock response with actual Ollama integration
    {:message "This is a simulated response from Ollama."}
    ;; Uncomment the actual call if the library is properly integrated
     (pyjama.core/ollama
       url
       :chat
       {:model model
        :messages user-messages})
    ))

;; 3. Handler for Incoming Telegram Messages
(defn handle-message [update]
  (when-let [text (get-in update [:message :text])]
    (let [chat-id (get-in update [:message :chat :id])]
      (cond
        ;; Command to set URL
        (str/starts-with? text "/seturl")
        (let [new-url (str/replace text "/seturl " "")]
          (swap! state assoc :url new-url)
          (tg/send-message token chat-id (str "Updated URL to: " new-url)))

        ;; Command to set model
        (str/starts-with? text "/setmodel")
        (let [new-model (str/replace text "/setmodel " "")]
          (swap! state assoc :model new-model)
          tg/send-message token chat-id (str "Updated model to: " new-model)))

        ;; Default: Send to Ollama
        :else
        (do
          (swap! chat-history conj {:role "user" :content text})
          (let [response (chat-to-ollama @chat-history)
                answer (:message response)]
            (swap! chat-history conj {:role "assistant" :content answer})
            tg/send-message token chat-id answer)))))

(def config
  {:timeout 10}) ;the bot api timeout is in seconds

(defn poll-updates
  "Long poll for recent chat messages from Telegram."
  ([bot]
   (poll-updates bot nil))

  ([bot offset]
   (let [resp (tbot/get-updates bot {:url ""
                                     :offset offset
                                     :timeout (:timeout config)}
                                )]
     (if (contains? resp :error)
       (prn "tbot/get-updates error:" (:error resp))
       resp))))

;; 5. Function to Start the Bot
(defn start-bot []
  (let [token (System/getenv token)]
    (println "Starting Telegram bot...")
    (poll-updates token)))

;; 6. Main Entry Point
(defn -main []
  (start-bot))