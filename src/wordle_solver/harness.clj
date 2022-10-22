(ns wordle-solver.harness)

(require '[wordle-solver.eval :as eval])
(require '[clojure.pprint])

(defn generate-random-word [l-answer-dict] 
    (rand-nth l-answer-dict))

;; Heuristic: Sum Entropy Based (Healy/Fetterman)
(defn select-best-guess-summed [l-results l-found-words]
  (first (filter (complement (set l-found-words)) 
                 (map first (eval/-sum-entropies
                             (map eval/just-words-and-entropy l-results))))))

;; Heuristic: Greedy - picks closest word to solve and goes for it.  "Vern's Gambit"

;; TODO - Note: picks MIN (first) of sorted, still doesn't randomly break ties
(defn select-best-guess-global-min [l-results l-found-words]
  (let [r (first (filter (complement (set l-found-words))
                         (sort (fn [a b] (< (-> a second second) (-> b second second)))
                               (reduce concat (map eval/just-words-and-entropy l-results)))))]
   (first r)))

(def initial-game-state 
  {
    :round 0
    :rounds-finished '()
    :found-words '()
    :guesses '()
  })

(defn update-game-state [game-state w-guess l-response-masks]
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


(defn log-results [answers-set game-state s-outputfilename]
  (spit s-outputfilename (apply str (interpose "," answers-set)) :append true)
  (spit s-outputfilename ";" :append true)
  (spit s-outputfilename (apply str (interpose "," (:rounds-finished game-state)))
        :append true)
  (spit s-outputfilename ";" :append true)
  (spit s-outputfilename (apply str (interpose "," (:guesses game-state))) :append true)
  (spit s-outputfilename \newline :append true)
  (clojure.pprint/pprint "DONE LOGGING"))


(defn -drop-indices [col indices]
  (let [s-indices (set indices)]
    (filter identity (map-indexed #(if ((complement s-indices) %1) %2) col))))

(defn -find-indices [col target]
  (keep-indexed #(when (= %2 target) %1) col))

;; EXAMPLE for doing 1-dle:
;; (run-one-trial
;;	(repeat 1 r-firstmove)
;;	(repeat 1 answer)
;;	data/dict-allowed-guesses
;;	select-best-guess-global-min
;;      "output.txt")
(defn run-one-trial [cached-results answers-set-oneboard l-allowed-guesses
                     f-heuristic s-outputfilename]
    (loop [game-state initial-game-state
           remaining-answers-set answers-set-oneboard
           l-results cached-results
           w-guess (f-heuristic l-results (:found-words game-state))]
            (let [
                  l-response-masks (map (partial eval/guess-and-answer-to-mask w-guess)
                                        remaining-answers-set)

                  new-game-state (update-game-state game-state w-guess l-response-masks)

                  _ (println "== NEW GAME STATE ==")
                  _ (clojure.pprint/pprint new-game-state)

                  ;; TODO - avoid sloppy use of 22222 
                  correct-indices (-find-indices l-response-masks '(2 2 2 2 2))
                  [l-new-results l-new-answer-lists] (eval/play-moves
                                                      l-allowed-guesses
                                                      w-guess l-response-masks l-results)
                  l-results (-drop-indices l-new-results correct-indices)
                  l-answer-lists (-drop-indices l-new-answer-lists correct-indices)
                  remaining-answers-set (-drop-indices remaining-answers-set correct-indices)]

              (if (zero? (count remaining-answers-set))
                ;; termination
                (log-results answers-set-oneboard new-game-state s-outputfilename)

                ;; else continue
                (let [w-next-guess (f-heuristic l-results (:found-words new-game-state))]
                  (recur new-game-state remaining-answers-set l-results w-next-guess))))))


;; Example - run 10 random 2-dle trials
;; (run-random-trials
;;   data/dict-answers data/dict-allowed-guesses 2 r-firstmove
;;   select-best-guess-global-min 10
;;   "output.txt")

(defn run-random-trials [l-answers l-allowed-guesses board-size
                         r-firstmove f-heuristic n s-outputfilename]
  (dotimes [_ n]
    (let [answer (repeatedly board-size #(generate-random-word l-answers))
          _ (println "Answers set")
          _ (clojure.pprint/pprint answer)]
      (run-one-trial
       (repeat board-size r-firstmove)
       answer
       l-allowed-guesses
       f-heuristic
       s-outputfilename))))

;; Note - can use this as either "run over all answers" (1-dle), or "run over this test set"
;; TODO NOTE - vern's gambit
;; Example - run over pre-generated answer sets for Octordle
;; (run-over-all-in-list
;;   l-my-octordle-boards data/dict-allowed-guesses 2 r-firstmove
;;   select-best-guess-summed
;;      "output.txt")
(defn run-over-all-in-list [l-answer-set l-allowed-guesses board-size
                                    r-firstmove f-heuristic s-outputfilename]
  (map (fn [answer]
         (println "Answers set")
         (clojure.pprint/pprint answer)
         (run-one-trial
           (repeat board-size r-firstmove)
           answer
           l-allowed-guesses
           f-heuristic
           s-outputfilename)
        l-answer-set)))

