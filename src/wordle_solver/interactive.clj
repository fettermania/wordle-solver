(ns wordle-solver.interactive)

(require '[wordle-solver.core :as core])
(require '[wordle-solver.data :as data])
(require '[wordle-solver.harness :as harness])
(require '[clojure.pprint])

;; == BEGIN GLOBAL STATE
(def l-answers data/dict-answers)
(def l-allowed-guesses data/dict-allowed-guesses)

;; first run takes about 18 minutes with pmap across guesses, 148 minutes without, makes sense among 8 cores. 
; UPDATE  - takes 47 seconds with the new group-by style
(def r-firstmove (core/evaluate-all-moves l-answers l-allowed-guesses))

(def f-heuristic harness/harness-select-best-guess-summed)

;; == END GLOBAL STATE

;; Example:
;; (initialize-game-play 4 harness/harness-select-best-guess-summed)

(defn initialize-ndle-state [board-size f-heuristic]
  {
   :l-results (repeat board-size r-firstmove)
   :game-state harness/harness-initial-game-state
   })


(defn examine-next-move [ndle-state]
  (clojure.pprint/pprint (take 10 (core/-sum-entropies
                    (map core/just-words-and-entropy
                         (:l-results ndle-state)))))
  (let [w-guess (f-heuristic
                 (:l-results ndle-state)
                 (:found-words (:l-results ndle-state)))]
    
    (println "STOP. Play move " w-guess)
    w-guess))

;; Fill in response masks here.
;; (def l-response-masks '(
;;  (2 2 2 2 2) 
;;  (2 2 2 2 2) 
;;  (0 1 1 1 0) 
;  (2 2 2 2 2) 
;;  ))


(defn record-guess-results [ndle-state w-guess l-response-masks]

  (let [game-state (harness/harness-update-game-state
                    (:game-state ndle-state)
                    w-guess l-response-masks)
        _ (println "New game state ")
        _ (clojure.pprint/pprint game-state)
        move-results (core/play-moves
                      l-allowed-guesses w-guess
                      l-response-masks
                      (:l-results ndle-state))
        l-results (first move-results)
        l-answer-lists (second move-results) 
        _ (clojure.pprint/pprint  (map list (map first l-results) l-answer-lists))
        _ (clojure.pprint/pprint (take 10 (core/-sum-entropies
                            (map core/just-words-and-entropy
                                 l-results))))]
        {
         :l-results l-results
         :game-state game-state
         }))
        
