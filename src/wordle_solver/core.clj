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

;; NOTE This is gross and not general but who cares
;; returns list(mask)
;; example return: ((0 0 0 0 0) (0 0 0 0 1) (0 0 0 0 2) ... (2 2 2 2 1) (2 2 2 2 2))
(defn generate-all-mask-lists [n]
  (if (= 1 n) '((0) (1) (2))
      (let [children (generate-all-mask-lists (dec n))
            result
            (reduce concat (list 
             (map #(conj % BLACK) children)
             (map #(conj % YELLOW) children)
             (map #(conj % GREEN) children)))]
        result)))

;; GLOBAL - all 3^5 mask lists
(def all-mask-lists (generate-all-mask-lists 5))


;;  == SECTION: OLD STRATEGY - incorrect ==

;; takes "a" and returns function that strips out everything but (\a \a \a...)
;; example: ((_gen-includer-filter "a") "adage"
(defn _gen-includer-filter [letter]
  (partial filter #(str/includes? % letter)))

; note: needs green list too - TODO: match EXACTLY this count
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

(defn old-filter-fn-from-mask-list-and-word [w-word mask-list]
  (let [seq-word (seq w-word)
        black-list (apply-mask-to-word mask-list seq-word BLACK)
        yellow-list (apply-mask-to-word mask-list seq-word  YELLOW)
        green-list (apply-mask-to-word mask-list seq-word GREEN)
        regex-str (apply str
                         (map (partial _generate-regex-entry (apply str black-list))
                              mask-list w-word))
       ; _ (println "Regex str for " w-word ", mask " (str mask-list) ": " regex-str)
        filter-regex (partial filter #(re-matches (re-pattern regex-str) %))
        filter-includes (gen-includer-filters yellow-list)
        ]
        (comp filter-regex filter-includes)))


;; == SECTION: New strategies ==

;  (color-seqs-from-word-and-mask "abase" '(0 1 2 1 0))
;;   yields {\a (2 0), \b (1), \s (1), \e (0)}
;;   really like \a (GREEN BLACK), \b (YELLOW) \s (YELLOW) \e (BLACK)
;; Another example
; (def w-word "dacha")
; (def mask-list '(1 2 0 0 0))
; (rev-color-seqs-from-word-and-mask w-word mask-list)
;; yields {\d (1), \a (0 2), \c (0), \h (0)}

; GOTCHA: Note that sequences are reversed from the order in word
; TODO FIX THIS?
(defn rev-color-seqs-from-word-and-mask [w-word mask-list]
  (loop [word w-word 
  			    mask mask-list
  						accum {}]
  						(if (empty? word)  ;; note: assuming |word| = |mask-list|
  							accum
  							(recur 
  							  	 	(rest word)
 									 	 	(rest mask)
	  	 									(update accum (first word) conj (first mask))))))


; (def invalid-mask (rev-color-seqs-from-word-and-mask "dacha" '(0 0 0 0 1)))
; (def valid-mask-1 (rev-color-seqs-from-word-and-mask "dacha" '(0 1 0 0 0)))
; (def valid-mask-2 (rev-color-seqs-from-word-and-mask "dacha" '(0 2 0 0 1)))
; (def valid-mask-3 (rev-color-seqs-from-word-and-mask "dacha" '(0 1 0 0 1)))
; (every? (complement rev-seq-contains-black-then-yellow) (vals invalid-mask))
; ; yields false
; (every? (complement rev-seq-contains-black-then-yellow) (vals valid-mask-1))
; ; yields true
; (every? (complement rev-seq-contains-black-then-yellow) (vals valid-mask-2))
; ; yields true
; (every? (complement rev-seq-contains-black-then-yellow) (vals valid-mask-3))
; ; yields true
;; Note: This might be prettier as "0.*1" in a regex

; ; MORE RAW: 
; (rev-seq-contains-black-then-yellow '(1 0))
; ; true
; (rev-seq-contains-black-then-yellow '(0 1))
; ; false
(defn rev-seq-contains-black-then-yellow [rev-color-seq]
  (let [rev-color-str (apply str rev-color-seq)
  					 color-str (apply str (reverse rev-color-seq))
  					 first-black-ix (.indexOf color-str "0")
  					 last-yellow-reverse-ix (.indexOf  rev-color-str "1")
  					 last-yellow-ix (- (dec (count rev-color-seq))
  					 										last-yellow-reverse-ix)]
  					(and (not= first-black-ix -1)
  									 (not= last-yellow-reverse-ix -1)
  									 (< first-black-ix last-yellow-ix))))
  								
;
; (def listy '("abcde" "aahed" "dacha" "hairy"))
; (regex-str-from-word-and-mask "dacha" '(1 2 0 0 0)) 
;; yields -> "[^d]a[^c][^h][^a]"
(defn regex-str-from-word-and-mask [w-word mask-list]
   (apply str
     (map 
       (fn [w m]
         (if (= m GREEN) w (str "[^" w "]")))
   		w-word
   		mask-list)))

;; Strategy 2: Create comp'd filter functions

; (def two-or-more-as (gen-char-count-filter-fn \a 2 >=))
;; (two-or-more-as "abadc") --> true
;; (two-or-more-as "abddc") --> false

;; (def two-as (gen-char-count-filter-fn \a 2 =))
;; (two-as "abcda") --> true
;; (two-as "abada") --> false

;; USING AS A FILTER
; (def listy '("abcde" "aahed" "dacha" "hairy"))
; (def one-or-more-cs (gen-char-count-filter-fn \c 1 >=))
; (def two-as (gen-char-count-filter-fn \a 2 =))
; ((comp two-as one-or-more-cs) listy)
;  ; ---> yields '("dacha")
(defn gen-char-count-filter-fn [ch-match ct-expected comp-fn]
  (partial filter 
    (fn [w] 
     (comp-fn 
   	  	(count (filter (partial = ch-match) w))
  		  	ct-expected))))



; (def listy '("abcde" "aahed" "dacha" "hairy"))
; ((regex-filter-from-word-and-mask "dacha" '(1 2 0 0 0))  listy)
;; yields -> ("aahed" "hairy")
(defn regex-filter-from-word-and-mask  [w-word mask-list]
		(partial filter
		  #(re-matches (re-pattern (regex-str-from-word-and-mask w-word mask-list)) %)))


; (def listy '("abcde" "aahed" "dacha" "hairy"))
; (def invalid-mask-seq (get (rev-color-seqs-from-word-and-mask "dacha" '(0 0 0 0 1)) \a))
; (def valid-mask-seq-1 (get (rev-color-seqs-from-word-and-mask "dacha" '(0 1 0 0 0)) \a))
; ((rev-color-seq-to-filter-fn \a invalid-mask-seq) listy)
; ; yields (), since invalidated by black-then-yellow
; ((rev-color-seq-to-filter-fn \a valid-mask-seq-1) listy)
; ; yields ("abcde" "hairy"), since they both have one a
; MORE RAW, it takes in \a and '(2 0), for example
(defn rev-color-seq-to-filter-fn [ch rev-color-seq]
  ;; if contains 1 0 (black "before" yellow), it's always fanlse
  ;; if it contains a 0, then it's exact count of non-0s
  ;; otherwise its that many non-0s or more
  (let [ct-black (count (filter (partial = 0) rev-color-seq))
				ct-yg (count (filter (partial not= 0) rev-color-seq))
				has-black-then-yellow 
					(rev-seq-contains-black-then-yellow rev-color-seq)]
  	(if has-black-then-yellow 
  	    (partial filter (fn [w] false))
  			(gen-char-count-filter-fn ch ct-yg (if (> ct-black 0) = >=)))))


; TODO Consider filter on this: (filter (apply every-pred (list number? even?)) '(3 4 5 )) 
(defn new-filter-fn-from-mask-list-and-word [w-word mask-list]
  (let [regex-filter (regex-filter-from-word-and-mask w-word mask-list)
  			rev-color-seqs (rev-color-seqs-from-word-and-mask w-word mask-list)
				char-count-filters (map rev-color-seq-to-filter-fn (keys rev-color-seqs) (vals rev-color-seqs))
  			ultimate-filter-fn (reduce comp (conj char-count-filters regex-filter))]
  			ultimate-filter-fn))


;; Filter strategy 3 (correct, faster) - create comp'd predicate, apply filter once
(defn gen-char-count-pred [ch-match ct-expected comp-fn]
    (fn [w] 
     (comp-fn 
   	  	(count (filter (partial = ch-match) w))
  		  	ct-expected)))

(defn regex-pred-from-word-and-mask  [w-word mask-list]
		  #(re-matches (re-pattern (regex-str-from-word-and-mask w-word mask-list)) %))


(defn rev-color-seq-to-pred [ch rev-color-seq]
  ;; if contains 1 0 (black "before" yellow), it's always fanlse
  ;; if it contains a 0, then it's exact count of non-0s
  ;; otherwise its that many non-0s or more
  (let [ct-black (count (filter (partial = 0) rev-color-seq))
				ct-yg (count (filter (partial not= 0) rev-color-seq))
				has-black-then-yellow 
					(rev-seq-contains-black-then-yellow rev-color-seq)]
  	(if has-black-then-yellow 
  	    (fn [w] false)
  			(gen-char-count-pred ch ct-yg (if (> ct-black 0) = >=)))))

(defn revised-new-filter-fn-from-mask-list-and-word [w-word mask-list]
  (let [regex-pred (regex-pred-from-word-and-mask w-word mask-list)
  			rev-color-seqs (rev-color-seqs-from-word-and-mask w-word mask-list)
				char-count-preds (map rev-color-seq-to-pred (keys rev-color-seqs) (vals rev-color-seqs))
  			ultimate-filter-fn (partial filter (apply every-pred (concat char-count-preds (list regex-pred))))]
  			ultimate-filter-fn))


; (def matching-new-words (map
;                       ;;  #((old-filter-fn-from-mask-list-and-word w-word %)
;                        #((new-filter-fn-from-mask-list-and-word w-word %)
;                           dict-answers) all-mask-lists))

; (def matching-old-words (map
;                       ;;  #((old-filter-fn-from-mask-list-and-word w-word %)
;                        #((old-filter-fn-from-mask-list-and-word w-word %)
;                           dict-answers) all-mask-lists))

;; TODO END NEW ZONE

;; Example filter functions: include, regex
(def fn-d (partial filter #(str/includes? % "D")))
(def fn-reg1 #(filter (fn [w] (re-matches #"[^ER][^ER]A[^S]E" w)) %))

(defn calculate-entropy-numerator [result-set]
  (let [c (count result-set)]
    (if (= 0 c) 0 
           (/ (* (Math/log c) c) (Math/log 2)))))

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



;; NOTE: disk-answers empty returns all 0, no matches
(defn evaluate-move [all-mask-lists dict-answers w-word]
  (let [;_ (println "Checking guess " w-word)
  			matching-words (map
   										;; TOOD here
                        #((revised-new-filter-fn-from-mask-list-and-word w-word %)
                          dict-answers) all-mask-lists)
        total-words (count dict-answers)
        n-entropy (cond 
        							(= 0 total-words) 
        							  0 ;; an error state - should not get here unless dict-answers empty
        							(and 
        							  	(= 1 total-words) 
        								  (-results-match-single-word? w-word matching-words))
      							    -100 ;; NOTE: A match! 
      							  :else 
											  (/
													(apply +
		               (map calculate-entropy-numerator
		                    matching-words))
												total-words))]
    {:entropy n-entropy
     :matches (zipmap all-mask-lists matching-words)}))

;; sorted list of evaluate-move mapped to each guess in allowed-guesues:
;; ["aahed"
;;  {:entropy 7.78318320736531353,
;;   :matches
;;   {(2 0 2 2 0) ("ashen"),
;;     (2 0 2 0 0) ("abhor"),
;;    (1 2 0 2 1) ("cadet" "laden"),
(defn evaluate-all-moves [l-answers l-allowed-guesses]
  (let [all-mask-lists (generate-all-mask-lists 5)
        results (zipmap
                 l-allowed-guesses
                 (map (partial evaluate-move all-mask-lists l-answers)
                      l-allowed-guesses))
        sorted-results (sort
                        #(< (:entropy (second %1)) (:entropy (second %2)))
                        results)]
    sorted-results))



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
(defn just-words-and-entropy [r]
  (map #(list (first %) (first (second %))) r))

;; filters words-and-entropy to only words in answer set
(defn viable-answer-words [l-answers r] (filter #(get (set l-answers) (first %))
                                    (just-words-and-entropy r)))


;; takes result set, finds the word's row, and extracts the matches from that row
;; TODO - this doesn't use the first two arguments
(defn play-move [dict-answers dict-allowed-guesses r-evals str-word l-mask]
  (let [entry (extract-row-from-results r-evals str-word)] 
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


;; SECTION: CUTTING DOWN INITIAL SET 

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

