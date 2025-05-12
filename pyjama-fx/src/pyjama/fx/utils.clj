(ns pyjama.fx.utils
  (:import (com.vladsch.flexmark.html HtmlRenderer$Builder)
           (com.vladsch.flexmark.parser Parser$Builder)
           (javafx.scene.input Clipboard)))

(defn get-clipboard-content []
  (let [clipboard (Clipboard/getSystemClipboard)]
    (if (.hasString clipboard)
      (.getString clipboard)
      "No text in clipboard")))

;; Flexmark markdown to HTML conversion function
(defn markdown->html [markdown-text]
  (let [parser (.build (Parser$Builder.))
        renderer (.build (HtmlRenderer$Builder.))]
    (.render renderer (.parse parser ^String markdown-text))))

(defn javafx-runtime-version []
  (let [version (System/getProperty "javafx.runtime.version")]
    (if (not (nil? version))
      (println "FX Runtime:" version)
      (do
        (println
          "JavaFX not available.
           Install a JavaFX runtime: https://www.azul.com/downloads/?package=jdk-fx#zulu")
        (System/exit 0)))))