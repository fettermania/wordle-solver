; (use 'wordle-solver.core :reload) ;; LAZY RELOAD

(ns wordle-solver.core
  (:gen-class))

(require '[clojure.string :as str])
(require '[clojure.set])
(require '[clojure.pprint])

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

;; sorted list of evaluate-move mapped to each guess in allowed-guesues:
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


;; NOTE: dict-answers empty returns all 0, no matches
;; Also note - this one won't have keys for masks with no matching answers
(defn evaluate-move [dict-answers w-guess]
   (let [
  			matching-words (group-by (partial guess-and-answer-to-mask w-guess) dict-answers)
        total-words (count dict-answers)
       
        n-entropy (cond 
        							(= 0 total-words) 
        							  0 ;; an error state - should not get here unless dict-answers empty
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


(defn evaluate-all-moves [l-answers l-allowed-guesses]
  ;; NOTE - this is just for convenience.  The harness shouldn't allow for
  ;; any empty result sets.
  (if (empty? l-answers) (generate-empty-eval l-allowed-guesses)
    (let [results (zipmap
                 l-allowed-guesses
                 (pmap (partial evaluate-move l-answers)
                      l-allowed-guesses))
        sorted-results (sort
                        #(< (:entropy (second %1)) (:entropy (second %2)))
                        results)]
    sorted-results)))

; NOTE - there's probably a better way to clean out empties from the visual print
(defn clean-results-row [r-row]
  (let [mymap (:matches (second r-row))]
    [(first r-row)
      {
        :entropy (:entropy (second r-row))
        :matches (select-keys mymap (for [[k v] mymap :when (not (empty? v))] k))
      }]))


;; takes result type (["guess" {:entropy 1.23 :matches {(1 0 0 0 0) ("right" "wrong")] ... )
;; returns a single row of type: ["guess" {:entropy 1.23 :matches {(1 0 0 0 0) ("right" "wrong")]
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
;; TODO - this doesn't use the first two arguments.  It requires r-evals to be current.
(defn play-move [w-guess r-evals l-mask]
  (let [entry (extract-row-from-results r-evals w-guess)] 
    (if (nil? entry) nil
        (-> entry second :matches (get l-mask) -nil-to-empty))))

;; SECITION: Quordle time
(defn result-set-to-map [l-result-set]
  (zipmap (map first l-result-set)
  				(map (comp second second) l-result-set)))

(defn -sum-entropies [l-result-sets]
  (let [l-result-maps (map result-set-to-map l-result-sets)
  			total-scores (apply merge-with + l-result-maps)
  			 sorted-results (sort (fn [[k1 v1] [k2 v2]] (< v1 v2)) total-scores)
  ]
  sorted-results))

;; TODO Consider returning answer lists for debugging
(defn play-moves [l-allowed-guesses w-word l-responses l-results]
  (let [l-new-answer-lists (map 
  												 		(partial play-move w-word) 
  														l-results 
  														l-responses)
  		  l-new-result-sets (map #(evaluate-all-moves % l-allowed-guesses) l-new-answer-lists)
  ]
  (list 
	  l-new-result-sets
	  l-new-answer-lists
	  )))

;; SECTION: Harness

(defn harness-generate-random-word [l-answer-dict] 
	(rand-nth l-answer-dict)
)

(defn harness-select-best-guess-summed [l-results l-found-words]
  (first (filter (complement (set l-found-words)) 
  							 (map first (-sum-entropies (map just-words-and-entropy l-results))))))

;; TODO - picks MIN (first) of sorted, still doesn't randomly break ties
(defn harness-select-best-guess-global-min [l-results l-found-words]
  (first (filter (complement (set l-found-words)) 
				 			  (sort (fn [a b] (< (-> a second second) (-> b second second)))  
				 			  						(map #(first (just-words-and-entropy %)) l-results)))))

(def harness-initial-game-state 
	{ 
		:round 0
		:rounds-finished '()
		:found-words '()
		:guesses '()
	})

(defn harness-update-game-state [game-state w-guess l-response-masks]
   (let [new-round (inc (:round game-state))
				 num-finished (count (filter #(= '(2 2 2 2 2) %) l-response-masks))
         new-found-words (concat (:found-words game-state) (repeat num-finished w-guess))
         new-rounds-finished (concat (:rounds-finished game-state) (repeat num-finished new-round))
         new-guesses (concat (:guesses game-state) (list w-guess))] 
        { 
          :round new-round
          :rounds-finished new-rounds-finished
          :found-words new-found-words
          :guesses new-guesses 
          }))


(defn log-results [answers-set game-state]
  (spit "output.txt" (apply str (interpose "," answers-set)) :append true)
  (spit "output.txt" ";" :append true)
  (spit "output.txt" (apply str (interpose "," (:rounds-finished game-state))) :append true)
  (spit "output.txt" ";" :append true)
  (spit "output.txt" (apply str (interpose "," (:guesses game-state))) :append true)
    (spit "output.txt" \newline :append true))


(defn drop-indices [col indices]
  (let [s-indices (set indices)]
    (filter identity (map-indexed #(if ((complement s-indices) %1) %2) col))))

(defn find-indices [col target]
  (keep-indexed #(when (= %2 target) %1) col))

;; game state - initial state = :round, :rounds-finished, :found-words, :guesses
;; remaining-answers-set - initial
;; l-results: 4x : "soare " -> {:entropy 0, :matches {22010 -> ["dacha" "dairy"}}
;; fixed guess: (results, gamestate:foundwords) -> Word

;; LOOP:Guess has been made
;; l-response-masks: R(g,A) over all Answers set) -> 4 x '((0 0 1  0 1)...)
;; New game state: N(state, g, masks) -> new state
;; IF ZERO EXIT
;; l-results: play(Guesses, guess, response-masks, results)
  ;;; -- play-moves: for each guesss, get nwe answer set for each guess, then eval entropy.
  ;; if l-repsonse-masks includes a 22222, drop that index from remaining-answers-set, l-results, recur
(defn harness-run-one-trial [cached-results answers-set l-allowed-guesses f-heuristic]
	(loop [game-state harness-initial-game-state
	       remaining-answers-set answers-set
							l-results cached-results
		 			 	w-guess (f-heuristic l-results
																																							(:found-words game-state))]
							(let [
	  				;	_ (println "== TOP OF LOOP: Guess is ==")
								; _ (clojure.pprint/pprint w-guess)

	  					l-response-masks (map (partial guess-and-answer-to-mask w-guess) remaining-answers-set)
	  					; _ (println "== Apply guess.  Response masks ==") _ (clojure.pprint/pprint 		l-response-masks)
	  					
	  					new-game-state (harness-update-game-state game-state w-guess l-response-masks)
	  					; _ (println "== NEW GAME STATE ==") _ (clojure.pprint/pprint new-game-state)

	  					correct-indices (find-indices l-response-masks '(2 2 2 2 2))
	  					; _ (println "== correct-indices ==") _ (clojure.pprint/pprint correct-indices)
	  					

	  					[l-new-results l-new-answer-lists] (play-moves l-allowed-guesses w-guess l-response-masks l-results)
				  		l-results (drop-indices l-new-results correct-indices)
				  		l-answer-lists (drop-indices l-new-answer-lists correct-indices)
				  		; _ (println "== l-results after filtering ==") _ (clojure.pprint/pprint l-results)

	  					remaining-answers-set (drop-indices remaining-answers-set correct-indices)
	  					; _ (println "== remaining-answers-set ==") _ (clojure.pprint/pprint remaining-answers-set)

	  					]
					  	(if (zero? (count remaining-answers-set)) (log-results answers-set new-game-state) ;; termination
				  			(let [
							  			w-next-guess (f-heuristic l-results 
																																																	  (:found-words new-game-state))
										;		_ (println "== w-next-guess ==") _ (clojure.pprint/pprint w-next-guess)

							  			]
							  			(recur new-game-state remaining-answers-set l-results w-next-guess))))))



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

