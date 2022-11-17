(ns wordle-solver.core)

(require '[wordle-solver.eval :as eval])
(require '[wordle-solver.data :as data])
(require '[wordle-solver.harness :as harness])
(require '[clojure.pprint])

;; TODO out of heap memory running this in nREPL?
;; TODO clean: consider destructuring this object in functions

;; Example (initialize-ndle-state 4 eval/select-best-guess-summed  r-firstmove)
;; NOTE: This will evaluate the first move as well.

;; first run takes about 18 minutes with pmap across guesses,
;; 148 minutes without, which makes sense among 8 evals. 
;; UPDATE  - takes 47 seconds with the new group-by style

;; NOTE: This presumes we're using the built-in
;; data/dict-answers and data/dict-allowed-guesses.
;; You can either change these files or call
;; eval/evaluate-all-moves with custom params.
(defn evaluate-first-move [] (eval/evaluate-all-moves
                              data/dict-answers
                              data/dict-allowed-guesses))

(defn initialize-ndle-state [board-size f-heuristic r-firstmove l-answers l-allowed-guesses]
  {
   :l-answers data/dict-answers
   :l-allowed-guesses  l-allowed-guesses
   :r-firstmove r-firstmove
   :l-results (repeat board-size r-firstmove)
   :l-answer-lists (repeat board-size l-answers)
   :game-state harness/initial-game-state
   :f-heuristic f-heuristic
   })


;; Example (examine-next-move ndle-state)
;;   prints =>
;; {:best-guess "guilt",
;;  :l-answer-lists (["belie" "belle" "bible" ... ]
;;                   [ ... ])}

;; TODO Consider verbose flag for f-heuristic
(defn examine-next-move [ndle-state]

  (let [w-guess ((:f-heuristic ndle-state)
                 (:l-results ndle-state)
                 (:found-words (:l-results ndle-state)))]
    {
     :best-guess w-guess
     :l-answer-lists (:l-answer-lists ndle-state)
    }))

;; Example:

;; First TODO - how to separate playing move from examining state?


(defn record-guess-results [ndle-state w-guess l-response-masks]
  (let [game-state (harness/update-game-state
                    (:game-state ndle-state)
                    w-guess l-response-masks)
        _ (println "New game state ")
        _ (clojure.pprint/pprint game-state)
        move-results (eval/play-moves
                      (:l-allowed-guesses ndle-state)
                      w-guess
                      l-response-masks
                      (:l-results ndle-state))
        l-results (first move-results)
        l-answer-lists (second move-results)]

        (assoc ndle-state 
               :l-results l-results
               :l-answer-lists l-answer-lists 
               :game-state game-state)))
