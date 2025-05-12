(ns pyjama.fx
  (:require [clojure.java.io :as io])
  (:import (java.awt Desktop Desktop$Action)
           (java.net URI URL)
           (javafx.scene.image Image)
           (javafx.scene.input DragEvent TransferMode)
           (javafx.stage FileChooser FileChooser$ExtensionFilter)))

(defn open-file [file-path]
  (let [desktop (Desktop/getDesktop)]
    (.open desktop (clojure.java.io/as-file file-path))))

(defn open-url [url]
  (when (.isSupported (Desktop/getDesktop) Desktop$Action/BROWSE)
    (.browse (Desktop/getDesktop) (URI. url))))

(defn rsc-image [file]
  (Image. (io/input-stream (io/resource file))))

(defn valid-url? [url]
  (try
    (URL. url)
    true
    (catch Exception _ false)))

(defn file-chooser [summary extensions]
  (let [chooser (FileChooser.)]
    (.getExtensionFilters chooser)
    (.add (.getExtensionFilters chooser)
          (FileChooser$ExtensionFilter. summary extensions))
    (.showOpenDialog chooser nil)))

(defn on-drag-over [^DragEvent event]
  (let [db (.getDragboard event)]
    (when (.hasFiles db)
      (doto event
        (.acceptTransferModes
          ; NOTE could also use LINK here
          (into-array TransferMode [TransferMode/COPY]))))))

; TODO: generic
(defn handle-drag-dropped [event]
  (let [db (.getDragboard event) files (.getFiles db)]
    files))
(defn handle-files-dropped [fn event]
  (fn (handle-drag-dropped event)))