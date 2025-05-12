(ns telegram.model_as_options
  (:require [morse.api :as api]
            [morse.handlers :as handlers]))

(def token
  (or (System/getenv "TELEGRAM_TOKEN")))

;; Define the command handler
(defmethod handlers/handle-text :models
  [_ {:keys [chat]}]
  (let [chat-id (:id chat)
        options [["Option 1" "option_1"]
                 ["Option 2" "option_2"]
                 ["Option 3" "option_3"]]
        keyboard {:inline_keyboard (mapv (fn [[label callback-data]]
                                           [{:text label :callback_data callback-data}])
                                         options)}]
    (api/send-text bot-token chat-id "Choose a model:" {:reply_markup keyboard})))

;; Handle callback queries
(defmethod handlers/handle-callback-query :default
  [_ {:keys [id data message]}]
  (let [chat-id (:id (:chat message))]
    ;; Acknowledge the callback query
    (api/answer-callback-query bot-token id {:text (str "You selected: " data)})
    ;; Optionally send a follow-up message
    (api/send-text bot-token chat-id (str "You chose: " data))))

;; Define your bot with the handlers
(def bot
  (handlers/async-router {:text    handlers/handle-text
                          :default handlers/handle-default
                          :callback_query handlers/handle-callback-query}))

;; Start polling
(defn -main []
  (api/start bot bot-token))
