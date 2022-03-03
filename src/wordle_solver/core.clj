; (use 'wordle-solver.core :reload)

(ns wordle-solver.core
  (:gen-class))

(require '[clojure.string :as str])
(require '[clojure.set])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))


;; Answers: w_a = 2315
;; Guesses: w_g = 12972

;; LOAD 
(def answers-filename "wordle-answers.txt")
(def allowed-guesses-filename "wordle-allowed-guesses.txt")

(def dict-answers (str/split (slurp answers-filename) #"\n"))
(def dict-allowed-guesses (str/split (slurp allowed-guesses-filename) #"\n"))


;; MASKS
(def BLACK 0)
(def YELLOW 1)
(def GREEN 2)

(def w-word "aback")
(def seq-chars (seq w-word))
(def mask-list (list BLACK YELLOW GREEN YELLOW BLACK))

; returns () or (\a \d \d) etc.
(defn apply-mask-to-word [mask-list seq-word val]
  (filter identity (map #(if (= val %1) %2) mask-list seq-word)))

;; TODO This is gross and not general but who cares
(defn generate-all-mask-lists [n]
  (if (= 1 n) '((0) (1) (2))
      (let [children (generate-all-mask-lists (dec n))
            result
            (reduce concat (list 
             (map #(conj % BLACK) children)
             (map #(conj % YELLOW) children)
             (map #(conj % GREEN) children)))]
        result)))

(def all-mask-lists (generate-all-mask-lists 5))

(defn _gen-includer-filter [letter]
  (partial filter #(str/includes? % letter)))

; accepts sequences of chars
(defn gen-includer-filters [yellow-list]
  (reduce comp
          (vec
           (map _gen-includer-filter
                (map str yellow-list)))))

(defn _generate-regex-entry [black-list mask-elt word-elt]
  (cond (= mask-elt GREEN) word-elt
        (= mask-elt YELLOW) (apply str "[^" black-list word-elt "]")
        :else   (apply str "[^" black-list "]")))

;; TODO START HERE - not a list?/
(defn filter-fn-from-mask-list-and-word [w-word mask-list]
  (let [seq-word (seq w-word)
        black-list (apply-mask-to-word mask-list seq-word BLACK)
        yellow-list (apply-mask-to-word mask-list seq-word  YELLOW)
        green-list (apply-mask-to-word mask-list seq-word GREEN)
        regex-str (apply str
                         (map (partial _generate-regex-entry (apply str black-list))
                              mask-list w-word))
        filter-regex (partial filter #(re-matches (re-pattern regex-str) %))
        filter-includes (gen-includer-filters yellow-list)
        ]
;        [regex-str yellow-list]
        (comp filter-regex filter-includes)
        ))
        

;  (map #((filter-fn-from-mask-list-and-word w-word %) dict-answers) all-mask-lists)


(def fn-d (partial filter #(str/includes? % "D")))
(def fn-reg1 #(filter (fn [w] (re-matches #"[^ER][^ER]A[^S]E" w)) %))

(defn calculate-entropy-numerator [result-set]
  (let [c (count result-set)]
    (cond (= 0 c) 0
          (= 1 c) (- c)
          :else  (/ (* (Math/log c) c) (Math/log 2)))))

(defn evaluate-move [all-mask-lists dict-answers w-word]
  (let [matching-words (map
                        #((filter-fn-from-mask-list-and-word w-word %)
                          dict-answers) all-mask-lists)
        ;; Klugey fix here.  Total words should always be dict-answers
        ;; but some patterns overlap so words are doubled.
        ;; This at least fixes the denominator
        total-words (apply + (map count matching-words))
        n-entropy-numerator (apply +
                                   (map calculate-entropy-numerator
                                        matching-words))
        n-entropy (/ n-entropy-numerator total-words)
        ]
    {:entropy n-entropy
     :matches (zipmap all-mask-lists matching-words)}))

;; sorted list of:
;; ["aahed"
;;  {:entropy 7.7831832073653135,
;;   :matches
;;   {(2 0 2 2 0) ("ashen"),
;;     (2 0 2 0 0) ("abhor"),
;;    (1 2 0 2 1) ("cadet" "laden"),
(defn evaluate-all-moves [dict-answers dict-allowed-guesses]
  (let [all-mask-lists (generate-all-mask-lists 5)
        results (zipmap
                 dict-allowed-guesses
                 (map (partial evaluate-move all-mask-lists dict-answers)
                      dict-allowed-guesses))
        sorted-results (sort
                        #(< (:entropy (second %1)) (:entropy (second %2)))
                        results)]
    sorted-results))

;; ["soare"
;;  {:entropy 5.281333128027922,
;;   :matches
;;   {(2 0 2 2 0)
;;    ("scarf"
;;     "scary"
;;     "shard"
;;     "shark"
;;     "sharp"
;;     "smart"
;;     "snarl"
;;     "spark"
;;     "stark"
;;     "start"
   ;;     "swarm"),

;; returns dict-answers
(defn extract-row-from-results [r str-word]
  (filter #(= str-word (first %)) r))

(defn just-words-and-entropy [evaluations]
  (map #(list (first %) (first (second %))) evaluations))

(defn play-move [dict-answers dict-allowed-guesses r-evals str-word l-mask]
  (let [entry (extract-row-from-results r-evals str-word)] 
    (if (nil? entry) nil
        (-> entry first second :matches (get l-mask)))))



#_(do
(def l-answers dict-answers)
(def l-allowed-guesses dict-allowed-guesses)
(def r-top   (evaluate-all-moves l-answers l-allowed-guesses))
(def r-evals r-top)
)

#_(do
    
    ;; NOTE: In Hard mode, l-allowed-guesses are CLOSE to l-answers
    ;; There are also "black-disqualified answers" that
    ;; are still legal moves
;    (def l-allowed-guesses l-answers)
  (evaluate-all-moves l-answers l-allowed-guesses)
  (def r-evals *1)
  (pprint (take 10 (just-words-and-entropy r-evals)))

  (def viable-answer-words  (filter #(get (set l-answers) (first %))
                                    (just-words-and-entropy r-evals)))
  (pprint (take 10 viable-answer-words))
  
  (def w-word "court")
  (def response-mask '(0 2 2 2 0))
  (play-move l-answers l-allowed-guesses r-evals w-word response-mask)
  (def l-answers *1)
  
)

;; (pprint (take 10 (just-words-and-entropy r-evals)))
;;(pprint (extract-row-from-results r-evals "cairn"))
; (clojure.pprint/pprint *map* (clojure.java.io/writer "foo.txt"))


;; NOTE A bug still exists
;; DACHA vs. HAIRY
;; 0 2 0 1 1 = [^DC]A[^DC][^HDC] includes A, H
;; (correct one) -> 0 2 0 1 0 = [^DCA]A[^DCA][^HDCA][^DCA] includes H
;; but hairy will show up in both sets as "matching" 

;; TODO Another bug - an answer set with one option makes everything -1 (b/c you know the answer!)

;; TODO Evaluate all moves - include "

;; TODO Did the word list change to exclude SORAE on 1/27?

;; TODO Filter results to include hard-mode-acceptable words
;;  (pprint (take 20
;;  (filter #(-> % first seq second (= \o))
;; (just-words-and-entropy r-evals))))
;; (filter #(get (set l-answers) (first %)) (just-words-and-entropy r-evals))


(defn drop-nth-from-seq [n seq-w]
  (concat
    (take n seq-w)
    (drop (inc n) seq-w)))

#_(do 
(def viable-answers (filter #(-> % first seq second (= \o))
                            (filter #(get (set l-answers) (first %))
                                    (just-words-and-entropy r-evals))))

(def viable-guesses-all (filter #(-> % first seq second (= \o))
                                    (just-words-and-entropy r-evals)))

(def viable-answer-words  (filter #(get (set l-answers) (first %))
                                    (just-words-and-entropy r-evals)))


                                        ; What words have five or more common endings?

;; TODO
(defn select-from-word [indices w]
  (let [s (seq w)]
  (map #(nth s %) indices)))

#_(do
(def endings-map (group-by rest dict-answers))
(def starters-map (group-by (partial take 4) dict-answers))

(def dict-answers  (into '()  (clojure.set/intersection (set three-or-more-ender-words) (set two-or-more-starter-words))))
)

(def block-24
(mapcat
 second
 (filter
  (fn [[k v]] (> (count v) 3))
  (group-by (partial select-from-word '(2 4)) dict-answers))))

(def block-014
(mapcat
 second
 (filter
  (fn [[k v]] (> (count v) 1))
  (group-by (partial select-from-word '(0 1 4)) dict-answers))))

(def block-124
(mapcat
 second
 (filter
  (fn [[k v]] (> (count v) 1))
  (group-by (partial select-from-word '(1 2 4)) dict-answers))))

(def l-answers-2-24 (into '() (clojure.set/intersection (set block-012) (set block-014) (set block-124))))

    
#_(do
    (def gbggg-words
      (mapcat second
(filter (fn [[k v]] (> (count v) 2)) (group-by #(drop-nth-from-seq 1 (seq %)) dict-answers)))
   ))
  (def l-answers gbggg-words)
)

;; ("skill" "spill" "swill" "silly" "sully")
;;(zipmap (map first viable-answer-words)
  ;;      (map (partial extract-row-from-results r-evals) (map first viable-answer-words)))

;; Another TODO - serialize and unserliaze (perhaps removing empties?
