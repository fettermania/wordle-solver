; (use 'wordle-solver.core :reload) ;; LAZY RELOAD

(ns wordle-solver.core
  (:gen-class))

(require '[clojure.string :as str])
(require '[clojure.set])

;; LOAD 
(def answers-filename "wordle-answers.txt")
(def allowed-guesses-filename "wordle-allowed-guesses.txt")

(def dict-answers (str/split (slurp answers-filename) #"\n"))
(def dict-allowed-guesses (str/split (slurp allowed-guesses-filename) #"\n"))

(def test-answers-filename "test-wordle-answers.txt")
(def test-allowed-guesses-filename "test-wordle-allowed-guesses.txt")

(def test-dict-answers (str/split (slurp test-answers-filename) #"\n"))
(def test-dict-allowed-guesses (str/split (slurp test-allowed-guesses-filename) #"\n"))


;; MASKS
(def BLACK 0)
(def YELLOW 1)
(def GREEN 2)

; returns () or (\a \d \d) etc.
(defn apply-mask-to-word [mask-list seq-word val]
  (filter identity (map #(if (= val %1) %2) mask-list seq-word)))



(defn calculate-entropy-numerator [result-set]
  (let [
  			c (count result-set)]
    (if (= 0 c) 0 
    			(let [answer
           (/ (* (Math/log c) c) (Math/log 2))]
           ;(println "Entropy on " result-set " is" answer)
           answer))))

;; TODO fix this - there shouldn't be dupes, I would think.
;; takes input like ((shown) () () () () ... ) and sees if there's a single word that matches.
(defn -results-match-single-word? [w-word l-matching-word-seq]
	 	(= w-word (first (first (filter (complement empty?) l-matching-word-seq)))))

;; evaluate-move takes global mask list, dict-answers, and "passe" to one row of r-evals
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





;; NOTE: dict-answers empty returns all 0, no matches
;; Also note - this one won't have keys for masks with no matching answers
(defn evaluate-move [dict-answers w-word]
  (let [
  			matching-words (group-by (partial guess-and-answer-to-mask w-word) dict-answers)
        total-words (count dict-answers)
       
        n-entropy (cond 
        							(= 0 total-words) 
        							  0 ;; an error state - should not get here unless dict-answers empty
        							(and 
        							  	(= 1 total-words) 
        								  (-results-match-single-word? w-word (vals matching-words)))
      							    -100 ;; NOTE: A match! 
      							  :else 
											  (/
													(apply +
							               (map calculate-entropy-numerator
		      			              (vals matching-words)))
												total-words))]
    {:entropy n-entropy
     :matches matching-words}))

;; sorted list of evaluate-move mapped to each guess in allowed-guesues:
;; ["aahed"
;;  {:entropy 7.78318320736531353,
;;   :matches
;;   {(2 0 2 2 0) ("ashen"),
;;     (2 0 2 0 0) ("abhor"),
;;    (1 2 0 2 1) ("cadet" "laden"),
(defn evaluate-all-moves [l-answers l-allowed-guesses]
  (let [results (zipmap
                 l-allowed-guesses
                 (pmap (partial evaluate-move l-answers)
                      l-allowed-guesses))
        sorted-results (sort
                        #(< (:entropy (second %1)) (:entropy (second %2)))
                        results)]
    sorted-results))



;; takes result type (["guess" {:entropy 1.23 :matches {(1 0 0 0 0) ("right" "wrong")] ... )
;; returns a single row of type: ["guess" {:entropy 1.23 :matches {(1 0 0 0 0) ("right" "wrong")]
(defn extract-row-from-results [r str-word]
  (first (filter #(= str-word (first %)) r)))

;; removes :matches from result row.
(defn just-words-and-entropy [r]
  (map #(list (first %) (first (second %))) r))

;; filters words-and-entropy to only words in provided answer set
(defn viable-answer-words [l-answers r] (filter #(get (set l-answers) (first %))
                                    (just-words-and-entropy r)))




;; finds the possible result set remaining after playing a guess.
;; extracts from pre-generated result types.
;; TODO - this doesn't use the first two arguments.  It requires r-evals to be current.
(defn play-move [w-guess r-evals l-mask]
  (let [entry (extract-row-from-results r-evals w-guess)] 
    (if (nil? entry) nil
        (-> entry second :matches (get l-mask)))))

;; SECITON: Quordle time


(defn result-set-to-map [l-result-set]
  (zipmap (map first l-result-set)
  				(map (comp second second) l-result-set)))

(defn -sum-entropies [l-result-sets]
  (let [l-result-maps (map result-set-to-map l-result-sets)
  			total-scores (apply merge-with + l-result-maps)
  			 sorted-results (sort (fn [[k1 v1] [k2 v2]] (< v1 v2)) total-scores)
  ]
  sorted-results))


;; SECTION: Cheater tools
;; -  cutting down initial set if you have other information.

(defn intersect-blocks [a b]
   (into '()  (clojure.set/intersection (set a) (set b))))

(defn drop-nth-from-seq [n seq-w]
  (concat
    (take n seq-w)
    (drop (inc n) seq-w)))


(defn select-from-word [indices w]
  (let [s (seq w)]
  (map #(nth s %) indices)))


(defn select-similar-block [l-greens min-ct l-answers] 
 (mapcat 
   second
   (filter
     (fn [[k v]] (>= (count v) min-ct))
       (group-by (partial select-from-word l-greens)
     dict-answers))))

(defn select-anagrams [l-answers] 
 (mapcat 
   second
   (filter
     (fn [[k v]] (>= (count v) 2))
       (group-by (comp seq sort)
                 dict-answers))))

