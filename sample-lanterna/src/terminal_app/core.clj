(ns terminal-app.core
  (:require [pyjama.core])
  (:gen-class)
  (:import (com.googlecode.lanterna TextColor$ANSI)
           (com.googlecode.lanterna.input KeyType)
           (com.googlecode.lanterna.screen TerminalScreen)
           (com.googlecode.lanterna.terminal DefaultTerminalFactory)))

(defn draw-menu [screen options selected-index]
  "Draws the menu on the screen."
  (let [graphics (.newTextGraphics screen)]
    ; This leaves bad colors
    ; (.setForegroundColor graphics TextColor$ANSI/WHITE)
    ; (.setBackgroundColor graphics TextColor$ANSI/BLACK)
    ; this crashes the screen
    ;(.clear graphics) ; Clear the screen
    (doseq [idx (range (count options))]
      (let [color (if (= idx selected-index) TextColor$ANSI/RED TextColor$ANSI/WHITE)]
        (.setForegroundColor graphics color)
        (.putString graphics 2 (+ 2 idx) (get options idx))))
    (.refresh screen)))

(defn run-app []
  "Main loop for the terminal app."
  (let [terminal (.createTerminal (DefaultTerminalFactory.))
        screen (TerminalScreen. terminal)
        options ["Option 1: Hello, World!"
                 "Option 2: About"
                 "Option 3: Why is the sky blue?"
                 "Option 4: Quit"]
        running (atom true)
        selected-index (atom 0)]
    (try
      (.startScreen screen)
      (.setCursorPosition screen nil) ; Hide the cursor
      (while @running
        (draw-menu screen options @selected-index)
        (let [key (.readInput screen)] ; Block until input is received
          (when key
            (cond
              (= (.getKeyType key) KeyType/ArrowDown)
              (swap! selected-index #(mod (inc %) (count options)))

              (= (.getKeyType key) KeyType/ArrowUp)
              (swap! selected-index #(mod (dec %) (count options)))

              (= (.getKeyType key) KeyType/Enter)
              (case @selected-index
                0 (println "Hello, World!") ; You can add actions here
                1 (println "This is a simple terminal app using Lanterna!")
                2 (pyjama.core/ollama "http://localhost:11434" :generate {:stream true :model "tinyllama" :prompt "Why is the sky blue"})
                3 (reset! running false))

              (= (.getKeyType key) KeyType/Escape)
              (reset! running false)
              ))))
      (.stopScreen screen)
    (catch Exception e
      ;(.printStackTrace e)
      (println "An error occurred:" (.getMessage e))
      (.printStackTrace e))
  (finally
    (.close terminal)))))


(defn -main [& _]
  "Entry point for the terminal app."
  (run-app))
