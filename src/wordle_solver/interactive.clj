(ns wordle-solver.interactive)

(require '[wordle-solver.core :as core])
(require '[wordle-solver.data :as data])
(require '[wordle-solver.harness :as harness])
(require '[clojure.pprint])

;; TODO out of heap memory running this in nREPL?
;; TODO clean: rename core -> evaluate, interactive -> core
;; TODO clean: consider destructuring this object in functions

;; Example (initialize-ndle-state 4 harness/select-best-guess-summed)
;; NOTE: This will evaluate the first move as well.

;; first run takes about 18 minutes with pmap across guesses,
;; 148 minutes without, which makes sense among 8 cores. 
;; UPDATE  - takes 47 seconds with the new group-by style

(defn evaluate-first-move [] (core/evaluate-all-moves
                              data/dict-answers
                              data/dict-allowed-guesses))

(defn initialize-ndle-state [board-size f-heuristic r-firstmove]
  {
   :l-answers data/dict-answers
   :l-allowed-guesses  data/dict-allowed-guesses
   :r-firstmove r-firstmove
   :l-results (repeat board-size r-firstmove)
   :game-state harness/initial-game-state
   :f-heuristic f-heuristic
   })

;; Example (examine-next-move ndle-state)
;;   prints =>
;;    (["soare" 21.151398853925446]
;;     ["roate" 21.152784576168425]
;;     ["raise" 21.178998005123777]
;;     ["reast" 21.221257747722216]
;;     ["raile" 21.231594514931395]
;;     ["slate" 21.268932854658406]
;;     ["salet" 21.348118702726552]
;;     ["crate" 21.351345900563334]
;;     ["irate" 21.36101430733284]
;;     ["trace" 21.370493398777437])
;;    STOP. Play move  soare
;;   returns => "soare"
(defn examine-next-move [ndle-state]
  (clojure.pprint/pprint (take 10 (core/-sum-entropies
                    (map core/just-words-and-entropy
                         (:l-results ndle-state)))))
  (let [w-guess ((:f-heuristic ndle-state)
                 (:l-results ndle-state)
                 (:found-words (:l-results ndle-state)))]
    
    (println "STOP. Play move " w-guess)
    w-guess))

;; Example:
;; (record-guess-results ndle-state "soare" 
;; s  '(
;;      (2 0 0 0 2) 
;;      (0 0 1 0 1) 
;;      (0 0 2 0 0) 
;;      (0 0 1 0 2) 
;;    ))

;; FIRST TODO - how to separate playing move from examining state?
(defn record-guess-results [ndle-state w-guess l-response-masks]
  (let [game-state (harness/update-game-state
                    (:game-state ndle-state)
                    w-guess l-response-masks)
        _ (println "New game state ")
        _ (clojure.pprint/pprint game-state)
        move-results (core/play-moves
                      (:l-allowed-guesses ndle-state)
                      w-guess
                      l-response-masks
                      (:l-results ndle-state))
        l-results (first move-results)
        l-answer-lists (second move-results)

        ;; Prints the best next choice for each board
         _ (println "Best move for each board")
        _ (clojure.pprint/pprint  (map list (map first l-results)
                                       l-answer-lists))

        - (println "Best 10 moves overall")
        _ (clojure.pprint/pprint (take 10 (core/-sum-entropies
                            (map core/just-words-and-entropy
                                 l-results))))]
        (assoc ndle-state 
               :l-results l-results
               :game-state game-state)))
