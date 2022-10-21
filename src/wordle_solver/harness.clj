(ns wordle-solver.harness)

(require '[wordle-solver.core :as wordle])
(require '[clojure.pprint])

;; SECTION: Simulation Harness

(defn harness-generate-random-word [l-answer-dict] 
    (rand-nth l-answer-dict))

(defn harness-select-best-guess-summed [l-results l-found-words]
  (first (filter (complement (set l-found-words)) 
                             (map first (wordle/-sum-entropies (map wordle/just-words-and-entropy l-results))))))

;; TODO - picks MIN (first) of sorted, still doesn't randomly break ties
(defn harness-select-best-guess-global-min [l-results l-found-words]
  (let [r (first (filter (complement (set l-found-words))
                         (sort (fn [a b] (< (-> a second second) (-> b second second)))
                               (reduce concat (map wordle/just-words-and-entropy l-results)))))]
   (first r)))

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
         new-rounds-finished (concat (:rounds-finished game-state)
                                     (repeat num-finished new-round))
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
  (spit "output.txt" (apply str (interpose "," (:rounds-finished game-state)))
        :append true)
  (spit "output.txt" ";" :append true)
  (spit "output.txt" (apply str (interpose "," (:guesses game-state))) :append true)
  (spit "output.txt" \newline :append true)
  (clojure.pprint/pprint "DONE LOGGING"))


(defn -drop-indices [col indices]
  (let [s-indices (set indices)]
    (filter identity (map-indexed #(if ((complement s-indices) %1) %2) col))))

(defn -find-indices [col target]
  (keep-indexed #(when (= %2 target) %1) col))



;; game state - initial state = :round, :rounds-finished, :found-words, :guesses
;; remaining-answers-set - initial
;; l-results: 4x : "soare " -> {:entropy 0, :matches {22010 -> ["dacha" "dairy"}}
;; fixed guessnot: (results, gamestate:foundwords) -> Word


;; LOOP:Guess has been made
;; l-response-masks: R(g,A) over all Answers set) -> 4 x '((0 0 1  0 1)...)
;; New game state: N(state, g, masks) -> new state
;; IF ZERO EXIT
;; l-results: play(Guesses, guess, response-masks, results)
  ;;; -- play-moves: for each guesss, get new answer set for each guess, eval entropy.
  ;; if l-repsonse-masks includes a 22222, drop that index from remaining-answers-set,
  ;;   l-results, recur
(defn harness-run-one-trial [cached-results answers-set l-allowed-guesses f-heuristic]
    (loop [game-state harness-initial-game-state
           remaining-answers-set answers-set
           l-results cached-results
           w-guess (f-heuristic l-results (:found-words game-state))]
            (let [
              l-response-masks (map (partial wordle/guess-and-answer-to-mask w-guess) remaining-answers-set)
              new-game-state (harness-update-game-state game-state w-guess l-response-masks)
              _ (println "== NEW GAME STATE ==") _ (clojure.pprint/pprint new-game-state)
              correct-indices (-find-indices l-response-masks '(2 2 2 2 2))
              [l-new-results l-new-answer-lists] (wordle/play-moves l-allowed-guesses w-guess l-response-masks l-results)
              l-results (-drop-indices l-new-results correct-indices)
              l-answer-lists (-drop-indices l-new-answer-lists correct-indices)
              remaining-answers-set (-drop-indices remaining-answers-set correct-indices)
              ]
              (if (zero? (count remaining-answers-set)) (log-results answers-set new-game-state) ;; termination
                  (let [w-next-guess (f-heuristic l-results (:found-words new-game-state))]
                    (recur new-game-state remaining-answers-set l-results w-next-guess))))))


;; Note - can use this as either "run over all answers" (1-dle), or "run over this test set"

;; (harness/harness-run-over-all-in-list data/dict-answers data/dict-allowed-guesses 1 r-firstmove  harness/harness-select-best-guess-summed)

;; TODO NOTE - vern's gambit
;; Example - run over pre-generated answer sets for Octordle
;; (harness-run-over-all-in-list
;;   l-answers l-allowed-guesses 1 r-firstmove
;;   harness-select-best-guess-summed)
(defn harness-run-over-all-in-list [l-answer-set l-allowed-guesses board-size
                                    r-firstmove f-heuristic]
  (map (fn [answer]
         (println "Answers set")
         (clojure.pprint/pprint answer)
         (harness-run-one-trial
           (repeat board-size r-firstmove)
           answer
           l-allowed-guesses
           f-heuristic)
        l-answer-set)))


;; Example - run 100 random 2-dle tirals
;; (harness-run-random-trials
;;   l-answers l-allowed-guesses 2 r-firstmove
;;   harness-select-best-guess-global-min 100)
(defn harness-run-random-trials [l-answers l-allowed-guesses board-size r-firstmove f-heuristic n]
  (dotimes [_ n]
    (let [answer (repeatedly board-size #(harness-generate-random-word l-answers))
          _ (println "Answers set")
          _ (clojure.pprint/pprint answer)]
      (harness-run-one-trial
       (repeat board-size r-firstmove)
       answer
       l-allowed-guesses
       f-heuristic))))
        
  
