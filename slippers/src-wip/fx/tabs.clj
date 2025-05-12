(ns fx.tabs
  (:require [cljfx.api :as fx])
  (:import (javafx.event EventHandler)
           [javafx.scene.input KeyCode KeyCombination KeyCodeCombination KeyEvent]))

;; Define application state
(defonce *state (atom {:tabs [{:id 1 :name "Session 1" :output ""}]
                       :next-tab-id 2}))

;; Function to run shell commands
(defn run-command [cmd]
  (try
    (let [process (.exec (Runtime/getRuntime) cmd)
          reader (clojure.java.io/reader (.getInputStream process))]
      (with-open [r reader]
        (apply str (line-seq r))))
    (catch Exception e
      (str "Error: " (.getMessage e)))))

;; Define event handlers
(defmulti event-handler :event/type)

(defmethod event-handler ::execute-command
  [{:keys [tab-id command]}]
  (swap! *state update :tabs
         (fn [tabs]
           (mapv (fn [tab]
                   (if (= (:id tab) tab-id)
                     (update tab :output str "\n> " command "\n" (run-command command) "\n")
                     tab))
                 tabs))))

(defmethod event-handler ::add-tab
  [_]
  (swap! *state update :tabs
         (fn [tabs]
           (let [next-id (:next-tab-id @*state)]
             (conj tabs {:id next-id
                         :name (str "Session " next-id)
                         :output ""}))))
  (swap! *state update :next-tab-id inc))

(defmethod event-handler ::remove-tab
  [{:keys [tab-id]}]
  (swap! *state update :tabs (fn [tabs] (remove #(= (:id %) tab-id) tabs))))


;; Define the UI renderer
(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc
                  (fn [state]
                    (assoc state :state @*state)))
    :opts {:fx.opt/map-event-handler event-handler}
    :desc-fn
    (fn [state]
      {:fx/type :v-box
       :spacing 10
       :children [{:fx/type :tab-pane
                   :tabs (mapv
                           (fn [{:keys [id name output]}]
                             {:fx/type :tab
                              :text name
                              :closable true
                              :on-close-request {:event/type ::remove-tab :tab-id id}
                              :content {:fx/type :v-box
                                        :spacing 10
                                        :children [{:fx/type :text-area
                                                    :v-box/vgrow :always
                                                    :wrap-text true
                                                    :editable false
                                                    :text output}
                                                   {:fx/type :text-field
                                                    :on-key-pressed
                                                    (fn [^KeyEvent event]
                                                      (when (= (.getCode event) KeyCode/ENTER)
                                                        {:event/type ::execute-command
                                                         :tab-id id
                                                         :command (.getText (.getSource event))}))
                                                    :prompt-text "Enter command..."}]}})
                           (:tabs state))}
                  {:fx/type :button
 :text "New Tab (Cmd-T / Ctrl-T)"
 :on-action {:event/type ::add-tab}}]})))

;; Handle global key bindings
(defn add-global-shortcut [stage]
  (let [scene (.getScene stage)
        ctrl-t (KeyCodeCombination. KeyCode/T KeyCombination/CONTROL_DOWN)
        cmd-t (KeyCodeCombination. KeyCode/T KeyCombination/META_DOWN)]
    (.addEventHandler scene
                      KeyEvent/KEY_PRESSED
                      (reify EventHandler
                        (handle [_ event]
                          (when (or (.match ctrl-t event) (.match cmd-t event))
                            (swap! *state update :tabs
                                   (fn [tabs]
                                     (conj tabs {:id (:next-tab-id @*state)
                                                 :name (str "Session " (:next-tab-id @*state))
                                                 :output ""})))
                            (swap! *state update :next-tab-id inc)))))))


;; Start the application
(defn -main []
  ;(add-global-shortcut)
  (fx/mount-renderer *state renderer))


(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type app-view)
    :opts {:state                    *state
           :fx.opt/map-event-handler handle-event}))
;
;(defn -main [& args]
;  (pyjama.state/local-models state)
;  (pyjama.state/remote-models state)
;  (fx/mount-renderer state renderer))