(ns pgn.core
  (:require [pgn.lib]))

(defn parse-square [square]
  "Converts a square like 'e4' to board coordinates [row col]."
  (let [col-map {"a" 0 "b" 1 "c" 2 "d" 3 "e" 4 "f" 5 "g" 6 "h" 7}
        row-map {"8" 0 "7" 1 "6" 2 "5" 3 "4" 4 "3" 5 "2" 6 "1" 7}]
    [(row-map (subs square 1 2)) (col-map (subs square 0 1))]))

(defn apply-move [board move]
  "Applies a single move to the board. This is a simplified implementation."
  (println "Applying move:" move)
  (cond
    ;; Example: castling (O-O)
    (= move "O-O") ;; Kingside castle
    (let [row (if (= (get-in board [7 4]) "♔") 7 0)] ;; Determine if it's white or black
      (assoc board
        row 4 " " ;; Clear king's initial square
        row 7 " " ;; Clear rook's initial square
        row 6 (get-in board [row 4]) ;; Move king
        row 5 (get-in board [row 7]))) ;; Move rook

    ;; Handle regular moves (e.g., d4, e4)
    (re-matches #"[a-h][1-8]" move) ;; Matches move notation like d4, e4, etc.
    (let [[from-row from-col] (parse-square (str (first move) "2")) ;; Assume starting square from second rank
          [to-row to-col] (parse-square move)]                     ;; The destination is the given move (e.g., d4)
      (println "Moving pawn from:" [from-row from-col] "to:" [to-row to-col])
      (assoc board
                [to-row to-col] "♙"  ;; Place pawn on the new square
                [from-row from-col] " ")) ;; Clear the old square

    ;; Handle captures (e.g., e4xd5 or Nf3)
    (re-matches #"[a-h][1-8]x[a-h][1-8]" move) ;; Capture notation (e.g., e4xd5)
    (let [[from-row from-col] (parse-square (str (first move) "2")) ;; Starting position from the second rank
          [to-row to-col] (parse-square (subs move 2 4))]         ;; Destination position
      (println "Capturing piece from:" [from-row from-col] "to:" [to-row to-col])
      (assoc board
                [to-row to-col] "♙"  ;; Place pawn on the new square (assuming pawn capture)
                [from-row from-col] " ")) ;; Clear the old square

    ;; Handle knight moves (e.g., Nf3)
    (re-matches #"^[N][a-h][1-8]" move) ;; Knight move notation (e.g., Nf3)
    (let [[from-row from-col] (parse-square "d2") ;; Assume knight starts at d2 for simplicity
          [to-row to-col] (parse-square move)]   ;; The destination is the provided move (e.g., f3)
      (println "Moving knight from:" [from-row from-col] "to:" [to-row to-col])
      (assoc board
                [to-row to-col] "♘"  ;; Place knight on the new square
                [from-row from-col] " ")) ;; Clear the old square

    ;; Default case if unsupported move
    :else board))

(defn play-game [board moves]
  "Plays a sequence of moves and returns the final board state."
  (pgn.lib/print-board board)
  (println moves)
  (reduce apply-move board (:moves moves)))

;; Parse the PGN and play the game
(def moves (pgn.lib/parse-pgn pgn.lib/sample-pgn-input))
(def final-board (play-game pgn.lib/initial-board moves))

(defn -main [& args]
  ;; Print the final board
  (pgn.lib/print-board final-board))
