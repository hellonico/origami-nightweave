(ns viewer.core
  (:require [cljfx.api :as fx]
            [clojure.edn :as edn]
            [pyjama.fx]
            [clojure.java.io :as io])
  (:import (javafx.stage FileChooser)))

(defn tree-item [data]
  (cond
    (map? data)
    {:fx/type :tree-item
     :value "Map"
     :expanded true
     :children (for [[k v] data]
                 {:fx/type :tree-item
                  :value (str k)
                  :expanded true
                  :children [(tree-item v)]})}

    (sequential? data)  ;; Covers lists and vectors
    {:fx/type :tree-item
     :value (if (vector? data) "Vector" "List")
     :expanded true
     :children (map tree-item data)}

    :else
    {:fx/type :tree-item
     :value (pr-str data)}))


(defonce *state (atom {:tree-root nil}))

(defn load-edn-file [file-path]
  (try
    (let [data (edn/read-string (slurp file-path))]
      (swap! *state assoc :tree-root (tree-item data)))
    (catch Exception e
      (println "Error reading EDN file:" (.getMessage e)))))

(defn root-view [{:keys [tree-root]}]
  {:fx/type :stage
   :title "EDN Viewer"
   :showing true
   :width 400
   :height 500
   :scene {:fx/type :scene
           :root {:fx/type :v-box
                  :children [{:fx/type :button
                              :text "Open EDN File"
                              :on-action (fn[_] (load-edn-file (pyjama.fx/file-chooser "Open EDN File" ["*.edn"])))}
                             {:fx/type :tree-view
                              :root {:fx/type :tree-item
                                     ;:text "Root"
                                     :expanded true
                                     :children (or (:children tree-root) [])}}]}}})

(def renderer (fx/create-renderer
                :middleware (fx/wrap-map-desc (fn [_] (root-view @*state)))
                :opts {:fx.opt/map-event-handler (fn [_])}))

(defn -main[& args]
  (fx/mount-renderer *state renderer)
  )

