(ns core3
  (:require [pgn.lib :refer :all]))

(defn resolve-ambiguity [candidates move board]
  "Resolves ambiguity when multiple pieces can move to the same square."
  (let [file-hint (some #(Character/isLetter %) move)
        rank-hint (some #(Character/isDigit %) move)]
    (first (filter (fn [[row col]]
                     (and (or (not file-hint) (= col (file->col file-hint)))
                          (or (not rank-hint) (= row (rank->row rank-hint)))))
                   candidates))))

(defn find-pieces [board piece turn destination]
  "Finds all pieces of the specified type that can move to the destination square."
  (let [[dest-row dest-col] destination]
    (for [row (range 8)
          col (range 8)
          :let [p (get-in board [row col])]
          :when (and (= p piece)
                     (= turn (piece-owner p))
                     (valid-move? board row col dest-row dest-col turn))]
      [row col])))

(defn parse-move [move turn board]
  "Parses a chess move and determines the piece, from-square, and to-square."
  (let [destination (parse-square move) ;; Destination always exists
        piece (if (Character/isUpperCase (first move))
                (first move) ;; Explicit piece type (e.g., N, B, R)
                \P)          ;; Default to pawn if no piece type
        candidates (find-pieces board piece turn destination)]
    (if (= 1 (count candidates))
      (first candidates)
      (resolve-ambiguity candidates move board))))



(defn -main [& args]
  (let [moves (:moves (pgn.lib/parse-pgn pgn.lib/sample-pgn-input))
        turns (cycle ["white" "black"])
        final-board (reduce
                      (fn [[board turn] move]
                        [(apply-move board turn move) (first (rest turn))])
                      [pgn.lib/initial-board "white"]
                      moves)]
    (pgn.lib/print-board (first final-board))))