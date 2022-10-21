(ns wordle-solver.boneyard
  (:gen-class))

(require '[wordle-solver.core :as core])
(require '[clojure.string :as str])
(require '[clojure.set])



;;  == SECTION: First Strategy - incorrect ==

;; Summary: Given an answer and guess dictionary, for each guess, and for each mask combination:
;;; - generate regex which that mask suggests
;;; - generate the "must include one or more" letter set that mask suggests
;;; - filter dictionary to a mapping mask_list -> viable answers
;; Problem: Some masks may return overlapping result sets;

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
  (cond (= mask-elt core/GREEN) word-elt
        (= mask-elt core/YELLOW) (apply str "[^" black-list word-elt "]")
        :else   (apply str "[^" black-list "]")))

(defn old-filter-fn-from-mask-list-and-word [w-word mask-list]
  (let [seq-word (seq w-word)
        black-list (core/apply-mask-to-word mask-list seq-word core/BLACK)
        yellow-list (core/apply-mask-to-word mask-list seq-word  core/YELLOW)
        green-list (core/apply-mask-to-word mask-list seq-word core/GREEN)
        regex-str (apply str
                         (map (partial _generate-regex-entry (apply str black-list))
                              mask-list w-word))
       ; _ (println "Regex str for " w-word ", mask " (str mask-list) ": " regex-str)
        filter-regex (partial filter #(re-matches (re-pattern regex-str) %))
        filter-includes (gen-includer-filters yellow-list)
        ]
        (comp filter-regex filter-includes)))


;; == SECTION: Second strategy - slow ==

;; Summary: Given an answer and guess dictionary, for each guess, and for each mask combination:
;;; - generate regex which that mask suggests, using only GREEN and BLACK components
;;; - generate the "exact count of each letter" the mask suggests. 
;;; - create a filter for a small set of non-viable mask combos
;;;;  - specifically if letter "A" is in the word, no (A, Black) pair can proceed an (A, Yellow) pair
;;;;  - if matching the elements of Guess and Mask (g_1, m_1)... etc.
;;; - filter dictionary to a mapping mask_list -> viable answers
;; Problem: This was even slower that stragey 1.

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
         (if (= m core/GREEN) w (str "[^" w "]")))
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


;; TODO END NEW ZONE

;; Example filter functions: include, regex
(def fn-r (partial filter #(str/includes? % "r")))
(def fn-reg1 #(filter (fn [w] (re-matches #".ai.." w)) %))



;; NOTE: dict-answers empty returns all 0, no matches
(defn old-evaluate-move [all-mask-lists dict-answers w-word]
  (let [;_ (println "OLD Evaluate move " w-word) 
    		matching-words (map
   										;; TODO - put pmap HERE for speed? ^^^
                        #((revised-new-filter-fn-from-mask-list-and-word w-word %)
                          dict-answers) all-mask-lists)
        total-words (count dict-answers)
        ; _ (println "entropy numerator OLD")
        ; _ (println  (map calculate-entropy-numerator
		      ;               matching-words))

        n-entropy (cond 
        							(= 0 total-words) 
        							  0 ;; an error state - should not get here unless dict-answers empty
        							(and 
        							  	(= 1 total-words) 
        								  (core/-results-match-single-word? w-word matching-words))
      							    -100 ;; NOTE: A match! 
      							  :else 
											  (/
													(apply +
		               (map core/calculate-entropy-numerator
		                    matching-words))
												total-words))]
    {:entropy n-entropy
     :matches (zipmap all-mask-lists matching-words)}))



(defn old-evaluate-all-moves [l-answers l-allowed-guesses]
  (let [all-mask-lists (core/generate-all-mask-lists 5)
        results (zipmap
                 l-allowed-guesses
                 (pmap (partial old-evaluate-move all-mask-lists l-answers)
                      l-allowed-guesses))
        sorted-results (sort
                        #(< (:entropy (second %1)) (:entropy (second %2)))
                        results)]
    sorted-results))

; NOTE - there are no empties produced with strategy 3.
; NOTE - there's probably a better way to clean out empties from the visual print
(defn clean-results-row [r-row]
  (let [mymap (:matches (second r-row))]
    [(first r-row)
      {
        :entropy (:entropy (second r-row))
        :matches (select-keys mymap (for [[k v] mymap :when (not (empty? v))] k))
      }]))

