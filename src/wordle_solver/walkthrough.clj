(ns wordle-solver.walkthrough)
(require '[wordle-solver.harness :as harness])
(require '[wordle-solver.data :as data])
(require '[wordle-solver.core :as core])
(require '[wordle-solver.eval :as eval])
(require '[clojure.pprint :refer [pprint]])


;; == WORDLE example ==

#_(do

  (set! *print-length* 10)

  ;; Explore the inputs: data/dict-answers
  data/dict-answers
  data/dict-allowed-guesses

     

  ;; Explore the "results" structure, which is a sorted list of pairs like:
  ;; ["soare" {:entropy 5.287849713481362 :matches {
  ;;   (2 0 2 2 0) ["scarf" "scary" "shard" "shark" "sharp" "smart" ) ;; ...]
  ;;   (2 0 2 0 0) ["scald" "scalp" "scaly" "scamp") ;; ...]
  ;;   ;; ...
  ;;	 }]
  (def r-firstmove (core/evaluate-first-move))
  (map first r-firstmove)
  (-> (eval/extract-row-from-results r-firstmove "soare"))
  (-> *1 second :matches (get '(1 0 0 0 0))) ;; play with this.


  (def ndle-state  (core/initialize-ndle-state
          1 ;; number of simultaneous boards.  Here, 1 for "wordle"
          eval/select-best-guess-summed ;; heuristic for next guess.
          r-firstmove ;; cached evaluation of a "blank" single board
          data/dict-answers ;; legal answers.  Can be customized.
          data/dict-allowed-guesses)) ;; legal guesses (contains the answers).  Can be customized.
  (keys ndle-state)


  ;; Look at the initial state
  (eval/just-words-and-entropy (nth (:l-results ndle-state) 0))
  (-> (eval/extract-row-from-results (nth (:l-results ndle-state) 0) "soare"))
   (->> (eval/extract-row-from-results
     	  (nth (:l-results ndle-state) 0) "soare")
	  second :matches vals (map count) frequencies (into (sorted-map)))

  (-> (eval/extract-row-from-results (nth (:l-results ndle-state) 0) "qajaq"))
     (->> (eval/extract-row-from-results
     	  (nth (:l-results ndle-state) 0) "qajaq")
	  second :matches vals (map count) frequencies (into (sorted-map)))

     (core/examine-next-move ndle-state)

 ;; LOOP: Make a move
     (def ndle-state
          (core/record-guess-results ndle-state
            "clint" ;; update this to your guess
            '((0 0 1 1 1))))

     (:l-results ndle-state )
     (eval/just-words-and-entropy (nth (:l-results ndle-state) 0))
;     (-> (eval/extract-row-from-results (nth (:l-results ndle-state) 0) "clint"))

     (core/examine-next-move ndle-state)
)
     ;; MAKE MOVE


;; == QUORDLE EXAMPLE

#_(do

  (def ndle-state  (core/initialize-ndle-state
          4 ;; number of simultaneous boards.  Here, 4 for "quordle"
          #(eval/select-best-guess-summed %1 %2 true) ;; heuristic for next guess.
          r-firstmove ;; cached evaluation of a "blank" single board
          data/dict-answers ;; legal answers.  Can be customized.
          data/dict-allowed-guesses)) ;; legal guesses (contains the answers).  Can be customized.


  ;; Look at the initial state
  (eval/just-words-and-entropy (nth (:l-results ndle-state) 0))
  (-> (eval/extract-row-from-results (nth (:l-results ndle-state) 0) "soare"))


  (pprint (eval/just-words-and-entropy (nth (:l-results ndle-state) 0)))
  (pprint (eval/just-words-and-entropy (nth (:l-results ndle-state) 1)))
  (pprint (eval/just-words-and-entropy (nth (:l-results ndle-state) 2)))
  (pprint (eval/just-words-and-entropy (nth (:l-results ndle-state) 3)))

  (pprint (core/examine-next-move ndle-state))

  ;; LOOP: Make a move
  (def ndle-state
    (core/record-guess-results ndle-state
                               "soare" ;; update this to your guess
                               '(
                                 (0 1 1 1 0)
                                 (2 1 0 0 0)
                                 (1 0 0 0 0)
                                 (0 0 2 2 0)
                                 )))

    (pprint (:l-results ndle-state ))

    (pprint (nth (:l-answer-lists ndle-state) 0))
    (pprint (nth (:l-results ndle-state) 0))

    (pprint (nth (:l-answer-lists ndle-state) 1))
    (pprint (nth (:l-results ndle-state) 1))

    (do
      (pprint (eval/just-words-and-entropy (nth (:l-results ndle-state) 0)))
      (pprint (eval/just-words-and-entropy (nth (:l-results ndle-state) 1)))
      (pprint (eval/just-words-and-entropy (nth (:l-results ndle-state) 2)))
      (pprint (eval/just-words-and-entropy (nth (:l-results ndle-state) 3)))
      )

    ;; STOP - what do we do here?
  
    (core/examine-next-move (assoc ndle-state :f-heuristic eval/select-best-guess-summed)))
     ;; Vern's gambit
     ;;(core/examine-next-move (assoc ndle-state :f-heuristic eval/select-best-guess-global-min)))

     ;; MAKE MOVE
     

;; == RUNNING SIMS

#_(do
  ;; #### Single board (example: 4-dle)

  (harness/run-one-trial
      4
      r-firstmove
;      ["lymph" "cover" "sassy" "foist"]
      ["amiss" "hefty" "fiber" "field"]
      data/dict-allowed-guesses
     #(eval/select-best-guess-summed %1 %2 true)
;      #(eval/select-best-guess-global-min %1 %2 true)
      "output.txt")

  ;;  Random trials
  (harness/run-random-trials
      10
      2
      r-firstmove
      data/dict-answers
      data/dict-allowed-guesses
      eval/select-best-guess-global-min
      "output.txt")
)
