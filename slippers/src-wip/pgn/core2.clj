(ns pgn.core2
  (:require [clojure.string :as str]))

(defn locate-piece [board piece dest disambiguation color]
  (let [dest-x (- (int (first dest)) (int \a))
        dest-y (- 8 (Integer. (str (second dest)))) ;; Convert rank from '1' to '8' to y-coordinate
        valid-pieces (for [x (range 8) y (range 8)
                           :when (= (get-in board [y x]) piece)] ;; Locate pieces
                       [x y])]
    (if disambiguation
      (filter #(or (= (first %) dest-x) ;; File disambiguation
                   (= (second %) dest-y)) ;; Rank disambiguation
              valid-pieces)
      valid-pieces)))

(defn parse-move [move]
  "Parses a PGN move notation and returns a map with relevant details."
  (let [pattern #"^([KQRBN])?([a-h1-8]{1,2})?(x)?([a-h][1-8])(?:=([QRBN]))?(\+|#)?$"
        match (re-matches (re-pattern pattern) move)]
    (when match
      {:type (if (nth match 1) :piece :pawn)  ;; Default to :pawn if no piece is given
       :piece (nth match 1)  ;; Piece type (K, Q, R, B, N) or nil for pawns
       :disambiguation (nth match 2)  ;; Optional disambiguation (e.g., "e" in exd5, "1" in R1e5)
       :capture (some? (nth match 3))  ;; True if 'x' is present
       :dest (nth match 4)  ;; Destination square
       :promotion (nth match 5)  ;; Optional promotion (e.g., "Q" in e8=Q)
       :checkmate (cond
                    (= (nth match 6) "#") :mate
                    (= (nth match 6) "+") :check
                    :else nil)})))  ;; Capture check (+) or checkmate (#)


(defn apply-move [board move]
  (let [{:keys [type piece dest disambiguation promotion]} (parse-move move)
        color (if (some #(= "♔" %) (flatten board)) :white :black)
        _ (println type piece dest color)
        piece (case piece
                ; (if (= color :white) "♙" "♟")
                "K" (if (= color :white) "♔" "♚")
                "Q" (if (= color :white) "♕" "♛")
                "R" (if (= color :white) "♖" "♜")
                "B" (if (= color :white) "♗" "♝")
                "N" (if (= color :white) "♘" "♞")
                (or "P" nil) (if (= color :white) "♙" "♟"))]

    (cond
      (= type :castle-kingside)
      (let [row (if (= color :white) 7 0)]
        (-> board
            (assoc-in [row 4] nil) ;; Remove king
            (assoc-in [row 6] piece) ;; Place king
            (assoc-in [row 7] nil) ;; Remove rook
            (assoc-in [row 5] (if (= color :white) "♖" "♜")))) ;; Place rook

      (= type :castle-queenside)
      (let [row (if (= color :white) 7 0)]
        (-> board
            (assoc-in [row 4] nil) ;; Remove king
            (assoc-in [row 2] piece) ;; Place king
            (assoc-in [row 0] nil) ;; Remove rook
            (assoc-in [row 3] (if (= color :white) "♖" "♜")))) ;; Place rook

      :else
      (let [[src-x src-y] (first (locate-piece board piece dest disambiguation color))
            dest-x (- (int (first dest)) (int \a))
            dest-y (- 8 (Integer. (str (second dest))))]
        (-> board
            (assoc-in [src-y src-x] nil) ;; Remove piece from source
            (assoc-in [dest-y dest-x] (if promotion
                                        (case promotion
                                          "Q" (if (= color :white) "♕" "♛")
                                          "R" (if (= color :white) "♖" "♜")
                                          "B" (if (= color :white) "♗" "♝")
                                          "N" (if (= color :white) "♘" "♞"))
                                        piece)))))))

(defn play-game [pgn]
  (let [initial-board [["♜" "♞" "♝" "♛" "♚" "♝" "♞" "♜"]
                       ["♟" "♟" "♟" "♟" "♟" "♟" "♟" "♟"]
                       [nil nil nil nil nil nil nil nil]
                       [nil nil nil nil nil nil nil nil]
                       [nil nil nil nil nil nil nil nil]
                       [nil nil nil nil nil nil nil nil]
                       ["♙" "♙" "♙" "♙" "♙" "♙" "♙" "♙"]
                       ["♖" "♘" "♗" "♕" "♔" "♗" "♘" "♖"]]
    moves (map str/trim (str/split pgn #" "))]
(reduce apply-move initial-board moves)))

(defn print-board [board]
  (doseq [row board]
    (println (apply str (map #(or % "·") row)))))

(defn -main [& args]
  (let [final-board (play-game "d4 Nf6 Nf3 d5 g3 e6 ")]
    (print-board final-board))
  )