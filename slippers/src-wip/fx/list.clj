(ns fx.list
  (:require [cljfx.api :as fx]
            [clojure.java.io :as io]))

(def app-state
  (atom {:items ["Item 1" "Item 2" "Item 3"]}))

(defn add-item [state]
  (swap! state update :items conj (str "Item " (inc (count (:items @state))))))

(defn remove-item [state index]
  (swap! state update :items #(vec (concat (subvec % 0 index) (subvec % (inc index))))))

(defn item-view [state]
  {:fx/type  :v-box
   :children [{:fx/type             :list-view
               :items               (:items state)
               :on-selected-items-changed  (fn [event] (do
                                                  (println event)
                                                  (println @app-state)
                                                  ;(swap! app-state assoc-in :selected (.getTarget event))
                                                  (println @app-state)))
               ;:on-key-pressed      (fn [e]
               ;                       (when (= :delete (.getCode e))
               ;                         (let [selected-index (.getSelectedIndex (.getSelectionModel (.get (.lookup (.getNode e) ".list-view"))))]
               ;                           (when (>= selected-index 0)
               ;                             (remove-item state selected-index)))))
               }
              {:fx/type  :h-box
               :spacing  10
               :children [{:fx/type   :button
                           :text      "Add Item"
                           :on-action (fn [_] (add-item state))}
                          {:fx/type   :button
                           :text      "Remove Selected"
                           :on-action (fn [e]
                                        ;(prn (.getTarget e)
                                        (prn (:selected app-state))
                                        (let [selected-index (.getSelectedIndex e)]
                                          (when (>= selected-index 0)
                                            (remove-item state selected-index))))}]}]})

(defn stage [state]
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
                       :children [
                                  {:fx/type  :v-box
                                   :children [{:fx/type        :list-view
                                               :items          (:items state)
                                               :on-key-pressed (fn [e]
                                                                 (when (= :delete (.getCode e))
                                                                   (let [selected-index (.getSelectedIndex (.getSelectionModel (.get (.lookup (.getNode e) ".list-view"))))]
                                                                     (when (>= selected-index 0)
                                                                       (remove-item state selected-index)))))
                                               }
                                              {:fx/type  :h-box
                                               :spacing  10
                                               :children [{:fx/type   :button
                                                           :text      "Add Item"
                                                           :on-action (fn [_] (add-item app-state))}
                                                          {:fx/type   :button
                                                           :text      "Remove Selected"
                                                           :on-action (fn [e]
                                                                        (prn e)
                                                                        (let [selected-index (.getSelectedIndex e)]
                                                                          (when (>= selected-index 0)
                                                                            (remove-item app-state selected-index))))}]}]}

                                  ]}}})

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type stage)
    :opts {:app-state app-state}))

(defn -main []
  (fx/mount-renderer app-state renderer))