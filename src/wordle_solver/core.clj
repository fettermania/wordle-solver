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

G
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

(defn calculate-entropy [count-dict result-set]
  (let [c (count result-set)]
    (cond (= 0 c) 0
          (= 1 c) (/ (- c) count-dict)
        :else  (/ (* (Math/log c) c)
                  (Math/log 2) ; NOTE not strictly necessary
                  count-dict))))

(defn evaluate-move [all-mask-lists dict-answers w-word]
  (let [matching-words (map
                        #((filter-fn-from-mask-list-and-word w-word %)
                          dict-answers)
                        all-mask-lists)
        n-entropy (apply +
                      (map (partial calculate-entropy (count dict-answers))
                       matching-words))
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
  (evaluate-all-moves dict-answers dict-allowed-guesses)
  (def r-evals *1)
  (pprint (take 10 (just-words-and-entropy r-evals)))
  (play-move dict-answers dict-allowed-guesses r-evals "raise" '(1 2 2 0 0))
  (def dict-answers *1)
)

;;(pprint (extract-row-from-results r-evals "cairn"))
; (clojure.pprint/pprint *map* (clojure.java.io/writer "foo.txt"))


;; NOTE A bug still exists
;; DACHA vs. HAIRY
;; 0 2 0 1 1 = [^DC]A[^DC][^HDC] includes A, H
;; (correct one) -> 0 2 0 1 0 = [^DCA]A[^DCA][^HDCA][^DCA] includes H
;; but hairy will show up in both sets as "matching" 

