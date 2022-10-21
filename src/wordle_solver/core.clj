; (Use 'wordle-solver.core :reload) ;; LAZY RELOAD
(ns wordle-solver.core
  (:gen-class))

(require '[wordle-solver.data :as data])
(require '[clojure.string :as str])
(require '[clojure.pprint])


;; SECTION: CORE

;; MASKS
(def BLACK 0)
(def YELLOW 1)
(def GREEN 2)

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

; returns () or (\a \d \d) etc.
(defn apply-mask-to-word [mask-list seq-word val]
  (filter identity (map #(if (= val %1) %2) mask-list seq-word)))

(defn calculate-entropy-numerator [result-set]
  (let [c (count result-set)]
    (if (= 0 c) 
				0 
        (let [answer
           (/ (* (Math/log c) c) (Math/log 2))]
           ;(println "Entropy on " result-set " is" answer)
           answer))))

;; TODO fix this - there shouldn't be dupes, I would think.
;; takes input like ((shown) () () () () ... ) and sees if there's a single word that matches.
(defn -results-match-single-word? [w-word l-matching-word-seq]
  (= w-word (first (first (filter (complement empty?) l-matching-word-seq)))))

;; evaluate-move takes global mask list, l-answers, and "passe" to one row of r-evals
 ;; ["passe"
  ; {:entropy 0.3333333333333333,
  ;  :matches
  ;  {(0 2 0 0 0) ("dacha" "hairy"),
  ;   (0 2 2 2 2) ("masse"),
  ;   (0 1 2 1 1) ("asses"),
  ;   (2 2 2 2 2) ("passe"),
  ;   (0 1 0 0 0) ("bland")}}]



; == Section: guess, answer to mask  ==
(defn -letter-counts [w-word]
  (let [m (group-by identity w-word)]
    (zipmap 
		    (keys m) 
		    (map count (vals m)))))

(defn -nil-or-zero? [v]
    (or (nil? v) (zero? v)))


; NOTE: Could use loop/recur but the stack will only be five. 
(defn -guess-and-answer-to-mask-helper [w-guess w-answer counts-remaining accum]
  (if (empty? w-guess) accum
    (let [g (first w-guess)
          a (first w-answer)
          r-g (rest w-guess)
          r-a (rest w-answer)]
      (if (= g a)
        (-guess-and-answer-to-mask-helper r-g r-a (update counts-remaining g dec) (concat accum (list GREEN)))
        (if (-nil-or-zero? (get counts-remaining g))
            (-guess-and-answer-to-mask-helper r-g r-a counts-remaining (concat accum (list BLACK)))
            (-guess-and-answer-to-mask-helper r-g r-a (update counts-remaining g dec) (concat accum (list YELLOW))))))))

(defn guess-and-answer-to-mask [w-guess w-answer]
    (-guess-and-answer-to-mask-helper 
        w-guess
        w-answer
        (-letter-counts w-answer)
        '()))

(defn pred-guess-produces-mask-with-answer [w-guess m-mask w-answer]
  (= m-mask (guess-and-answer-to-mask w-guess w-answer)))

;; sorted list of evaluate-move mapped to each guess in allowed-guess
;; ["aahed"
;;  {:entropy 7.78318320736531353,
;;   :matches
;;   {(2 0 2 2 0) ("ashen"),
;;     (2 0 2 0 0) ("abhor"),
;;    (1 2 0 2 1) ("cadet" "laden"),
;; TODO Apply this to evaluate-move
(defn generate-empty-eval [l-allowed-guesses]
  (map (fn [g]
     [g {:entropy 0 :matches {}}]) l-allowed-guesses))


;; TODO - This generates the move's row in l-results
;; NOTE: l-answers empty returns all 0, no matches
;; Also note - this one won't have keys for masks with no matching answers
(defn evaluate-move [l-answers w-guess]
   (let [matching-words (group-by (partial guess-and-answer-to-mask w-guess) l-answers)
         total-words (count l-answers)
         n-entropy (cond 
                       (= 0 total-words) 
                         0 ;; an error state - should not get here unless l-answers empty

                       ;; NOTE: could probably short-circuit here with just (= 1 total-words)
                       ;; There should only be one bucket (specifically, '(2 2 2 2 2)) 
                       ;; if the dictionary count is 1.
                       (and 
                           (= 1 total-words) 
                           (-results-match-single-word? w-guess (vals matching-words)))
                       		-100 ;; NOTE: A match! 
                    	 :else 
                           (/
                              (apply +
                              (map calculate-entropy-numerator
                         			  (vals matching-words)))
                                total-words))]
    {:entropy n-entropy
     :matches matching-words}))

;; Call evaluate-move on every possible guess to 
;;   get l-result row for every l-allowed-guess.
;; Sort by entropy (ascending) and return
(defn evaluate-all-moves [l-answers l-allowed-guesses]
  ;; NOTE - this is just for convenience.  The harness shouldn't allow for
  ;; any empty result sets.
  (if (empty? l-answers) (generate-empty-eval l-allowed-guesses)
    (let [results (zipmap
                   l-allowed-guesses
                   (pmap (partial evaluate-move l-answers)
                         l-allowed-guesses))
       	  sorted-results (sort #(< (:entropy (second %1))
                                   (:entropy (second %2))) results)]
    		sorted-results)))

;; NOTE - there's probably a better way to clean out empties from the
;;  visual print
;; TODO - this may be moot now.  Remove? 
(defn clean-results-row [r-row]
  (let [mymap (:matches (second r-row))]
    [(first r-row)
      {
        :entropy (:entropy (second r-row))
       :matches (select-keys mymap (for [[k v] mymap
                                         :when (not (empty? v))] k))
      }]))


;; takes result type (["guess" {:entropy 1.23 :matches {(1 0 0 0 0) ("right" "wrong")] ... )
;; returns a single row of type: ["guess" {:entropy 1.23 :matches {(1 0 0 0 0) ("right" "wrong")]
;; TODO Perhaps result type should be a map (sorted map?), since this just
;; searches for the *one row* we know should be there.
(defn extract-row-from-results [r str-word]
  (first (filter #(= str-word (first %)) r)))

;; removes :matches from result row.
;; 
;; wordle-solver.core=> (pprint (take 10 (just-words-and-entropy r-evals-1)))
;; (("clint" [:entropy 2.0072359492575713])
;; ... )

(defn just-words-and-entropy [r]
  (map #(list (first %) (first (second %))) r))

;; filters words-and-entropy to only words in provided answer set
;; 
;; wordle-solver.core=> (pprint (take 10 (viable-answer-words l-answers-1 r-evals-1)))
;; (("lunch" [:entropy 2.283230818590316])
;; ... )

;; NOTE: In "hard mode", the next guess HAS to come from here.
;; In "easy mode", the actual best guess could be (almost?) anything in the dictionary
(defn viable-answer-words [l-answers r] (filter #(get (set l-answers) (first %))
                                    (just-words-and-entropy r)))

(defn -nil-to-empty [r]
  (if (nil? r) '() r))
;; finds the possible result set remaining after playing a guess.
;; extracts from pre-generated result types.
;; returns a list of possible answers when playing the guess.
;; TODO - this doesn't use the first two arguments.  It requires r-evals to be current.
;; TODO - this extracts the w-guess row from the already-evaluated r-evals
(defn play-move [w-guess r-evals l-mask]
  (let [entry (extract-row-from-results r-evals w-guess)] 
    (if (nil? entry) nil
        (-> entry second :matches (get l-mask) -nil-to-empty))))

(defn result-set-to-map [l-result-set]
  (zipmap (map first l-result-set)
                (map (comp second second) l-result-set)))

(defn -sum-entropies [l-result-sets]
  (let [l-result-maps (map result-set-to-map l-result-sets)
        total-scores (apply merge-with + l-result-maps)
        sorted-results (sort (fn [[k1 v1] [k2 v2]] (< v1 v2)) total-scores)]
			  sorted-results))

;; TODO Consider returning answer lists for debugging
(defn play-moves [l-allowed-guesses w-word l-response-masks l-results]
  (let [l-new-answer-lists (map 
                             (partial play-move w-word) 
                             l-results 
                             l-response-masks)
        l-new-result-sets (map #(evaluate-all-moves % l-allowed-guesses)
                               l-new-answer-lists)
  ]
  (list 
     l-new-result-sets
     l-new-answer-lists
     )))
