(ns wordle-solver.core)
(require '[wordle-solver.harness :as harness])
(require '[wordle-solver.data :as data])
(require '[wordle-solver.core :as core])
(require '[wordle-solver.eval :as eval])
(require '[clojure.pprint :refer [pprint]])

;; (My love letter to CIDER)

;; == WORDLE example ==

(when false

  (set! *print-length* 20)

  ;; WT: Explore the inputs
  data/dict-answers
  data/dict-allowed-guesses


  ;; Explore the "results" structure, which is a sorted list of:
  ;;   guess ->  {<entropy score> {RGY response -> [matching answers]}}
  ;;   - guess in allowed guesses (starts as data/dict-allowed-guesses)
  ;;   - answers in remaining answers (starts at data/dict-answers)
  
  ;; ["soare" {:entropy 5.287849713481362 :matches {
  ;;   (2 0 2 2 0) ["scarf" "scary" "shard" "shark" "sharp" "smart" ) ;; ...]
  ;;   (2 0 2 0 0) ["scald" "scalp" "scaly" "scamp") ;; ...]
  ;;   ;; ...
  ;;	 }]

  ;; WT: Generate our best first move
  (def r-firstmove (core/evaluate-first-move))
  (map first r-firstmove)
  (-> (eval/extract-row-from-results r-firstmove "soare"))
  (-> *1 second :matches (get '(0 0 0 0 0)))


  ;; Package fixed global elements
  (def ndle-state  (core/initialize-ndle-state
          1 ;; number of simultaneous boards.  Here, 1 for "wordle"
          eval/select-best-guess-summed ;; heuristic for next guess.
          r-firstmove;; cached evaluation of a "blank" single board
          data/dict-answers ;; legal answers.  Can be customized.
          data/dict-allowed-guesses   ;; legal guesses (contains the answers).  Can be customized.
          ))

  ;; Initialize adds:
  ;;  :l-results - current move structure (:l-results)
  ;;  :l-answer-lists - current viable answers remaining
  ;;  :game-state - submit list, answers found, round #, etc. (for sim harness)
  (keys ndle-state)


  ;; Look at the initial state (note: this is r-firstmove for now)
  (eval/just-words-and-entropy (nth (:l-results ndle-state) 0))
  (-> (eval/extract-row-from-results (nth (:l-results ndle-state) 0) "soare"))
  (-> *1 second :matches (get '(0 0 0 0 0)))

  ;; "SOARE" can result in what size remaining answer sets?
   (->> (eval/extract-row-from-results
     	  (nth (:l-results ndle-state) 0) "soare")
	  second :matches vals (map count) frequencies (into (sorted-map)))

  (-> (eval/extract-row-from-results (nth (:l-results ndle-state) 0) "qajaq"))
     (->> (eval/extract-row-from-results
     	  (nth (:l-results ndle-state) 0) "qajaq")
	  second :matches vals (map count) frequencies (into (sorted-map)))

  ; runs f-heuristic 
  (core/examine-next-move ndle-state)

 ;; ===  LOOP: Make a move===
     (def ndle-state
          (core/record-guess-results ndle-state
            "plumb" ;; update this to your guess
            '(
              (0 2 0 0 0)
              )
            ))

     (:l-results ndle-state )
     (eval/just-words-and-entropy (nth (:l-results ndle-state) 0))
     (-> (eval/extract-row-from-results (nth (:l-results ndle-state) 0) "plank"))

     (core/examine-next-move ndle-state)
)


;; == QUORDLE EXAMPLE

(when false

  (def ndle-state  (core/initialize-ndle-state
          4 ;; CHANGED: number of simultaneous boards.  Here, 4 for "quordle"

          ;; heuristic for next guess. (verbose = true)
          eval/select-best-guess-summed
;          eval/select-best-guess-global-min 

          r-firstmove ;; cached evaluation of a "blank" single board
          data/dict-answers ;; legal answers.  Can be customized.
          data/dict-allowed-guesses ;; legal guesses (contains the answers).  Can be customized.
          ))


  ;; Look at the initial state
  ;; 0 is just the first board!  Boards indexed by [0, 1, 2, 3]
  (eval/just-words-and-entropy (nth (:l-results ndle-state) 0))
  (-> (eval/extract-row-from-results (nth (:l-results ndle-state) 0) "soare"))


  (core/examine-next-move ndle-state)

  ;; LOOP: Make a move
  (def ndle-state
    (core/record-guess-results ndle-state
                               "soare" ;; update this to your guess
                               '(
                                 (0 0 0 1 0)
                                 (0 0 0 2 1)
                                 (0 0 0 0 0)
                                 (0 0 2 0 1)
                                 )))

    (:l-results ndle-state )

    (nth (:l-answer-lists ndle-state) 0)
    (nth (:l-results ndle-state) 0)

    (nth (:l-answer-lists ndle-state) 1)
    (nth (:l-results ndle-state) 1)

    (do
      (eval/just-words-and-entropy (nth (:l-results ndle-state) 0))
      (eval/just-words-and-entropy (nth (:l-results ndle-state) 1))
      (eval/just-words-and-entropy (nth (:l-results ndle-state) 2))
      (eval/just-words-and-entropy (nth (:l-results ndle-state) 3))
      )

    ;; STOP - what do we do here?
  
    (core/examine-next-move ndle-state )

    ;; Verbose heuristic - shows each board's input state, total evaluation
    ;;  (core/examine-next-move (assoc ndle-state :f-heuristic
    ;;                              #(eval/select-best-guess-summed %1 %2 true)))
    ;; Vern's gambit
    ;; (core/examine-next-move
    ;;   (assoc ndle-state :f-heuristic eval/select-best-guess-global-min))

     ;; MAKE MOVE
     

;; == RUNNING SIMS

(when false

  (harness/run-one-trial
      1
      r-firstmove
      ["wafer"]
      data/dict-allowed-guesses
      eval/select-best-guess-summed
      "output.txt")

    ;; #### Single board (example: 4-dle)

  (harness/run-one-trial
      4
      r-firstmove
      ["titan" "yeast" "canon" "leaky"]
      data/dict-allowed-guesses
     eval/select-best-guess-summed
;    eval/select-best-guess-global-min 
      "output.txt")

  ;;  Random trials
  ;;  NOTE: Time roughly linear in number of boards (dominated by first move,
  ;;   measure is number of times a word is returned in an answer list
  ;;   and consider each list is cut by >> 50% each guess, likely.
(time  (harness/run-random-trials
      1
      4
      r-firstmove
      data/dict-answers
      data/dict-allowed-guesses
      eval/select-best-guess-summed
      "output.txt"))
)
