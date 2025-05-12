(ns my-telegram-bot.realtime
  (:require [clojure.core.async :as async]
            [morse.api :as api]
            [morse.handlers :as handlers]
            [morse.polling :as polling]
            [pyjama.core]))

(def token
  (or (System/getenv "TELEGRAM_TOKEN")))

(def chat-state (atom {}))                                  ;; To track the message being updated

;(defn update-message-progressively-char [chat-id message]
;  (let [
;        chars (seq message)
;        ;words (clojure.string/split message #"\s+")
;        ]
;    (async/go
;      (let [
;            initial-text (first chars)
;            initial-message (api/send-text token chat-id initial-text)
;            msg-id (get-in initial-message [:result :message_id])
;            ]
;        (swap! chat-state assoc chat-id  {:message-id msg-id  :text initial-text})
;        ;(doseq [char chars]
;        (doseq [char (rest chars)]
;          (let [current-text (str (:text (get @chat-state chat-id)) char )] ;; Append character to current text
;            (api/edit-text token chat-id msg-id current-text) ;; Edit message
;            (swap! chat-state assoc chat-id {:message-id (:message-id @chat-state chat-id) :text current-text})
;            ))))))

(defn update-message-progressively-word [chat-id message]
  (let [words (clojure.string/split message #"\s+")]
    (async/go
      (let [
            initial-text "..."
            initial-message (api/send-text token chat-id initial-text)
            msg-id (get-in initial-message [:result :message_id])
            ]
        (swap! chat-state assoc chat-id {:message-id msg-id :text ""})
        (doseq [word words]
          (let [current-text (str (:text (get @chat-state chat-id)) " " word)]
            (api/edit-text token chat-id msg-id current-text)
            (swap! chat-state assoc chat-id {:message-id (:message-id @chat-state chat-id) :text current-text})))))))

(defn query-ollama [chat-id message]
  (async/go
    (let [
          initial-text "..."
          initial-message (api/send-text token chat-id initial-text)
          msg-id (get-in initial-message [:result :message_id])
          ]
      (swap! chat-state assoc chat-id {:message-id msg-id :text ""})

      (let [ch (async/chan)
            _fn (partial pyjama.core/pipe-chat-tokens ch)
            result-ch (async/go
                        (pyjama.core/ollama "http://localhost:11434"
                                            :chat
                                            {:messages [{:role :user :content message}] :stream true :model "llama3.2"} _fn))
            ]
        (async/go
          (let [_ (async/<! result-ch)]
            (async/close! ch)
            (flush)))

        (loop []
          (when-let [val (async/<!! ch)]
            (when (not (empty val))
              (let [current-text (str (:text (get @chat-state chat-id)) val)]
                (api/edit-text token chat-id msg-id current-text)
                (swap! chat-state assoc chat-id {:message-id (:message-id @chat-state chat-id) :text current-text})
                ;(print val)
                ;(flush)
                (recur)))))))))

(defn message-handler [update]
  (let [chat-id (get-in update [:chat :id])
        text (get-in update [:text])]
    (when (not (empty? text))
      ;(update-message-progressively-word chat-id text)
      (query-ollama chat-id text)
      )))


(handlers/defhandler bot-api
                     (handlers/message message (message-handler message)))

(defn start-bot []
  (polling/start token bot-api {:timeout 2})
  (println "Bot is running... Press Ctrl+C to stop.")
  (loop []
    (Thread/sleep 1000)
    (recur)))

(defn -main [& arg]
  (start-bot))
