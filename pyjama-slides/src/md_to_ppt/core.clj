(ns md-to-ppt.core
 (:require [clojure.string :as str]
           [clojure.java.io :as io])
 (:import (org.apache.poi.xslf.usermodel XMLSlideShow XSLFSlide XSLFTextBox
                                         XSLFTextRun XSLFTextParagraph XSLFTable XSLFTableRow XSLFTableCell
                                         XSLFPictureShape XSLFPictureData)
          (org.apache.poi.sl.usermodel PictureData$PictureType VerticalAlignment)
          (java.awt Rectangle Color)
          (java.io FileInputStream FileOutputStream)))

;; -----------------------------
;; Parsing
;; -----------------------------

(defn parse-md [filepath]
 (with-open [rdr (io/reader filepath)]
  (let [lines (line-seq rdr)]
   (loop [remaining lines
          slides []
          current nil
          in-code? false
          code-block []
          ]
    (if (empty? remaining)
     (if current (conj slides current) slides)
     (let [line (first remaining)
           header-match (re-find #"^(#+) (.+)$" line)
           img-match (re-find (re-pattern "!\\[.*?\\]\\((.*?)\\)") line)
           is-table (re-matches #"\|.*\|" line)
           is-code-start (re-matches #"^```(\w+)?$" line)
           is-code-end (re-matches #"^```$" line)]

      (cond
       ;; start code block
       (and is-code-start (not in-code?))
       (recur (rest remaining) slides current true [])

       ;; end code block
       (and is-code-end in-code?)
       (recur (rest remaining)
              slides
              (update current :code-blocks (fnil conj []) {:lang "code" :code code-block})
              false
              [])

       ;; inside code block
       in-code?
       (recur (rest remaining) slides current true (conj code-block line))

       ;; new header
       header-match
       (let [[_ hashes title] header-match
             level (count hashes)]
        (recur (rest remaining)
               (if current (conj slides current) slides)
               {:type (if (= level 1) :title :slide)
                :title title
                :level level
                :body []
                :tables []
                :images []
                :code-blocks []}
               false
               []))

       ;; image
       img-match
       (let [img-path (second img-match)]
        (recur (rest remaining)
               slides
               (update current :images conj img-path)
               false
               []))

       ;; table
       is-table
       (let [[table-lines rest-lines] (split-with #(re-matches #"\|.*\|" %) remaining)
             rows (->> table-lines
                       (map #(->> (str/split % #"\|")
                                  (map str/trim)
                                  (remove str/blank?))))]
        (recur rest-lines
               slides
               (update current :tables conj rows)
               false
               []))

       ;; normal line
       :else
       (recur (rest remaining)
              slides
              (update current :body conj line)
              false
              []))))))))

 ;; -----------------------------
 ;; Formatting Helpers
 ;; -----------------------------

 (defn format-run [^XSLFTextParagraph para text]
  (let [tokens (-> text
                   (str/replace "**" "__B__")
                   (str/replace "*" "__I__")
                   (str/replace "`" "__C__")
                   (str/split #"(__[BIC]__)"))]
   (loop [segments tokens
          state {:bold false :italic false :code false}]
    (when (seq segments)
     (let [seg (first segments)]
      (cond
       (= seg "__B__") (recur (rest segments) (update state :bold not))
       (= seg "__I__") (recur (rest segments) (update state :italic not))
       (= seg "__C__") (recur (rest segments) (update state :code not))
       :else
       (let [run (.addNewTextRun para)]
        (.setText run seg)
        (.setFontSize run (if (:code state) 14.0 16.0))
        (.setFontFamily run (if (:code state) "Fira Code" "Inter"))
        (.setBold run (:bold state))
        (.setItalic run (:italic state))
        (recur (rest segments) state))))))))

 ;; -----------------------------
 ;; Slide Generation
 ;; -----------------------------

 (defn add-slide [ppt {:keys [type title body images tables code-blocks]}]
  (let [slide (.createSlide ppt)]
   ;; Title
   (when (some? title)
    (let [title-box (.createTextBox slide)
          para (.addNewTextParagraph title-box)
          run (.addNewTextRun para)]
     (.setAnchor title-box (Rectangle. 50 30 600 60))
     (.setText run (if (str/blank? title) " " title))
     (.setFontSize run 20.0)
     (.setBold run true)
     (.setFontFamily run "Inter")))
   ;
   ;(let [title-box (.createTextBox slide)]
   ; (.setAnchor title-box (Rectangle. 50 30 600 60))
   ; (let [para (.addNewTextParagraph title-box)
   ;       run (.addNewTextRun para)]
   ;  (.setText run title)
   ;  (.setFontSize run 28.0)
   ;  (.setBold run true)
   ;  (.setFontFamily run "Inter")))

   ;; Body text
   (when (seq body)
    (let [box (.createTextBox slide)]
     (.setAnchor box (Rectangle. 50 100 600 200))
     (doseq [line body]
      (let [para (.addNewTextParagraph box)]
       (cond
        (re-find #"^\s*[-*] " line)
        (do
         (.setBullet para true)
         (let [txt (str/replace line #"^\s*[-*] " "")]
          (format-run para txt)))

        :else
        (format-run para line))))))

   ;; Code blocks
   (doseq [{:keys [lang code]} code-blocks]
    (let [box (.createTextBox slide)]
     (.setAnchor box (Rectangle. 60 310 600 100))
     (let [para (.addNewTextParagraph box)]
      (.setBullet para false)
      (let [run (.addNewTextRun para)]
       (.setText run (str/join "\n" code))
       (.setFontFamily run "Fira Code")
       (.setFontSize run 12.0)
       (.setBold run false)
       (.setItalic run false)))))

   ;; Images
   (doseq [img-path images]
    (try
     (let [file (io/file img-path)
           data (.addPicture ppt (io/input-stream file) PictureData$PictureType/PNG)
           pic (.createPicture slide data)]
      (.setAnchor pic (Rectangle. 400 200 200 150)))
     (catch Exception e
      (println "⚠️  Failed to load image:" img-path))))

   ;; Tables
   (doseq [table tables]
    (let [rows (count table)
          cols (count (first table))
          ppt-table (.createTable slide rows cols)]
     (.setAnchor ppt-table (Rectangle. 60 420 500 100))
     (doseq [r (range rows)
             c (range cols)]
      (let [cell (.getCell ppt-table r c)
            value (get-in table [r c])
            para (.addNewTextParagraph cell)
            run (.addNewTextRun para)
            ]
       (.setText run value)
       (.setFontSize run 11.0)
       (.setFontFamily run "Inter")
       (when (= r 0)
        (.setBold run true))
       (.setVerticalAlignment cell VerticalAlignment/MIDDLE)
       (when (= r 0)
        (.setBold (.addNewTextRun para) true))
       ;(doto cell
       ; (.setBorderWidthTop 1.0)
       ; (.setBorderWidthBottom 1.0)
       ; (.setBorderWidthLeft 1.0)
       ; (.setBorderWidthRight 1.0))
       ))))))

 ;; -----------------------------
 ;; Entry Point
 ;; -----------------------------

 (defn md-to-ppt [md-path]
  (let [slides (parse-md md-path)
        ppt (XMLSlideShow.)
        out-path (str/replace md-path #"\.md$" ".pptx")]
   (doseq [slide slides]
    (add-slide ppt slide))
   (with-open [out (FileOutputStream. out-path)]
    (.write ppt out))
   (println "✅ PowerPoint created at:" out-path)))

 (defn -main [& args]
  (if-let [md-path (first args)]
   (md-to-ppt md-path)
   (println "Usage: clj -M -m md-to-ppt.core file.md")))
