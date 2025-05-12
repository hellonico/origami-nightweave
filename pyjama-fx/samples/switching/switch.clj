(ns switching.switch
  (:require [cljfx.api :as fx]
            [clojure.java.io :as io])
  (:import (javafx.scene.input KeyCode)))


(def app-state (atom {:current-scene :scene-1}))

;(def scenes
;  (into {}
;        (map (fn [n]
;               [(keyword (str "scene-" n))
;                {:fx/type :v-box
;                 :children [{:fx/type :label
;                             :text (str "This is Scene " n)}]}])
;             (range 1 10))))

;
(defn scene-1 []
  {:fx/type  :v-box
   :children [{:fx/type :label
               :text    "This is Scene 1"}]})

(defn scene-2 []
  {:fx/type  :v-box
   :children [{:fx/type :label
               :text    "This is Scene 2"}]})

(defn scene-3 []
  {:fx/type  :v-box
   :children [{:fx/type :label
               :text    "This is Scene 3"}]})

(defn scene-4 []
  {:fx/type  :v-box
   :children [{:fx/type :label
               :text    "This is Scene 4"}]})

(defn scene-5 []
  {:fx/type  :v-box
   :children [{:fx/type :label
               :text    "This is Scene 5"}]})

(defn scene-6 []
  {:fx/type  :v-box
   :children [{:fx/type :label
               :text    "This is Scene 6"}]})

(defn scene-7 []
  {:fx/type  :v-box
   :children [{:fx/type :label
               :text    "This is Scene 7"}]})

(defn scene-8 []
  {:fx/type  :v-box
   :children [{:fx/type :label
               :text    "This is Scene 8"}]})

(defn scene-9 []
  {:fx/type  :v-box
   :children [{:fx/type :label
               :text    "This is Scene 9"}]})


(defn handle-key-press [event]
  (let [code (.getCode event)
        command? (.isMetaDown event)]                       ; Check if the Command key is pressed
    (cond
      (and command? (= code KeyCode/DIGIT1))
      (reset! app-state {:current-scene :scene-1})

      (and command? (= code KeyCode/DIGIT2))
      (reset! app-state {:current-scene :scene-2})

      (and command? (= code KeyCode/DIGIT3))
      (reset! app-state {:current-scene :scene-3})

      (and command? (= code KeyCode/DIGIT4))
      (reset! app-state {:current-scene :scene-4})

      (and command? (= code KeyCode/DIGIT5))
      (reset! app-state {:current-scene :scene-5})

      (and command? (= code KeyCode/DIGIT6))
      (reset! app-state {:current-scene :scene-6})

      (and command? (= code KeyCode/DIGIT7))
      (reset! app-state {:current-scene :scene-7})

      (and command? (= code KeyCode/DIGIT8))
      (reset! app-state {:current-scene :scene-8})

      (and command? (= code KeyCode/DIGIT9))
      (reset! app-state {:current-scene :scene-9})

      )))

;(defn handle-key-press [event]
;  (let [code (.getCode event)
;        command? (.isMetaDown event) ; Check if the Command key is pressed
;        digit (-> code .getName Integer/parseInt)] ; Parse the digit from the key code
;    (when (and command? (<= 1 digit 9)) ; Ensure digit is between 1 and 9
;      (reset! app-state {:current-scene (keyword (str "scene-" digit))}))))


(defn root-view [state]
  {:fx/type          :stage
   :showing          true
   :width            500
   :on-close-request (fn [_] (System/exit 0))               ; Exit when the window is closed
   :height           600
   :scene            {:fx/type        :scene
                      ;:root (case (:current-scene @app-state)
                      ;        :scene-1 (scene-1)
                      ;        :scene-2 (scene-2))
                      ;
                      :on-key-pressed handle-key-press
                      :stylesheets    #{"style.css" (.toExternalForm (io/resource "terminal.css"))}
                      :root
                      (case (:current-scene @app-state)
                        :scene-1 (scene-1)
                        :scene-2 (scene-2)
                        :scene-3 (scene-3)
                        :scene-4 (scene-4)
                        :scene-5 (scene-5)
                        :scene-6 (scene-6)
                        :scene-7 (scene-7)
                        :scene-8 (scene-8)
                        :scene-9 (scene-9)

                        )
                      ;(:current-scene state)
                      }})
;
;(defn app [state]
;  {:fx/type :stage
;   :showing true
;   :scene {:fx/type :scene
;           :on-key-pressed handle-key-press
;           :root (case (:current-scene @app-state)
;                   :scene-1 (scene-1)
;                   :scene-2 (scene-2))}})


(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc root-view)
    :opts {:fx.opt/map-event-handler handle-key-press
           :app-state                app-state}))

(defn -main []
  (fx/mount-renderer app-state renderer))