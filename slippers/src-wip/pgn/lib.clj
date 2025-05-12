(ns pgn.lib
  (:require [clojure.string :as str]))


(defn print-board [board]
  "Prints the chess board using emojis."
  (doseq [row board]
    (println (str/join " " row))))


(defn parse-metadata [lines]
  "Parses the PGN metadata section."
  (reduce (fn [acc line]
            (if (re-matches #"\[.*?\]" line)
              (let [[_ key value] (re-matches #"\[(\w+) \"(.*)\"\]" line)]
                (assoc acc (keyword key) value))
              acc))
          {}
          lines))

(defn parse-moves [move-text]
  "Parses the move section of the PGN."
  (let [tokens (str/split move-text #"\s+")
        moves (filter #(re-matches #"\d+\..*|[a-hKQRBNOox1-8+#=]+" %) tokens)]
    (reduce (fn [acc token]
              (if (re-matches #"\d+\." token)
                (assoc acc :current-round token)
                (update acc :moves conj token)))
            {:current-round nil, :moves []}
            moves)))

(defn parse-pgn [pgn-text]
  "Parses the entire PGN file and returns a structured map."
  (let [lines (str/split-lines pgn-text)
        [metadata-lines moves-lines] (split-with #(re-matches #"\[.*?\]" %) lines)
        metadata (parse-metadata metadata-lines)
        move-text (str/join " " moves-lines)
        moves (parse-moves move-text)]
    (merge metadata moves)))


;; Initial board state
(def initial-board
  [["♜" "♞" "♝" "♛" "♚" "♝" "♞" "♜"]
   ["♟" "♟" "♟" "♟" "♟" "♟" "♟" "♟"]
   [" " " " " " " " " " " " " " " "]
   [" " " " " " " " " " " " " " " "]
   [" " " " " " " " " " " " " " " "]
   [" " " " " " " " " " " " " " " "]
   ["♙" "♙" "♙" "♙" "♙" "♙" "♙" "♙"]
   ["♖" "♘" "♗" "♕" "♔" "♗" "♘" "♖"]])

;; PGN input
(def sample-pgn-input "
[Event \"FIDE World Chess Championship 2021\"]
[Site \"Chess.com\"]
[Date \"2021.12.03\"]
[Round \"06\"]
[White \"Carlsen, Magnus\"]
[Black \"Nepomniachtchi, Ian\"]
[Result \"1-0\"]
[WhiteElo \"2855\"]
[BlackElo \"2782\"]
[TimeControl \"5400+30\"]

1. d4 Nf6 2. Nf3 d5 3. g3 e6 4. Bg2 Be7 5. O-O O-O 6. b3 c5 7. dxc5 Bxc5 8. c4 dxc4 9. Qc2 Qe7 10. Nbd2 Nc6 11. Nxc4 b5 12. Nce5 Nb4 13. Qb2 Bb7")


(defn parse-square [square]
  "Parse a chess square notation (e.g., d4) into row and column indices."
  (let [column (int (- (int (first square)) (int \a)))   ;; Convert 'a'..'h' to 0..7
        row    (- 8 (Integer. (str (second square))))]     ;; Convert '1'..'8' to 7..0
    [row column]))


;
;
;
;
;;; Function to get all intermediate squares between two points
;(defn get-intermediate-squares [from-row from-col to-row to-col]
;  (let [row-step (cond
;                   (> to-row from-row) 1
;                   (< to-row from-row) -1
;                   :else 0)
;        col-step (cond
;                   (> to-col from-col) 1
;                   (< to-col from-col) -1
;                   :else 0)]
;    (loop [row (+ from-row row-step)
;           col (+ from-col col-step)
;           squares []]
;      (if (and (not= row to-row) (not= col to-col))
;        (recur (+ row row-step) (+ col col-step) (conj squares [row col]))
;        squares))))
;
;;; Helper function to check if a move is within the board boundaries
;(defn on-board? [row col]
;  (println "on-board?" row ":" col)
;  (and (>= row 0) (< row 8) (>= col 0) (< col 8)))
;
;(defn move-piece [board from-row from-col to-row to-col]
;  "Move a piece from one square to another, ensuring the move is within bounds."
;  (if (on-board? to-row to-col)
;    (assoc-in board [to-row to-col] (get-in board [from-row from-col]))
;    board))  ;; Return the unchanged board if the move is out of bounds
;
;(defn valid-pawn-move? [board from-row from-col to-row to-col turn]
;  "Check if the pawn's move is valid."
;  ;; Logic for a pawn move (this can be more advanced, e.g., for capturing, en passant, etc.)
;  (if (= from-col to-col) ;; Same column
;    (if (or (= (Math/abs (- from-row to-row)) 1)
;            (and (= (Math/abs (- from-row to-row)) 2) ;; First move for pawn
;                 (or (= from-row 6) (= from-row 1)))) ;; Only allowed on first move for white/black pawns
;      true
;      false)
;    false)) ;; Not valid if moving to a different column
;
;(defn valid-knight-move? [board from-row from-col to-row to-col turn]
;  "Check if the knight's move is valid."
;  (let [row-diff (Math/abs (- from-row to-row))
;        col-diff (Math/abs (- from-col to-col))]
;    (and (or (= row-diff 2) (= col-diff 2))
;         (or (= row-diff 1) (= col-diff 1))))) ;; L-shaped move
;
;(defn valid-bishop-move? [board from-row from-col to-row to-col turn]
;  "Check if the bishop's move is valid."
;  (let [row-diff (Math/abs (- from-row to-row))
;        col-diff (Math/abs (- from-col to-col))]
;    (= row-diff col-diff))) ;; Diagonal move
;
;(defn valid-rook-move? [board from-row from-col to-row to-col turn]
;  "Check if the rook's move is valid."
;  (or (= from-row to-row) (= from-col to-col))) ;; Vertical or horizontal move
;
;(defn valid-queen-move? [board from-row from-col to-row to-col turn]
;  "Check if the queen's move is valid."
;  (or (valid-rook-move? board from-row from-col to-row to-col turn)
;      (valid-bishop-move? board from-row from-col to-row to-col turn))) ;; Queen moves like rook or bishop
;
;(defn valid-king-move? [board from-row from-col to-row to-col turn]
;  "Check if the king's move is valid."
;  (let [row-diff (Math/abs (- from-row to-row))
;        col-diff (Math/abs (- from-col to-col))]
;    (and (<= row-diff 1) (<= col-diff 1)))) ;; King moves one square in any direction
;
;;; Helper function to check castling conditions
;(defn valid-castling? [king-row king-col rook-row rook-col board]
;  (let [empty? (fn [r c] (= (get-in board [r c]) " "))]
;    (and
;      (empty? (king-row king-col))
;      (empty? (rook-row rook-col))
;      (not (get-in board [king-row king-col]))
;      (not (get-in board [rook-row rook-col]))
;      ;; ensure all squares between king and rook are empty
;      )))

(defn -main [& args]
  (let [moves (:moves (pgn.lib/parse-pgn pgn.lib/sample-pgn-input))]
    (println moves)))