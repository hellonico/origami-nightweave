(ns pgn.core4
  (:require [clojure.string :as str]))

;; Sample PGN content as a string
(def pgn-content "1.d4 Nf6 2.Nf3 d5 3.e3 Bf5 4.c4 c6 5.Nc3 e6 6.Bd3 Bxd3 7.Qxd3 Nbd7 8.b3 Bd6
9.O-O O-O 10.Bb2 Qe7 11.Rad1 Rad8 12.Rfe1 dxc4 13.bxc4 e5 14.dxe5 Nxe5 15.Nxe5 Bxe5
16.Qe2 Rxd1 17.Rxd1 Rd8 18.Rxd8+ Qxd8 19.Qd1 Qxd1+ 20.Nxd1 Bxb2 21.Nxb2 b5
22.f3 Kf8 23.Kf2 Ke7  1/2-1/2")

;; Initialize the starting chessboard
(def initial-board
  [["r" "n" "b" "q" "k" "b" "n" "r"]
   ["p" "p" "p" "p" "p" "p" "p" "p"]
   [" " " " " " " " " " " " " " " "]
   [" " " " " " " " " " " " " " " "]
   [" " " " " " " " " " " " " " " "]
   [" " " " " " " " " " " " " " " "]
   ["P" "P" "P" "P" "P" "P" "P" "P"]
   ["R" "N" "B" "Q" "K" "B" "N" "R"]])

;; Function to display the board
(defn display-board [board]
  (doseq [row board]
    (println (str/join " | " row)))
  (println))

;; Function to convert algebraic notation to board coordinates
(defn algebraic->coords [alg]
  (let [file (-> alg first int (- (int \a)))
        rank (- 8 (-> alg second str read-string))]
    [rank file]))

;; Function to update the board based on a move
(defn make-move [board move]
  (let [[from to] (map algebraic->coords (str/split move #"-"))
        [piece] (get-in board from)]
    (-> board
        (assoc-in from " ")
        (assoc-in to piece))))

;; Function to extract moves from the PGN content
(defn extract-moves [pgn]
  (let [moves (-> pgn
                  (str/replace #"\d+\.\.\." "")             ; Remove ellipsis
                  (str/split #"\d+\.")                      ; Split by move numbers
                  rest                                      ; Drop the first empty string
                  (->> (map str/trim)                       ; Trim whitespace
                       (map #(str/split % #"\s+"))))]       ; Split into individual moves
    (flatten moves)))

;; Function to parse the PGN content and simulate the game
(defn simulate-game [pgn]
  (let [moves (extract-moves pgn)
        game-state (atom initial-board)]
    (display-board @game-state)
    (doseq [[i move] (map-indexed vector moves)]
      (println (str "Move " (inc i) ": " move))
      (swap! game-state make-move move)
      (display-board @game-state))))

(defn -main [& args]
  ;; Simulate the game
  (simulate-game pgn-content))