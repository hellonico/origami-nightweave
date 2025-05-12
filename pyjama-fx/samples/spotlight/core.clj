(ns spotlight.core
  (:require
    [cljfx.api :as fx]
    [clojure.java.io :as io]
    [clojure.string :as str]))

(defonce *state
         (atom {:query          ""
                :all-items      ["Apple" "Banana" "Cherry" "Date" "Elderberry" "Fig" "Grape"]
                :selected       ""
                :filtered-items []}))

(defn filter-items [query items]
  (if (or (nil? query) (empty? query))
    []
    (->>
      items
      (filter #(str/includes? (str/lower-case %)
                              (str/lower-case query)))
      vec)))

(defn update-filtered-items! []
  (let [ite (filter-items (:query @*state) (:all-items @*state))]
    (swap! *state assoc :filtered-items ite)))

(defmulti handle-event :event/type)

(defmethod handle-event :search/update-query [event]
  (swap! *state assoc :query (:fx/event event))
  (update-filtered-items!))

(defmethod handle-event :search/update-selected [event]
  (swap! *state assoc :selected (:fx/event event)))

(defn search-bar-view [state]
  {:fx/type          :stage
   :title            "Spotlight"
   :showing          true
   :width            500
   :on-close-request (fn [_] (System/exit 0))               ; Exit when the window is closed
   :height           600
   :scene            {:fx/type     :scene
                      :stylesheets #{"style.css" (.toExternalForm (io/resource "terminal.css"))}
                      :root
                      {:fx/type  :v-box
                       :spacing  10
                       :children [{:fx/type         :text-field
                                   :prompt-text     "Search..."
                                   :text            (:query state)
                                   :on-text-changed {:event/type :search/update-query}}
                                  {:fx/type :list-view
                                   :items   (:filtered-items state)
                                   :pref-height (* 24 (count (:filtered-items state)))
                                   :on-selected-item-changed {:event/type :search/update-selected}}
                                  {:fx/type :label
                                   :text    (str "Selected Item:" (:selected state))}]}}})

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc search-bar-view)
    :opts {:fx.opt/map-event-handler handle-event
           :app-state                *state}))

(defn -main []
  (fx/mount-renderer *state renderer))